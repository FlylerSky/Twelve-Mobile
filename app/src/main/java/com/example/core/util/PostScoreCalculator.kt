package com.example.core.util

/**
 * Công thức điểm bài viết/blog dùng để cập nhật UI lạc quan khi tương tác ở client;
 * backend luôn là nguồn sự thật khi tải danh sách/chi tiết.
 * Nguồn đối chiếu: backend/src/utils/helpers.js#POST_SCORE_WEIGHTS, frontend/src/utils/postScore.js.
 */
object PostScoreCalculator {
    const val WEIGHT_VIEW = 1
    const val WEIGHT_LIKE = 2
    const val WEIGHT_DISLIKE = -2
    const val WEIGHT_COMMENT = 3
    const val WEIGHT_SHARE = 4

    fun computePostScore(
        viewCount: Int = 0,
        likeCount: Int = 0,
        dislikeCount: Int = 0,
        commentCount: Int = 0,
        shareCount: Int = 0,
    ): Int =
        viewCount * WEIGHT_VIEW +
            likeCount * WEIGHT_LIKE +
            dislikeCount * WEIGHT_DISLIKE +
            commentCount * WEIGHT_COMMENT +
            shareCount * WEIGHT_SHARE
}
