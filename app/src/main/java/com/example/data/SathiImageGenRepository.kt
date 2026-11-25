package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Base64
import com.example.core.BitmapUtils
import com.example.model.SathiLanguage
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GeneratedImageOutcome(
    val bitmap: Bitmap,
    val caption: String,
    val requiresFirebaseNotice: String,
    val isPlaceholderPreview: Boolean
)

interface SathiImageGenRepository {
    suspend fun generateImage(
        prompt: String,
        language: SathiLanguage,
        referenceBitmap: Bitmap? = null
    ): Result<GeneratedImageOutcome>
}

class GeminiRestImageGenRepository(
    private val apiKeyProvider: () -> String,
    private val fallback: PlaceholderSathiImageGenRepository = PlaceholderSathiImageGenRepository()
) : SathiImageGenRepository {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(18, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun generateImage(
        prompt: String,
        language: SathiLanguage,
        referenceBitmap: Bitmap?
    ): Result<GeneratedImageOutcome> = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim().ifBlank {
            if (language == SathiLanguage.BN) "সুন্দর প্রকৃতির ছবি" else "Scenic sunrise landscape"
        }
        val key = SathiAiService.sanitizeApiKeyInput(apiKeyProvider())
        if (key.isNotBlank()) {
            tryGenerateWithGeminiRest(key, cleanPrompt, language, referenceBitmap)?.let { outcome ->
                return@withContext Result.success(outcome)
            }
            if (referenceBitmap == null) {
                tryGenerateWithImagenRest(key, cleanPrompt, language)?.let { outcome ->
                    return@withContext Result.success(outcome)
                }
            }
        }
        fallback.generateImage(cleanPrompt, language, referenceBitmap)
    }

    private fun tryGenerateWithGeminiRest(
        apiKey: String,
        prompt: String,
        language: SathiLanguage,
        referenceBitmap: Bitmap?
    ): GeneratedImageOutcome? {
        val models = listOf(
            "gemini-2.5-flash-image",
            "gemini-2.0-flash-exp-image-generation",
            "gemini-2.0-flash-preview-image-generation"
        )

        val partsArray = JSONArray().apply {
            val fullInstruction = if (referenceBitmap != null) {
                "Using the attached example photo as visual inspiration and reference, create a brand-new high-quality image matching this request: $prompt"
            } else {
                "Generate a rich, high-quality artistic image for: $prompt"
            }
            put(JSONObject().put("text", fullInstruction))
            if (referenceBitmap != null) {
                val safeRef = BitmapUtils.ensureSoftwareBitmap(referenceBitmap)
                val maxSide = maxOf(safeRef.width, safeRef.height).coerceAtLeast(1)
                val scaled = if (maxSide > 896) {
                    val scale = 896f / maxSide
                    Bitmap.createScaledBitmap(
                        safeRef,
                        (safeRef.width * scale).toInt().coerceAtLeast(1),
                        (safeRef.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else safeRef
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 86, baos)
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", b64)
                        }
                    )
                )
            }
        }

        for (model in models) {
            try {
                val payload = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("parts", partsArray)
                            }
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                    )
                }
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) continue
                    val body = resp.body?.string() ?: continue
                    val parts = JSONObject(body)
                        .optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts") ?: continue

                    var textNote = ""
                    var decodedBmp: Bitmap? = null
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val t = part.optString("text").trim()
                        if (t.isNotBlank()) textNote = t
                        val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                        val b64 = inline?.optString("data").orEmpty()
                        if (b64.isNotBlank() && decodedBmp == null) {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            decodedBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?.let { BitmapUtils.ensureSoftwareBitmap(it) }
                        }
                    }

                    if (decodedBmp != null) {
                        val badge = if (language == SathiLanguage.BN) {
                            if (referenceBitmap != null) "সাথী এআই • উদাহরণ অনুযায়ী তৈরি"
                            else "সাথী এআই • ডাউনলোড করতে নিচের বাটনে ট্যাপ করুন"
                        } else {
                            if (referenceBitmap != null) "Generated from Example by Sathi AI"
                            else "Generated by Sathi AI • Tap Save to download"
                        }
                        val caption = if (language == SathiLanguage.BN) {
                            "\"$prompt\"-এর ছবি তৈরি হয়েছে। নিচের 'ডাউনলোড করুন' বাটনে চাপ দিয়ে ফোনের গ্যালারিতে সেভ করতে পারবেন।" +
                                if (textNote.isNotBlank()) "\n\n$textNote" else ""
                        } else {
                            "Created image for \"$prompt\". Tap Download below to save it to your gallery." +
                                if (textNote.isNotBlank()) "\n\n$textNote" else ""
                        }
                        return GeneratedImageOutcome(
                            bitmap = decodedBmp,
                            caption = caption,
                            requiresFirebaseNotice = badge,
                            isPlaceholderPreview = false
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun tryGenerateWithImagenRest(
        apiKey: String,
        prompt: String,
        language: SathiLanguage
    ): GeneratedImageOutcome? {
        return try {
            val payload = JSONObject().apply {
                put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
                put("parameters", JSONObject().put("sampleCount", 1))
            }
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict")
                .addHeader("x-goog-api-key", apiKey)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val b64 = JSONObject(body)
                    .optJSONArray("predictions")
                    ?.optJSONObject(0)
                    ?.optString("bytesBase64Encoded")
                    .orEmpty()
                if (b64.isBlank()) return null
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.let { BitmapUtils.ensureSoftwareBitmap(it) }
                    ?: return null
                val badge = if (language == SathiLanguage.BN) {
                    "সাথী এআই • Imagen দ্বারা তৈরি"
                } else {
                    "Generated by Sathi AI"
                }
                val caption = if (language == SathiLanguage.BN) {
                    "\"$prompt\"-এর ছবি তৈরি হয়েছে। নিচের 'ডাউনলোড করুন' বাটনে চাপ দিয়ে গ্যালারিতে সেভ করতে পারবেন।"
                } else {
                    "Created image for \"$prompt\". Tap Download below to save to gallery."
                }
                GeneratedImageOutcome(
                    bitmap = bmp,
                    caption = caption,
                    requiresFirebaseNotice = badge,
                    isPlaceholderPreview = false
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

class PlaceholderSathiImageGenRepository : SathiImageGenRepository {
    override suspend fun generateImage(
        prompt: String,
        language: SathiLanguage,
        referenceBitmap: Bitmap?
    ): Result<GeneratedImageOutcome> = withContext(Dispatchers.Default) {
        val cleanPrompt = prompt.trim().ifBlank {
            if (language == SathiLanguage.BN) "সুন্দর প্রকৃতির ছবি" else "Scenic sunrise landscape"
        }
        val bitmap = renderLabeledSamplePreview(cleanPrompt, language, referenceBitmap)
        val notice = if (language == SathiLanguage.BN) {
            if (referenceBitmap != null) {
                "উদাহরণ ছবির প্রিভিউ (ডাউনলোডযোগ্য) • পূর্ণ AI ইমেজ তৈরির জন্য সেটিংসে সঠিক Google API Key যুক্ত করুন।"
            } else {
                "নমুনা প্রিভিউ (ডাউনলোডযোগ্য) • আসল AI ছবির জন্য Google চাবি বা Firebase AI Logic সংযোগ করুন।"
            }
        } else {
            "Sample Preview (Downloadable) • Add a Google API Key for full Gemini image generation."
        }
        val caption = if (language == SathiLanguage.BN) {
            "\"$cleanPrompt\"-এর জন্য নমুনা প্রিভিউ কার্ড তৈরি করা হয়েছে। নিচের 'ডাউনলোড করুন' বাটনে চাপ দিলে ছবিটি ফোনের গ্যালারিতে সেভ হবে।\n\n$notice"
        } else {
            "Created a sample preview card for \"$cleanPrompt\". Tap Download below to save it to your gallery.\n\n$notice"
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

    private fun renderLabeledSamplePreview(
        prompt: String,
        language: SathiLanguage,
        referenceBitmap: Bitmap?
    ): Bitmap {
        val width = 640
        val height = 420
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(
                    Color.rgb(255, 217, 61),
                    Color.rgb(255, 248, 220),
                    Color.rgb(255, 255, 255)
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 30, 27, 22)
        }
        val cardRect = RectF(28f, 30f, width - 28f, height - 30f)
        canvas.drawRoundRect(cardRect, 28f, 28f, badgePaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 217, 61)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 28f, 28f, borderPaint)

        if (referenceBitmap != null) {
            val safeRef = BitmapUtils.ensureSoftwareBitmap(referenceBitmap)
            val destRect = Rect(48, 92, 228, 272)
            canvas.drawBitmap(safeRef, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(RectF(destRect), borderPaint)
        }

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 217, 61)
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            if (language == SathiLanguage.BN) "সাথী এআই • SATHI AI" else "SATHI AI • IMAGE PREVIEW",
            width / 2f,
            74f,
            tagPaint
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
            textAlign = if (referenceBitmap != null) Paint.Align.LEFT else Paint.Align.CENTER
        }
        val textX = if (referenceBitmap != null) 252f else width / 2f
        val maxLen = if (referenceBitmap != null) 22 else 34
        val clippedPrompt = prompt.take(maxLen) + if (prompt.length > maxLen) "..." else ""
        canvas.drawText(clippedPrompt, textX, 185f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 232, 160)
            textSize = 19f
            textAlign = if (referenceBitmap != null) Paint.Align.LEFT else Paint.Align.CENTER
        }
        canvas.drawText(
            if (language == SathiLanguage.BN) "গ্যালারিতে ডাউনলোড ও শেয়ার করা যাবে"
            else "Save & share to your gallery",
            textX,
            240f,
            subPaint
        )

        return bmp
    }
}
