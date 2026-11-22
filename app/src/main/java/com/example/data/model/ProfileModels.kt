package com.example.data.model

import com.example.core.util.TolerantTimestampSerializer
import kotlinx.serialization.Serializable

/**
 * Hồ sơ công khai đầy đủ trả về từ GET /api/users/:uid và GET /api/users/by-tag/:tag.
 * Nguồn đối chiếu: backend/src/utils/helpers.js#toPublicProfile.
 */
@Serializable
data class PublicProfile(
    val uid: String = "",
    val displayName: String? = null,
    val tagName: String? = null,
    val avatarUrl: String? = null,
    val accountGroup: String? = null,
    val accountRole: String? = null,
    val country: String? = null,
    val isLegacyMember: Boolean = false,
    val legacySince: String? = null,
    val badges: List<String> = emptyList(),
    val coverUrl: String? = null,
    val coverCrop: CoverCrop? = null,
    val bio: String = "",
    @Serializable(with = TolerantTimestampSerializer::class)
    val createdAt: String? = null,
) {
    val isSA: Boolean
        get() = accountGroup == "SA"
}

@Serializable
data class PublicProfileResponse(
    val user: PublicProfile,
)

@Serializable
data class PinnedPostsResponse(
    val posts: List<ContentCard> = emptyList(),
)

@Serializable
data class WeeklyAchievementEntry(
    val weekCode: String = "",
    val rank: Int = 0,
    val weeklyGrowth: Int = 0,
)

@Serializable
data class WeeklyAchievementSummary(
    val weeksRecorded: Int = 0,
    val top10Count: Int = 0,
    val top3Count: Int = 0,
    val firstPlaceCount: Int = 0,
    val best: WeeklyAchievementEntry? = null,
    val recent: List<WeeklyAchievementEntry> = emptyList(),
)

@Serializable
data class AchievementsData(
    val accountScore: Int = 0,
    val weekly: WeeklyAchievementSummary = WeeklyAchievementSummary(),
)

@Serializable
data class ExtraInfoResponse(
    val fields: Map<String, String?> = emptyMap(),
    val achievements: AchievementsData? = null,
)

@Serializable
data class RecordVisitResponse(
    val recorded: Boolean = false,
)
