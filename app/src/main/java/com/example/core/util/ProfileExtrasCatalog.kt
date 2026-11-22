package com.example.core.util

/**
 * Danh mục 9 khoá "Thông tin mở rộng" trên hồ sơ.
 * Nguồn đối chiếu: backend/src/utils/profileExtras.js, frontend/src/utils/profileExtras.js.
 */
data class ExtraFieldMeta(
    val key: String,
    val label: String,
    val defaultOn: Boolean,
    val isPrivate: Boolean,
    val hint: String,
)

data class ExtraDisplayRow(
    val key: String,
    val label: String,
    val value: String,
)

object ProfileExtrasCatalog {
    val EXTRA_FIELDS: List<ExtraFieldMeta> = listOf(
        ExtraFieldMeta("displayName", "Tên hiển thị", defaultOn = true, isPrivate = false, hint = "Tên bạn đang dùng trên Twelve."),
        ExtraFieldMeta("accountGroup", "Nhóm tài khoản", defaultOn = true, isPrivate = false, hint = "Nhóm của tài khoản (SA, RA hoặc LLA)."),
        ExtraFieldMeta("accountRole", "Vai trò tài khoản", defaultOn = true, isPrivate = false, hint = "Vai trò của tài khoản, ví dụ Người dùng, Người dùng Pro, Quản trị viên."),
        ExtraFieldMeta("birthday", "Ngày sinh", defaultOn = false, isPrivate = true, hint = "Chỉ lộ ra khi bạn bật."),
        ExtraFieldMeta("createdAt", "Ngày tham gia", defaultOn = true, isPrivate = false, hint = "Ngày bạn tạo tài khoản Twelve."),
        ExtraFieldMeta("gender", "Giới tính", defaultOn = false, isPrivate = true, hint = "Chỉ lộ ra khi bạn bật."),
        ExtraFieldMeta("status", "Trạng thái tài khoản", defaultOn = false, isPrivate = true, hint = "Đang hoạt động, bị tạm khóa… Chỉ lộ ra khi bạn bật."),
        ExtraFieldMeta("uid", "UID", defaultOn = false, isPrivate = true, hint = "Mã định danh tài khoản. Chỉ lộ ra khi bạn bật."),
        ExtraFieldMeta("achievements", "Thành tích", defaultOn = true, isPrivate = false, hint = "Điểm tài khoản và các tuần bạn lọt top 10 người tăng điểm mạnh nhất ở trang Xu hướng."),
    )

    val GROUP_LABELS: Map<String, String> = mapOf(
        "SA" to "Tài khoản cấp cao (SA)",
        "RA" to "Tài khoản thường (RA)",
        "LLA" to "Tài khoản thử nghiệm (LLA)",
    )

    val ROLE_LABELS: Map<String, String> = mapOf(
        "admin" to "Quản trị viên",
        "dev" to "Lập trình viên",
        "moderator" to "Điều hành viên",
        "pro_user" to "Người dùng Pro",
        "user" to "Người dùng",
        "test" to "Tài khoản thử nghiệm",
    )

    val GENDER_LABELS: Map<String, String> = mapOf(
        "male" to "Nam",
        "female" to "Nữ",
        "other" to "Khác",
    )

    val STATUS_LABELS: Map<String, String> = mapOf(
        "active" to "Đang hoạt động",
        "suspended" to "Bị tạm khóa",
        "pending" to "Chờ kích hoạt",
        "deleting" to "Đang xóa",
    )

    fun resolveExtras(stored: Map<String, Boolean>?): Map<String, Boolean> {
        val src = stored ?: emptyMap()
        return EXTRA_FIELDS.associate { field ->
            field.key to (src[field.key] ?: field.defaultOn)
        }
    }

    fun formatExtraValue(key: String, rawValue: String): String = when (key) {
        "accountGroup" -> GROUP_LABELS[rawValue] ?: rawValue
        "accountRole" -> ROLE_LABELS[rawValue] ?: rawValue
        "birthday" -> TimeFormatter.formatBirthday(rawValue)
        "createdAt" -> TimeFormatter.formatJoinDate(rawValue)
        "gender" -> GENDER_LABELS[rawValue] ?: rawValue
        "status" -> STATUS_LABELS[rawValue] ?: rawValue
        else -> rawValue
    }

    fun buildExtraRows(fields: Map<String, String?>?): List<ExtraDisplayRow> {
        if (fields == null) return emptyList()
        return EXTRA_FIELDS
            .filter { it.key != "achievements" }
            .mapNotNull { meta ->
                val raw = fields[meta.key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val formatted = formatExtraValue(meta.key, raw)
                if (formatted.isBlank()) null else ExtraDisplayRow(meta.key, meta.label, formatted)
            }
    }
}
