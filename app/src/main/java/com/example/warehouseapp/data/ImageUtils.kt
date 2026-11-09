package com.example.warehouseapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    /**
     * Читает фото, уменьшает до maxSide и сжимает в JPEG так, чтобы base64 гарантированно
     * влез в документ Firestore (лимит 1 МБ). Учитывает поворот снимка с камеры.
     */
    fun uriToBase64(context: Context, uri: Uri, maxSide: Int, maxBytes: Int = 600_000): String {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val s = info.size
                val scale = maxOf(s.width, s.height).toFloat() / maxSide
                if (scale > 1f) d.setTargetSize((s.width / scale).toInt(), (s.height / scale).toInt())
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("Не удалось прочитать изображение")
        }
        val bmp = scaleDown(src, maxSide)
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (b64.length <= maxBytes || quality <= 40) return b64
            quality -= 10
        }
    }

    private fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= maxSide) return b
        val k = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
    }

    fun decode(base64: String): Bitmap? = runCatching {
        val clean = base64.substringAfter("base64,", base64)
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
