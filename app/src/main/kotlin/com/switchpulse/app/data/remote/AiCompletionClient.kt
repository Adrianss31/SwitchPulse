package com.switchpulse.app.data.remote

import com.switchpulse.app.data.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed interface AiCallResult {
    data class Success(val text: String, val httpCode: Int = 200) : AiCallResult
    data class Failure(val message: String, val httpCode: Int? = null) : AiCallResult
}

/**
 * Unified transport layer across Google Gemini, OpenRouter and DeepSeek chat/JSON generation APIs.
 */
@OptIn(ExperimentalSerializationApi::class)
class AiCompletionClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val geminiEndpointOverride: String? = null,
    private val openRouterEndpointOverride: String? = null,
    private val deepSeekEndpointOverride: String? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun resolveEndpoint(provider: AiProvider, modelId: String): String = when (provider) {
        AiProvider.GEMINI -> geminiEndpointOverride?.takeIf { it.isNotBlank() }
            ?: "https://generativelanguage.googleapis.com/v1beta/models/${modelId.trim()}:generateContent"
        AiProvider.OPENROUTER -> openRouterEndpointOverride?.takeIf { it.isNotBlank() }
            ?: "https://openrouter.ai/api/v1/chat/completions"
        AiProvider.DEEPSEEK -> deepSeekEndpointOverride?.takeIf { it.isNotBlank() }
            ?: "https://api.deepseek.com/chat/completions"
    }

    suspend fun generate(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        prompt: String,
        temperature: Double = 0.25,
        maxOutputTokens: Int = 4096,
        expectJsonObject: Boolean = true,
        enableGeminiGoogleSearch: Boolean = false
    ): AiCallResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider) }
        if (cleanKey.isBlank()) {
            return@withContext AiCallResult.Failure("Chiave API mancante per ${provider.displayName}")
        }

        val request = when (provider) {
            AiProvider.GEMINI -> buildGeminiRequest(
                apiKey = cleanKey,
                modelId = effectiveModel,
                prompt = prompt,
                temperature = temperature,
                maxOutputTokens = maxOutputTokens,
                enableGoogleSearch = enableGeminiGoogleSearch
            )
            AiProvider.OPENROUTER,
            AiProvider.DEEPSEEK -> buildOpenAiCompatibleRequest(
                provider = provider,
                apiKey = cleanKey,
                modelId = effectiveModel,
                prompt = prompt,
                temperature = temperature,
                maxOutputTokens = maxOutputTokens,
                expectJsonObject = expectJsonObject
            )
        }

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = extractProviderError(provider, raw).ifBlank { "richiesta rifiutata" }
                    return@use AiCallResult.Failure(
                        message = "HTTP ${response.code}: $errMsg",
                        httpCode = response.code
                    )
                }

                val text = when (provider) {
                    AiProvider.GEMINI -> extractGeminiText(raw)
                    AiProvider.OPENROUTER,
                    AiProvider.DEEPSEEK -> extractOpenAiText(raw)
                }

                if (text.isBlank()) {
                    AiCallResult.Failure("Risposta vuota da ${provider.shortLabel} ($effectiveModel)", response.code)
                } else {
                    AiCallResult.Success(text = text, httpCode = response.code)
                }
            }
        }.getOrElse { err ->
            AiCallResult.Failure("Timeout/errore rete ${provider.shortLabel}: ${err.message ?: "sconosciuto"}")
        }
    }

    suspend fun testConnection(
        provider: AiProvider,
        apiKey: String,
        modelId: String
    ): Result<String> {
        val cleanKey = apiKey.trim()
        val effectiveModel = modelId.trim().ifBlank { GeminiModels.defaultModelFor(provider) }
        if (cleanKey.isBlank()) {
            return Result.failure(IllegalArgumentException("Inserisci prima la API key per ${provider.displayName}."))
        }

        val startMs = System.currentTimeMillis()
        return when (
            val outcome = generate(
                provider = provider,
                apiKey = cleanKey,
                modelId = effectiveModel,
                prompt = "Rispondi esattamente con: OK",
                temperature = 0.0,
                maxOutputTokens = if (provider == AiProvider.GEMINI) 16 else 64,
                expectJsonObject = false,
                enableGeminiGoogleSearch = false
            )
        ) {
            is AiCallResult.Success -> {
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val preview = outcome.text.replace("\n", " ").trim().take(24)
                Result.success(
                    "✨ Connessione riuscita a ${provider.shortLabel} · $effectiveModel (${elapsed} ms${if (preview.isNotBlank()) " · \"$preview\"" else ""})"
                )
            }
            is AiCallResult.Failure -> Result.failure(IllegalStateException(outcome.message))
        }
    }

    private fun buildGeminiRequest(
        apiKey: String,
        modelId: String,
        prompt: String,
        temperature: Double,
        maxOutputTokens: Int,
        enableGoogleSearch: Boolean
    ): Request {
        val thinking = GeminiModels.thinkingConfigFor(modelId)
        val reqObj = if (enableGoogleSearch) {
            GeminiGenerateRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                tools = listOf(GeminiTool(emptyMap())),
                generationConfig = GeminiGenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = maxOutputTokens,
                    thinkingConfig = thinking
                )
            )
        } else {
            GeminiGenerateRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = temperature,
                    maxOutputTokens = maxOutputTokens,
                    thinkingConfig = thinking
                )
            )
        }
        return Request.Builder()
            .url(resolveEndpoint(AiProvider.GEMINI, modelId))
            .header("x-goog-api-key", apiKey)
            .post(json.encodeToString(reqObj).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildOpenAiCompatibleRequest(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        prompt: String,
        temperature: Double,
        maxOutputTokens: Int,
        expectJsonObject: Boolean
    ): Request {
        val isDeepSeekReasoner = provider == AiProvider.DEEPSEEK &&
            modelId.contains("reasoner", ignoreCase = true)
        // Only DeepSeek's non-reasoner endpoint consistently enforces json_object without breaking arrays
        // or OpenRouter heterogeneous providers lacking json_object support.
        val responseFormat = if (provider == AiProvider.DEEPSEEK && expectJsonObject && !isDeepSeekReasoner) {
            OpenAiResponseFormat(type = "json_object")
        } else {
            null
        }
        val reqObj = OpenAiChatRequest(
            model = modelId,
            messages = listOf(OpenAiRequestMessageDto(role = "user", content = prompt)),
            temperature = if (isDeepSeekReasoner) null else temperature,
            max_tokens = maxOutputTokens,
            response_format = responseFormat
        )
        val builder = Request.Builder()
            .url(resolveEndpoint(provider, modelId))
            .header("Authorization", "Bearer $apiKey")
            .post(json.encodeToString(reqObj).toRequestBody(JSON_MEDIA_TYPE))
        if (provider == AiProvider.OPENROUTER) {
            builder
                .header("HTTP-Referer", "https://github.com/Adrianss31/SwitchPulse")
                .header("X-Title", "SwitchPulse")
        }
        return builder.build()
    }

    private fun extractGeminiText(raw: String): String {
        val envelope = json.decodeFromString<GeminiGenerateResponse>(raw)
        return envelope.candidates
            .firstOrNull()
            ?.content
            ?.parts
            .orEmpty()
            .filter { it.thought != true && !it.text.isNullOrBlank() }
            .joinToString("\n") { it.text.orEmpty() }
            .trim()
    }

    private fun extractOpenAiText(raw: String): String {
        val envelope = json.decodeFromString<OpenAiChatResponse>(raw)
        val content = envelope.choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
        return THINK_BLOCK_REGEX.replace(content, "").trim()
    }

    private fun extractProviderError(provider: AiProvider, rawBody: String): String {
        val msg = when (provider) {
            AiProvider.GEMINI -> runCatching {
                json.decodeFromString<GeminiErrorEnvelope>(rawBody).error?.message
            }.getOrNull()
            AiProvider.OPENROUTER,
            AiProvider.DEEPSEEK -> runCatching {
                json.decodeFromString<OpenAiErrorEnvelope>(rawBody).error?.message
            }.getOrNull() ?: runCatching {
                json.decodeFromString<GeminiErrorEnvelope>(rawBody).error?.message
            }.getOrNull()
        }
        return msg.orEmpty().trim().take(240)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val THINK_BLOCK_REGEX = Regex("(?is)<think>.*?</think>")

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(55, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiRequestMessageDto>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val response_format: OpenAiResponseFormat? = null
)

@Serializable
private data class OpenAiRequestMessageDto(
    val role: String,
    val content: String
)

@Serializable
private data class OpenAiResponseFormat(
    val type: String
)

@Serializable
private data class OpenAiChatResponse(
    val choices: List<OpenAiChoiceDto> = emptyList()
)

@Serializable
private data class OpenAiChoiceDto(
    val message: OpenAiResponseMessageDto? = null
)

@Serializable
private data class OpenAiResponseMessageDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
private data class OpenAiErrorEnvelope(
    val error: OpenAiErrorDetail? = null
)

@Serializable
private data class OpenAiErrorDetail(
    val message: String? = null
)
