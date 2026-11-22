package com.example.core.util

import java.text.Normalizer

/**
 * Kiểm tra & chuẩn hoá Tiểu sử (bio) khớp backend & web.
 * Nguồn đối chiếu: frontend/src/utils/bio.js, backend/src/utils/input.js#cleanBio, constants.js#PROFILE.
 */
object BioValidator {
    const val BIO_MAX = 300
    const val BIO_MAX_LINES = 6

    private val BIDI_AND_INVISIBLE = Regex("[\\u061C\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF]")
    private val BLANK_LOOKALIKES = Regex("[\\u2800\\u3164\\uFFA0\\u115F\\u1160\\u17B4\\u17B5\\u180E]")
    private val CTRL_CHARS = Regex("[\\u0000-\\u0009\\u000B-\\u001F\\u007F-\\u009F]")
    private val LINE_BREAK_VARIANTS = Regex("\\r\\n?|[\\u2028\\u2029]")
    private val HORIZONTAL_WS = Regex("[^\\S\\n]+")
    private val MULTI_EMPTY_LINES = Regex("\\n{3,}")

    data class BioStats(
        val normalized: String,
        val chars: Int,
        val lines: Int,
        val tooLong: Boolean,
        val tooManyLines: Boolean,
        val valid: Boolean,
    )

    fun normalizeBio(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val nfc = Normalizer.normalize(value, Normalizer.Form.NFC)
        val cleaned = BIDI_AND_INVISIBLE.replace(nfc, "")
            .let { LINE_BREAK_VARIANTS.replace(it, "\n") }
            .let { CTRL_CHARS.replace(it, " ") }
            .let { BLANK_LOOKALIKES.replace(it, " ") }
        return cleaned
            .split("\n")
            .joinToString("\n") { line -> HORIZONTAL_WS.replace(line, " ").trim() }
            .let { MULTI_EMPTY_LINES.replace(it, "\n\n") }
            .trim()
    }

    fun bioStats(value: String?): BioStats {
        val text = normalizeBio(value)
        val chars = text.codePointCount(0, text.length)
        val lines = if (text.isEmpty()) 0 else text.split("\n").size
        val tooLong = chars > BIO_MAX
        val tooManyLines = lines > BIO_MAX_LINES
        return BioStats(
            normalized = text,
            chars = chars,
            lines = lines,
            tooLong = tooLong,
            tooManyLines = tooManyLines,
            valid = !tooLong && !tooManyLines,
        )
    }
}
