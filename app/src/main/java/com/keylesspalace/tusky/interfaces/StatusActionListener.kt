/* Copyright 2017 Andrew Dawson
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
package com.keylesspalace.tusky.interfaces

import android.view.View
import at.connyduck.sparkbutton.SparkButton
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.viewdata.ConcreteViewData

interface StatusActionListener<in C : ConcreteViewData> : LinkListener {
    fun onReply(viewData: C)

    /**
     * Reblog the post represented by [viewData]
     * @param reblog true to reblog, false to undo a reblog
     * @param visibility The visibility to use for the reblog, if the user has already chosen it, null otherwise
     * @param button Optional button to animate
     */
    fun onReblog(viewData: C, reblog: Boolean, visibility: Status.Visibility?, button: SparkButton? = null)

    /**
     * Favourite the post represented by [viewData]
     * @param button Optional button to animate
     */
    fun onFavourite(viewData: C, favourite: Boolean, button: SparkButton? = null)
    fun onBookmark(viewData: C, bookmark: Boolean)
    fun onMore(viewData: C, view: View)
    fun onViewMedia(viewData: C, attachmentIndex: Int, view: View?)
    fun onViewThread(viewData: C)

    /**
     * Open reblog author for the status [viewData].
     */
    fun onOpenReblog(viewData: C)
    fun onExpandedChange(viewData: C, expanded: Boolean)
    fun onContentHiddenChange(viewData: C, isShowing: Boolean)

    /**
     * Called when the status [android.widget.ToggleButton] responsible for collapsing long
     * status content is interacted with.
     *
     * @param viewData    The status that is being toggled
     * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
     */
    fun onContentCollapsedChange(viewData: C, isCollapsed: Boolean)

    /**
     * called when the reblog count has been clicked
     */
    fun onShowReblogs(viewData: C) {}

    /**
     * called when the favourite count has been clicked
     */
    fun onShowFavs(viewData: C) {}

    fun onVoteInPoll(viewData: C, choices: List<Int>)

    fun onShowPollResults(viewData: C)

    fun onShowEdits(viewData: C) {}

    fun changeFilter(filtered: Boolean, viewData: C)

    fun onUntranslate(viewData: C)
}
