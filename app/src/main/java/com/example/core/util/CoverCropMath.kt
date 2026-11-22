package com.example.core.util

import com.example.data.model.CoverCrop

/**
 * Toán học khung hiển thị ảnh bìa 3:1 không cắt cứng ảnh.
 * Nguồn đối chiếu: frontend/src/components/profile/ProfileCover.jsx, backend/src/utils/constants.js#PROFILE.
 */
object CoverCropMath {
    const val COVER_ASPECT = 3f
    const val MIN_PERCENT = 10.0

    fun isValidCrop(crop: CoverCrop?): Boolean {
        if (crop == null) return false
        val nums = listOf(crop.x, crop.y, crop.width, crop.height)
        if (nums.any { !it.isFinite() }) return false
        return crop.x >= 0.0 &&
            crop.y >= 0.0 &&
            crop.width >= MIN_PERCENT &&
            crop.height >= MIN_PERCENT &&
            crop.width <= 100.0 &&
            crop.height <= 100.0 &&
            crop.x + crop.width <= 100.5 &&
            crop.y + crop.height <= 100.5
    }

    /**
     * Trả về tỷ lệ rộng/cao so với khung chứa (1.0f = vừa khít) và độ dời tương đối theo chiều rộng/cao khung.
     */
    data class FrameTransform(
        val widthFactor: Float,
        val heightFactor: Float,
        val offsetXFactor: Float,
        val offsetYFactor: Float,
    )

    data class PixelPlacement(
        val widthPx: Int,
        val heightPx: Int,
        val offsetXPx: Int,
        val offsetYPx: Int,
    )

    fun computeTransform(crop: CoverCrop?): FrameTransform? {
        if (!isValidCrop(crop) || crop == null) return null
        val wFactor = (100.0 / crop.width).toFloat()
        val hFactor = (100.0 / crop.height).toFloat()
        val xFactor = (-crop.x / crop.width).toFloat()
        val yFactor = (-crop.y / crop.height).toFloat()
        return FrameTransform(
            widthFactor = wFactor,
            heightFactor = hFactor,
            offsetXFactor = xFactor,
            offsetYFactor = yFactor,
        )
    }

    /**
     * Tính kích thước và tọa độ pixel thực để đặt ảnh bên trong khung 3:1 mà KHÔNG bị
     * `Modifier.width/height` kẹp lại theo ràng buộc khung cha.
     */
    fun computePixelPlacement(
        containerWidthPx: Int,
        containerHeightPx: Int,
        crop: CoverCrop?,
    ): PixelPlacement? {
        if (containerWidthPx <= 0 || containerHeightPx <= 0) return null
        val transform = computeTransform(crop) ?: return null
        return PixelPlacement(
            widthPx = kotlin.math.max(containerWidthPx, (containerWidthPx * transform.widthFactor).toInt()),
            heightPx = kotlin.math.max(containerHeightPx, (containerHeightPx * transform.heightFactor).toInt()),
            offsetXPx = (containerWidthPx * transform.offsetXFactor).toInt(),
            offsetYPx = (containerHeightPx * transform.offsetYFactor).toInt(),
        )
    }
}
