package com.keylesspalace.tusky.ui.statuscomponents.text

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.components.timeline.fakeStatus
import com.keylesspalace.tusky.components.timeline.fakeStatusViewData
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.util.HASHTAG_EXPRESSION
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(AndroidJUnit4::class)
class TrailingHashtagsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

    @Test
    fun `get trailing hashtags correctly works on full status`() {
        val status = fakeStatusViewData().copy(
            status = fakeStatus().copy(
                content = """
                    <p>I was a backer of The Comic Shop! A Workplace <a href=\"https://social.coop/tags/Comedy\" class=\"mention hashtag\"
                            rel=\"nofollow noopener\" target=\"_blank\">#<span>Comedy</span></a> on Kickstarter.</p>
                    <p>Lovely to see a cute show made by and featuring so many creators of color! The whole thing is out on YouTube now for
                        free.</p>
                    <p>I just watched the pilot, it was good! Each episode is 15 minutes or less.</p>
                    <p><a href=\"https://youtube.com/playlist?list=PLcBItu-HVRPe-Nx-ASwZrDdk2pj6iG4Dj\" rel=\"nofollow noopener\"
                            translate=\"no\" target=\"_blank\"><span class=\"invisible\">https://</span><span
                                class=\"ellipsis\">youtube.com/playlist?list=PLcB</span><span
                                class=\"invisible\">Itu-HVRPe-Nx-ASwZrDdk2pj6iG4Dj</span></a></p>
                    <p><a href=\"https://social.coop/tags/Comics\" class=\"mention hashtag\" rel=\"nofollow noopener\"
                            target=\"_blank\">#<span>Comics</span></a> <a href=\"https://social.coop/tags/PoCCreators\" class=\"mention
                            hashtag\" rel=\"nofollow noopener\" target=\"_blank\">#<span>PoCCreators</span></a> <a
                            href=\"https://social.coop/tags/WebSeries\" class=\"mention hashtag\" rel=\"nofollow noopener\"
                            target=\"_blank\">#<span>WebSeries</span></a></p>
                """.trimIndent()
            )
        )
        composeTestRule.setContent {
            val (_, trailingHashtags) = mastodonHtmlText(
                status = status,
                onMentionClick = { },
                onHashtagClick = { },
                onUrlClick = { },
                splitOffTrailingHashtags = true
            )
            assertEquals(listOf("Comics", "PoCCreators", "WebSeries"), trailingHashtags)
        }
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
