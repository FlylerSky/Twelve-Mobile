package com.example.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Các tuyến điều hướng an toàn kiểu (@Serializable) cho Navigation Compose.
 */
@Serializable
data object FeedTabRoute

@Serializable
data object BlogTabRoute

@Serializable
data object TrendingTabRoute

@Serializable
data object MyProfileTabRoute

@Serializable
data object SettingsTabRoute

@Serializable
data class ContentDetailRoute(
    val contentType: String, // "POST" hoặc "BLOG"
    val contentId: String,
    val sharerUid: String? = null,
    val shareSignature: String? = null,
    val sharedFlag: Boolean = false,
)

@Serializable
data class PublicProfileRoute(
    val uidOrTag: String,
    val isTag: Boolean = false,
)

@Serializable
data class HashtagPostsRoute(
    val tag: String,
    val mode: String = "CLASSIC",
)

@Serializable
data class ComposerRoute(
    val contentType: String = "POST", // "POST" hoặc "BLOG"
    val editContentId: String? = null,
)

@Serializable
data object AccountInfoRoute

@Serializable
data object VisitorsRoute

@Serializable
data object SecurityRoute
