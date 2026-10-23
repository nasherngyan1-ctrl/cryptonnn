package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.local.ApiKeyEntity
import com.example.data.local.OkxTradingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "MultiKeyAiRouter"

enum class ApiProviderType(
    val id: String,
    val titleKu: String,
    val keyPrefixHint: String,
    val defaultModel: String
) {
    GEMINI(
        id = "GEMINI",
        titleKu = "Google Gemini API (AIza...)",
        keyPrefixHint = "AIzaSy...",
        defaultModel = "gemini-2.5-flash"
    ),
    GROQ(
        id = "GROQ",
        titleKu = "Groq Ultra-Fast AI (gsk_...)",
        keyPrefixHint = "gsk_...",
        defaultModel = "llama-3.3-70b-versatile"
    ),
    OPENROUTER(
        id = "OPENROUTER",
        titleKu = "OpenRouter Multi-AI (sk-or-...)",
        keyPrefixHint = "sk-or-v1-...",
        defaultModel = "google/gemini-2.5-flash"
    ),
    OPENAI(
        id = "OPENAI",
        titleKu = "OpenAI ChatGPT (sk-...)",
        keyPrefixHint = "sk-proj-...",
        defaultModel = "gpt-4o-mini"
    ),
    DEEPSEEK(
        id = "DEEPSEEK",
        titleKu = "DeepSeek AI (sk-...)",
        keyPrefixHint = "sk-...",
        defaultModel = "deepseek-chat"
    );

    companion object {
        fun fromId(id: String): ApiProviderType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: GEMINI

        fun autoDetectProvider(rawKey: String): ApiProviderType {
            val k = rawKey.trim()
            return when {
                k.startsWith("AIza") -> GEMINI
                k.startsWith("gsk_") -> GROQ
                k.startsWith("sk-or-") -> OPENROUTER
                else -> GEMINI
            }
        }
    }
}

data class KeyRotationExecutionResult(
    val responseText: String,
    val usedKeyLabelKu: String,
    val usedProvider: String,
    val rotatedCountDuringCall: Int
)

class MultiKeyAiRouter(
    private val dao: OkxTradingDao
) {
    private val roundRobinCursor = AtomicInteger(0)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Automatically rotates through all enabled user API keys in Round-Robin order.
     * If a key hits quota (429), auth error (401/403), or server error, it marks that key
     * with a cooldown and immediately fails over to the next available key!
     */
    suspend fun generateWithAutoRotation(
        prompt: String,
        expectJson: Boolean,
        bitmap: Bitmap? = null,
        onKeySwitchCallback: ((String) -> Unit)? = null
    ): KeyRotationExecutionResult? = withContext(Dispatchers.IO) {
        val enabledKeys = dao.getEnabledApiKeys()
        if (enabledKeys.isEmpty()) return@withContext null

        val now = System.currentTimeMillis()
        val totalKeys = enabledKeys.size
        val startIndex = roundRobinCursor.getAndIncrement().mod(totalKeys)

        // Order keys starting from Round-Robin cursor, prioritizing keys not on cooldown
        val orderedIndices = (0 until totalKeys).map { (startIndex + it) % totalKeys }
        val readyKeys = orderedIndices.map { enabledKeys[it] }.filter { it.cooldownUntilMs <= now }
        val cooldownKeys = orderedIndices.map { enabledKeys[it] }.filter { it.cooldownUntilMs > now }
            .sortedBy { it.cooldownUntilMs }

        val candidatePool = readyKeys + cooldownKeys
        var rotations = 0

        for (keyEntity in candidatePool) {
            try {
                onKeySwitchCallback?.invoke("بەکارهێنانی کلیلی: ${keyEntity.labelKu} (${keyEntity.providerType})")
                val rawOutput = callProviderEndpoint(
                    keyEntity = keyEntity,
                    prompt = prompt,
                    expectJson = expectJson,
                    bitmap = bitmap
                )
                val cleanedOutput = if (expectJson) extractCleanJson(rawOutput) else rawOutput
                if (cleanedOutput.isNotBlank()) {
                    val updated = keyEntity.copy(
                        successCount = keyEntity.successCount + 1,
                        lastUsedAtMs = System.currentTimeMillis(),
                        cooldownUntilMs = 0L,
                        lastStatusKu = "چالاک و سەرکەوتوو (${keyEntity.providerType})"
                    )
                    dao.updateApiKey(updated)
                    return@withContext KeyRotationExecutionResult(
                        responseText = cleanedOutput,
                        usedKeyLabelKu = keyEntity.labelKu,
                        usedProvider = keyEntity.providerType,
                        rotatedCountDuringCall = rotations
                    )
                }
            } catch (e: Exception) {
                rotations++
                Log.w(TAG, "Key '${keyEntity.labelKu}' (${keyEntity.providerType}) failed, rotating to next key: ${e.message}")
                val cooldownMs = if (e.message?.contains("429") == true || e.message?.contains("quota", ignoreCase = true) == true) {
                    60_000L // 60s cooldown on rate limit
                } else {
                    20_000L
                }
                val updatedFailed = keyEntity.copy(
                    rotationFailoverCount = keyEntity.rotationFailoverCount + 1,
                    lastUsedAtMs = System.currentTimeMillis(),
                    cooldownUntilMs = System.currentTimeMillis() + cooldownMs,
                    lastStatusKu = "گۆڕدرا بۆ کلیلی دواتر (${e.message?.take(38) ?: "هەڵە"})"
                )
                dao.updateApiKey(updatedFailed)
            }
        }

        null
    }

    suspend fun testSingleKey(keyEntity: ApiKeyEntity): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val reply = callProviderEndpoint(
                keyEntity = keyEntity,
                prompt = "Respond with only the word OK",
                expectJson = false,
                bitmap = null
            )
            val latencyMs = System.currentTimeMillis() - startMs
            if (reply.isNotBlank()) {
                val updated = keyEntity.copy(
                    successCount = keyEntity.successCount + 1,
                    lastUsedAtMs = System.currentTimeMillis(),
                    cooldownUntilMs = 0L,
                    lastStatusKu = "تاقیکرایەوە: سەرکەوتوو (${latencyMs}ms)"
                )
                dao.updateApiKey(updated)
                true to "کلیلی ${keyEntity.labelKu} بە سەرکەوتوویی کار دەکات (${latencyMs}ms)"
            } else {
                false to "وەڵامی بەتاڵ لە کلیلی ${keyEntity.labelKu}"
            }
        } catch (e: Exception) {
            val msg = e.message?.take(80) ?: "کلیلەکە کار ناکات"
            val updated = keyEntity.copy(
                rotationFailoverCount = keyEntity.rotationFailoverCount + 1,
                lastStatusKu = "کێشە لە کلیل: $msg"
            )
            dao.updateApiKey(updated)
            false to "کێشە لە کلیلی ${keyEntity.labelKu}: $msg"
        }
    }

    private fun callProviderEndpoint(
        keyEntity: ApiKeyEntity,
        prompt: String,
        expectJson: Boolean,
        bitmap: Bitmap?
    ): String {
        val provider = ApiProviderType.fromId(keyEntity.providerType)
        return when (provider) {
            ApiProviderType.GEMINI -> callDirectGeminiRest(
                apiKey = keyEntity.apiKey.trim(),
                preferredModel = keyEntity.modelName.ifBlank { "gemini-2.5-flash" },
                prompt = prompt,
                expectJson = expectJson,
                bitmap = bitmap
            )
            ApiProviderType.GROQ -> callOpenAiCompatibleRest(
                endpointUrl = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = keyEntity.apiKey.trim(),
                model = if (bitmap != null) "meta-llama/llama-4-scout-17b-16e-instruct"
                else keyEntity.modelName.ifBlank { "llama-3.3-70b-versatile" },
                prompt = prompt,
                expectJson = expectJson,
                bitmap = bitmap
            )
            ApiProviderType.OPENROUTER -> callOpenAiCompatibleRest(
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = keyEntity.apiKey.trim(),
                model = keyEntity.modelName.ifBlank { "google/gemini-2.5-flash" },
                prompt = prompt,
                expectJson = expectJson,
                bitmap = bitmap
            )
            ApiProviderType.OPENAI -> callOpenAiCompatibleRest(
                endpointUrl = "https://api.openai.com/v1/chat/completions",
                apiKey = keyEntity.apiKey.trim(),
                model = keyEntity.modelName.ifBlank { "gpt-4o-mini" },
                prompt = prompt,
                expectJson = expectJson,
                bitmap = bitmap
            )
            ApiProviderType.DEEPSEEK -> callOpenAiCompatibleRest(
                endpointUrl = "https://api.deepseek.com/chat/completions",
                apiKey = keyEntity.apiKey.trim(),
                model = keyEntity.modelName.ifBlank { "deepseek-chat" },
                prompt = prompt,
                expectJson = expectJson,
                bitmap = null // DeepSeek chat endpoint is text-first
            )
        }
    }

    private fun callDirectGeminiRest(
        apiKey: String,
        preferredModel: String,
        prompt: String,
        expectJson: Boolean,
        bitmap: Bitmap?
    ): String {
        // Automatic model fallback chain inside the same Gemini key if one model is unavailable/rate-limited
        val candidateModels = listOf(
            preferredModel,
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash"
        ).distinct()

        var lastError: Exception? = null
        for (model in candidateModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val partsArray = JSONArray()
                if (bitmap != null) {
                    val base64Jpeg = encodeBitmapToBase64Jpeg(bitmap)
                    val inlineDataObj = JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Jpeg)
                    }
                    partsArray.put(JSONObject().apply { put("inline_data", inlineDataObj) })
                }
                val finalPrompt = if (expectJson) {
                    "$prompt\n\nIMPORTANT: Return ONLY valid raw JSON without markdown code blocks."
                } else prompt
                partsArray.put(JSONObject().apply { put("text", finalPrompt) })

                val payload = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", partsArray)
                    }))
                    if (expectJson) {
                        put("generationConfig", JSONObject().apply {
                            put("responseMimeType", "application/json")
                            put("temperature", 0.25)
                        })
                    }
                }

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: ${bodyStr.take(120)}")
                    }
                    val root = JSONObject(bodyStr)
                    val text = root.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "")
                        .orEmpty()
                    if (text.isNotBlank()) return text
                }
            } catch (e: Exception) {
                lastError = e
                // If invalid API key (400/401/403 API_KEY_INVALID), don't waste time trying other models on same key
                if (e.message?.contains("API_KEY_INVALID") == true || e.message?.contains("HTTP 401") == true) {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Gemini API request failed")
    }

    private fun callOpenAiCompatibleRest(
        endpointUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        expectJson: Boolean,
        bitmap: Bitmap?
    ): String {
        val messagesArray = JSONArray()
        val finalPrompt = if (expectJson) {
            "$prompt\n\nIMPORTANT: Respond with ONLY a valid JSON object matching the requested fields. Do not include markdown fences."
        } else prompt

        if (bitmap != null) {
            val base64Jpeg = encodeBitmapToBase64Jpeg(bitmap)
            val contentArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", finalPrompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Jpeg")
                    })
                })
            }
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", contentArr)
            })
        } else {
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", finalPrompt)
            })
        }

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.25)
            if (expectJson && bitmap == null) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }

        val request = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${bodyStr.take(120)}")
            }
            val root = JSONObject(bodyStr)
            return root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
        }
    }

    private fun encodeBitmapToBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun extractCleanJson(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }
}
