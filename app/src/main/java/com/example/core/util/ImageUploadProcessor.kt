package com.example.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.data.model.CoverCrop
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Xử lý ảnh đại diện (512x512 JPEG <= 2MB) và ảnh bìa (cạnh dài <= 2400px, JPEG <= 3MB) kèm xoay EXIF.
 * Nguồn đối chiếu: frontend/src/components/AvatarCropper.jsx, frontend/src/utils/imageDownscale.js,
 * backend/src/utils/constants.js#ASSET_LIMITS.
 */
object ImageUploadProcessor {
    const val AVATAR_OUTPUT_SIZE = 512
    const val AVATAR_MAX_BYTES = 2 * 1024 * 1024
    const val COVER_MAX_SIDE = 2400
    const val COVER_MAX_BYTES = 3 * 1024 * 1024
    private const val MAX_DECODE_SIDE = 3200
    private val COVER_QUALITIES = intArrayOf(90, 80, 70, 55)

    data class ProcessedImage(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
    )

    suspend fun decodeForPreview(context: Context, uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val orientation = readExifOrientation(context, uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            require(srcW > 0 && srcH > 0) { "Không đọc được kích thước ảnh. Vui lòng chọn ảnh hợp lệ." }

            val sampleSize = computeInSampleSize(srcW, srcH, MAX_DECODE_SIDE)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = openStream(context, uri).use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: error("Không thể giải mã tệp ảnh này.")
            applyExifOrientation(rawBitmap, orientation)
        }
    }

    /** Cắt ảnh đại diện vuông 1:1 -> 512x512 JPEG <= 2MB. */
    suspend fun cropAvatarToJpeg(
        source: Bitmap,
        panX: Float,
        panY: Float,
        zoom: Float,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val w = source.width.toFloat()
            val h = source.height.toFloat()
            val minSide = min(w, h)
            val safeZoom = zoom.coerceIn(1f, 3f)
            val cropSize = (minSide / safeZoom).coerceAtLeast(1f)
            val maxShiftX = ((w - cropSize) / 2f).coerceAtLeast(0f)
            val maxShiftY = ((h - cropSize) / 2f).coerceAtLeast(0f)
            val centerX = w / 2f - panX.coerceIn(-1f, 1f) * maxShiftX
            val centerY = h / 2f - panY.coerceIn(-1f, 1f) * maxShiftY

            val left = (centerX - cropSize / 2f).coerceIn(0f, (w - cropSize).coerceAtLeast(0f))
            val top = (centerY - cropSize / 2f).coerceIn(0f, (h - cropSize).coerceAtLeast(0f))

            val output = Bitmap.createBitmap(AVATAR_OUTPUT_SIZE, AVATAR_OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val srcRect = android.graphics.Rect(
                left.roundToInt(),
                top.roundToInt(),
                (left + cropSize).roundToInt().coerceAtMost(source.width),
                (top + cropSize).roundToInt().coerceAtMost(source.height),
            )
            val dstRect = android.graphics.Rect(0, 0, AVATAR_OUTPUT_SIZE, AVATAR_OUTPUT_SIZE)
            canvas.drawBitmap(source, srcRect, dstRect, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

            val bytes = compressToJpegWithinBudget(output, AVATAR_MAX_BYTES, intArrayOf(92, 85, 75, 60))
            output.recycle()
            bytes
        }
    }

    /** Chuẩn bị ảnh bìa <= 2400px, nền trắng, nén JPEG <= 3MB (giữ tỉ lệ gốc, KHÔNG cắt cứng). */
    suspend fun prepareCoverForUpload(source: Bitmap): Result<ProcessedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val scale = min(1f, COVER_MAX_SIDE.toFloat() / max(source.width, source.height).toFloat())
            val outW = (source.width * scale).roundToInt().coerceAtLeast(1)
            val outH = (source.height * scale).roundToInt().coerceAtLeast(1)
            val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val srcRect = android.graphics.Rect(0, 0, source.width, source.height)
            val dstRect = android.graphics.Rect(0, 0, outW, outH)
            canvas.drawBitmap(source, srcRect, dstRect, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))

            val bytes = compressToJpegWithinBudget(output, COVER_MAX_BYTES, COVER_QUALITIES)
            output.recycle()
            ProcessedImage(bytes = bytes, width = outW, height = outH)
        }
    }

    /**
     * Tính `CoverCrop` (% ảnh gốc) theo góc nhìn 3:1 trong khung kéo/thu phóng.
     * `panX`, `panY` nằm trong `[-1f, 1f]` biểu diễn độ dịch tâm khung cắt so với giữa ảnh.
     */
    fun computeCoverCropFromViewport(
        imageAspect: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        targetAspect: Float = CoverCropMath.COVER_ASPECT,
    ): CoverCrop {
        val safeImageAspect = if (imageAspect.isFinite() && imageAspect > 0f) imageAspect else targetAspect
        val safeZoom = zoom.coerceIn(1f, 3f)
        // Khung lớn nhất tỉ lệ targetAspect (3:1) nằm trọn trong ảnh gốc ở zoom=1
        val baseWidthPct: Double
        val baseHeightPct: Double
        if (safeImageAspect >= targetAspect) {
            baseHeightPct = 100.0
            baseWidthPct = (targetAspect / safeImageAspect) * 100.0
        } else {
            baseWidthPct = 100.0
            baseHeightPct = (safeImageAspect / targetAspect) * 100.0
        }
        val cropW = (baseWidthPct / safeZoom).coerceIn(CoverCropMath.MIN_PERCENT, 100.0)
        val cropH = (baseHeightPct / safeZoom).coerceIn(CoverCropMath.MIN_PERCENT, 100.0)
        val freeX = (100.0 - cropW).coerceAtLeast(0.0)
        val freeY = (100.0 - cropH).coerceAtLeast(0.0)
        val x = ((1.0 - panX.coerceIn(-1f, 1f)) * 0.5 * freeX).coerceIn(0.0, freeX)
        val y = ((1.0 - panY.coerceIn(-1f, 1f)) * 0.5 * freeY).coerceIn(0.0, freeY)

        return CoverCrop(
            x = round2(x),
            y = round2(y),
            width = round2(cropW),
            height = round2(cropH),
        )
    }

    /** Suy ngược tham số ban đầu `(panX, panY, zoom)` từ `CoverCrop` đã lưu để mở lại hộp điều chỉnh đúng vị trí. */
    fun estimateControlsFromCrop(
        crop: CoverCrop?,
        imageAspect: Float,
        targetAspect: Float = CoverCropMath.COVER_ASPECT,
    ): Triple<Float, Float, Float> {
        if (!CoverCropMath.isValidCrop(crop) || crop == null) return Triple(0f, 0f, 1f)
        val safeImageAspect = if (imageAspect.isFinite() && imageAspect > 0f) imageAspect else targetAspect
        val baseWidthPct = if (safeImageAspect >= targetAspect) (targetAspect / safeImageAspect) * 100.0 else 100.0
        val zoom = (baseWidthPct / crop.width).toFloat().coerceIn(1f, 3f)
        val freeX = (100.0 - crop.width).coerceAtLeast(0.0001)
        val freeY = (100.0 - crop.height).coerceAtLeast(0.0001)
        val panX = if (100.0 - crop.width <= 0.1) 0f else (1.0 - (crop.x / (0.5 * freeX))).toFloat().coerceIn(-1f, 1f)
        val panY = if (100.0 - crop.height <= 0.1) 0f else (1.0 - (crop.y / (0.5 * freeY))).toFloat().coerceIn(-1f, 1f)
        return Triple(panX, panY, zoom)
    }

    fun compressToJpegWithinBudget(
        bitmap: Bitmap,
        maxBytes: Int,
        qualities: IntArray = COVER_QUALITIES,
    ): ByteArray {
        var last = ByteArray(0)
        for (q in qualities) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, q, out)
            last = out.toByteArray()
            if (last.size <= maxBytes) return last
        }
        require(last.size <= maxBytes) {
            "Ảnh sau khi nén vẫn vượt quá ${maxBytes / (1024 * 1024)}MB. Vui lòng chọn ảnh khác."
        }
        return last
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0

    private fun openStream(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("Không mở được tệp ảnh đã chọn.")

    private fun readExifOrientation(context: Context, uri: Uri): Int = runCatching {
        openStream(context, uri).use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun computeInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > maxSide) {
            sample *= 2
        }
        return sample
    }
}
