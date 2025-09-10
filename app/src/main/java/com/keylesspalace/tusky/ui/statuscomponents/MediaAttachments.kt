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

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.ui.TuskyPreviewTheme
import com.keylesspalace.tusky.ui.tuskyColors
import com.keylesspalace.tusky.util.BlurHashDecoder
import com.keylesspalace.tusky.util.getFormattedDescription
import com.keylesspalace.tusky.util.hasPreviewableAttachment
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun MediaAttachments(
    attachments: List<Attachment>,
    onOpenAttachment: (Int) -> Unit,
    onMediaHiddenChanged: () -> Unit,
    sensitive: Boolean,
    showMedia: Boolean,
    downloadPreviews: Boolean,
    showBlurhash: Boolean,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) {
        return
    }

    if (downloadPreviews && attachments.hasPreviewableAttachment()) {
        AttachmentPreviewGrid(
            attachments = attachments,
            onOpenAttachment = onOpenAttachment,
            onMediaHiddenChanged = onMediaHiddenChanged,
            sensitive = sensitive,
            showMedia = showMedia,
            showBlurhash = showBlurhash,
            modifier = modifier
        )
    } else {
        AttachmentDescriptionList(attachments, modifier, onOpenAttachment)
    }
}

@Composable
private fun AttachmentPreviewGrid(
    attachments: List<Attachment>,
    onOpenAttachment: (Int) -> Unit,
    onMediaHiddenChanged: () -> Unit,
    sensitive: Boolean,
    showMedia: Boolean,
    showBlurhash: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
    ) {
        if (attachments.size == 1) {
            val attachment = attachments[0]

            MediaItem(
                attachment = attachment,
                onOpenAttachment = { onOpenAttachment(0) },
                showMedia = showMedia,
                showBlurhash = showBlurhash,
                modifier = Modifier
                    .aspectRatio(attachment.limitedAspectRatio())
            )
        } else if (attachments.size == 2) {
            val aspect1 = attachments[0].limitedAspectRatio()
            val aspect2 = attachments[1].limitedAspectRatio()
            if (aspect1 * aspect2 > 1) {
                // show above each other
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MediaItem(
                        attachment = attachments[0],
                        onOpenAttachment = { onOpenAttachment(0) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier.aspectRatio(aspect1)
                    )
                    MediaItem(
                        attachment = attachments[1],
                        onOpenAttachment = { onOpenAttachment(1) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier.aspectRatio(aspect2)
                    )
                }
            } else {
                // show next to each other
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(IntrinsicSize.Max)
                ) {
                    MediaItem(
                        attachment = attachments[0],
                        onOpenAttachment = { onOpenAttachment(0) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspect1.coerceAtLeast(0.6f))
                    )
                    MediaItem(
                        attachment = attachments[1],
                        onOpenAttachment = { onOpenAttachment(1) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else if (attachments.size == 3) {
            val aspect0 = attachments[0].limitedAspectRatio()
            val aspect1 = attachments[1].limitedAspectRatio()
            if (aspect0 >= 1) {
                // |     1     |
                // -------------
                // |  2  |  3  |

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MediaItem(
                        attachment = attachments[0],
                        onOpenAttachment = { onOpenAttachment(0) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier.aspectRatio(aspect0)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // first image defines the aspect for both
                        val aspectRatio = aspect1.coerceIn(0.6f, 1.6f)
                        MediaItem(
                            attachment = attachments[1],
                            onOpenAttachment = { onOpenAttachment(1) },
                            showMedia = showMedia,
                            showBlurhash = showBlurhash,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(aspectRatio)
                        )
                        MediaItem(
                            attachment = attachments[2],
                            onOpenAttachment = { onOpenAttachment(2) },
                            showMedia = showMedia,
                            showBlurhash = showBlurhash,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(aspectRatio)
                        )
                    }
                }
            } else {
                // |     |  2  |
                // |  1  |-----|
                // |     |  3  |

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(IntrinsicSize.Max)
                ) {
                    MediaItem(
                        attachment = attachments[0],
                        onOpenAttachment = { onOpenAttachment(0) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspect0)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        MediaItem(
                            attachment = attachments[1],
                            onOpenAttachment = { onOpenAttachment(1) },
                            showMedia = showMedia,
                            showBlurhash = showBlurhash,
                            modifier = Modifier.weight(1f)
                        )
                        MediaItem(
                            attachment = attachments[2],
                            onOpenAttachment = { onOpenAttachment(2) },
                            showMedia = showMedia,
                            showBlurhash = showBlurhash,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else {
            // 4 or more attachments
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(IntrinsicSize.Max)
                ) {
                    // first image defines the aspect for both
                    val aspectRatio = attachments[0].limitedAspectRatio().coerceIn(0.75f, 1.6f)
                    MediaItem(
                        attachment = attachments[0],
                        onOpenAttachment = { onOpenAttachment(0) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                    )
                    MediaItem(
                        attachment = attachments[1],
                        onOpenAttachment = { onOpenAttachment(1) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(IntrinsicSize.Max)
                ) {
                    // first image defines the aspect for both
                    val aspectRatio = attachments[2].limitedAspectRatio().coerceIn(0.75f, 1.6f)
                    MediaItem(
                        attachment = attachments[2],
                        onOpenAttachment = { onOpenAttachment(2) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                    )
                    MediaItem(
                        attachment = attachments[3],
                        onOpenAttachment = { onOpenAttachment(3) },
                        showMedia = showMedia,
                        showBlurhash = showBlurhash,
                        additionalCount = attachments.size - 4,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                    )
                }
            }
        }
        if (!showMedia) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        onMediaHiddenChanged()
                    }
            ) {
                Text(
                    text = if (sensitive) {
                        stringResource(R.string.post_sensitive_media_title)
                    } else {
                        stringResource(R.string.post_media_hidden_title)
                    },
                    fontSize = 16.sp,
                    color = tuskyColors.secondaryTextColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            color = colorScheme.background.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else {
            Icon(
                painterResource(R.drawable.ic_visibility_24dp),
                contentDescription = stringResource(R.string.action_hide_media),
                tint = tuskyColors.secondaryTextColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .background(
                        color = colorScheme.background.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable {
                        onMediaHiddenChanged()
                    }
                    .padding(5.dp)
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MediaItem(
    attachment: Attachment,
    onOpenAttachment: () -> Unit,
    showMedia: Boolean,
    showBlurhash: Boolean,
    modifier: Modifier = Modifier,
    additionalCount: Int = 0,
) {
    Box(
        modifier = modifier
            .clickable {
                onOpenAttachment()
            }
    ) {
        val backgroundAccent = tuskyColors.backgroundAccent
        val res = LocalContext.current.resources

        if (attachment.previewUrl != null) {
            val placeholderDrawable: Drawable = remember(attachment) {
                if (showBlurhash && attachment.blurhash != null) {
                    // Render blurhashes in similar aspect ratio as the preview image.
                    // Otherwise they might get cropped differently and look very differently.
                    val aspectRatio = attachment.aspectRatio()
                    val height = sqrt(128 / aspectRatio).roundToInt().coerceIn(16, 256)
                    val width = (height * aspectRatio).roundToInt().coerceIn(16, 256)
                    BlurHashDecoder.decode(attachment.blurhash, width, height, 1f)?.toDrawable(res)
                        ?: backgroundAccent.toArgb().toDrawable()
                } else {
                    backgroundAccent.toArgb().toDrawable()
                }
            }

            GlideImage(
                model = if (showMedia) attachment.previewUrl else null,
                contentDescription = attachment.description ?: stringResource(R.string.description_post_media_no_description_placeholder),
                contentScale = ContentScale.Crop,
                alignment = attachment.meta?.focus.asAlignment(),
                modifier = Modifier.fillMaxSize(),
                requestBuilderTransform = { requestBuilder ->
                    requestBuilder.placeholder(placeholderDrawable)
                }
            )
        } else {
            if (showMedia) {
                Icon(
                    painter = if (attachment.type == Attachment.Type.AUDIO) {
                        painterResource(R.drawable.ic_music_box_24dp)
                    } else {
                        painterResource(R.drawable.ic_broken_image_24dp)
                    },
                    tint = tuskyColors.tertiaryTextColor,
                    contentDescription = attachment.description ?: stringResource(R.string.description_post_media_no_description_placeholder),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundAccent)
                        .padding(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundAccent)
                )
            }
        }

        if (showMedia) {
            if (additionalCount > 0) {
                Box(
                    modifier
                        .matchParentSize()
                        .background(colorScheme.background.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "+${additionalCount + 1}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Medium,
                        color = tuskyColors.primaryTextColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                if (!attachment.description.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.post_media_alt),
                        color = tuskyColors.secondaryTextColor,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(3.dp)
                            .background(
                                color = colorScheme.background.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(7.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                            .align(Alignment.BottomEnd)
                    )
                }

                if (attachment.type == Attachment.Type.VIDEO || attachment.type == Attachment.Type.GIFV) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow_24dp),
                        tint = colorScheme.primary,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .border(
                                width = 4.dp,
                                color = colorScheme.primary,
                                shape = CircleShape
                            )
                            .padding(2.dp)
                            .background(colorScheme.surface.copy(alpha = 0.8f), shape = CircleShape)
                            .padding(6.dp)
                            .size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentDescriptionList(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier,
    onOpenAttachment: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        attachments.forEachIndexed { index, attachment ->

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onOpenAttachment(index) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val mediaIcon = when (attachment.type) {
                    Attachment.Type.IMAGE -> R.drawable.ic_image_24dp
                    Attachment.Type.GIFV -> R.drawable.ic_gif_box_24dp
                    Attachment.Type.VIDEO -> R.drawable.ic_slideshow_24dp
                    Attachment.Type.AUDIO -> R.drawable.ic_music_box_24dp
                    else -> R.drawable.ic_attach_file_24dp
                }

                Icon(
                    painter = painterResource(mediaIcon),
                    tint = tuskyColors.primaryTextColor,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = attachment.getFormattedDescription(LocalContext.current),
                    color = tuskyColors.primaryTextColor,
                )
            }
        }
    }
}

private fun Attachment.Focus?.asAlignment(): Alignment {
    return if (this == null || this.x == null || this.y == null) {
        Alignment.Center
    } else {
        BiasAlignment(this.x, this.y)
    }
}

private fun Attachment.aspectRatio(): Float {
    val size = (meta?.small ?: meta?.original) ?: return 1.7778f
    if (size.aspect > 0) return size.aspect
    if (size.width == 0 || size.height == 0) return 1.7778f
    return (size.width.toFloat() / size.height)
}

private fun Attachment.limitedAspectRatio(): Float {
    return aspectRatio().coerceIn(0.5f, 2.0f)
}

@Preview(name = "Light", heightDp = 1500)
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL, heightDp = 1500)
@Composable
fun MediaAttachmentsPreview() {
    val fourAttachments = listOf(
        Attachment(
            id = "1",
            url = "https://example.com/1",
            previewUrl = "https://example.com/preview/1",
            type = Attachment.Type.IMAGE,
            description = "description 1",
            blurhash = "U8EC~$^REK-:~Tt6Rjxv9FWYxbRlr{s=oxMz"
        ),
        Attachment(
            id = "2",
            url = "https://example.com/2",
            previewUrl = null,
            meta = Attachment.MetaData(
                original = Attachment.Size(
                    duration = 123f
                )
            ),
            type = Attachment.Type.AUDIO,
            description = "description 2",
            blurhash = "U~Kd[4p#_%mN%MkQv~j1fQfQfQfQ%MkQv~j1"
        ),
        Attachment(
            id = "3",
            url = "https://example.com/3",
            previewUrl = "https://example.com/preview/3",
            type = Attachment.Type.IMAGE,
            meta = Attachment.MetaData(
                original = Attachment.Size(
                    width = 3527,
                    height = 2351,
                    aspect = 1.5002127f
                )
            ),
            description = null,
            blurhash = "UDEfoh0L.3xupB%Kt6oIxmxZRWR\$t2t6R-n*"
        ),
        Attachment(
            id = "4",
            url = "https://example.com/4",
            previewUrl = "https://example.com/preview/4",
            type = Attachment.Type.IMAGE,
            meta = Attachment.MetaData(
                original = Attachment.Size(
                    width = 2352,
                    height = 3527,
                    aspect = 0.6668557f
                )
            ),
            description = "description 4",
            blurhash = "UBB#%BxsI:%GxtWFj?WE0gf%-UIuNZV[tMbY"
        )
    )

    TuskyPreviewTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(colorScheme.background)
                .padding(8.dp)
        ) {
            MediaAttachments(
                attachments = fourAttachments.take(1),
                onOpenAttachment = { },
                onMediaHiddenChanged = { },
                sensitive = false,
                showMedia = true,
                downloadPreviews = true,
                showBlurhash = true,
            )

            MediaAttachments(
                attachments = fourAttachments.take(2),
                onOpenAttachment = { },
                onMediaHiddenChanged = { },
                sensitive = true,
                showMedia = false,
                downloadPreviews = true,
                showBlurhash = true,
            )

            MediaAttachments(
                attachments = fourAttachments.take(3),
                onOpenAttachment = { },
                onMediaHiddenChanged = { },
                sensitive = false,
                showMedia = true,
                downloadPreviews = true,
                showBlurhash = false,
            )

            MediaAttachments(
                attachments = fourAttachments,
                onOpenAttachment = { },
                onMediaHiddenChanged = { },
                sensitive = false,
                showMedia = true,
                downloadPreviews = true,
                showBlurhash = true,
            )

            MediaAttachments(
                attachments = fourAttachments,
                onOpenAttachment = { },
                onMediaHiddenChanged = { },
                sensitive = false,
                showMedia = true,
                downloadPreviews = false,
                showBlurhash = true,
            )
        }
    }
}
