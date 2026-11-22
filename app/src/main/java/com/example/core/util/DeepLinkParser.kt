package com.example.core.util

import com.example.BuildConfig
import com.example.data.model.ContentType
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Kết quả phân tích Deep Link hợp lệ từ `https://twelveta.vercel.app/...`.
 * Nguồn đối chiếu: frontend/src/App.jsx, frontend/src/utils/profilePath.js.
 */
sealed interface TwelveDeepLink {
    data class Content(
        val contentType: ContentType,
        val id: String,
        val sharerUid: String? = null,
        val shareSignature: String? = null,
        val sharedFlag: Boolean = false,
    ) : TwelveDeepLink

    data class PublicProfile(
        val uidOrTag: String,
        val isTag: Boolean,
    ) : TwelveDeepLink

    data object OwnProfile : TwelveDeepLink
}

object DeepLinkParser {
    private val DOC_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val RESERVED_DOC_ID_REGEX = Regex("^__.*__$")
    private val SHARE_SIG_REGEX = Regex("^[A-Za-z0-9_=-]{1,256}$")

    fun defaultAllowedHost(): String =
        runCatching { URI(BuildConfig.WEB_BASE_URL).host?.lowercase() }.getOrNull()
            ?: "twelveta.vercel.app"

    fun isSafeDocId(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return DOC_ID_REGEX.matches(raw) && !RESERVED_DOC_ID_REGEX.matches(raw)
    }

    /**
     * Phân tích URL deep link (không tin cậy dữ liệu đầu vào).
     * Chỉ nhận `https://<expectedHost>` với `/post/:id`, `/blog/:id`, `/u/@:tag`, `/u/myProfile`, `/u/:uid`.
     */
    fun parse(
        rawUrl: String?,
        expectedHost: String = defaultAllowedHost(),
    ): TwelveDeepLink? {
        if (rawUrl.isNullOrBlank()) return null
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "https" || host != expectedHost.lowercase()) return null

        val rawPath = uri.rawPath.orEmpty()
        if (rawPath.contains("..")) return null
        val segments = rawPath.split('/')
            .filter { it.isNotEmpty() }
            .mapNotNull { seg -> runCatching { URLDecoder.decode(seg, StandardCharsets.UTF_8.name()) }.getOrNull() }
            .map { it.trim() }

        if (segments.size != 2) return null

        val kind = segments[0]
        val target = segments[1]
        if (target.isEmpty() || target.contains('/') || target.contains('\\')) return null

        val queryParams = parseQueryParams(uri.rawQuery)

        return when (kind) {
            "post" -> buildContentLink(ContentType.POST, target, queryParams)
            "blog" -> buildContentLink(ContentType.BLOG, target, queryParams)
            "u" -> classifyProfileRef(target)
            else -> null
        }
    }

    private fun buildContentLink(
        type: ContentType,
        id: String,
        queryParams: Map<String, String>,
    ): TwelveDeepLink.Content? {
        if (!isSafeDocId(id)) return null
        val rawIdu = queryParams["idu"]?.takeIf { isSafeDocId(it) }
        val rawSig = queryParams["s"]?.takeIf { SHARE_SIG_REGEX.matches(it) }
        val sharedFlag = queryParams["shared"] == "1"
        val (idu, sig) = if (rawIdu != null && rawSig != null) rawIdu to rawSig else null to null
        return TwelveDeepLink.Content(
            contentType = type,
            id = id,
            sharerUid = idu,
            shareSignature = sig,
            sharedFlag = sharedFlag,
        )
    }

    /**
     * Phản chiếu `frontend/src/utils/profilePath.js#classifyProfileRef`:
     * - `@...` hợp lệ -> `PublicProfile(bareTag, isTag = true)`; `@...` sai định dạng -> null.
     * - `myProfile` (không phân biệt hoa/thường) -> `OwnProfile`.
     * - UID an toàn -> `PublicProfile(uid, isTag = false)`.
     */
    private fun classifyProfileRef(rawRef: String): TwelveDeepLink? {
        if (rawRef.startsWith("@")) {
            return if (TagNameNormalizer.isValid(rawRef)) {
                TwelveDeepLink.PublicProfile(
                    uidOrTag = TagNameNormalizer.toBareTag(rawRef),
                    isTag = true,
                )
            } else {
                null
            }
        }
        if (rawRef.equals("myProfile", ignoreCase = true)) {
            return TwelveDeepLink.OwnProfile
        }
        return if (isSafeDocId(rawRef)) {
            TwelveDeepLink.PublicProfile(uidOrTag = rawRef, isTag = false)
        } else {
            null
        }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val rawKey = if (idx >= 0) pair.substring(0, idx) else pair
            val rawVal = if (idx >= 0) pair.substring(idx + 1) else ""
            val key = runCatching { URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name()) }.getOrNull()
            val value = runCatching { URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name()) }.getOrNull()
            if (!key.isNullOrBlank() && value != null) {
                result[key] = value.trim()
            }
        }
        return result
    }
}
