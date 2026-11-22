package com.example.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.SathiLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeneratedImageOutcome(
    val bitmap: Bitmap,
    val caption: String,
    val requiresFirebaseNotice: String,
    val isPlaceholderPreview: Boolean
)

interface SathiImageGenRepository {
    suspend fun generateImage(prompt: String, language: SathiLanguage): Result<GeneratedImageOutcome>
}

class PlaceholderSathiImageGenRepository : SathiImageGenRepository {
    override suspend fun generateImage(
        prompt: String,
        language: SathiLanguage
    ): Result<GeneratedImageOutcome> = withContext(Dispatchers.Default) {
        val cleanPrompt = prompt.trim().ifBlank {
            if (language == SathiLanguage.BN) "সুন্দর প্রকৃতির ছবি" else "Scenic sunrise landscape"
        }
        val bitmap = renderLabeledSamplePreview(cleanPrompt, language)
        val notice = if (language == SathiLanguage.BN) {
            "নমুনা প্রিভিউ (Sample Preview): আসল এআই ছবি তৈরি করতে Firebase AI Logic অনুমোদন প্রয়োজন।"
        } else {
            "Sample Preview: Real AI image generation requires enabling Firebase AI Logic."
        }
        val caption = if (language == SathiLanguage.BN) {
            "\"$cleanPrompt\"-এর জন্য নমুনা প্রিভিউ কার্ড তৈরি করা হয়েছে।\n\n$notice"
        } else {
            "Created a sample preview card for \"$cleanPrompt\".\n\n$notice"
        }
        Result.success(
            GeneratedImageOutcome(
                bitmap = bitmap,
                caption = caption,
                requiresFirebaseNotice = notice,
                isPlaceholderPreview = true
            )
        )
    }

    private fun renderLabeledSamplePreview(prompt: String, language: SathiLanguage): Bitmap {
        val width = 560
        val height = 380
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(
                    Color.rgb(255, 224, 130),
                    Color.rgb(255, 248, 225),
                    Color.rgb(100, 181, 246),
                    Color.rgb(25, 118, 210)
                ),
                floatArrayOf(0f, 0.42f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 18, 30, 52)
        }
        val cardRect = RectF(28f, 32f, width - 28f, height - 32f)
        canvas.drawRoundRect(cardRect, 26f, 26f, badgePaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 213, 79)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 26f, 26f, borderPaint)

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 213, 79)
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            if (language == SathiLanguage.BN) "SAMPLE PREVIEW • নমুনা প্রিভিউ" else "SAMPLE PREVIEW",
            width / 2f,
            92f,
            tagPaint
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val clippedPrompt = prompt.take(34) + if (prompt.length > 34) "..." else ""
        canvas.drawText(clippedPrompt, width / 2f, 178f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(187, 222, 251)
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            if (language == SathiLanguage.BN) "আসল এআই ছবি তৈরিতে Firebase AI Logic প্রয়োজন"
            else "Requires Firebase AI Logic for real AI image synthesis",
            width / 2f,
            258f,
            subPaint
        )

        return bmp
    }
}
