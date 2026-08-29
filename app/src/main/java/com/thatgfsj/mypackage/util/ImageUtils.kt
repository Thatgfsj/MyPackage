package com.thatgfsj.mypackage.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtils {

    private fun picturesDir(context: Context): File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
            "stations"
        ).apply { mkdirs() }

    fun newCameraFile(context: Context): File =
        File(picturesDir(context), "st_${System.currentTimeMillis()}.jpg")

    /** 把相册选择的图片复制到应用私有目录并压缩，返回绝对路径 */
    fun importFromUri(context: Context, uri: Uri): String? = try {
        val out = newCameraFile(context)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { ins -> out.outputStream().use { ins.copyTo(it) } }
        downscale(out)
        out.absolutePath
    } catch (e: Exception) {
        null
    }

    /** 采样压缩，最长边限制在 maxDim 以内，防止大图导致 OOM */
    fun downscale(file: File, maxDim: Int = 1280) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
            val bmp = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
        } catch (e: Exception) {
            // 压缩失败时保留原图
        }
    }

    fun deleteImage(path: String) {
        if (path.isBlank()) return
        try {
            File(path).delete()
        } catch (e: Exception) {
        }
    }

    /**
     * 读取图片并压缩为小尺寸 JPEG 的 base64（用于二维码分享内嵌）。
     * 无图/失败返回空串。
     */
    fun toSmallBase64(path: String, maxDim: Int = 200, quality: Int = 55): String = try {
        if (path.isBlank()) ""
        else {
            val file = File(path)
            if (!file.exists()) "" else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
                val bmp = BitmapFactory.decodeFile(
                    path,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: return ""
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bmp.recycle()
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        }
    } catch (e: Exception) {
        ""
    }

    /** base64 图片还原为私有目录文件，返回绝对路径；失败返回 null */
    fun saveJpegFromBase64(context: Context, b64: String): String? = try {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        if (bytes.isEmpty()) null else {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val out = newCameraFile(context)
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            out.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}
