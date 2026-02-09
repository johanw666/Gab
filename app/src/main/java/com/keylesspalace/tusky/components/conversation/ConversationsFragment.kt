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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import at.connyduck.sparkbutton.compose.SparkButtonState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.appstore.ConversationsLoadingEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.ui.ErrorSnackbars
import com.keylesspalace.tusky.ui.MessageViewMode
import com.keylesspalace.tusky.ui.TuskyMessageView
import com.keylesspalace.tusky.ui.TuskyPullToRefreshBox
import com.keylesspalace.tusky.ui.TuskyTheme
import com.keylesspalace.tusky.ui.preferences.LocalAccount
import com.keylesspalace.tusky.ui.preferences.textStyle
import com.keylesspalace.tusky.ui.statuscomponents.Conversation
import com.keylesspalace.tusky.ui.statuscomponents.StatusPlaceholder
import com.keylesspalace.tusky.ui.tuskyColors
import com.keylesspalace.tusky.util.addIconAnnotations
import com.keylesspalace.tusky.util.iconInlineContent
import com.keylesspalace.tusky.util.isAnyLoading
import com.keylesspalace.tusky.util.isRefreshing
import com.keylesspalace.tusky.util.reply
import com.keylesspalace.tusky.util.report
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.viewMedia
import com.keylesspalace.tusky.util.viewThread
import com.keylesspalace.tusky.view.ConfirmationBottomSheet.Companion.confirmFavourite
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationsFragment :
    Fragment(),
    StatusActionListener,
    ReselectableFragment,
    MenuProvider {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var preferences: SharedPreferences

    private val viewModel: ConversationsViewModel by viewModels()

    private val jumpUp: MutableSharedFlow<Unit> = MutableSharedFlow()
    private val refresh: MutableSharedFlow<Unit> = MutableSharedFlow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ComposeView(inflater.context)
        view.setContent {
            TuskyTheme {
                ConversationContent()
            }
        }
        return view
    }

    @Composable
    private fun ConversationContent() {
        Box {
            val activeAccount = LocalAccount.current ?: return
            val listState = rememberLazyListState()
            val conversations = viewModel.conversationFlow.collectAsLazyPagingItems()

            LaunchedEffect(Unit) {
                jumpUp.collect {
                    listState.scrollToItem(0)
                }
            }

            LaunchedEffect(Unit) {
                refresh.collect {
                    conversations.refresh()
                }
            }

            LaunchedEffect(conversations.loadState.isAnyLoading()) {
                if (conversations.loadState.isAnyLoading()) {
                    eventHub.dispatch(
                        ConversationsLoadingEvent(activeAccount.accountId)
                    )
                }
            }

            if (conversations.itemCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 640.dp)
                        .align(Alignment.Center)
                        .background(colorScheme.background)
                ) {
                    val error = (conversations.loadState.source.refresh as? LoadState.Error)?.error ?: (conversations.loadState.mediator?.refresh as? LoadState.Error)?.error
                    if (conversations.loadState.isRefreshing()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (error != null) {
                        TuskyMessageView(
                            onRetry = conversations::retry,
                            error = error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = addIconAnnotations(stringResource(R.string.help_empty_conversations)),
                                style = textStyle(16.sp),
                                color = tuskyColors.primaryTextColor,
                                inlineContent = iconInlineContent(tuskyColors.primaryTextColor),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .background(colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            TuskyMessageView(
                                modifier = Modifier.weight(1f),
                                onRetry = null,
                                message = stringResource(R.string.message_empty),
                                mode = MessageViewMode.EMPTY,
                            )
                        }
                    }
                }
            } else {
                ConversationTopLoadedEffect(listState, conversations)

                var refreshing by remember { mutableStateOf(false) }

                if (refreshing &&
                    conversations.loadState.refresh !is LoadState.Loading &&
                    conversations.loadState.source.refresh !is LoadState.Loading &&
                    conversations.loadState.mediator?.refresh !is LoadState.Loading
                ) {
                    refreshing = false
                }

                TuskyPullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        conversations.refresh()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .align(Alignment.Center)
                            .background(colorScheme.background)
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(rememberNestedScrollInteropConnection()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(
                            count = conversations.itemCount,
                            key = conversations.itemKey { it.id }
                        ) { index ->
                            when (val viewData = conversations[index]) {
                                null -> {
                                    StatusPlaceholder(
                                        modifier = Modifier.widthIn(max = 640.dp)
                                    )
                                }
                                else -> {
                                    Conversation(
                                        viewData = viewData,
                                        listener = this@ConversationsFragment,
                                        onDeleteConversation = ::deleteConversation,
                                        modifier = Modifier.widthIn(max = 640.dp)
                                    )
                                }
                            }
                        }

                        item(key = "bottomSpacer") {
                            Column {
                                Spacer(
                                    modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars)
                                )
                                Spacer(
                                    modifier = Modifier.height(dimensionResource(R.dimen.recyclerview_bottom_padding_actionbutton))
                                )
                            }
                        }
                    }
                }
            }
            ErrorSnackbars(
                viewModel = viewModel,
                legacyFallback = true,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
            )
        }
    }

    /** move the timeline down slightly when new posts at the top have been loaded **/
    @Composable
    private fun ConversationTopLoadedEffect(listState: LazyListState, conversations: LazyPagingItems<ConversationViewData>) {
        val jumpUpDistance = with(LocalDensity.current) { -32.dp.toPx() }

        var previousTopId: String? by remember { mutableStateOf(null) }

        val firstVisibleItemIndex = listState.firstVisibleItemIndex
        val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset

        // when the list is scrolled all the way to the top, save the id of the topmost item
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            previousTopId = conversations.peek(0)?.id
        }

        // when new items are inserted and the first visible item is the one that was previously on top,
        // jump up a bit to reveal the newly loaded items
        LaunchedEffect(conversations.itemCount, conversations.peek(0)) {
            if (firstVisibleItemScrollOffset == 0 && firstVisibleItemIndex == 0 && conversations.peek(0)?.id == previousTopId) {
                listState.scrollBy(jumpUpDistance)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.startComposing.collect { composeOptions ->
                val intent = ComposeActivity.newIntent(requireContext(), composeOptions)
                requireContext().startActivityWithSlideInAnimation(intent)
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_conversations, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                lifecycleScope.launch {
                    refresh.emit(Unit)
                }
                true
            }
            else -> false
        }
    }

    override fun onReselect() {
        lifecycleScope.launch {
            jumpUp.emit(Unit)
        }
    }

    override fun onReblog(viewData: StatusViewData.Concrete, reblog: Boolean, visibility: Status.Visibility?, state: SparkButtonState?) {
        // its impossible to reblog private messages
    }

    override fun onFavourite(
        viewData: StatusViewData.Concrete,
        favourite: Boolean,
        state: SparkButtonState?
    ) {
        if (favourite) {
            confirmFavourite(preferences) {
                viewModel.favorite(viewData.id, true)
                state?.animate()
            }
        } else {
            viewModel.favorite(viewData.id, false)
        }
    }

    override fun onBookmark(viewData: StatusViewData.Concrete, bookmark: Boolean) {
        viewModel.bookmark(viewData.id, bookmark)
    }

    override fun onExpandedChange(viewData: StatusViewData.Concrete, expanded: Boolean) {
        viewModel.expandHiddenStatus(expanded, viewData)
    }

    override fun onContentHiddenChange(viewData: StatusViewData.Concrete, isShowing: Boolean) {
        viewModel.showContent(isShowing, viewData)
    }

    override fun onContentCollapsedChange(viewData: StatusViewData.Concrete, isCollapsed: Boolean) {
        viewModel.collapseLongStatus(isCollapsed, viewData)
    }

    override fun onVoteInPoll(viewData: StatusViewData.Concrete, pollId: String, choices: List<Int>) {
        viewModel.voteInPoll(viewData.id, pollId, choices)
    }

    override fun onShowPollResults(viewData: StatusViewData.Concrete) {
        viewModel.showPollResults(viewData)
    }

    override fun changeFilter(viewData: StatusViewData.Concrete, filtered: Boolean) { }

    override fun onTranslate(viewData: StatusViewData.Concrete) {
        // direct messages can't be translated
    }

    override fun onUntranslate(viewData: StatusViewData.Concrete) {
        // direct messages can't be translated
    }

    override fun onBlock(accountId: String) {
        viewModel.block(accountId)
    }

    override fun onMute(accountId: String, hideNotifications: Boolean, duration: Int?) {
        viewModel.mute(accountId, hideNotifications, duration)
    }

    override fun onMuteConversation(viewData: StatusViewData.Concrete, mute: Boolean) {
        viewModel.muteConversation(viewData.id, mute)
    }

    override fun onDelete(viewData: StatusViewData.Concrete) {
        // not needed
    }

    override fun onRedraft(viewData: StatusViewData.Concrete) {
        // not needed
    }

    override fun onPin(viewData: StatusViewData.Concrete, pin: Boolean) {
        // can't pin a direct message
    }

    override fun onViewMedia(viewData: StatusViewData.Concrete, attachmentIndex: Int) {
        requireContext().viewMedia(
            attachmentIndex,
            AttachmentViewData.list(viewData)
        )
    }

    override fun onViewThread(viewData: StatusViewData.Concrete) {
        requireContext().viewThread(viewData)
    }

    override fun onEdit(viewData: StatusViewData.Concrete) {
        // not needed
    }

    override fun onReply(viewData: StatusViewData.Concrete) {
        viewModel.activeAccountFlow.value?.let { activeAccount ->
            requireContext().reply(viewData, activeAccount)
        }
    }

    override fun onReport(viewData: StatusViewData.Concrete) {
        requireContext().report(viewData)
    }

    override fun onViewAccount(accountId: String) {
        val intent = AccountActivity.newIntent(requireContext(), accountId)
        startActivity(intent)
    }

    override fun onViewUrl(url: String) {
        (requireActivity() as BottomSheetActivity).viewUrl(url)
    }

    override fun onViewTag(tag: String) {
        val intent = StatusListActivity.newHashtagIntent(requireContext(), tag)
        startActivity(intent)
    }

    override fun onShowQuote(viewData: StatusViewData.Concrete) {
        // no quotes in conversations
    }

    override fun removeQuote(viewData: StatusViewData.Concrete) {
        // no quotes in conversations
    }

    private fun deleteConversation(conversation: ConversationViewData) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.dialog_delete_conversation_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.remove(conversation)
            }
            .show()
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}
