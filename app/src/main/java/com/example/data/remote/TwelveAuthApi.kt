package com.example.data.remote

import com.example.data.model.CheckTagNameResponse
import com.example.data.model.GetMeResponse
import com.example.data.model.HealthResponse
import com.example.data.model.SyncActivationResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Các endpoint xác thực & phiên.
 * Nguồn đối chiếu: backend/server.js, backend/src/routes/auth.routes.js, backend/src/routes/users.routes.js.
 */
interface TwelveAuthApi {
    @GET
    suspend fun pingHealth(@Url healthUrl: String): HealthResponse

    @GET("users/me")
    suspend fun getMe(): GetMeResponse

    @POST("auth/activation/sync")
    suspend fun syncActivation(): SyncActivationResponse

    @GET("auth/check-tagname")
    suspend fun checkTagName(@Query("tag") tag: String): CheckTagNameResponse
}
