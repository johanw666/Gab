/* Copyright 2021 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.conversation

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PollVoteEvent
import com.keylesspalace.tusky.appstore.StatusChangedEvent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.ConversationsDao
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewmodel.StatusActionsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val api: MastodonApi,
    database: AppDatabase,
    eventHub: EventHub,
    accountManager: AccountManager
) : StatusActionsViewModel(api, eventHub) {

    private val conversationsDao: ConversationsDao = database.conversationDao()

    val activeAccountFlow = accountManager.activeAccount(viewModelScope)
    private val accountId: Long = activeAccountFlow.value!!.id

    @OptIn(ExperimentalPagingApi::class)
    val conversationFlow = Pager(
        config = PagingConfig(
            pageSize = 30,
            initialLoadSize = 40
        ),
        remoteMediator = ConversationsRemoteMediator(api, database, this),
        pagingSourceFactory = {
            conversationsDao.conversationsForAccount(accountId)
        }
    )
        .flow
        .map { pagingData ->
            pagingData.map { conversation -> conversation.toViewData() }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            eventHub.events.collect { event ->
                when (event) {
                    is StatusChangedEvent -> updateStatus(event.status)
                    is PollVoteEvent -> handlePollVotedEvent(event.statusId, event.poll)
                }
            }
        }
    }

    fun showPollResults(statusViewData: StatusViewData.Concrete) = viewModelScope.launch {
        statusViewData.status.poll?.let { poll ->
            updateStatus(
                statusViewData.status.copy(poll = poll.copy(voted = true))
            )
        }
    }

    fun expandHiddenStatus(expanded: Boolean, statusViewData: StatusViewData.Concrete) {
        viewModelScope.launch {
            conversationsDao.setExpanded(accountId, statusViewData.id, expanded)
        }
    }

    fun collapseLongStatus(collapsed: Boolean, statusViewData: StatusViewData.Concrete) {
        viewModelScope.launch {
            conversationsDao.setContentCollapsed(accountId, statusViewData.id, collapsed)
        }
    }

    fun showContent(showing: Boolean, statusViewData: StatusViewData.Concrete) {
        viewModelScope.launch {
            conversationsDao.setContentShowing(accountId, statusViewData.id, showing)
        }
    }

    fun remove(conversation: ConversationViewData) {
        viewModelScope.launch {
            api.deleteConversation(conversationId = conversation.id).fold(
                onSuccess = {
                    conversationsDao.delete(
                        id = conversation.id,
                        tuskyAccountId = accountId
                    )
                },
                onFailure = { e ->
                    Log.w(TAG, "failed to delete conversation", e)
                }
            )
        }
    }

    private suspend fun updateStatus(status: Status) {
        conversationsDao.update(
            status = status,
            tuskyAccountId = accountId
        )
    }

    private suspend fun handlePollVotedEvent(statusId: String, poll: Poll) {
        conversationsDao.updatePoll(
            tuskyAccountId = accountId,
            statusId = statusId,
            poll = poll
        )
    }

    companion object {
        private const val TAG = "ConversationsViewModel"
    }
}
