package com.example.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ProfileImageHelper {

    suspend fun compressAndSaveAvatar(
        context: Context,
        uri: Uri,
        previousPath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val maxDim = 512
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDim * 2 || boundsOpts.outHeight / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val targetW = (original.width * scale).toInt().coerceAtLeast(1)
            val targetH = (original.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
                original
            }

            val dir = File(context.filesDir, "profile_photos").apply { mkdirs() }
            val outFile = File(dir, "avatar_${UUID.randomUUID().toString().take(8)}.jpg")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            if (scaled !== original) original.recycle()
            scaled.recycle()

            if (!previousPath.isNullOrBlank() && previousPath != outFile.absolutePath) {
                runCatching { File(previousPath).delete() }
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deletePhoto(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        runCatching {
            val f = File(path)
            if (f.exists()) f.delete()
        }
    }
}
