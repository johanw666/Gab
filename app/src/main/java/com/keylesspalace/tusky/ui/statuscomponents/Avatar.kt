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

import androidx.annotation.FloatRange
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ui.preferences.LocalPreferences
import com.keylesspalace.tusky.ui.tuskyColors

@Composable
fun Avatar(
    url: String,
    staticUrl: String,
    isBot: Boolean,
    boostedAvatarUrl: String?,
    staticBoostedAvatarUrl: String?,
    onOpenProfile: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(48.dp)
            .semantics { hideFromAccessibility() }
    ) {
        val animateAvatars = LocalPreferences.current.animateAvatars
        val placeholder = painterResource(R.drawable.avatar_default)
        AsyncImage(
            model = if (animateAvatars) {
                url
            } else {
                staticUrl
            },
            contentDescription = null,
            placeholder = placeholder,
            error = placeholder,
            modifier = Modifier
                .run {
                    if (boostedAvatarUrl == null) {
                        fillMaxSize()
                    } else {
                        fillMaxSize(0.75f)
                    }
                }
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(PercentCornerSize(12.5f)))
                .run {
                    if (onOpenProfile != null) {
                        clickable { onOpenProfile() }
                    } else {
                        this
                    }
                }
        )
        if (boostedAvatarUrl != null) {
            val boostedAvatarPlaceholder = painterResource(R.drawable.avatar_default)
            AsyncImage(
                model = if (animateAvatars) {
                    boostedAvatarUrl
                } else {
                    staticBoostedAvatarUrl
                },
                contentDescription = null,
                placeholder = boostedAvatarPlaceholder,
                error = boostedAvatarPlaceholder,
                modifier = Modifier
                    .fillMaxSize(0.5f)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(PercentCornerSize(25f)))
            )
        } else if (isBot && LocalPreferences.current.showBotBadge) {
            Icon(
                painterResource(R.drawable.ic_bot_24dp),
                tint = tuskyColors.primaryTextColor,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(0.5f)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(PercentCornerSize(25f)))
                    .background(tuskyColors.windowBackground.copy(alpha = 0.75f))
                    .padding(2.dp)
            )
        }
    }
}

// copied from androidx.compose.foundation.shape.CornerSize.kt because it is not public 🙄
data class PercentCornerSize(
    @FloatRange(from = 0.0, to = 100.0) private val percent: Float
) : CornerSize, InspectableValue {
    init {
        if (percent !in 0.0..100.0) {
            throw IllegalArgumentException("The percent should be in the range of [0, 100]")
        }
    }

    override fun toPx(shapeSize: Size, density: Density) = shapeSize.minDimension * (percent / 100f)

    override fun toString(): String = "CornerSize(size = $percent%)"

    override val valueOverride: String
        get() = "$percent%"
}
