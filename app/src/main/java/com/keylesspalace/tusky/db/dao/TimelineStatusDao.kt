/* Copyright 2024 Tusky Contributors
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

package com.keylesspalace.tusky.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.keylesspalace.tusky.db.Converters
import com.keylesspalace.tusky.db.entity.StatusAndAccountEntity
import com.keylesspalace.tusky.db.entity.TimelineStatusEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.PreviewCard
import com.keylesspalace.tusky.entity.Quote
import com.keylesspalace.tusky.entity.Status

@Dao
abstract class TimelineStatusDao {

    @Insert(onConflict = REPLACE)
    abstract suspend fun insert(timelineStatusEntity: TimelineStatusEntity): Long

    @Query(
        """
SELECT s.serverId, s.url, s.tuskyAccountId,
s.authorServerId, s.inReplyToId, s.inReplyToAccountId, s.createdAt, s.editedAt,
s.emojis, s.reblogsCount, s.favouritesCount, s.repliesCount, s.quotesCount, s.reblogged, s.favourited, s.bookmarked, s.sensitive,
s.spoilerText, s.visibility, s.mentions, s.tags, s.application,
s.content, s.attachments, s.poll, s.card, s.muted, s.expanded, s.contentShowing, s.contentCollapsed, s.pinned, s.language, s.filtered, s.filterActive, s.quoteState, s.quotedStatusId, s.quoteShown,
a.serverId as 'a_serverId', a.tuskyAccountId as 'a_tuskyAccountId',
a.localUsername as 'a_localUsername', a.username as 'a_username',
a.displayName as 'a_displayName', a.url as 'a_url', a.avatar as 'a_avatar', a.staticAvatar as 'a_staticAvatar',
a.note as 'a_note', a.emojis as 'a_emojis', a.bot as 'a_bot',
q.serverId as 'q_serverId', q.url as 'q_url', q.tuskyAccountId as 'q_tuskyAccountId',
q.authorServerId as 'q_authorServerId', q.inReplyToId as 'q_inReplyToId', q.inReplyToAccountId as 'q_inReplyToAccountId', q.createdAt as 'q_createdAt', q.editedAt as 'q_editedAt',
q.emojis as 'q_emojis', q.reblogsCount as 'q_reblogsCount', q.favouritesCount as 'q_favouritesCount', q.repliesCount as 'q_repliesCount', q.quotesCount as 'q_quotesCount',
q.reblogged as 'q_reblogged', q.favourited as 'q_favourited', q.bookmarked as 'q_bookmarked', q.sensitive as 'q_sensitive',
q.spoilerText as 'q_spoilerText', q.visibility as 'q_visibility', q.mentions as 'q_mentions', q.tags as 'q_tags', q.application as 'q_application',
q.content as 'q_content', q.attachments as 'q_attachments', q.poll as 'q_poll', q.card as 'q_card', q.muted as 'q_muted', q.expanded as 'q_expanded', q.contentShowing as 'q_contentShowing',
q.contentCollapsed as 'q_contentCollapsed', q.pinned as 'q_pinned', q.language as 'q_language', q.filtered as 'q_filtered', q.filterActive as 'q_filterActive', q.quoteState as 'q_quoteState', q.quotedStatusId as 'q_quotedStatusId', q.quoteShown as 'q_quoteShown',
qa.serverId as 'qa_serverId', qa.tuskyAccountId as 'qa_tuskyAccountId',
qa.localUsername as 'qa_localUsername', qa.username as 'qa_username',
qa.displayName as 'qa_displayName', qa.url as 'qa_url', qa.avatar as 'qa_avatar', qa.staticAvatar as 'qa_staticAvatar',
qa.note as 'qa_note', qa.emojis as 'qa_emojis', qa.bot as 'qa_bot'
FROM TimelineStatusEntity s
LEFT JOIN TimelineAccountEntity a ON (s.authorServerId = a.serverId AND a.tuskyAccountId = :tuskyAccountId)
LEFT JOIN TimelineStatusEntity q ON (s.quotedStatusId = q.serverId AND q.tuskyAccountId = :tuskyAccountId)
LEFT JOIN TimelineAccountEntity qa ON (q.authorServerId = qa.serverId AND qa.tuskyAccountId = :tuskyAccountId)
WHERE s.serverId == :statusId AND s.tuskyAccountId = :tuskyAccountId"""
    )
    abstract suspend fun getFullStatus(tuskyAccountId: Long, statusId: String): StatusAndAccountEntity?

    @Query(
        """
SELECT * FROM TimelineStatusEntity s
WHERE s.serverId = :statusId
AND s.authorServerId IS NOT NULL
AND s.tuskyAccountId = :tuskyAccountId"""
    )
    abstract suspend fun getStatus(tuskyAccountId: Long, statusId: String): TimelineStatusEntity?

    suspend fun update(tuskyAccountId: Long, status: Status) {
        update(
            tuskyAccountId = tuskyAccountId,
            statusId = status.id,
            content = status.content,
            editedAt = status.editedAt?.time,
            emojis = status.emojis,
            reblogsCount = status.reblogsCount,
            favouritesCount = status.favouritesCount,
            repliesCount = status.repliesCount,
            quotesCount = status.quotesCount,
            reblogged = status.reblogged,
            bookmarked = status.bookmarked,
            favourited = status.favourited,
            sensitive = status.sensitive,
            spoilerText = status.spoilerText,
            visibility = status.visibility,
            attachments = status.attachments,
            mentions = status.mentions,
            tags = status.tags,
            poll = status.poll,
            muted = status.muted,
            pinned = status.pinned,
            card = status.card,
            language = status.language,
            quoteState = status.quote?.state,
            quotedStatusId = status.quote?.quotedStatus?.id
        )
    }

    @Query(
        """UPDATE TimelineStatusEntity
           SET content = :content,
           editedAt = :editedAt,
           emojis = :emojis,
           reblogsCount = :reblogsCount,
           favouritesCount = :favouritesCount,
           repliesCount = :repliesCount,
           quotesCount = :quotesCount,
           reblogged = :reblogged,
           bookmarked = :bookmarked,
           favourited = :favourited,
           sensitive = :sensitive,
           spoilerText = :spoilerText,
           visibility = :visibility,
           attachments = :attachments,
           mentions = :mentions,
           tags = :tags,
           poll = :poll,
           muted = :muted,
           pinned = :pinned,
           card = :card,
           language = :language,
           quoteState = :quoteState,
           quotedStatusId = :quotedStatusId
           WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    @TypeConverters(Converters::class)
    abstract suspend fun update(
        tuskyAccountId: Long,
        statusId: String,
        content: String?,
        editedAt: Long?,
        emojis: List<Emoji>?,
        reblogsCount: Int,
        favouritesCount: Int,
        repliesCount: Int,
        quotesCount: Int,
        reblogged: Boolean,
        bookmarked: Boolean,
        favourited: Boolean,
        sensitive: Boolean,
        spoilerText: String,
        visibility: Status.Visibility,
        attachments: List<Attachment>?,
        mentions: List<Status.Mention>?,
        tags: List<HashTag>?,
        poll: Poll?,
        muted: Boolean?,
        pinned: Boolean,
        card: PreviewCard?,
        language: String?,
        quoteState: Quote.State?,
        quotedStatusId: String?
    )

    @Query(
        """UPDATE TimelineStatusEntity SET bookmarked = :bookmarked
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setBookmarked(tuskyAccountId: Long, statusId: String, bookmarked: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET reblogged = :reblogged
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setReblogged(tuskyAccountId: Long, statusId: String, reblogged: Boolean)

    @Query("DELETE FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun removeAllStatuses(tuskyAccountId: Long)

    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND id = :id"""
    )
    abstract suspend fun deleteHomeTimelineItem(tuskyAccountId: Long, id: String)

    /**
     * Deletes all hometimeline items that reference the status with it [statusId]. They can be regular statuses or reblogs.
     */
    @Query(
        """DELETE FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId = :statusId"""
    )
    abstract suspend fun deleteAllWithStatus(tuskyAccountId: Long, statusId: String)

    /**
     * Cleans the TimelineStatusEntity table from unreferenced status entries.
     * @param tuskyAccountId id of the account for which to clean statuses
     */
    @Query(
        """DELETE FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId
        AND serverId NOT IN
        (SELECT statusId FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NOT NULL)
        AND serverId NOT IN
        (SELECT statusId FROM NotificationEntity WHERE tuskyAccountId = :tuskyAccountId AND statusId IS NOT NULL)
        AND serverId NOT IN
        (SELECT quotedStatusId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND quotedStatusId IS NOT NULL)"""
    )
    internal abstract suspend fun cleanupStatuses(tuskyAccountId: Long)

    @Query(
        """UPDATE TimelineStatusEntity SET poll = :poll
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    @TypeConverters(Converters::class)
    abstract suspend fun setVoted(tuskyAccountId: Long, statusId: String, poll: Poll)

    @Transaction
    open suspend fun setShowResults(tuskyAccountId: Long, statusId: String) {
        getStatus(tuskyAccountId, statusId)?.let { status ->
            status.poll?.let { poll ->
                setVoted(tuskyAccountId, statusId, poll.copy(voted = true))
            }
        }
    }

    @Query(
        """UPDATE TimelineStatusEntity SET expanded = :expanded
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setExpanded(tuskyAccountId: Long, statusId: String, expanded: Boolean)

    @Query(
        """UPDATE TimelineStatusEntity SET contentShowing = :contentShowing
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setContentShowing(
        tuskyAccountId: Long,
        statusId: String,
        contentShowing: Boolean
    )

    @Query(
        """UPDATE TimelineStatusEntity SET contentCollapsed = :contentCollapsed
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setContentCollapsed(
        tuskyAccountId: Long,
        statusId: String,
        contentCollapsed: Boolean
    )

    @Query(
        """UPDATE TimelineStatusEntity SET pinned = :pinned
WHERE tuskyAccountId = :tuskyAccountId AND serverId = :statusId"""
    )
    abstract suspend fun setPinned(tuskyAccountId: Long, statusId: String, pinned: Boolean)

    @Query(
        """DELETE FROM HomeTimelineEntity
WHERE tuskyAccountId = :tuskyAccountId AND statusId IN (
SELECT serverId FROM TimelineStatusEntity WHERE tuskyAccountId = :tuskyAccountId AND authorServerId in
( SELECT serverId FROM TimelineAccountEntity WHERE username LIKE '%@' || :instanceDomain
AND tuskyAccountId = :tuskyAccountId
))"""
    )
    abstract suspend fun deleteAllFromInstance(tuskyAccountId: Long, instanceDomain: String)

    @Query(
        "UPDATE TimelineStatusEntity " +
            "SET filterActive = :filtered " +
            "WHERE tuskyAccountId = :tuskyAccountId " +
            "AND serverId = :statusId"
    )
    abstract suspend fun changeFilter(tuskyAccountId: Long, statusId: String, filtered: Boolean)

    @Query(
        "UPDATE TimelineStatusEntity " +
            "SET quoteShown = :show " +
            "WHERE tuskyAccountId = :tuskyAccountId " +
            "AND serverId = :statusId"
    )
    abstract suspend fun showQuote(tuskyAccountId: Long, statusId: String, show: Boolean)

    @Query(
        "SELECT id FROM HomeTimelineEntity " +
            "WHERE tuskyAccountId = :tuskyAccountId " +
            "ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getTopId(tuskyAccountId: Long): String?

    @Query(
        "SELECT id FROM HomeTimelineEntity " +
            "WHERE tuskyAccountId = :tuskyAccountId " +
            "AND statusId IS NULL " +
            "ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getTopPlaceholderId(tuskyAccountId: Long): String?

    /**
     * Returns the id directly above [id], or null if [id] is the id of the top item
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId " +
            "AND (LENGTH(:id) < LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id < id)) " +
            "ORDER BY LENGTH(id) ASC, id ASC LIMIT 1"
    )
    abstract suspend fun getIdAbove(tuskyAccountId: Long, id: String): String?

    /**
     * Returns the ID directly below [id], or null if [id] is the ID of the bottom item
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId " +
            "AND (LENGTH(:id) > LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id > id)) " +
            "ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getIdBelow(tuskyAccountId: Long, id: String): String?

    /**
     * Returns the id of the next placeholder after [id], or null if there is no placeholder.
     */
    @Query(
        "SELECT id FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId " +
            "AND statusId IS NULL AND (LENGTH(:id) > LENGTH(id) OR (LENGTH(:id) = LENGTH(id) AND :id > id)) " +
            "ORDER BY LENGTH(id) DESC, id DESC LIMIT 1"
    )
    abstract suspend fun getNextPlaceholderIdAfter(tuskyAccountId: Long, id: String): String?

    @Query("SELECT COUNT(*) FROM HomeTimelineEntity WHERE tuskyAccountId = :tuskyAccountId")
    abstract suspend fun getHomeTimelineItemCount(tuskyAccountId: Long): Int

    /** Developer tools: Find N most recent status IDs */
    @Query(
        "SELECT id FROM HomeTimelineEntity " +
            "WHERE tuskyAccountId = :tuskyAccountId " +
            "ORDER BY LENGTH(id) DESC, id DESC LIMIT :count"
    )
    abstract suspend fun getMostRecentNStatusIds(tuskyAccountId: Long, count: Int): List<String>

    /** Developer tools: Convert a home timeline item to a placeholder */
    @Query("UPDATE HomeTimelineEntity SET statusId = NULL, reblogAccountId = NULL WHERE id = :serverId")
    abstract suspend fun convertStatusToPlaceholder(serverId: String)
}
