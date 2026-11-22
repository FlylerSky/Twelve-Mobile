package com.example.core.network

import com.example.BuildConfig
import com.example.core.config.DefaultFirebaseAvailability
import com.example.core.config.FirebaseAvailability
import com.example.data.remote.TwelveAuthApi
import com.example.data.remote.TwelveContentApi
import com.example.data.remote.TwelveTrendingApi
import com.example.data.remote.TwelveUsersApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindFirebaseAvailability(impl: DefaultFirebaseAvailability): FirebaseAvailability

    @Binds
    @Singleton
    abstract fun bindFirebaseTokenProvider(impl: DefaultFirebaseTokenProvider): FirebaseTokenProvider
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val RENDER_COLD_START_TIMEOUT_SEC = 60L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authTokenInterceptor: AuthTokenInterceptor,
    ): OkHttpClient {
        // Không gắn body/header logger để bảo đảm không bao giờ lộ token, email, mật khẩu hay tên thật ra log.
        return OkHttpClient.Builder()
            .connectTimeout(RENDER_COLD_START_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(RENDER_COLD_START_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(RENDER_COLD_START_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(authTokenInterceptor)
            .addInterceptor(GetRetryInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTwelveAuthApi(retrofit: Retrofit): TwelveAuthApi =
        retrofit.create(TwelveAuthApi::class.java)

    @Provides
    @Singleton
    fun provideTwelveContentApi(retrofit: Retrofit): TwelveContentApi =
        retrofit.create(TwelveContentApi::class.java)

    @Provides
    @Singleton
    fun provideTwelveUsersApi(retrofit: Retrofit): TwelveUsersApi =
        retrofit.create(TwelveUsersApi::class.java)

    @Provides
    @Singleton
    fun provideTwelveTrendingApi(retrofit: Retrofit): TwelveTrendingApi =
        retrofit.create(TwelveTrendingApi::class.java)
}
