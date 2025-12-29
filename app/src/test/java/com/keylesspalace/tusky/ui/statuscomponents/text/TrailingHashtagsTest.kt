package com.keylesspalace.tusky.ui.statuscomponents.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.util.HASHTAG_EXPRESSION
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Test

class TrailingHashtagsTest {

    /** The [Pattern.UNICODE_CHARACTER_CLASS] flag is not supported on Android, on Android it is just always on.
     * Since these tests run on a regular Jvm, we need to set this flag or they would behave differently.
     * */
    private val hashtagPattern = "^#$HASHTAG_EXPRESSION$".toPattern(Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)

    @Test
    fun `get trailing hashtags with empty content returns empty list`() {
        val contentIn = AnnotatedString("")
        val (contentOut, trailingHashtags) = getTrailingHashtags(AnnotatedString(""), emptyList(), hashtagPattern)
        assertEquals(contentIn, contentOut)
        assert(trailingHashtags.isEmpty())
    }

    @Test
    fun `get trailing hashtags with no hashtags returns empty list`() {
        val contentIn = AnnotatedString("some untagged content")
        val (contentOut, trailingHashtags) = getTrailingHashtags(AnnotatedString("some untagged content"), emptyList(), hashtagPattern)
        assertEquals(contentIn, contentOut)
        assert(trailingHashtags.isEmpty())
    }

    @Test
    fun `get trailing hashtags with all inline hashtags returns empty list`() {
        val contentIn = AnnotatedString("some #inline #tagged #content")
        val (contentOut, trailingHashtags) = getTrailingHashtags(contentIn, emptyList(), hashtagPattern)
        assertEquals(contentIn, contentOut)
        assert(trailingHashtags.isEmpty())
    }

    @Test
    fun `get trailing hashtags with only tags returns empty content`() {
        val contentIn = buildAnnotatedString {
            appendTag("#some")
            append(" ")
            appendTag("#tags")
            append(" ")
            appendTag("#but")
            append(" ")
            appendTag("#nothing")
            append(" ")
            appendTag("#else")
        }
        val (contentOut, trailingHashtags) = getTrailingHashtags(
            contentIn,
            listOf(tag("some"), tag("tags"), tag("but"), tag("nothing"), tag("else")),
            hashtagPattern
        )
        assert(contentOut.isEmpty())
        assertEquals(listOf("some", "tags", "but", "nothing", "else"), trailingHashtags)
    }

    @Test
    fun `get trailing hashtags with one tag`() {
        val contentIn = buildAnnotatedString {
            append("some content followed by tags:\n")
            appendTag("#tag")
        }

        val (contentOut, trailingHashtags) = getTrailingHashtags(contentIn, listOf(tag("tag")), hashtagPattern)
        assertEquals(AnnotatedString("some content followed by tags:"), contentOut)
        assertEquals(listOf("tag"), trailingHashtags)
    }

    @Test
    fun `get trailing hashtags with additional server tag`() {
        val contentIn = buildAnnotatedString {
            append("some content followed by tags:\n")
            appendTag("#tag")
        }

        val (contentOut, trailingHashtags) = getTrailingHashtags(
            contentIn,
            listOf(tag("additional"), tag("tag")),
            hashtagPattern
        )
        assertEquals(AnnotatedString("some content followed by tags:"), contentOut)
        assertEquals(listOf("tag", "additional"), trailingHashtags)
    }

    @Test
    fun `get trailing hashtags with multiple tags`() {
        for (separator in listOf(" ", "\t", "\n", "\r\n")) {
            val contentIn = buildAnnotatedString {
                append("some content followed by tags:\n")
                append(separator)
                appendTag("#tusky")
                append(separator)
                appendTag("#tuskydev")
            }

            val (contentOut, trailingHashtags) = getTrailingHashtags(
                contentIn,
                listOf(tag("tusky"), tag("tuskydev")),
                hashtagPattern
            )
            assertEquals(AnnotatedString("some content followed by tags:"), contentOut)
            assertEquals(listOf("tusky", "tuskydev"), trailingHashtags)
        }
    }

    @Test
    fun `get trailing hashtags ignores inline tags`() {
        for (separator in listOf(" ", "\t", "\n", "\r\n")) {
            val contentIn = buildAnnotatedString {
                append("some ")
                appendTag("#content")
                append(" followed by tags:\n")
                append(separator)
                appendTag("#tusky")
                append(separator)
                appendTag("#tuskydev")
            }

            val (contentOut, trailingHashtags) = getTrailingHashtags(
                contentIn,
                listOf(tag("content"), tag("tusky"), tag("tuskydev")),
                hashtagPattern
            )
            val expectedContentOut = buildAnnotatedString {
                append("some ")
                appendTag("#content")
                append(" followed by tags:")
            }
            assertEquals(expectedContentOut, contentOut)
            assertEquals(listOf("tusky", "tuskydev"), trailingHashtags)
        }
    }

    @Test
    fun `get trailing hashtags correctly normalizes tags`() {
        val contentIn = buildAnnotatedString {
            append("some ")
            appendTag("#content")
            append(" followed by tags:")
            append("\n")
            appendTag("#tag")
            append(" ")
            appendTag("#tëst")
        }

        val (contentOut, trailingHashtags) = getTrailingHashtags(
            contentIn,
            listOf(tag("content"), tag("Tag"), tag("test")),
            hashtagPattern
        )
        val expectedContentOut = buildAnnotatedString {
            append("some ")
            appendTag("#content")
            append(" followed by tags:")
        }
        assertEquals(expectedContentOut, contentOut)
        assertEquals(listOf("tag", "tëst"), trailingHashtags)
    }

    private fun AnnotatedString.Builder.appendTag(tag: String) {
        withLink(
            LinkAnnotation.Clickable(
                tag = tag,
                styles = TextLinkStyles(),
                linkInteractionListener = { }
            )
        ) {
            append(tag)
        }
    }
    private fun tag(tag: String) = HashTag(
        name = tag,
        url = "https://some.mastodon.server/tags/$tag"
    )
}
