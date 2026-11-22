package com.example.data.remote

import com.example.data.model.HashtagPostsResponse
import com.example.data.model.HashtagRankingResponse
import com.example.data.model.SearchTrendingResponse
import com.example.data.model.UsersRankingResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API Xu hướng & Tìm kiếm.
 * Nguồn đối chiếu: backend/src/routes/trending.routes.js, frontend/src/api/trending.api.js.
 */
interface TwelveTrendingApi {
    @GET("trending/users/weekly")
    suspend fun getWeeklyUsersRanking(): UsersRankingResponse

    @GET("trending/users/alltime")
    suspend fun getAllTimeUsersRanking(): UsersRankingResponse

    @GET("trending/hashtags/weekly")
    suspend fun getHashtagWeeklyRanking(
        @Query("mode") mode: String = "classic",
    ): HashtagRankingResponse

    @GET("trending/hashtags/alltime")
    suspend fun getHashtagAllTimeRanking(
        @Query("mode") mode: String = "classic",
    ): HashtagRankingResponse

    @GET("trending/hashtags/{tag}/posts")
    suspend fun getHashtagPosts(
        @Path("tag") tag: String,
        @Query("mode") mode: String = "classic",
    ): HashtagPostsResponse

    @GET("trending/search")
    suspend fun searchTrending(
        @Query("q") query: String,
        @Query("type") type: String = "post",
        @Query("sort") sort: String = "newest",
        @Query("mode") mode: String = "classic",
    ): SearchTrendingResponse
}
