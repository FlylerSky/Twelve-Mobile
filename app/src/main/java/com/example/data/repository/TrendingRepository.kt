package com.example.data.repository

import com.example.data.model.HashtagPostsResponse
import com.example.data.model.HashtagRankingResponse
import com.example.data.model.SearchTrendingResponse
import com.example.data.model.UsersRankingResponse
import com.example.data.remote.TwelveTrendingApi
import javax.inject.Inject
import javax.inject.Singleton

enum class ScoreMode(val apiValue: String) {
    CLASSIC("classic"),
    ADVANCED("advanced"),
}

enum class SearchType(val apiValue: String) {
    POST("post"),
    HASHTAG("hashtag"),
}

enum class SearchSort(val apiValue: String) {
    NEWEST("newest"),
    TRENDING("trending"),
}

/**
 * Kho dữ liệu Xu hướng & Tìm kiếm.
 * Nguồn đối chiếu: backend/src/controllers/trending.controller.js, frontend/src/api/trending.api.js.
 */
@Singleton
class TrendingRepository @Inject constructor(
    private val trendingApi: TwelveTrendingApi,
) {
    suspend fun getWeeklyUsers(): Result<UsersRankingResponse> = safeApiCall {
        trendingApi.getWeeklyUsersRanking()
    }

    suspend fun getAllTimeUsers(): Result<UsersRankingResponse> = safeApiCall {
        trendingApi.getAllTimeUsersRanking()
    }

    suspend fun getHashtagWeekly(mode: ScoreMode): Result<HashtagRankingResponse> = safeApiCall {
        trendingApi.getHashtagWeeklyRanking(mode.apiValue)
    }

    suspend fun getHashtagAllTime(mode: ScoreMode): Result<HashtagRankingResponse> = safeApiCall {
        trendingApi.getHashtagAllTimeRanking(mode.apiValue)
    }

    suspend fun getHashtagPosts(rawTag: String, mode: ScoreMode): Result<HashtagPostsResponse> = safeApiCall {
        val normalizedTag = rawTag.trim().removePrefix("#").lowercase()
        val res = trendingApi.getHashtagPosts(tag = normalizedTag, mode = mode.apiValue)
        res.copy(
            posts = res.posts.map { card ->
                val resolvedPath = card.detailPath ?: if (card.type == "blog") "/blog" else "/post"
                card.copy(detailPath = resolvedPath)
            },
        )
    }

    suspend fun search(
        query: String,
        type: SearchType,
        sort: SearchSort,
        mode: ScoreMode,
    ): Result<SearchTrendingResponse> = safeApiCall {
        trendingApi.searchTrending(
            query = query.trim(),
            type = type.apiValue,
            sort = sort.apiValue,
            mode = mode.apiValue,
        )
    }
}
