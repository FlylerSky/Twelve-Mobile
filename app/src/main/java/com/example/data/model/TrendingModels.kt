package com.example.data.model

import kotlinx.serialization.Serializable

/**
 * Mô hình Xu hướng & Tìm kiếm.
 * Nguồn đối chiếu: backend/src/controllers/trending.controller.js.
 */
@Serializable
data class RankingUser(
    val rank: Int = 0,
    val uid: String = "",
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val tagName: String? = null,
    val accountGroup: String? = null,
    val badges: List<String> = emptyList(),
    val accountScore: Int = 0,
    val weeklyGrowth: Int? = null,
)

@Serializable
data class UsersRankingResponse(
    val weekCode: String? = null,
    val ranking: List<RankingUser> = emptyList(),
)

@Serializable
data class HashtagRankingItem(
    val rank: Int = 0,
    val tag: String = "",
    val usageCount: Int = 0,
    val growth: Int? = null,
    val score: Int? = null,
)

@Serializable
data class HashtagRankingResponse(
    val weekCode: String? = null,
    val mode: String = "classic",
    val ranking: List<HashtagRankingItem> = emptyList(),
)

@Serializable
data class HashtagPostsResponse(
    val tag: String = "",
    val mode: String = "classic",
    val posts: List<ContentCard> = emptyList(),
)

@Serializable
data class SearchHashtagItem(
    val tag: String = "",
    val usageCount: Int = 0,
    val score: Int = 0,
)

@Serializable
data class SearchTrendingResponse(
    val query: String = "",
    val type: String = "post",
    val sort: String = "newest",
    val mode: String = "classic",
    val hashtags: List<SearchHashtagItem> = emptyList(),
    val posts: List<ContentCard> = emptyList(),
)
