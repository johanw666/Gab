package com.keylesspalace.tusky.appstore

import androidx.room.withTransaction
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Status
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Updates the database cache in response to events.
 * This is important for the home timeline and notifications to be up to date.
 */
class CacheUpdater @Inject constructor(
    private val appDatabase: AppDatabase,
    eventHub: EventHub,
    accountManager: AccountManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val timelineDao = appDatabase.timelineDao()
    private val statusDao = appDatabase.timelineStatusDao()
    private val accountDao = appDatabase.timelineAccountDao()
    private val notificationsDao = appDatabase.notificationsDao()

    init {
        scope.launch {
            eventHub.events.collect { event ->
                val tuskyAccount = accountManager.activeAccount ?: return@collect
                val tuskyAccountId = tuskyAccount.id
                when (event) {
                    is StatusChangedEvent -> updateStatus(tuskyAccount = tuskyAccount, status = event.status)
                    is UnfollowEvent -> timelineDao.removeStatusesAndReblogsByUser(tuskyAccountId, event.accountId)
                    is BlockEvent -> removeAllByUser(tuskyAccountId, event.accountId)
                    is MuteEvent -> removeAllByUser(tuskyAccountId, event.accountId)

                    is DomainMuteEvent -> {
                        timelineDao.deleteAllFromInstance(tuskyAccountId, event.instance)
                        notificationsDao.deleteAllFromInstance(tuskyAccountId, event.instance)
                    }

                    is StatusDeletedEvent -> {
                        timelineDao.deleteAllWithStatus(tuskyAccountId, event.statusId)
                        notificationsDao.deleteAllWithStatus(tuskyAccountId, event.statusId)
                    }

                    is PollVoteEvent -> statusDao.setVoted(tuskyAccountId, event.statusId, event.poll)
                    is PollShowResultsEvent -> statusDao.setShowResults(tuskyAccountId, event.statusId)
                }
            }
        }
    }

    private suspend fun updateStatus(tuskyAccount: AccountEntity, status: Status) {
        appDatabase.withTransaction {
            status.quote?.quotedStatus?.let { newQuotedStatus ->

                accountDao.insert(newQuotedStatus.account.toEntity(tuskyAccount.id))

                val oldQuotedStatus = statusDao.getStatus(tuskyAccount.id, newQuotedStatus.id)

                val quoteExpanded = oldQuotedStatus?.expanded ?: tuskyAccount.alwaysOpenSpoiler
                val quoteContentShowing = oldQuotedStatus?.contentShowing ?: tuskyAccount.alwaysShowSensitiveMedia
                val quoteContentCollapsed = oldQuotedStatus?.contentCollapsed ?: true
                val quoteFilterActive = oldQuotedStatus?.filterActive ?: true

                statusDao.insert(
                    newQuotedStatus.toEntity(
                        tuskyAccountId = tuskyAccount.id,
                        expanded = quoteExpanded,
                        contentShowing = quoteContentShowing,
                        contentCollapsed = quoteContentCollapsed,
                        filterActive = quoteFilterActive
                    )
                )
            }
            statusDao.update(tuskyAccountId = tuskyAccount.id, status = status)
        }
    }

    private suspend fun removeAllByUser(tuskyAccountId: Long, accountId: String) {
        timelineDao.removeAllByUser(tuskyAccountId, accountId)
        notificationsDao.removeAllByUser(tuskyAccountId, accountId)
    }

    fun stop() {
        this.scope.cancel()
    }
}
