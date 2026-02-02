package com.keylesspalace.tusky.ui.statuscomponents.text

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.components.timeline.fakeStatus
import com.keylesspalace.tusky.components.timeline.fakeStatusViewData
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Quote
import com.keylesspalace.tusky.ui.statuscomponents.text.html.AnnotatedStringHtmlHandler.Companion.LINK_ICON_ID
import com.keylesspalace.tusky.ui.tuskyBlueDark
import com.keylesspalace.tusky.viewdata.QuoteViewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MastodonHtmlTextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** artificial cases **/

    @Test
    fun `empty input`() {
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText("")
            assertEquals(AnnotatedString(""), contentOut)
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `input without html`() {
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText("just some text")
            assertEquals(AnnotatedString("just some text"), contentOut)
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `input with broken html`() {
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText("<p>test</blockquote>")
            assertEquals(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("test")
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `input with nested blockquotes - should not crash`() {
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText("<blockquote>blockquote<blockquote>nested blockquote</blockquote></blockquote>")
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withAnnotation("QUOTE", "8") {
                        withStyle(SpanStyle()) {
                            withStyle(
                                ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = 8.sp,
                                        restLine = 8.sp
                                    )
                                )
                            ) {
                                append("blockquote")
                            }
                            withStyle(
                                ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = 16.sp,
                                        restLine = 16.sp
                                    )
                                )
                            ) {
                                append("\n")
                                withAnnotation("QUOTE", "16") {
                                    withStyle(SpanStyle()) {
                                        append("nested blockquote")
                                    }
                                }
                            }
                        }
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `should correctly handle empty blockquotes`() {
        val input = "<blockquote></blockquote><p>Test</p>"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("Test")
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `should correctly handle tags nested into a filtered tag`() {
        val input = "<p class=\"quote-inline\"><a> <p> </p></a> </p>"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input)
            assertEquals(AnnotatedString(""), contentOut)
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    /** real fedi posts **/

    @Test
    fun `should hide inline quote when quote is attached`() {
        val input = "<p class=\"quote-inline\">RE: <a href=\"https://mastodon.social/@ConnyDuck/115757662798419485\" rel=\"nofollow noopener\" translate=\"no\" target=\"_blank\">" +
            "<span class=\"invisible\">https://</span><span class=\"ellipsis\">mastodon.social/@ConnyDuck/115</span><span class=\"invisible\">757662798419485</span></a></p><p>Test</p>"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input, withQuote = true)
            assertEquals(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("Test")
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `should not hide inline quote when no quote is attached`() {
        val input = "<p class=\"quote-inline\">RE: <a href=\"https://mastodon.social/@ConnyDuck/115757662798419485\" rel=\"nofollow noopener\" translate=\"no\" target=\"_blank\">" +
            "<span class=\"invisible\">https://</span><span class=\"ellipsis\">mastodon.social/@ConnyDuck/115</span><span class=\"invisible\">757662798419485</span></a></p><p>Test</p>"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input, withQuote = false)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("RE: ")
                        withLink(LinkAnnotation.Clickable(tag = "", linkInteractionListener = null)) {
                            append("https://mastodon.social/@ConnyDuck/115757662798419485")
                        }
                    }
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("\nTest")
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `hashtags and mentions`() {
        val input = "<p>Test post with <a href=\"https://goblin.technology/tags/some\" class=\"mention hashtag\" rel=\"tag nofollow noreferrer noopener\" target=\"_blank\">#<span>some</span></a> " +
            "<a href=\"https://goblin.technology/tags/hashtags\" class=\"mention hashtag\" rel=\"tag nofollow noreferrer noopener\" target=\"_blank\">#<span>hashtags</span></a><span class=\"h-card\"> " +
            "<a href=\"https://chaos.social/@ConnyDuck\" class=\"u-url mention\" rel=\"nofollow noreferrer noopener\" target=\"_blank\">@<span>ConnyDuck</span></a></span></p>"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("Test post with ")
                        withLink(LinkAnnotation.Clickable(tag = "", linkInteractionListener = null)) {
                            append("#some")
                        }
                        append(" ")
                        withLink(LinkAnnotation.Clickable(tag = "", linkInteractionListener = null)) {
                            append("#hashtags")
                        }
                        append(" ")
                        withLink(LinkAnnotation.Clickable(tag = "", linkInteractionListener = null)) {
                            append("@ConnyDuck")
                        }
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    val postWithTrailingHashtags = "<p>test<br><a href=\"https://goblin.technology/tags/hashtag1\" class=\"mention hashtag\" rel=\"tag nofollow noreferrer noopener\" target=\"_blank\">#<span>hashtag1</span></a> " +
        "<a href=\"https://goblin.technology/tags/hashtag2\" class=\"mention hashtag\" rel=\"tag nofollow noreferrer noopener\" target=\"_blank\">#<span>hashtag2</span></a></p>"

    @Test
    fun `trailing hashtags split off`() {
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(postWithTrailingHashtags)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("test")
                    }
                },
                contentOut
            )
            assertEquals(listOf("hashtag1", "hashtag2"), trailingHashtags)
        }
    }

    @Test
    fun `trailing hashtags included in content`() {
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(postWithTrailingHashtags, splitOffTrailingHashtags = false)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("test")
                        withLink(LinkAnnotation.Clickable(tag = "", linkInteractionListener = null)) {
                            append("\n#hashtag1")
                        }
                        append(" ")
                        withLink(LinkAnnotation.Clickable(tag = "", linkInteractionListener = null)) {
                            append("#hashtag2")
                        }
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `basic formatting`() {
        val input = "<h2>A headline</h2><p>Some <strong>bold</strong> <em>italic</em> <em><strong>Text</strong></em> with <del>strikethrough</del>. And <code>inline code</code>.</p>" +
            "<ol><li>Ordered List Item 1</li><li>Ordered List Item 2</li><li>Ordered List Item 3</li></ol><ul><li>Unordered List Item 1<ul><li>Nested List Item 1</li>" +
            "<li>Nested List Item 2</li></ul></li><li>Unordered List Item 2</li></ul><pre><code>{\n    aCodeBlock();\n}\n</code></pre>"

        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        withStyle(SpanStyle(fontSize = 1.4.em, fontWeight = FontWeight.Bold)) {
                            append("A headline")
                        }
                    }
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("\nSome ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("bold")
                        }
                        append(" ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append("italic")
                        }
                        append(" ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Text")
                            }
                        }
                        append(" with ")
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append("strikethrough")
                        }
                        append(". And ")
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append("inline code")
                        }
                        append(".")
                    }
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("\n1. Ordered List Item 1")
                    }
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("2. Ordered List Item 2")
                    }
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("3. Ordered List Item 3")
                    }

                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("\n• Unordered List Item 1")
                    }
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 16.sp))) {
                        append("◦ Nested List Item 1")
                    }
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 16.sp, restLine = 16.sp))) {
                        append("◦ Nested List Item 2")
                    }
                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("• Unordered List Item 2")
                    }

                    withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 0.sp))) {
                        append("\n")
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                append("{\n    aCodeBlock();\n}")
                            }
                        }
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `custom emojis`() {
        val input = "<p>\uD83D\uDE2D :blob_disapproval:\u200B \u200B:upside_down_cry:\u200B</p>"
        val customEmojis = listOf(
            Emoji(
                shortcode = "blob_disapproval",
                url = "https://goblin.technology/fileserver/01BPSX2MKCRVMD4YN4D71G9CP5/emoji/original/01J49HYXA0J9CTK2DREP1RN6XZ.png",
                staticUrl = "https://goblin.technology/fileserver/01BPSX2MKCRVMD4YN4D71G9CP5/emoji/original/01J49HYXA0J9CTK2DREP1RN6XZ.png",
                category = "blobs"
            ),
            Emoji(
                shortcode = "upside_down_cry",
                url = "https://goblin.technology/fileserver/01BPSX2MKCRVMD4YN4D71G9CP5/emoji/original/01JQZVSRNKAJZC1JFSH63QEEMK.png",
                staticUrl = "https://goblin.technology/fileserver/01BPSX2MKCRVMD4YN4D71G9CP5/emoji/original/01JQZVSRNKAJZC1JFSH63QEEMK.png",
                category = null
            )
        )
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input, customEmojis = customEmojis)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("\uD83D\uDE2D ")
                        appendInlineContent("blob_disapproval", ":blob_disapproval:")
                        append("\u200B \u200B")
                        appendInlineContent("upside_down_cry", ":upside_down_cry:")
                        append("\u200B")
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun blockquotes() {
        val input = "<blockquote><p>a quote</p></blockquote><blockquote><p>another quote</p></blockquote>"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 8.sp,
                                restLine = 8.sp
                            )
                        )
                    ) {
                        withAnnotation("QUOTE", "8") {
                            withStyle(SpanStyle()) {
                                append("a quote")
                            }
                        }
                    }

                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 8.sp,
                                restLine = 8.sp
                            )
                        )
                    ) {
                        append("\n")
                        withAnnotation("QUOTE", "8") {
                            withStyle(SpanStyle()) {
                                append("another quote")
                            }
                        }
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `should show hidden link`() {
        val input = "<p><a href=\"https://codeberg.org/tusky/Tusky\" rel=\"nofollow noreferrer noopener\" target=\"_blank\">Obscured Link</a></p>"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        withLink(
                            LinkAnnotation.Url(
                                url = "https://codeberg.org/tusky/Tusky",
                                styles = linkStyles,
                                linkInteractionListener = null
                            )
                        ) {
                            append("Obscured Link (")
                            appendInlineContent(LINK_ICON_ID, "🔗")
                            append(" codeberg.org)")
                        }
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    @Test
    fun `should remove extra whitespace from between blocks but keep it in text`() {
        val input = "<p>This<br/> is<br/>  a<br/>   test</p>\n<blockquote>\n <p>This is a blockquote.</p>\n</blockquote>\n<p>\nTest</p>\n<p>\n\n"
        composeTestRule.setContent {
            val (contentOut, trailingHashtags) = mastodonHtmlText(input)
            assertEqualAnnotatedString(
                buildAnnotatedString {
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("This\n is\n  a\n   test")
                    }
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 8.sp,
                                restLine = 8.sp
                            )
                        )
                    ) {
                        append("\n")
                        withAnnotation("QUOTE", "8") {
                            withStyle(SpanStyle()) {
                                append("This is a blockquote.")
                            }
                        }
                    }
                    withStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(
                                firstLine = 0.sp,
                                restLine = 0.sp
                            )
                        )
                    ) {
                        append("\nTest")
                    }
                },
                contentOut
            )
            assertEquals(emptyList<String>(), trailingHashtags)
        }
    }

    /** helper methods **/

    @Composable
    private fun mastodonHtmlText(
        input: String,
        splitOffTrailingHashtags: Boolean = true,
        withQuote: Boolean = true,
        customEmojis: List<Emoji> = emptyList()
    ): Pair<AnnotatedString, List<String>> {
        val status = fakeStatusViewData().copy(
            status = fakeStatus().copy(
                content = input,
                emojis = customEmojis,
                quote = if (withQuote) {
                    Quote(
                        state = Quote.State.ACCEPTED,
                        quotedStatus = fakeStatus()
                    )
                } else {
                    null
                }
            ),
            quote = if (withQuote) {
                QuoteViewData(
                    state = Quote.State.ACCEPTED,
                    quotedStatusViewData = fakeStatusViewData(),
                    quoteShown = true,
                )
            } else {
                null
            }
        )
        return mastodonHtmlText(status, {}, {}, {}, splitOffTrailingHashtags, linkStyles)
    }

    private val activeLinkStyle = SpanStyle(color = tuskyBlueDark, background = tuskyBlueDark.copy(alpha = 0.25f))
    private val linkStyles = TextLinkStyles(
        style = SpanStyle(color = tuskyBlueDark),
        focusedStyle = activeLinkStyle,
        hoveredStyle = activeLinkStyle,
        pressedStyle = activeLinkStyle
    )

    // directly comparing two AnnotatedStrings also compares clicklisteners which can't be controlled from tests
    private fun assertEqualAnnotatedString(expected: AnnotatedString, actual: AnnotatedString) {
        assertEquals(expected.text, actual.text)
        assertEquals(expected.paragraphStyles, actual.paragraphStyles)
        assertEquals(expected.spanStyles, actual.spanStyles)
        assertEquals(expected.getStringAnnotations(0, expected.length), actual.getStringAnnotations(0, actual.length))
        val expectedLinkAnnotations = expected.getLinkAnnotations(0, expected.length)
        val actualLinkAnnotations = actual.getLinkAnnotations(0, actual.length)
        assertEquals(expectedLinkAnnotations.size, actualLinkAnnotations.size)
        expectedLinkAnnotations.forEachIndexed { index, expectedAnnotation ->
            val actualLinkAnnotation = actualLinkAnnotations[index]
            assertEquals(expectedAnnotation.tag, actualLinkAnnotation.tag)
            assertEquals(expectedAnnotation.start, actualLinkAnnotation.start)
            assertEquals(expectedAnnotation.end, actualLinkAnnotation.end)
            if (expectedAnnotation.item is LinkAnnotation.Url && actualLinkAnnotation.item is LinkAnnotation.Url) {
                assertEquals((expectedAnnotation.item as LinkAnnotation.Url).url, (actualLinkAnnotation.item as LinkAnnotation.Url).url)
            }
        }
    }
}
