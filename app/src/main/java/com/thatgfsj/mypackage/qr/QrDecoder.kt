package com.thatgfsj.mypackage.qr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 从本地图片文件中识别二维码内容 */
object QrDecoder {

    suspend fun decodeFile(path: String): String? = withContext(Dispatchers.IO) {
        val bmp = decodeSampled(File(path), 1600) ?: return@withContext null
        try {
            decodeBitmap(bmp)
        } finally {
            bmp.recycle()
        }
    }

    private fun decodeBitmap(bmp: Bitmap): String? {
        attempt(bmp)?.let { return it }
        // 拍摄方向不确定，旋转后重试
        for (angle in intArrayOf(90, 180, 270)) {
            val matrix = Matrix().apply { postRotate(angle.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, false)
            val result = attempt(rotated)
            if (rotated !== bmp) rotated.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun attempt(bmp: Bitmap): String? = try {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        val reader = MultiFormatReader().apply { setHints(hints) }
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (e: Exception) {
        null
    }

    private fun decodeSampled(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}
