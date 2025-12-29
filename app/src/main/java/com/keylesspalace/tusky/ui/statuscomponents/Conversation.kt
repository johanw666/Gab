/* Copyright 2025 Tusky Contributors
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

package com.keylesspalace.tusky.ui.statuscomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import at.connyduck.sparkbutton.compose.SparkButton
import at.connyduck.sparkbutton.compose.rememberSparkButtonState
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.conversation.ConversationViewData
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.ui.TuskyPreviewTheme
import com.keylesspalace.tusky.ui.preferences.LocalPreferences
import com.keylesspalace.tusky.ui.statuscomponents.fake.fakeConversation
import com.keylesspalace.tusky.ui.statuscomponents.fake.noopListener
import com.keylesspalace.tusky.ui.statuscomponents.text.emojify
import com.keylesspalace.tusky.ui.statuscomponents.text.toInlineContent
import com.keylesspalace.tusky.ui.tuskyColors
import com.keylesspalace.tusky.ui.tuskyGreenDark
import com.keylesspalace.tusky.ui.tuskyGreenLight
import com.keylesspalace.tusky.ui.tuskyOrange
import com.keylesspalace.tusky.ui.tuskyOrangeLight

@Composable
fun Conversation(
    viewData: ConversationViewData,
    listener: StatusActionListener,
    onDeleteConversation: (ConversationViewData) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusViewData = viewData.lastStatus
    val status = statusViewData.actionable

    val actions = statusActions(
        statusViewData = statusViewData,
        listener = listener
    )

    Column(
        modifier
            .clickable {
                listener.onViewThread(statusViewData)
            }
            .semantics(mergeDescendants = true) {
                customActions = actions
            }
    ) {
        val accounts = viewData.accounts

        val conversationName = when (accounts.size) {
            1 -> stringResource(R.string.conversation_1_recipients, accounts[0].username)
            2 -> stringResource(R.string.conversation_2_recipients, accounts[0].username, accounts[1].username)
            else -> stringResource(R.string.conversation_more_recipients, accounts[0].username, accounts[1].username, accounts.size - 2)
        }

        Text(
            text = conversationName,
            color = tuskyColors.primaryTextColor,
            fontWeight = FontWeight.Bold,
            style = LocalPreferences.current.statusTextStyles.medium,
            modifier = Modifier.padding(top = 8.dp, start = 14.dp, end = 14.dp)
        )
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 14.dp)
        ) {
            Box(
                modifier = Modifier.clearAndSetSemantics {
                    hideFromAccessibility()
                }
            ) {
                for (i in accounts.size - 1 downTo 0) {
                    val account = accounts[i]
                    Avatar(
                        url = account.avatar,
                        staticUrl = account.staticAvatar,
                        isBot = false,
                        boostedAvatarUrl = null,
                        staticBoostedAvatarUrl = null,
                        onOpenProfile = if (i == 0) {
                            { listener.onViewAccount(account.id) }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .padding(top = 18.dp * i)
                            .background(colorScheme.background, RoundedCornerShape(7.dp))
                            .padding(2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val username = stringResource(R.string.post_username_format, status.account.username)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics {
                                contentDescription = "${status.account.name} $username"
                            }
                            .clickable {
                                listener.onViewAccount(status.account.id)
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = status.account.name.emojify(status.account.emojis),
                            fontWeight = FontWeight.Bold,
                            color = tuskyColors.primaryTextColor,
                            style = LocalPreferences.current.statusTextStyles.medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            inlineContent = status.account.emojis.toInlineContent()
                        )
                        Text(
                            text = username,
                            color = tuskyColors.secondaryTextColor,
                            style = LocalPreferences.current.statusTextStyles.medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Timestamp(
                        date = status.createdAt,
                        isEdited = status.editedAt != null,
                        textColor = tuskyColors.secondaryTextColor,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                StatusContent(
                    statusViewData = statusViewData,
                    listener = listener
                )
            }
        }
        ConversationButtons(
            viewData = viewData,
            listener = listener,
            onDeleteConversation = onDeleteConversation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 72.dp, end = 8.dp, top = 2.dp)
        )
        HorizontalDivider()
    }
}

@Composable
private fun ConversationButtons(
    viewData: ConversationViewData,
    listener: StatusActionListener,
    onDeleteConversation: (ConversationViewData) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = viewData.lastStatus.actionable
    val description = buildString {
        if (status.favourited) {
            append(stringResource(R.string.description_post_favourited))
            if (status.bookmarked) {
                append(", ")
            }
        }
        if (status.bookmarked) {
            append(stringResource(R.string.description_post_bookmarked))
        }
    }

    ConstraintLayout(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = description
            },
    ) {
        var favourited by remember(status.favourited) { mutableStateOf(status.favourited) }
        var bookmarked by remember(status.bookmarked) { mutableStateOf(status.bookmarked) }

        val (replyButton, favButton, bookmarkButton, moreButton) = createRefs()

        // work around for https://issuetracker.google.com/issues/455056601
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
            createHorizontalChain(replyButton, favButton, bookmarkButton, moreButton, chainStyle = ChainStyle.SpreadInside)
        } else {
            createHorizontalChain(moreButton, bookmarkButton, favButton, replyButton, chainStyle = ChainStyle.SpreadInside)
        }
        IconButton(
            onClick = {
                listener.onReply(viewData.lastStatus)
            },
            modifier = Modifier.constrainAs(replyButton) {
                start.linkTo(parent.start)
                end.linkTo(favButton.start)
                centerVerticallyTo(parent)
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reply_24dp),
                tint = tuskyColors.tertiaryTextColor,
                contentDescription = null
            )
        }

        val sparkButtonState = rememberSparkButtonState()
        SparkButton(
            animateOnClick = false,
            onClick = {
                listener.onFavourite(viewData.lastStatus, !favourited, state = sparkButtonState)
            },
            state = sparkButtonState,
            primaryColor = tuskyOrange,
            secondaryColor = tuskyOrangeLight,
            modifier = Modifier.constrainAs(favButton) {
                start.linkTo(replyButton.end)
                end.linkTo(bookmarkButton.start)
                centerVerticallyTo(parent)
            }
        ) {
            if (favourited) {
                Icon(
                    painter = painterResource(R.drawable.ic_star_24dp_filled),
                    tint = tuskyColors.favoriteButtonActiveColor,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_star_24dp),
                    tint = tuskyColors.tertiaryTextColor,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        SparkButton(
            animateOnClick = !bookmarked,
            onClick = {
                bookmarked = !bookmarked
                listener.onBookmark(viewData.lastStatus, bookmarked)
            },
            primaryColor = tuskyGreenDark,
            secondaryColor = tuskyGreenLight,
            modifier = Modifier.constrainAs(bookmarkButton) {
                start.linkTo(favButton.end)
                end.linkTo(moreButton.start)
                centerVerticallyTo(parent)
            }
        ) {
            if (bookmarked) {
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark_24dp_filled),
                    tint = tuskyColors.bookmarkButtonActiveColor,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark_24dp),
                    tint = tuskyColors.tertiaryTextColor,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        var moreVisible by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.constrainAs(moreButton) {
                start.linkTo(bookmarkButton.end)
                end.linkTo(parent.end)
                centerVerticallyTo(parent)
            }
        ) {
            IconButton(
                onClick = {
                    moreVisible = !moreVisible
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz_24dp),
                    tint = tuskyColors.tertiaryTextColor,
                    contentDescription = null
                )
            }
            ConversationMoreMenu(
                viewData = viewData,
                expanded = moreVisible,
                onDismissRequest = {
                    moreVisible = !moreVisible
                },
                listener = listener,
                onDeleteConversation = onDeleteConversation
            )
        }
    }
}

@Composable
fun ConversationMoreMenu(
    viewData: ConversationViewData,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    listener: StatusActionListener,
    onDeleteConversation: (ConversationViewData) -> Unit
) {
    val status = viewData.lastStatus.status

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = {
                if (status.muted) {
                    Text(stringResource(R.string.action_unmute_conversation))
                } else {
                    Text(stringResource(R.string.action_mute_conversation))
                }
            },
            onClick = {
                onDismissRequest()
                listener.onMuteConversation(viewData.lastStatus, !status.muted)
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete_conversation)) },
            onClick = {
                onDismissRequest()
                onDeleteConversation(viewData)
            }
        )
    }
}

@PreviewLightDark
@Composable
fun ConversationPreview() {
    TuskyPreviewTheme {
        Conversation(
            viewData = fakeConversation(),
            listener = noopListener,
            onDeleteConversation = {},
            modifier = Modifier.background(colorScheme.background)
        )
    }
}
