package com.example.core.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Giới hạn nội dung phía client (UX cảnh báo sớm — backend vẫn là nguồn kiểm tra bắt buộc).
 * Nguồn đối chiếu: backend/src/utils/constants.js#CONTENT_LIMITS,
 * backend/src/utils/helpers.js, frontend/src/utils/contentLimits.js.
 */
object ContentLimits {
    const val TITLE_MIN = 5
    const val TITLE_MAX = 200
    const val COMMENT_MAX = 2000
    const val COVER_IMAGE_URL_MAX = 2000
    const val HASHTAG_MAX_COUNT = 20
    const val HASHTAG_MAX_LENGTH = 30
    const val HTML_MAX_CHARS = 250_000
    const val HTML_MAX_ELEMENTS = 5_000
    const val HTML_MAX_MEDIA = 200
    private const val WARN_RATIO = 0.8

    private val HASHTAG_REGEX = Regex("""^#[\p{L}\p{N}_]{1,30}$""")
    private val HTML_TAG_REGEX = Regex("""<([A-Za-z][A-Za-z0-9:-]*)""")
    private val MEDIA_TAGS = setOf("img", "video", "audio", "source", "iframe")
    private val viNumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))

    enum class Severity { OK, WARN, ERROR }

    data class HtmlAssessment(
        val chars: Int,
        val elements: Int,
        val media: Int,
        val ratio: Double,
        val severity: Severity,
        val message: String,
    )

    data class HashtagsValidation(
        val hashtags: List<String>,
        val error: String?,
    )

    fun validateTitle(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Vui lòng nhập tiêu đề bài viết."
        if (trimmed.length < TITLE_MIN || trimmed.length > TITLE_MAX) {
            return "Tiêu đề phải từ $TITLE_MIN-$TITLE_MAX ký tự."
        }
        return null
    }

    fun validateCoverUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length > COVER_IMAGE_URL_MAX) {
            return "Đường dẫn ảnh bìa quá dài (tối đa $COVER_IMAGE_URL_MAX ký tự)."
        }
        if (SafeHtmlParser.isSafeHttpsMedia(trimmed) == null) {
            return "Ảnh bìa phải là đường dẫn https:// hợp lệ."
        }
        return null
    }

    fun parseAndValidateHashtags(rawInput: String): HashtagsValidation {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return HashtagsValidation(emptyList(), null)
        val tokens = trimmed.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.size > HASHTAG_MAX_COUNT) {
            return HashtagsValidation(emptyList(), "Tối đa $HASHTAG_MAX_COUNT hashtag (đang có ${tokens.size}).")
        }
        val seen = HashSet<String>()
        val clean = mutableListOf<String>()
        for (token in tokens) {
            if (!HASHTAG_REGEX.matches(token)) {
                return HashtagsValidation(
                    emptyList(),
                    "Hashtag \"$token\" không hợp lệ — phải có dạng #tag, chỉ gồm chữ/số/gạch dưới, tối đa $HASHTAG_MAX_LENGTH ký tự sau #.",
                )
            }
            val key = token.lowercase()
            if (seen.add(key)) {
                clean.add(token)
            }
        }
        return HashtagsValidation(clean, null)
    }

    fun validateComment(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Nội dung bình luận không được để trống."
        if (raw.length > COMMENT_MAX) {
            return "Bình luận quá dài (tối đa ${viNumberFormat.format(COMMENT_MAX)} ký tự)."
        }
        return null
    }

    fun assessContentHtml(html: String): HtmlAssessment {
        val chars = html.length
        var elements = 0
        var media = 0
        for (match in HTML_TAG_REGEX.findAll(html)) {
            elements++
            if (match.groupValues[1].lowercase() in MEDIA_TAGS) {
                media++
            }
        }
        val checks = listOf(
            chars.toDouble() / HTML_MAX_CHARS to
                "Nội dung quá dài (${viNumberFormat.format(chars)} / ${viNumberFormat.format(HTML_MAX_CHARS)} ký tự). Hãy rút gọn trước khi đăng.",
            elements.toDouble() / HTML_MAX_ELEMENTS to
                "Nội dung có quá nhiều thành phần định dạng (${viNumberFormat.format(elements)} / ${viNumberFormat.format(HTML_MAX_ELEMENTS)}). Hãy bỏ bớt định dạng.",
            media.toDouble() / HTML_MAX_MEDIA to
                "Nội dung chứa quá nhiều ảnh/video/âm thanh ($media / $HTML_MAX_MEDIA).",
        )
        val worst = checks.maxBy { it.first }
        val severity = when {
            worst.first > 1.0 -> Severity.ERROR
            worst.first >= WARN_RATIO -> Severity.WARN
            else -> Severity.OK
        }
        val message = when (severity) {
            Severity.ERROR -> worst.second
            Severity.WARN -> "Sắp chạm giới hạn nội dung (${(worst.first * 100).toInt()}%)."
            Severity.OK -> ""
        }
        return HtmlAssessment(
            chars = chars,
            elements = elements,
            media = media,
            ratio = worst.first,
            severity = severity,
            message = message,
        )
    }
}
