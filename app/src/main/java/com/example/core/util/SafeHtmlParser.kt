package com.example.core.util

import java.net.URI

/**
 * Bộ phân tích HTML an toàn hoàn toàn bằng Kotlin thuần (không WebView / không JS) cho `contentHtml` do Quill tạo ra.
 * Nguồn đối chiếu: backend/src/services/sanitize.service.js.
 *
 * Bảo mật:
 * - Bỏ hoàn toàn `<script>`, `<style>`, `<iframe>` không hợp lệ, `<object>`, `<embed>`, `<svg>`, `<form>`.
 * - Chỉ giữ `a[href]` với `http://` hoặc `https://` (tuyệt đối chặn `javascript:`, `intent:`, `file:`, `content:`, `data:`).
 * - Chỉ giữ `img`/`video`/`audio`/`source`/`iframe` với `https://`.
 * - Các trường plain text (`title`, `displayName`, `bio`, comment `content`) KHÔNG bao giờ đi qua bộ phân tích này.
 */
object SafeHtmlParser {

    enum class TextAlign { START, CENTER, RIGHT, JUSTIFY }
    enum class InlineStyleType { BOLD, ITALIC, UNDERLINE, STRIKE, CODE, SMALL, LARGE, HUGE, SERIF, MONOSPACE }

    sealed interface InlineSpan {
        data class Text(val text: String) : InlineSpan
        data class Styled(
            val style: InlineStyleType,
            val children: List<InlineSpan>,
        ) : InlineSpan
        data class Link(
            val url: String,
            val children: List<InlineSpan>,
        ) : InlineSpan
        data object LineBreak : InlineSpan
    }

    sealed interface HtmlBlock {
        data class Paragraph(
            val spans: List<InlineSpan>,
            val align: TextAlign = TextAlign.START,
            val indent: Int = 0,
        ) : HtmlBlock

        data class Heading(
            val level: Int,
            val spans: List<InlineSpan>,
            val align: TextAlign = TextAlign.START,
        ) : HtmlBlock

        data class Quote(
            val spans: List<InlineSpan>,
        ) : HtmlBlock

        data class CodeBlock(
            val code: String,
        ) : HtmlBlock

        data class BulletListItem(
            val marker: String,
            val spans: List<InlineSpan>,
            val indent: Int = 0,
        ) : HtmlBlock

        data class Image(
            val url: String,
            val alt: String,
        ) : HtmlBlock

        data class EmbedCard(
            val url: String,
            val kind: EmbedKind,
        ) : HtmlBlock
    }

    enum class EmbedKind {
        IFRAME,
        VIDEO,
        AUDIO,
    }

    private val STRIP_CONTENT_TAGS = setOf("script", "style", "noscript", "svg", "math", "template")
    private val VOID_TAGS = setOf("br", "hr", "img", "source", "input", "meta", "link", "wbr")
    private val TAG_REGEX = Regex("""<(/?)([a-zA-Z0-9]+)([^>]*)>""", RegexOption.DOT_MATCHES_ALL)
    private val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
    private val ALIGN_REGEX = Regex("""ql-align-(center|right|justify)""")
    private val INDENT_REGEX = Regex("""ql-indent-([1-8])""")

    fun isSafeWebLink(raw: String?): String? {
        val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Từ chối control chars và khoảng trắng đầu
        if (candidate.any { it.code < 32 }) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        if (uri.host.isNullOrBlank()) return null
        return candidate
    }

    fun isSafeHttpsMedia(raw: String?): String? {
        val candidate = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (candidate.any { it.code < 32 }) return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return candidate
    }

    private sealed interface Token {
        data class Text(val raw: String) : Token
        data class OpenTag(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean) : Token
        data class CloseTag(val name: String) : Token
    }

    private data class Node(
        val tag: String,
        val attrs: Map<String, String> = emptyMap(),
        val text: String? = null,
        val children: MutableList<Node> = mutableListOf(),
    )

    fun parse(rawHtml: String?): List<HtmlBlock> {
        if (rawHtml.isNullOrBlank()) return emptyList()
        val tokens = tokenize(rawHtml)
        val root = buildTree(tokens)
        val blocks = mutableListOf<HtmlBlock>()
        collectBlocks(root, blocks, parentAlign = TextAlign.START, parentIndent = 0)
        return blocks
    }

    private fun tokenize(html: String): List<Token> {
        val result = mutableListOf<Token>()
        var cursor = 0
        var skippingTag: String? = null

        for (match in TAG_REGEX.findAll(html)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor && skippingTag == null) {
                val textSlice = html.substring(cursor, start)
                if (textSlice.isNotEmpty()) result.add(Token.Text(unescapeHtmlEntities(textSlice)))
            }
            cursor = end

            val isClosing = match.groupValues[1] == "/"
            val name = match.groupValues[2].lowercase()
            val rawAttrs = match.groupValues[3]
            val selfClosing = rawAttrs.trimEnd().endsWith("/") || name in VOID_TAGS

            if (skippingTag != null) {
                if (isClosing && name == skippingTag) {
                    skippingTag = null
                }
                continue
            }

            if (!isClosing && name in STRIP_CONTENT_TAGS && !selfClosing) {
                skippingTag = name
                continue
            }
            if (name in STRIP_CONTENT_TAGS) continue

            if (isClosing) {
                result.add(Token.CloseTag(name))
            } else {
                result.add(Token.OpenTag(name, parseAttrs(rawAttrs), selfClosing))
            }
        }
        if (cursor < html.length && skippingTag == null) {
            result.add(Token.Text(unescapeHtmlEntities(html.substring(cursor))))
        }
        return result
    }

    private fun parseAttrs(rawAttrs: String): Map<String, String> {
        if (rawAttrs.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (m in ATTR_REGEX.findAll(rawAttrs)) {
            val k = m.groupValues[1].lowercase()
            if (k.startsWith("on")) continue
            val v = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
            map[k] = unescapeHtmlEntities(v)
        }
        return map
    }

    private fun buildTree(tokens: List<Token>): Node {
        val root = Node("#root")
        val stack = ArrayDeque<Node>()
        stack.addLast(root)
        for (token in tokens) {
            when (token) {
                is Token.Text -> {
                    stack.last().children.add(Node("#text", text = token.raw))
                }
                is Token.OpenTag -> {
                    val node = Node(token.name, token.attrs)
                    stack.last().children.add(node)
                    if (!token.selfClosing) {
                        stack.addLast(node)
                    }
                }
                is Token.CloseTag -> {
                    val idx = stack.indexOfLast { it.tag == token.name }
                    if (idx > 0) {
                        while (stack.size > idx) {
                            stack.removeLast()
                        }
                    }
                }
            }
        }
        return root
    }

    private fun collectBlocks(
        node: Node,
        out: MutableList<HtmlBlock>,
        parentAlign: TextAlign,
        parentIndent: Int,
    ) {
        val inlineAcc = mutableListOf<InlineSpan>()

        fun flushInline() {
            val cleaned = trimEdgeWhitespace(inlineAcc)
            inlineAcc.clear()
            if (cleaned.isNotEmpty() && !isVisuallyBlank(cleaned)) {
                out.add(HtmlBlock.Paragraph(cleaned, align = parentAlign, indent = parentIndent))
            }
        }

        for (child in node.children) {
            when (child.tag) {
                "#text" -> {
                    val t = child.text.orEmpty()
                    if (t.isNotEmpty()) inlineAcc.add(InlineSpan.Text(collapseInlineSpaces(t)))
                }
                "br" -> inlineAcc.add(InlineSpan.LineBreak)
                "strong", "b", "em", "i", "u", "s", "strike", "del", "code", "span", "a" -> {
                    if (child.containsMediaOrBlock()) {
                        flushInline()
                        collectBlocks(child, out, parentAlign, parentIndent)
                    } else {
                        inlineAcc.addAll(nodeToInlineSpans(child))
                    }
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flushInline()
                    val level = child.tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 2
                    val align = extractAlign(child.attrs["class"], parentAlign)
                    val spans = trimEdgeWhitespace(child.children.flatMap { nodeToInlineSpans(it) })
                    if (spans.isNotEmpty() && !isVisuallyBlank(spans)) {
                        out.add(HtmlBlock.Heading(level = level, spans = spans, align = align))
                    }
                }
                "blockquote" -> {
                    flushInline()
                    val spans = trimEdgeWhitespace(child.children.flatMap { nodeToInlineSpans(it) })
                    if (spans.isNotEmpty() && !isVisuallyBlank(spans)) {
                        out.add(HtmlBlock.Quote(spans))
                    }
                }
                "pre" -> {
                    flushInline()
                    val text = child.extractPlainText().trimEnd()
                    if (text.isNotBlank()) out.add(HtmlBlock.CodeBlock(text))
                }
                "div" -> {
                    val cls = child.attrs["class"].orEmpty()
                    if (cls.contains("ql-code-block-container") || cls.contains("ql-code-block")) {
                        flushInline()
                        val code = child.extractCodeLines().trimEnd()
                        if (code.isNotBlank()) out.add(HtmlBlock.CodeBlock(code))
                    } else {
                        flushInline()
                        val align = extractAlign(cls, parentAlign)
                        val indent = extractIndent(cls, parentIndent)
                        collectBlocks(child, out, align, indent)
                    }
                }
                "p" -> {
                    flushInline()
                    val cls = child.attrs["class"].orEmpty()
                    val align = extractAlign(cls, parentAlign)
                    val indent = extractIndent(cls, parentIndent)
                    if (child.containsMediaOrBlock()) {
                        collectBlocks(child, out, align, indent)
                    } else {
                        val spans = trimEdgeWhitespace(child.children.flatMap { nodeToInlineSpans(it) })
                        if (spans.isNotEmpty() && !isVisuallyBlank(spans)) {
                            out.add(HtmlBlock.Paragraph(spans, align = align, indent = indent))
                        }
                    }
                }
                "ul", "ol" -> {
                    flushInline()
                    val isOrdered = child.tag == "ol"
                    var index = 1
                    for (li in child.children.filter { it.tag == "li" }) {
                        val dataList = li.attrs["data-list"]
                        val marker = when (dataList) {
                            "checked" -> "[x]"
                            "unchecked" -> "[ ]"
                            "bullet" -> "•"
                            "ordered" -> "${index++}."
                            else -> if (isOrdered) "${index++}." else "•"
                        }
                        val indent = extractIndent(li.attrs["class"], parentIndent)
                        val spans = trimEdgeWhitespace(li.children.flatMap { nodeToInlineSpans(it) })
                        if (spans.isNotEmpty() && !isVisuallyBlank(spans)) {
                            out.add(HtmlBlock.BulletListItem(marker = marker, spans = spans, indent = indent))
                        }
                    }
                }
                "img" -> {
                    flushInline()
                    val safeUrl = isSafeHttpsMedia(child.attrs["src"])
                    if (safeUrl != null) {
                        out.add(HtmlBlock.Image(url = safeUrl, alt = child.attrs["alt"].orEmpty()))
                    }
                }
                "iframe" -> {
                    flushInline()
                    val safeUrl = isSafeHttpsMedia(child.attrs["src"])
                    if (safeUrl != null) {
                        out.add(HtmlBlock.EmbedCard(url = safeUrl, kind = EmbedKind.IFRAME))
                    }
                }
                "video" -> {
                    flushInline()
                    val src = isSafeHttpsMedia(child.attrs["src"])
                        ?: child.children.firstOrNull { it.tag == "source" }?.let { isSafeHttpsMedia(it.attrs["src"]) }
                    if (src != null) {
                        out.add(HtmlBlock.EmbedCard(url = src, kind = EmbedKind.VIDEO))
                    }
                }
                "audio" -> {
                    flushInline()
                    val src = isSafeHttpsMedia(child.attrs["src"])
                        ?: child.children.firstOrNull { it.tag == "source" }?.let { isSafeHttpsMedia(it.attrs["src"]) }
                    if (src != null) {
                        out.add(HtmlBlock.EmbedCard(url = src, kind = EmbedKind.AUDIO))
                    }
                }
                else -> {
                    // Thẻ khác ngoài block chuẩn: đi tiếp vào con
                    if (child.containsMediaOrBlock()) {
                        flushInline()
                        collectBlocks(child, out, parentAlign, parentIndent)
                    } else {
                        inlineAcc.addAll(nodeToInlineSpans(child))
                    }
                }
            }
        }
        flushInline()
    }

    private fun Node.containsMediaOrBlock(): Boolean = children.any { child ->
        child.tag in setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote", "pre", "img", "iframe", "video", "audio") ||
            child.containsMediaOrBlock()
    }

    private fun Node.extractPlainText(): String {
        if (tag == "#text") return text.orEmpty()
        if (tag == "br") return "\n"
        return children.joinToString("") { it.extractPlainText() }
    }

    private fun Node.extractCodeLines(): String {
        if (tag == "#text") return text.orEmpty()
        if (tag == "br") return "\n"
        if (tag == "div" && attrs["class"].orEmpty().contains("ql-code-block") && !attrs["class"].orEmpty().contains("container")) {
            return children.joinToString("") { it.extractPlainText() } + "\n"
        }
        return children.joinToString("") { it.extractCodeLines() }
    }

    private fun nodeToInlineSpans(node: Node): List<InlineSpan> {
        return when (node.tag) {
            "#text" -> {
                val collapsed = collapseInlineSpaces(node.text.orEmpty())
                if (collapsed.isEmpty()) emptyList() else listOf(InlineSpan.Text(collapsed))
            }
            "br" -> listOf(InlineSpan.LineBreak)
            "strong", "b" -> wrapStyled(InlineStyleType.BOLD, node)
            "em", "i" -> wrapStyled(InlineStyleType.ITALIC, node)
            "u" -> wrapStyled(InlineStyleType.UNDERLINE, node)
            "s", "strike", "del" -> wrapStyled(InlineStyleType.STRIKE, node)
            "code" -> wrapStyled(InlineStyleType.CODE, node)
            "a" -> {
                val safeHref = isSafeWebLink(node.attrs["href"])
                val inner = node.children.flatMap { nodeToInlineSpans(it) }
                if (inner.isEmpty()) {
                    emptyList()
                } else if (safeHref != null) {
                    listOf(InlineSpan.Link(safeHref, inner))
                } else {
                    inner
                }
            }
            "span" -> {
                val cls = node.attrs["class"].orEmpty()
                var spans = node.children.flatMap { nodeToInlineSpans(it) }
                if (spans.isEmpty()) return emptyList()
                if (cls.contains("ql-size-small")) spans = listOf(InlineSpan.Styled(InlineStyleType.SMALL, spans))
                else if (cls.contains("ql-size-large")) spans = listOf(InlineSpan.Styled(InlineStyleType.LARGE, spans))
                else if (cls.contains("ql-size-huge")) spans = listOf(InlineSpan.Styled(InlineStyleType.HUGE, spans))
                if (cls.contains("ql-font-serif")) spans = listOf(InlineSpan.Styled(InlineStyleType.SERIF, spans))
                else if (cls.contains("ql-font-monospace")) spans = listOf(InlineSpan.Styled(InlineStyleType.MONOSPACE, spans))
                spans
            }
            else -> node.children.flatMap { nodeToInlineSpans(it) }
        }
    }

    private fun wrapStyled(type: InlineStyleType, node: Node): List<InlineSpan> {
        val inner = node.children.flatMap { nodeToInlineSpans(it) }
        return if (inner.isEmpty()) emptyList() else listOf(InlineSpan.Styled(type, inner))
    }

    private fun extractAlign(cls: String?, fallback: TextAlign): TextAlign {
        if (cls.isNullOrBlank()) return fallback
        val match = ALIGN_REGEX.find(cls) ?: return fallback
        return when (match.groupValues[1]) {
            "center" -> TextAlign.CENTER
            "right" -> TextAlign.RIGHT
            "justify" -> TextAlign.JUSTIFY
            else -> fallback
        }
    }

    private fun extractIndent(cls: String?, fallback: Int): Int {
        if (cls.isNullOrBlank()) return fallback
        val match = INDENT_REGEX.find(cls) ?: return fallback
        return match.groupValues[1].toIntOrNull()?.coerceIn(0, 8) ?: fallback
    }

    private fun collapseInlineSpaces(s: String): String = s.replace(Regex("""[ \t\r\n]+"""), " ")

    private fun trimEdgeWhitespace(spans: List<InlineSpan>): List<InlineSpan> {
        if (spans.isEmpty()) return spans
        val out = spans.toMutableList()
        while (out.firstOrNull() is InlineSpan.Text && (out.first() as InlineSpan.Text).text.isBlank()) {
            out.removeAt(0)
        }
        while (out.lastOrNull() is InlineSpan.Text && (out.last() as InlineSpan.Text).text.isBlank()) {
            out.removeAt(out.lastIndex)
        }
        return out
    }

    private fun isVisuallyBlank(spans: List<InlineSpan>): Boolean = spans.all {
        when (it) {
            is InlineSpan.Text -> it.text.isBlank()
            InlineSpan.LineBreak -> true
            is InlineSpan.Styled -> isVisuallyBlank(it.children)
            is InlineSpan.Link -> isVisuallyBlank(it.children)
        }
    }

    private fun unescapeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        return input
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
