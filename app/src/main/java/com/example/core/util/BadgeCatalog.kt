package com.example.core.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Hệ thống huy hiệu Twelve — ánh xạ id -> biểu tượng + nhãn + mô tả tiếng Việt.
 * Nguồn đối chiếu: backend/src/utils/badges.js & frontend/src/utils/badges.js.
 */
data class BadgeMeta(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val description: String,
    val colorRole: BadgeColorRole,
)

enum class BadgeColorRole {
    PRIMARY,
    ERROR,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
}

object BadgeCatalog {
    private val staffByRole = mapOf(
        "admin" to "admin",
        "dev" to "dev",
        "moderator" to "moderator",
    )

    private val roleToGroup = mapOf(
        "admin" to "SA",
        "dev" to "SA",
        "moderator" to "SA",
        "pro_user" to "RA",
        "user" to "RA",
        "test" to "LLA",
    )

    private val catalog = mapOf(
        "sa" to BadgeMeta(
            id = "sa",
            icon = Icons.Rounded.Shield,
            label = "Tài khoản cấp cao",
            description = "Thuộc nhóm tài khoản cấp cao (SA) của Twelve.",
            colorRole = BadgeColorRole.PRIMARY,
        ),
        "admin" to BadgeMeta(
            id = "admin",
            icon = Icons.Rounded.AdminPanelSettings,
            label = "Quản trị viên",
            description = "Quản trị Twelve — toàn quyền quản lý hệ thống.",
            colorRole = BadgeColorRole.ERROR,
        ),
        "dev" to BadgeMeta(
            id = "dev",
            icon = Icons.Rounded.Code,
            label = "Lập trình viên",
            description = "Thành viên đội phát triển Twelve.",
            colorRole = BadgeColorRole.TERTIARY,
        ),
        "moderator" to BadgeMeta(
            id = "moderator",
            icon = Icons.Rounded.Gavel,
            label = "Điều hành viên",
            description = "Giữ gìn nội dung và trật tự cộng đồng Twelve.",
            colorRole = BadgeColorRole.PRIMARY,
        ),
        "pro" to BadgeMeta(
            id = "pro",
            icon = Icons.Rounded.Diamond,
            label = "Người dùng Pro",
            description = "Tài khoản Pro của Twelve.",
            colorRole = BadgeColorRole.SECONDARY,
        ),
        "tester" to BadgeMeta(
            id = "tester",
            icon = Icons.Rounded.Science,
            label = "Tài khoản thử nghiệm",
            description = "Tài khoản dùng để thử nghiệm, không tham gia xếp hạng.",
            colorRole = BadgeColorRole.TERTIARY,
        ),
        "legacy" to BadgeMeta(
            id = "legacy",
            icon = Icons.Rounded.History,
            label = "Thành viên Twelve cũ",
            description = "Đã đồng hành cùng Twelve từ phiên bản đầu tiên.",
            colorRole = BadgeColorRole.SECONDARY,
        ),
    )

    fun getBadgeInfo(id: String): BadgeMeta = catalog[id] ?: BadgeMeta(
        id = id,
        icon = Icons.Rounded.Verified,
        label = "Huy hiệu",
        description = "Huy hiệu của Twelve.",
        colorRole = BadgeColorRole.NEUTRAL,
    )

    /** Suy danh sách id huy hiệu khi snapshot Firestore chưa chứa trường `badges`. */
    fun deriveBadgeIds(
        accountGroup: String?,
        accountRole: String?,
        isLegacyMember: Boolean,
    ): List<String> {
        val ids = mutableListOf<String>()
        val group = accountGroup ?: roleToGroup[accountRole]
        when {
            group == "SA" -> ids.add(staffByRole[accountRole] ?: "sa")
            group == "LLA" -> ids.add("tester")
            group == "RA" && accountRole == "pro_user" -> ids.add("pro")
        }
        if (isLegacyMember) {
            ids.add("legacy")
        }
        return ids
    }
}
