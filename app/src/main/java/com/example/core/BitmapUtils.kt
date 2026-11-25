package com.example.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtils {
    fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        return if (bitmap.config == null) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    fun decodeUriToSoftwareBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280
    ): Bitmap? {
        return try {
            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > maxDimension && w > 0 && h > 0) {
                        val scale = maxDimension.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } else {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
                var sampleSize = 1
                while (longest / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
            }
            decoded?.let { ensureSoftwareBitmap(it) }
        } catch (_: Exception) {
            null
        }
    }

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        titlePrefix: String = "SathiAI"
    ): Result<Uri> {
        return try {
            val safeBmp = ensureSoftwareBitmap(bitmap)
            val safeTitle = titlePrefix
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .take(28)
                .ifBlank { "SathiAI" }
            val fileName = "${safeTitle}_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SathiAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("MediaStore insert returned null"))
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        if (!safeBmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw IllegalStateException("PNG compression failed")
                        }
                    } ?: throw IllegalStateException("Could not open output stream")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Result.success(uri)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    Result.failure(e)
                }
            } else {
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val folder = File(picturesDir, "SathiAI").apply { mkdirs() }
                val outFile = File(folder, fileName)
                FileOutputStream(outFile).use { out ->
                    if (!safeBmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IllegalStateException("PNG compression failed")
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                Result.success(Uri.fromFile(outFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareImageToApps(context: Context, bitmap: Bitmap, title: String = "Sathi AI Image"): Result<Unit> {
        return try {
            val savedUri = saveBitmapToGallery(context, bitmap, "SathiAI_Share").getOrThrow()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, savedUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
