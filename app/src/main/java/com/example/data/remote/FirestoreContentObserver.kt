package com.example.data.remote

import com.example.core.config.FirebaseAvailability
import com.example.core.util.CreatedAtParser
import com.example.data.model.ContentType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

data class LiveDocMetrics(
    val likeCount: Int?,
    val dislikeCount: Int?,
    val commentCount: Int?,
    val shareCount: Int?,
    val viewCount: Int?,
    val aiViewCount: Int?,
    val postScore: Int?,
)

sealed interface LiveCommentChange {
    data class Upsert(
        val id: String,
        val authorUid: String?,
        val content: String,
        val isEdited: Boolean,
        val createdAtIso: String?,
        val updatedAtIso: String?,
    ) : LiveCommentChange

    data class Removed(val id: String) : LiveCommentChange
}

data class LiveCommentsBatch(
    val windowFull: Boolean,
    val changes: List<LiveCommentChange>,
)

/**
 * Listener Firestore CHỈ ĐỌC cho bài viết/blog đang mở và bình luận bên dưới.
 *
 * Nguồn đối chiếu:
 * - backend/firestore.rules (chỉ cho phép `get` doc công khai & `list` bình luận với `limit <= 50`)
 * - frontend/src/hooks/useRealtimeDoc.js
 * - frontend/src/hooks/usePaginatedComments.js
 *
 * Quy tắc:
 * - KHÔNG BAO GIỜ ghi Firestore từ client.
 * - Chỉ gắn khi `FirebaseAvailability.isConfigured == true` và trong vòng đời `ON_START`..`ON_STOP`.
 * - Không bao giờ ghi đè `myReaction` từ snapshot document (trường này chỉ API trả theo user).
 */
@Singleton
class FirestoreContentObserver @Inject constructor(
    private val firebaseAvailability: FirebaseAvailability,
) {
    fun observeContentDoc(type: ContentType, id: String): Flow<LiveDocMetrics> {
        if (!firebaseAvailability.isConfigured || id.isBlank()) return emptyFlow()
        val collection = collectionName(type)
        return callbackFlow {
            val db = FirebaseFirestore.getInstance()
            val registration = db.collection(collection).document(id)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val data = snapshot.data ?: return@addSnapshotListener
                    trySend(
                        LiveDocMetrics(
                            likeCount = (data["likeCount"] as? Number)?.toInt(),
                            dislikeCount = (data["dislikeCount"] as? Number)?.toInt(),
                            commentCount = (data["commentCount"] as? Number)?.toInt(),
                            shareCount = (data["shareCount"] as? Number)?.toInt(),
                            viewCount = (data["viewCount"] as? Number)?.toInt(),
                            aiViewCount = (data["aiViewCount"] as? Number)?.toInt(),
                            postScore = (data["postScore"] as? Number)?.toInt(),
                        ),
                    )
                }
            awaitClose { registration.remove() }
        }
    }

    fun observeRecentComments(
        type: ContentType,
        id: String,
        baselineMs: Long,
    ): Flow<LiveCommentsBatch> {
        if (!firebaseAvailability.isConfigured || id.isBlank()) return emptyFlow()
        val collection = collectionName(type)
        val safeBaselineMs = (baselineMs - BASELINE_MARGIN_MS).coerceAtLeast(0L)
        return callbackFlow {
            val db = FirebaseFirestore.getInstance()
            val query = db.collection(collection)
                .document(id)
                .collection("comments")
                .whereGreaterThanOrEqualTo("updatedAt", Timestamp(Date(safeBaselineMs)))
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(LIVE_WINDOW_LIMIT)

            val registration = query.addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val changes = snapshot.documentChanges.mapNotNull { change ->
                    val docId = change.document.id
                    if (change.type == DocumentChange.Type.REMOVED) {
                        LiveCommentChange.Removed(docId)
                    } else {
                        val d = change.document.data
                        LiveCommentChange.Upsert(
                            id = docId,
                            authorUid = d["authorUid"] as? String,
                            content = d["content"] as? String ?: "",
                            isEdited = (d["isEdited"] as? Boolean) ?: false,
                            createdAtIso = CreatedAtParser.parseAny(d["createdAt"]),
                            updatedAtIso = CreatedAtParser.parseAny(d["updatedAt"]),
                        )
                    }
                }
                if (changes.isNotEmpty()) {
                    trySend(
                        LiveCommentsBatch(
                            windowFull = snapshot.size() >= LIVE_WINDOW_LIMIT.toInt(),
                            changes = changes,
                        ),
                    )
                }
            }
            awaitClose { registration.remove() }
        }
    }

    private fun collectionName(type: ContentType): String = when (type) {
        ContentType.POST -> "posts"
        ContentType.BLOG -> "blogs"
    }

    companion object {
        // Khớp firestore.rules: allow list: if request.query.limit <= 50
        const val LIVE_WINDOW_LIMIT = 50L
        const val BASELINE_MARGIN_MS = 60_000L
    }
}
