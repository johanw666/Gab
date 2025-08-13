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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import at.connyduck.sparkbutton.SparkButton
import at.connyduck.sparkbutton.helpers.Utils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.adapter.LoadStateFooterAdapter
import com.keylesspalace.tusky.appstore.ConversationsLoadingEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.databinding.FragmentTimelineBinding
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.isAnyLoading
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.updateRelativeTimePeriodically
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.ConfirmationBottomSheet.Companion.confirmFavourite
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConversationsFragment :
    SFragment<ConversationViewData>(R.layout.fragment_timeline),
    StatusActionListener<ConversationViewData>,
    ReselectableFragment,
    MenuProvider {

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var preferences: SharedPreferences

    private val viewModel: ConversationsViewModel by viewModels()

    private val binding by viewBinding(FragmentTimelineBinding::bind)

    private var adapter: ConversationPagingAdapter? = null

    private var buttonToAnimate: SparkButton? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount?.mediaPreviewEnabled != false,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = CardViewMode.NONE,
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )

        val adapter = ConversationPagingAdapter(statusDisplayOptions, this)
        this.adapter = adapter

        setupRecyclerView(adapter)

        binding.swipeRefreshLayout.setOnRefreshListener { refreshContent() }

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh != LoadState.Loading && loadState.source.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            binding.statusView.hide()
            binding.progressBar.hide()

            if (loadState.isAnyLoading()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    eventHub.dispatch(
                        ConversationsLoadingEvent(
                            accountManager.activeAccount?.accountId ?: ""
                        )
                    )
                }
            }

            if (adapter.itemCount == 0) {
                when (loadState.refresh) {
                    is LoadState.NotLoading -> {
                        if (loadState.append is LoadState.NotLoading && loadState.source.refresh is LoadState.NotLoading) {
                            binding.statusView.show()
                            binding.statusView.setup(
                                R.drawable.elephant_friend_empty,
                                R.string.message_empty,
                                null
                            )
                            binding.statusView.showHelp(R.string.help_empty_conversations)
                        }
                    }

                    is LoadState.Error -> {
                        binding.statusView.show()
                        binding.statusView.setup(
                            (loadState.refresh as LoadState.Error).error
                        ) { refreshContent() }
                    }

                    is LoadState.Loading -> {
                        binding.progressBar.show()
                    }
                }
            }
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val firstPos = (binding.recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                if (firstPos == 0 && positionStart == 0 && adapter.itemCount != itemCount) {
                    binding.recyclerView.post {
                        if (getView() != null) {
                            binding.recyclerView.scrollBy(0, Utils.dpToPx(requireContext(), -30))
                        }
                    }
                }
            }
        })

        // Workaround RecyclerView jumping to bottom on first load because of the load state footer
        // https://issuetracker.google.com/issues/184874613#comment7
        var firstLoad = true
        adapter.addOnPagesUpdatedListener {
            if (adapter.itemCount > 0 && firstLoad) {
                (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
                firstLoad = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.conversationFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        updateRelativeTimePeriodically(preferences, adapter)

        viewLifecycleOwner.lifecycleScope.launch {
            eventHub.events.collect { event ->
                if (event is PreferenceChangedEvent) {
                    onPreferenceChanged(adapter, event.preferenceKey)
                }
            }
        }
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        buttonToAnimate = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_conversations, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                refreshContent()
                true
            }

            else -> false
        }
    }

    private fun setupRecyclerView(adapter: ConversationPagingAdapter) {
        binding.recyclerView.ensureBottomPadding(fab = true)
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        binding.recyclerView.adapter =
            adapter.withLoadStateFooter(LoadStateFooterAdapter(adapter::retry))
    }

    private fun refreshContent() {
        adapter?.refresh()
    }

    override fun onReblog(viewData: ConversationViewData, reblog: Boolean, visibility: Status.Visibility?, button: SparkButton?) {
        // its impossible to reblog private messages
    }

    override fun onFavourite(viewData: ConversationViewData, favourite: Boolean, button: SparkButton?) {
        buttonToAnimate = button

        if (favourite) {
            confirmFavourite(preferences) {
                viewModel.favourite(true, viewData)
                buttonToAnimate?.playAnimation()
                buttonToAnimate?.isChecked = true
            }
        } else {
            viewModel.favourite(false, viewData)
            buttonToAnimate?.isChecked = false
        }
    }

    override fun onBookmark(viewData: ConversationViewData, bookmark: Boolean) {
        viewModel.bookmark(bookmark, viewData)
    }

    override val onMoreTranslate: ((translate: Boolean, viewData: ConversationViewData) -> Unit)? = null

    override fun onMore(viewData: ConversationViewData, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.inflate(R.menu.conversation_more)

        if (viewData.lastStatus.status.muted) {
            popup.menu.removeItem(R.id.status_mute_conversation)
        } else {
            popup.menu.removeItem(R.id.status_unmute_conversation)
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.status_mute_conversation -> viewModel.muteConversation(viewData)
                R.id.status_unmute_conversation -> viewModel.muteConversation(viewData)
                R.id.conversation_delete -> deleteConversation(viewData)
            }
            true
        }
        popup.show()
    }

    override fun onViewMedia(viewData: ConversationViewData, attachmentIndex: Int, view: View?) {
        viewMedia(
            attachmentIndex,
            AttachmentViewData.list(viewData.lastStatus),
            view
        )
    }

    override fun onViewThread(viewData: ConversationViewData) {
        viewThread(viewData.lastStatus.id, viewData.lastStatus.status.url)
    }

    override fun onOpenReblog(viewData: ConversationViewData) {
        // there are no reblogs in conversations
    }

    override fun onExpandedChange(viewData: ConversationViewData, expanded: Boolean) {
        viewModel.expandHiddenStatus(expanded, viewData)
    }

    override fun onContentHiddenChange(viewData: ConversationViewData, isShowing: Boolean) {
        viewModel.showContent(isShowing, viewData)
    }

    override fun onContentCollapsedChange(viewData: ConversationViewData, isCollapsed: Boolean) {
        viewModel.collapseLongStatus(isCollapsed, viewData)
    }

    override fun onViewAccount(id: String) {
        val intent = AccountActivity.getIntent(requireContext(), id)
        startActivity(intent)
    }

    override fun onViewTag(tag: String) {
        val intent = StatusListActivity.newHashtagIntent(requireContext(), tag)
        startActivity(intent)
    }

    override fun removeItem(viewData: ConversationViewData) {
        // not needed
    }

    override fun onReply(viewData: ConversationViewData) {
        reply(viewData.lastStatus.status)
    }

    override fun onVoteInPoll(viewData: ConversationViewData, choices: List<Int>) {
        viewModel.voteInPoll(choices, viewData)
    }

    override fun onShowPollResults(viewData: ConversationViewData) {
        viewModel.showPollResults(viewData)
    }

    override fun changeFilter(filtered: Boolean, viewData: ConversationViewData) { }

    override fun onReselect() {
        if (view != null) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    override fun onUntranslate(viewData: ConversationViewData) {
        // not needed
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

    private fun onPreferenceChanged(adapter: ConversationPagingAdapter, key: String) {
        when (key) {
            PrefKeys.MEDIA_PREVIEW_ENABLED -> {
                val enabled = accountManager.activeAccount!!.mediaPreviewEnabled
                val oldMediaPreviewEnabled = adapter.mediaPreviewEnabled
                if (enabled != oldMediaPreviewEnabled) {
                    adapter.mediaPreviewEnabled = enabled
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        }
    }

    companion object {
        fun newInstance() = ConversationsFragment()
    }
}
