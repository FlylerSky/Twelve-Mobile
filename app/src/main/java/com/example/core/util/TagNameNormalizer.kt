package com.example.core.util

/**
 * Chuẩn hoá Tag Name Search dùng chung giữa /users/me (backend trả không có "@")
 * và hồ sơ công khai / tác giả bài viết (backend trả có "@").
 * Nguồn đối chiếu: backend/src/utils/helpers.js & frontend/src/utils/profilePath.js.
 */
object TagNameNormalizer {
    private val TAG_REGEX = Regex("^[a-z0-9_]{3,20}$")

    /** Trả về dạng chuẩn chữ thường không có tiền tố "@", ví dụ "alice_12". */
    fun toBareTag(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().removePrefix("@").trim().lowercase()
    }

    /** Trả về dạng hiển thị có "@" phía trước (hoặc null nếu rỗng), ví dụ "@alice_12". */
    fun toDisplayTag(raw: String?): String? {
        val bare = toBareTag(raw)
        return if (bare.isEmpty()) null else "@$bare"
    }

    /** Kiểm tra tag hợp lệ theo quy tắc backend/src/utils/helpers.js#isValidTagName. */
    fun isValid(raw: String?): Boolean {
        val bare = toBareTag(raw)
        return TAG_REGEX.matches(bare)
    }

    /** Tạo đường dẫn hồ sơ công khai dạng `/u/@tag` hoặc `/u/:uid` giống frontend/src/utils/profilePath.js. */
    fun buildWebProfilePath(tagName: String?, uid: String): String {
        val bare = toBareTag(tagName)
        return if (bare.isNotEmpty() && TAG_REGEX.matches(bare)) {
            "/u/@$bare"
        } else {
            "/u/${uid.trim()}"
        }
    }
}
