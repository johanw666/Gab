/* Copyright 2018 Conny Duck
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

package com.keylesspalace.tusky.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import com.keylesspalace.tusky.components.conversation.ConversationEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    suspend fun update(tuskyAccountId: Long, status: Status) {
        update(
            tuskyAccountId = tuskyAccountId,
            statusId = status.id,
            content = status.content,
            editedAt = status.editedAt?.time,
            emojis = status.emojis,
            favouritesCount = status.favouritesCount,
            repliesCount = status.repliesCount,
            bookmarked = status.bookmarked,
            favourited = status.favourited,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            attachments = status.attachments,
            mentions = status.mentions,
            tags = status.tags,
            poll = status.poll,
            muted = status.muted,
            language = status.language
        )
    }

    @Query(
        """UPDATE ConversationEntity
           SET s_content = :content,
           s_editedAt = :editedAt,
           s_emojis = :emojis,
           s_favouritesCount = :favouritesCount,
           s_repliesCount = :repliesCount,
           s_bookmarked = :bookmarked,
           s_favourited = :favourited,
           s_sensitive = :sensitive,
           s_spoilerText = :spoilerText,
           s_attachments = :attachments,
           s_mentions = :mentions,
           s_tags = :tags,
           s_poll = :poll,
           s_muted = :muted,
           s_language = :language
           WHERE accountId = :tuskyAccountId AND s_id = :statusId"""
    )
    @TypeConverters(Converters::class)
    suspend fun update(
        tuskyAccountId: Long,
        statusId: String,
        content: String?,
        editedAt: Long?,
        emojis: List<Emoji>?,
        favouritesCount: Int,
        repliesCount: Int,
        bookmarked: Boolean,
        favourited: Boolean,
        sensitive: Boolean,
        spoilerText: String,
        attachments: List<Attachment>?,
        mentions: List<Status.Mention>?,
        tags: List<HashTag>?,
        poll: Poll?,
        muted: Boolean?,
        language: String?
    )

    @Query("DELETE FROM ConversationEntity WHERE id = :id AND accountId = :tuskyAccountId")
    suspend fun delete(id: String, tuskyAccountId: Long)

    @Query("SELECT * FROM ConversationEntity WHERE accountId = :tuskyAccountId ORDER BY `order` ASC")
    fun conversationsForAccount(tuskyAccountId: Long): PagingSource<Int, ConversationEntity>

    @Query("DELETE FROM ConversationEntity WHERE accountId = :tuskyAccountId")
    suspend fun deleteForAccount(tuskyAccountId: Long)

    @Query(
        """UPDATE ConversationEntity SET s_expanded = :expanded
WHERE accountId = :tuskyAccountId AND s_id = :statusId"""
    )
    suspend fun setExpanded(
        tuskyAccountId: Long,
        statusId: String,
        expanded: Boolean
    )

    @Query(
        """UPDATE ConversationEntity SET s_showingHiddenContent = :contentShowing
WHERE accountId = :tuskyAccountId AND s_id = :statusId"""
    )
    suspend fun setContentShowing(
        tuskyAccountId: Long,
        statusId: String,
        contentShowing: Boolean
    )

    @Query(
        """UPDATE ConversationEntity SET s_collapsed = :contentCollapsed
WHERE accountId = :tuskyAccountId AND s_id = :statusId"""
    )
    suspend fun setContentCollapsed(
        tuskyAccountId: Long,
        statusId: String,
        contentCollapsed: Boolean
    )

    @Query(
        """UPDATE ConversationEntity SET s_poll = :poll
WHERE accountId = :tuskyAccountId AND s_id = :statusId"""
    )
    @TypeConverters(Converters::class)
    suspend fun updatePoll(
        tuskyAccountId: Long,
        statusId: String,
        poll: Poll?
    )
}
