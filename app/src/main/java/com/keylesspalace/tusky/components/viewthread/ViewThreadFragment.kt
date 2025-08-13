/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.viewthread

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.CheckResult
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.sparkbutton.SparkButton
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Companion.newIntent
import com.keylesspalace.tusky.components.viewthread.edits.ViewEditsFragment
import com.keylesspalace.tusky.databinding.FragmentViewThreadBinding
import com.keylesspalace.tusky.db.DraftsAlert
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.updateRelativeTimePeriodically
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.ConfirmationBottomSheet.Companion.confirmFavourite
import com.keylesspalace.tusky.view.ConfirmationBottomSheet.Companion.confirmReblog
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ViewThreadFragment :
    SFragment<StatusViewData.Concrete>(R.layout.fragment_view_thread),
    OnRefreshListener,
    StatusActionListener<StatusViewData.Concrete>,
    MenuProvider {

    @Inject
    lateinit var preferences: SharedPreferences

    @Inject
    lateinit var draftsAlert: DraftsAlert

    private val viewModel: ViewThreadViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<ViewThreadViewModel.Factory> { factory ->
                factory.create(
                    threadId = thisThreadsStatusId,
                )
            }
        }
    )

    private val binding by viewBinding(FragmentViewThreadBinding::bind)

    private var adapter: ThreadAdapter? = null
    private lateinit var thisThreadsStatusId: String

    private var alwaysShowSensitiveMedia = false
    private var alwaysOpenSpoiler = false

    private var buttonToAnimate: SparkButton? = null

    /**
     * State of the "reveal" menu item that shows/hides content that is behind a content
     * warning. Setting this invalidates the menu to redraw the menu item.
     */
    private var revealButtonState = RevealButtonState.NO_BUTTON
        set(value) {
            field = value
            requireActivity().invalidateMenu()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        thisThreadsStatusId = requireArguments().getString(ID_EXTRA)!!
    }

    private fun createAdapter(): ThreadAdapter {
        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = if (preferences.getBoolean(PrefKeys.SHOW_CARDS_IN_TIMELINES, false)) {
                CardViewMode.INDENTED
            } else {
                CardViewMode.NONE
            },
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )
        return ThreadAdapter(statusDisplayOptions, this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val adapter = createAdapter()
        this.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener(this)

        binding.recyclerView.ensureBottomPadding()

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(
                binding.recyclerView,
                this
            ) { index -> adapter.currentList.getOrNull(index) }
        )
        val divider = DividerItemDecoration(context, LinearLayout.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)
        binding.recyclerView.addItemDecoration(ConversationLineItemDecoration(requireContext()))
        alwaysShowSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia
        alwaysOpenSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler

        binding.recyclerView.adapter = adapter

        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        var initialProgressBar = getProgressBarJob(binding.initialProgressBar, 500)
        var threadProgressBar = getProgressBarJob(binding.threadProgressBar, 500)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    is ThreadUiState.Loading -> {
                        revealButtonState = RevealButtonState.NO_BUTTON

                        binding.recyclerView.hide()
                        binding.statusView.hide()

                        initialProgressBar = getProgressBarJob(binding.initialProgressBar, 500)
                        initialProgressBar.start()
                    }

                    is ThreadUiState.LoadingThread -> {
                        if (uiState.statusViewDatum == null) {
                            // no detailed statuses available, e.g. because author is blocked
                            activity?.finish()
                            return@collect
                        }

                        initialProgressBar.cancel()
                        threadProgressBar = getProgressBarJob(binding.threadProgressBar, 500)
                        threadProgressBar.start()

                        if (viewModel.isInitialLoad) {
                            adapter.submitList(listOf(uiState.statusViewDatum))

                            // else this "submit one and then all on success below" will always center on the one
                        }

                        revealButtonState = uiState.revealButton
                        binding.swipeRefreshLayout.isRefreshing = false

                        binding.recyclerView.show()
                        binding.statusView.hide()
                    }

                    is ThreadUiState.Error -> {
                        Log.w(TAG, "failed to load status", uiState.throwable)
                        initialProgressBar.cancel()
                        threadProgressBar.cancel()

                        revealButtonState = RevealButtonState.NO_BUTTON
                        binding.swipeRefreshLayout.isRefreshing = false

                        binding.recyclerView.hide()
                        binding.statusView.show()

                        binding.statusView.setup(
                            uiState.throwable
                        ) { viewModel.retry() }
                    }

                    is ThreadUiState.Success -> {
                        if (uiState.statusViewData.none { viewData -> viewData.isDetailed }) {
                            // no detailed statuses available, e.g. because author is blocked
                            activity?.finish()
                            return@collect
                        }

                        threadProgressBar.cancel()

                        adapter.submitList(uiState.statusViewData) {
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && viewModel.isInitialLoad) {
                                viewModel.isInitialLoad = false

                                // Ensure the top of the status is visible
                                (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                                    uiState.detailedStatusPosition,
                                    0
                                )
                            }
                        }

                        revealButtonState = uiState.revealButton
                        binding.swipeRefreshLayout.isRefreshing = false

                        binding.recyclerView.show()
                        binding.statusView.hide()
                    }

                    is ThreadUiState.Refreshing -> {
                        threadProgressBar.cancel()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errors.collect { throwable ->
                Log.w(TAG, "failed to load status context", throwable)
                Snackbar.make(binding.root, R.string.error_generic, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.action_retry) {
                        viewModel.retry()
                    }
                    .show()
            }
        }

        updateRelativeTimePeriodically(preferences, adapter)

        draftsAlert.observeInContext(requireActivity(), true)
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        buttonToAnimate = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_view_thread, menu)
        val actionReveal = menu.findItem(R.id.action_reveal)
        actionReveal.isVisible = revealButtonState != RevealButtonState.NO_BUTTON
        actionReveal.setIcon(
            when (revealButtonState) {
                RevealButtonState.REVEAL -> R.drawable.ic_visibility_24dp
                else -> R.drawable.ic_visibility_off_24dp
            }
        )
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_reveal -> {
                viewModel.toggleRevealButton()
                true
            }

            R.id.action_open_in_web -> {
                context?.openLink(requireArguments().getString(URL_EXTRA)!!)
                true
            }

            R.id.action_refresh -> {
                onRefresh()
                true
            }

            else -> false
        }
    }

    /**
     * Create a job to implement a delayed-visible progress bar.
     *
     * Delaying the visibility of the progress bar can improve user perception of UI speed because
     * fewer UI elements are appearing and disappearing.
     *
     * When started the job will wait `delayMs` then show `view`. If the job is cancelled at
     * any time `view` is hidden.
     */
    @CheckResult
    private fun getProgressBarJob(view: View, delayMs: Long) =
        viewLifecycleOwner.lifecycleScope.launch(
            start = CoroutineStart.LAZY
        ) {
            try {
                delay(delayMs)
                view.show()
                awaitCancellation()
            } finally {
                view.hide()
            }
        }

    override fun onRefresh() {
        viewModel.refresh()
    }

    override fun onReply(viewData: StatusViewData.Concrete) {
        super.reply(viewData.status)
    }

    override fun onReblog(viewData: StatusViewData.Concrete, reblog: Boolean, visibility: Status.Visibility?, button: SparkButton?) {
        buttonToAnimate = button

        if (reblog && visibility == null) {
            confirmReblog(preferences) { visibility ->
                viewModel.reblog(true, viewData, visibility)
                buttonToAnimate?.playAnimation()
                buttonToAnimate?.isChecked = true
            }
        } else {
            viewModel.reblog(reblog, viewData, visibility ?: Status.Visibility.PUBLIC)
            if (reblog) {
                buttonToAnimate?.playAnimation()
            }
            buttonToAnimate?.isChecked = false
        }
    }

    override val onMoreTranslate: ((translate: Boolean, viewData: StatusViewData.Concrete) -> Unit) =
        { translate: Boolean, viewData: StatusViewData.Concrete ->
            if (translate) {
                onTranslate(viewData)
            } else {
                onUntranslate(viewData)
            }
        }

    private fun onTranslate(viewData: StatusViewData.Concrete) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.translate(viewData)
                .onFailure {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.ui_error_translate, it.message),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
        }
    }

    override fun onUntranslate(viewData: StatusViewData.Concrete) {
        viewModel.untranslate(viewData)
    }

    override fun onFavourite(viewData: StatusViewData.Concrete, favourite: Boolean, button: SparkButton?) {
        buttonToAnimate = button

        if (favourite) {
            confirmFavourite(preferences) {
                viewModel.favorite(true, viewData)
                buttonToAnimate?.playAnimation()
                buttonToAnimate?.isChecked = true
            }
        } else {
            viewModel.favorite(false, viewData)
            buttonToAnimate?.isChecked = false
        }
    }

    override fun onBookmark(viewData: StatusViewData.Concrete, bookmark: Boolean) {
        viewModel.bookmark(bookmark, viewData)
    }

    override fun onMore(viewData: StatusViewData.Concrete, view: View) {
        super.more(viewData, view)
    }

    override fun onViewMedia(viewData: StatusViewData.Concrete, attachmentIndex: Int, view: View?) {
        super.viewMedia(
            attachmentIndex,
            AttachmentViewData.list(viewData, alwaysShowSensitiveMedia),
            view
        )
    }

    override fun onViewThread(viewData: StatusViewData.Concrete) {
        if (thisThreadsStatusId == viewData.id) {
            // If already viewing this thread, don't reopen it.
            return
        }
        super.viewThread(viewData.actionableId, viewData.actionable.url)
    }

    override fun onViewUrl(url: String) {
        val status: StatusViewData.Concrete? = viewModel.detailedStatus()
        if (status != null && status.status.url == url) {
            // already viewing the status with this url
            // probably just a preview federated and the user is clicking again to view more -> open the browser
            // this can happen with some friendica statuses
            requireContext().openLink(url)
            return
        }
        super.onViewUrl(url)
    }

    override fun onOpenReblog(viewData: StatusViewData.Concrete) {
        // there are no reblogs in threads
    }

    override fun onExpandedChange(viewData: StatusViewData.Concrete, expanded: Boolean) {
        viewModel.changeExpanded(expanded, viewData)
    }

    override fun onContentHiddenChange(viewData: StatusViewData.Concrete, isShowing: Boolean) {
        viewModel.changeContentShowing(isShowing, viewData)
    }

    override fun onShowReblogs(viewData: StatusViewData.Concrete) {
        val intent = newIntent(requireContext(), AccountListActivity.Type.REBLOGGED, viewData.id)
        requireActivity().startActivityWithSlideInAnimation(intent)
    }

    override fun onShowFavs(viewData: StatusViewData.Concrete) {
        val intent = newIntent(requireContext(), AccountListActivity.Type.FAVOURITED, viewData.id)
        requireActivity().startActivityWithSlideInAnimation(intent)
    }

    override fun onContentCollapsedChange(viewData: StatusViewData.Concrete, isCollapsed: Boolean) {
        viewModel.changeContentCollapsed(isCollapsed, viewData)
    }

    override fun onViewTag(tag: String) {
        super.viewTag(tag)
    }

    override fun onViewAccount(id: String) {
        super.viewAccount(id)
    }

    public override fun removeItem(viewData: StatusViewData.Concrete) {
        if (viewData.isDetailed) {
            // the main status we are viewing is being removed, finish the activity
            activity?.finish()
            return
        }
        viewModel.removeStatus(viewData)
    }

    override fun onVoteInPoll(viewData: StatusViewData.Concrete, choices: List<Int>) {
        viewModel.voteInPoll(choices, viewData)
    }

    override fun onShowPollResults(viewData: StatusViewData.Concrete) {
        viewModel.showPollResults(viewData)
    }

    override fun onShowEdits(viewData: StatusViewData.Concrete) {
        val viewEditsFragment = ViewEditsFragment.newInstance(viewData.actionableId)

        parentFragmentManager.commit {
            setCustomAnimations(
                R.anim.activity_open_enter,
                R.anim.activity_open_exit,
                R.anim.activity_close_enter,
                R.anim.activity_close_exit
            )
            replace(R.id.fragment_container, viewEditsFragment, "ViewEditsFragment_$id")
            addToBackStack(null)
        }
    }

    override fun changeFilter(filtered: Boolean, viewData: StatusViewData.Concrete) {
        viewModel.changeFilter(filtered, viewData)
    }

    companion object {
        private const val TAG = "ViewThreadFragment"

        private const val ID_EXTRA = "id"
        private const val URL_EXTRA = "url"

        fun newInstance(id: String, url: String): ViewThreadFragment {
            val arguments = Bundle(2)
            val fragment = ViewThreadFragment()
            arguments.putString(ID_EXTRA, id)
            arguments.putString(URL_EXTRA, url)
            fragment.arguments = arguments
            return fragment
        }
    }
}
