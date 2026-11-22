package com.example.core.util

/**
 * Tương thích email đăng ký trước bản v2.0.5 (bỏ dấu chấm / hậu tố +tag).
 * Nguồn đối chiếu: frontend/src/utils/email.js.
 */
object EmailCompat {
    private val PLUS_DOMAINS = setOf(
        "gmail.com",
        "googlemail.com",
        "outlook.com",
        "hotmail.com",
        "live.com",
        "yahoo.com",
        "icloud.com",
        "me.com",
    )

    fun normalizeLegacyEmail(raw: String): String {
        val email = raw.trim().lowercase()
        val at = email.lastIndexOf('@')
        if (at <= 0 || at == email.lastIndex) return email

        var local = email.substring(0, at)
        var domain = email.substring(at + 1)

        if (domain in PLUS_DOMAINS) {
            local = local.substringBefore('+')
        }
        if (domain == "gmail.com" || domain == "googlemail.com") {
            local = local.replace(".", "")
            domain = "gmail.com"
        }
        return "$local@$domain"
    }

    /** Trả danh sách tối đa 2 ứng viên: [email đã nhập, email legacy nếu khác]. */
    fun loginCandidates(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val legacy = normalizeLegacyEmail(trimmed)
        return if (legacy.equals(trimmed, ignoreCase = true)) {
            listOf(trimmed)
        } else {
            listOf(trimmed, legacy)
        }
    }
}
