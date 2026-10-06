// ChangelogMarkdown.kt
//
// Renders a GitHub release body. Release notes are written in GitHub-flavoured markdown and often
// paste screenshots as HTML (<img src=...>), so showing the raw body printed the asterisks, the
// `![...]()` syntax and the tags instead of bold text and pictures. This covers what release
// notes actually use - headings, paragraphs, bullet/numbered lists, quotes, rules, code fences,
// **bold**, *italic*, ~~strike~~, `code`, links, and images (markdown or <img>) - not all of
// CommonMark. Anything it doesn't recognise falls through as plain text, never dropped.

package com.example.musicfy.ui.screens.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Image(val url: String, val alt: String) : MdBlock
    data object Rule : MdBlock
}

private val MdImage = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")
private val HtmlImage = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val HtmlAlt = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val HtmlTag = Regex("""</?[a-zA-Z][^>]*>""")
private val HeadingLine = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BulletLine = Regex("""^(\s*)[-*+]\s+(?:\[[ xX]]\s+)?(.*)$""")
private val NumberLine = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val RuleLine = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val Fence = Regex("""^\s*(```|~~~)""")

/** Pulls image references out of [line]; returns the text left over plus the images, in order. */
private fun extractImages(line: String): Pair<String, List<MdBlock.Image>> {
    val images = mutableListOf<Pair<Int, MdBlock.Image>>()
    MdImage.findAll(line).forEach { images += it.range.first to MdBlock.Image(it.groupValues[2], it.groupValues[1]) }
    HtmlImage.findAll(line).forEach { match ->
        val alt = HtmlAlt.find(match.value)?.groupValues?.get(1).orEmpty()
        images += match.range.first to MdBlock.Image(match.groupValues[1], alt)
    }
    if (images.isEmpty()) return line to emptyList()
    val rest = line.replace(MdImage, " ").replace(HtmlImage, " ")
    return rest to images.sortedBy { it.first }.map { it.second }
}

private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
        paragraph.clear()
    }

    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]

        if (Fence.containsMatchIn(raw)) {
            flushParagraph()
            val code = StringBuilder()
            i++
            while (i < lines.size && !Fence.containsMatchIn(lines[i])) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            blocks += MdBlock.Code(code.toString())
            i++
            continue
        }

        // HTML comments (release templates leave them in) and <br>-style tags carry no text.
        val (withoutImages, images) = extractImages(raw.replace(Regex("<!--.*?-->"), ""))
        val line = withoutImages.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
            .replace(HtmlTag, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        when {
            line.isBlank() -> flushParagraph()
            RuleLine.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }
            HeadingLine.matches(line) -> {
                flushParagraph()
                val match = HeadingLine.find(line)!!
                blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2])
            }
            BulletLine.matches(line) -> {
                flushParagraph()
                val match = BulletLine.find(line)!!
                blocks += MdBlock.ListItem("•", match.groupValues[2].trim(), match.groupValues[1].length / 2)
            }
            NumberLine.matches(line) -> {
                flushParagraph()
                val match = NumberLine.find(line)!!
                blocks += MdBlock.ListItem("${match.groupValues[2]}.", match.groupValues[3].trim(), match.groupValues[1].length / 2)
            }
            line.trimStart().startsWith(">") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.trimStart().removePrefix(">").trim())
            }
            else -> {
                // A line under a list item that's just indented continues that item.
                val last = blocks.lastOrNull()
                if (paragraph.isEmpty() && last is MdBlock.ListItem && raw.startsWith("  ")) {
                    blocks[blocks.lastIndex] = last.copy(text = last.text + " " + line.trim())
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }

        if (images.isNotEmpty()) {
            flushParagraph()
            blocks += images
        }
        i++
    }
    flushParagraph()
    return blocks
}

private val InlinePattern = Regex(
    """(\*\*|__)(.+?)\1""" + // 1-2: bold
        """|~~(.+?)~~""" + // 3: strike
        """|`([^`]+)`""" + // 4: code
        """|\[([^\]]+)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""" + // 5-6: link
        """|(?<![\w*])\*(?![\s*])(.+?)(?<![\s*])\*(?![\w*])""" + // 7: *italic*
        """|(?<![\w_])_(?![\s_])(.+?)(?<![\s_])_(?![\w_])""" + // 8: _italic_
        """|(https?://[^\s<>()]+[^\s<>().,;:!?'"])""", // 9: bare link
)

private fun AnnotatedString.Builder.appendInline(text: String, strong: SpanStyle, link: SpanStyle) {
    var cursor = 0
    while (cursor < text.length) {
        val match = InlinePattern.find(text, cursor)
        if (match == null) {
            append(text.substring(cursor))
            return
        }
        append(text.substring(cursor, match.range.first))
        val g = match.groupValues
        when {
            g[2].isNotEmpty() -> withStyle(strong) { appendInline(g[2], strong, link) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInline(g[3], strong, link)
            }
            g[4].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = 0.08f))
            ) { append(g[4]) }
            g[5].isNotEmpty() -> withLink(LinkAnnotation.Url(g[6], TextLinkStyles(style = link))) {
                appendInline(g[5], strong, link)
            }
            g[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(g[7], strong, link) }
            g[8].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(g[8], strong, link) }
            g[9].isNotEmpty() -> withLink(LinkAnnotation.Url(g[9], TextLinkStyles(style = link))) { append(g[9]) }
        }
        cursor = match.range.last + 1
    }
}

private fun inline(text: String, strongColor: Color): AnnotatedString = buildAnnotatedString {
    appendInline(
        text = text,
        strong = SpanStyle(fontWeight = FontWeight.Bold, color = strongColor),
        link = SpanStyle(color = strongColor, textDecoration = TextDecoration.Underline),
    )
}

/**
 * A release body as styled text and images. [color] is the body text colour; bold text, headings
 * and links use [strongColor] so they stand out from it.
 */
@Composable
fun ChangelogMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF9A9A9A),
    strongColor: Color = Color.White,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 17.sp,
    emptyText: String = "No changelog for this release.",
) {
    val blocks = remember(markdown) { parseBlocks(markdown.trim()) }

    if (blocks.isEmpty()) {
        Text(text = emptyText, color = color, fontSize = fontSize, lineHeight = lineHeight, modifier = modifier)
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = remember(block) { inline(block.text, strongColor) },
                    color = strongColor,
                    fontSize = when (block.level) {
                        1 -> fontSize * 1.5f
                        2 -> fontSize * 1.32f
                        else -> fontSize * 1.16f
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = remember(block) { inline(block.text, strongColor) },
                    color = color,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )

                is MdBlock.ListItem -> Row(modifier = Modifier.padding(start = (block.indent * 12).dp)) {
                    Text(
                        text = block.marker,
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.width(if (block.marker == "•") 12.dp else 20.dp),
                    )
                    Text(
                        text = remember(block) { inline(block.text, strongColor) },
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                    )
                }

                is MdBlock.Quote -> Row {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .heightIn(min = 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color.copy(alpha = 0.5f))
                    )
                    Text(
                        text = remember(block) { inline(block.text, strongColor) },
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }

                is MdBlock.Code -> Text(
                    text = block.text,
                    color = strongColor.copy(alpha = 0.85f),
                    fontSize = fontSize * 0.95f,
                    lineHeight = lineHeight,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(10.dp),
                )

                is MdBlock.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = block.alt.ifBlank { null },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                MdBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .heightIn(min = 1.dp, max = 1.dp)
                        .background(color.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/**
 * First meaningful line of a release body as plain text - for one-line summaries and the update
 * notification, where markdown syntax would just show up as stray symbols.
 */
fun changelogSummaryLine(markdown: String): String =
    parseBlocks(markdown).firstNotNullOfOrNull { block ->
        when (block) {
            is MdBlock.Heading -> block.text
            is MdBlock.Paragraph -> block.text
            is MdBlock.ListItem -> block.text
            is MdBlock.Quote -> block.text
            else -> null
        }?.let { inline(it, Color.Unspecified).text.trim() }?.takeIf { it.isNotEmpty() }
    }.orEmpty()
