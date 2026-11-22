package com.example.core.util

/**
 * Chuyển đổi qua lại giữa văn bản soạn thảo nhẹ (hỗ trợ tiêu đề, trích dẫn, danh sách, code, in đậm/nghiêng, link, ảnh)
 * và HTML tương thích Quill (`<p>`, `<h2>`, `<h3>`, `<blockquote>`, `<ul><li>`, `<pre><code>`, `<strong>`, `<em>`, `<a>`, `<img>`).
 *
 * Nguồn đối chiếu: backend/src/services/sanitize.service.js (whitelist thẻ & URL an toàn).
 */
object QuillHtmlCodec {

    private val LINK_MD_REGEX = Regex("""\[([^\]\n]+)\]\(([^\s)]+)\)""")
    private val IMAGE_LINE_REGEX = Regex("""^!\[([^\]]*)\]\((https://[^\s)]+)\)$""")
    private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
    private val ITALIC_REGEX = Regex("""(?<!\*)\*([^\s*][^*]*?)\*(?!\*)""")
    private val CODE_INLINE_REGEX = Regex("""`([^`\n]+)`""")

    fun toQuillHtml(rawEditorText: String): String {
        val normalized = rawEditorText.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return ""

        val lines = normalized.split('\n')
        val out = StringBuilder()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            // Khối mã ```...```
            if (trimmed.startsWith("```")) {
                val codeLines = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    codeLines.add(lines[index])
                    index++
                }
                if (index < lines.size) index++ // bỏ dòng đóng ```
                out.append("<pre><code>").append(escapeHtml(codeLines.joinToString("\n"))).append("</code></pre>")
                continue
            }

            if (trimmed.isEmpty()) {
                index++
                continue
            }

            // Ảnh độc lập ![alt](https://...)
            val imgMatch = IMAGE_LINE_REGEX.matchEntire(trimmed)
            if (imgMatch != null) {
                val safeSrc = SafeHtmlParser.isSafeHttpsMedia(imgMatch.groupValues[2])
                if (safeSrc != null) {
                    val alt = escapeHtml(imgMatch.groupValues[1])
                    out.append("<p><img src=\"").append(escapeHtml(safeSrc)).append("\" alt=\"").append(alt).append("\"></p>")
                    index++
                    continue
                }
            }

            // Danh sách gạch đầu dòng liên tiếp (- item hoặc * item)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                out.append("<ul>")
                while (index < lines.size) {
                    val itemTrim = lines[index].trim()
                    if (!itemTrim.startsWith("- ") && !itemTrim.startsWith("* ")) break
                    val itemText = itemTrim.drop(2).trim()
                    out.append("<li>").append(formatInlineToHtml(itemText)).append("</li>")
                    index++
                }
                out.append("</ul>")
                continue
            }

            // Danh sách đánh số liên tiếp (1. item)
            if (trimmed.matches(Regex("""^\d+\.\s+.+"""))) {
                out.append("<ol>")
                while (index < lines.size) {
                    val itemTrim = lines[index].trim()
                    val m = Regex("""^\d+\.\s+(.+)$""").matchEntire(itemTrim) ?: break
                    out.append("<li>").append(formatInlineToHtml(m.groupValues[1].trim())).append("</li>")
                    index++
                }
                out.append("</ol>")
                continue
            }

            // Tiêu đề H2 / H3
            if (trimmed.startsWith("### ")) {
                out.append("<h3>").append(formatInlineToHtml(trimmed.removePrefix("### ").trim())).append("</h3>")
                index++
                continue
            }
            if (trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                val headingBody = if (trimmed.startsWith("## ")) trimmed.removePrefix("## ") else trimmed.removePrefix("# ")
                out.append("<h2>").append(formatInlineToHtml(headingBody.trim())).append("</h2>")
                index++
                continue
            }

            // Trích dẫn >
            if (trimmed.startsWith("> ")) {
                val quoteLines = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith("> ")) {
                    quoteLines.add(formatInlineToHtml(lines[index].trim().removePrefix("> ").trim()))
                    index++
                }
                out.append("<blockquote>").append(quoteLines.joinToString("<br>")).append("</blockquote>")
                continue
            }

            // Đoạn văn bản thường (gộp các dòng không trống liên tiếp cho tự nhiên, dùng <br>)
            val paraLines = mutableListOf<String>()
            while (index < lines.size) {
                val t = lines[index].trim()
                if (t.isEmpty() ||
                    t.startsWith("```") ||
                    t.startsWith("## ") ||
                    t.startsWith("### ") ||
                    t.startsWith("# ") ||
                    t.startsWith("> ") ||
                    t.startsWith("- ") ||
                    t.startsWith("* ") ||
                    t.matches(Regex("""^\d+\.\s+.+""")) ||
                    IMAGE_LINE_REGEX.matches(t)
                ) {
                    break
                }
                paraLines.add(formatInlineToHtml(lines[index].trimEnd()))
                index++
            }
            if (paraLines.isNotEmpty()) {
                out.append("<p>").append(paraLines.joinToString("<br>")).append("</p>")
            }
        }

        return out.toString()
    }

    fun isHtmlVisuallyEmpty(html: String): Boolean {
        val blocks = SafeHtmlParser.parse(html)
        return blocks.isEmpty()
    }

    /** Chuyển HTML bài viết sẵn có về dạng văn bản soạn thảo khi mở chế độ Chỉnh sửa bài viết. */
    fun fromHtmlToEditorText(html: String): String {
        val blocks = SafeHtmlParser.parse(html)
        if (blocks.isEmpty()) return ""
        return blocks.joinToString("\n\n") { block ->
            when (block) {
                is SafeHtmlParser.HtmlBlock.Heading -> {
                    val prefix = if (block.level <= 2) "## " else "### "
                    prefix + spansToEditor(block.spans)
                }
                is SafeHtmlParser.HtmlBlock.Paragraph -> spansToEditor(block.spans)
                is SafeHtmlParser.HtmlBlock.Quote ->
                    spansToEditor(block.spans).lines().joinToString("\n") { "> $it" }
                is SafeHtmlParser.HtmlBlock.CodeBlock -> "```\n${block.code}\n```"
                is SafeHtmlParser.HtmlBlock.BulletListItem -> {
                    val prefix = if (block.marker.endsWith(".")) "${block.marker} " else "- "
                    prefix + spansToEditor(block.spans)
                }
                is SafeHtmlParser.HtmlBlock.Image -> "![${block.alt}](${block.url})"
                is SafeHtmlParser.HtmlBlock.EmbedCard -> "[${block.url}](${block.url})"
            }
        }
    }

    private fun spansToEditor(spans: List<SafeHtmlParser.InlineSpan>): String {
        return spans.joinToString("") { span ->
            when (span) {
                is SafeHtmlParser.InlineSpan.Text -> span.text
                SafeHtmlParser.InlineSpan.LineBreak -> "\n"
                is SafeHtmlParser.InlineSpan.Link -> {
                    val label = spansToEditor(span.children).ifBlank { span.url }
                    "[$label](${span.url})"
                }
                is SafeHtmlParser.InlineSpan.Styled -> {
                    val inner = spansToEditor(span.children)
                    when (span.style) {
                        SafeHtmlParser.InlineStyleType.BOLD -> "**$inner**"
                        SafeHtmlParser.InlineStyleType.ITALIC -> "*$inner*"
                        SafeHtmlParser.InlineStyleType.CODE -> "`$inner`"
                        else -> inner
                    }
                }
            }
        }
    }

    private fun formatInlineToHtml(raw: String): String {
        // Thay thế an toàn: bảo vệ token link & inline code trước khi escape các ký tự HTML còn lại.
        val placeholders = mutableListOf<String>()
        var working = raw

        // 1. Liên kết [nhãn](http/https://url)
        working = LINK_MD_REGEX.replace(working) { m ->
            val safeHref = SafeHtmlParser.isSafeWebLink(m.groupValues[2])
            if (safeHref == null) {
                escapeHtml(m.groupValues[1])
            } else {
                val token = "\u0000P${placeholders.size}\u0000"
                placeholders.add("<a href=\"${escapeHtml(safeHref)}\" target=\"_blank\" rel=\"noopener noreferrer\">${escapeHtml(m.groupValues[1])}</a>")
                token
            }
        }

        // 2. Mã nội dòng `code`
        working = CODE_INLINE_REGEX.replace(working) { m ->
            val token = "\u0000P${placeholders.size}\u0000"
            placeholders.add("<code>${escapeHtml(m.groupValues[1])}</code>")
            token
        }

        // Escape phần chữ thường còn lại trước khi bọc **bold** / *italic*
        working = escapeHtml(working)

        // 3. In đậm **text** và nghiêng *text*
        working = BOLD_REGEX.replace(working) { "<strong>${it.groupValues[1]}</strong>" }
        working = ITALIC_REGEX.replace(working) { "<em>${it.groupValues[1]}</em>" }

        // Khôi phục placeholders
        placeholders.forEachIndexed { i, htmlFrag ->
            working = working.replace("\u0000P$i\u0000", htmlFrag)
        }
        return working
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
