package com.example.core.util

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Chuẩn hoá & kiểm tra các trường thông tin tài khoản ở client trước khi gửi API.
 * Nguồn đối chiếu: backend/src/utils/input.js, backend/src/middleware/validators.js,
 * frontend/src/pages/settings/ChangePasswordPanel.jsx.
 */
object AccountValidators {
    const val DISPLAY_NAME_MIN = 1
    const val DISPLAY_NAME_MAX = 50
    const val REAL_NAME_MIN = 1
    const val REAL_NAME_MAX = 100
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 128

    private val UNSAFE_DISPLAY_CHARS = Regex("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}\\p{Co}]")
    private val BLANK_LOOKALIKES = Regex("[\\u2800\\u3164\\uFFA0\\u115F\\u1160\\u17B4\\u17B5\\u180E]")
    private val MULTISPACE = Regex("\\s+")
    private val BIRTHDAY_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val MIN_BIRTHDAY = LocalDate.of(1900, 1, 1)

    val GENDER_OPTIONS = listOf("male", "female", "other")

    fun cleanDisplayText(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val nfc = Normalizer.normalize(raw, Normalizer.Form.NFC)
        return nfc
            .let { UNSAFE_DISPLAY_CHARS.replace(it, " ") }
            .let { BLANK_LOOKALIKES.replace(it, " ") }
            .let { MULTISPACE.replace(it, " ") }
            .trim()
    }

    fun validateDisplayName(raw: String?): String? {
        val cleaned = cleanDisplayText(raw)
        val len = cleaned.codePointCount(0, cleaned.length)
        return when {
            len < DISPLAY_NAME_MIN -> "Tên hiển thị không được để trống."
            len > DISPLAY_NAME_MAX -> "Tên hiển thị từ 1–$DISPLAY_NAME_MAX ký tự."
            else -> null
        }
    }

    fun validateRealName(raw: String?): String? {
        val cleaned = cleanDisplayText(raw)
        val len = cleaned.codePointCount(0, cleaned.length)
        return when {
            len < REAL_NAME_MIN -> "Họ và tên không được để trống."
            len > REAL_NAME_MAX -> "Họ và tên phải từ 1–$REAL_NAME_MAX ký tự."
            else -> null
        }
    }

    fun validateBirthday(raw: String?, todayUtc: LocalDate = LocalDate.now(ZoneOffset.UTC)): String? {
        val trimmed = raw?.trim().orEmpty()
        val match = BIRTHDAY_REGEX.matchEntire(trimmed)
            ?: return "Ngày sinh không hợp lệ (định dạng YYYY-MM-DD)."
        val (yStr, mStr, dStr) = match.destructured
        val date = runCatching {
            LocalDate.of(yStr.toInt(), mStr.toInt(), dStr.toInt())
        }.getOrNull() ?: return "Ngày sinh không tồn tại trong lịch."

        if (date.isBefore(MIN_BIRTHDAY)) return "Ngày sinh không hợp lệ."
        if (date.isAfter(todayUtc)) return "Ngày sinh không được ở tương lai."
        return null
    }

    fun validateNewPassword(current: String, next: String, confirm: String): String? {
        if (current.isEmpty()) return "Vui lòng nhập mật khẩu hiện tại."
        if (next.length < PASSWORD_MIN) return "Mật khẩu mới phải có ít nhất $PASSWORD_MIN ký tự."
        if (next.length > PASSWORD_MAX) return "Mật khẩu mới tối đa $PASSWORD_MAX ký tự."
        if (next == current) return "Mật khẩu mới phải khác mật khẩu hiện tại."
        if (next != confirm) return "Mật khẩu nhập lại không khớp."
        return null
    }

    fun isValidCoverUrlInput(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.startsWith("https://", ignoreCase = true) && trimmed.length > "https://".length
    }
}
