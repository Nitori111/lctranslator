package com.example.lctranslator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class LlmProvider { CLAUDE, OPENAI, GEMINI }

object LlmClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        text: String,
        targetLang: String,
        provider: LlmProvider,
        apiKey: String,
        baseUrl: String = "",
        model: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "[No API key set]"
        val prompt = "Translate the following text to $targetLang. " +
                "Respond with ONLY the translation, no explanations:\n\n$text"
        when {
            baseUrl.isNotBlank() -> callOpenAICompat(prompt, apiKey, baseUrl, model)
            provider == LlmProvider.CLAUDE -> callClaude(prompt, apiKey, model)
            provider == LlmProvider.OPENAI -> callOpenAICompat(prompt, apiKey,
                "https://api.openai.com", model.ifBlank { "gpt-4o-mini" })
            provider == LlmProvider.GEMINI -> callGemini(prompt, apiKey, model)
            else -> "[Unknown provider]"
        }
    }

    suspend fun fetchModels(
        provider: LlmProvider,
        apiKey: String,
        baseUrl: String = ""
    ): List<String> = withContext(Dispatchers.IO) {
        when {
            baseUrl.isNotBlank() -> fetchOpenAICompatModels(apiKey, baseUrl)
            provider == LlmProvider.OPENAI -> fetchOpenAICompatModels(apiKey, "https://api.openai.com")
            provider == LlmProvider.CLAUDE -> listOf(
                "claude-opus-4-20250514",
                "claude-sonnet-4-20250514",
                "claude-haiku-4-5-20251001"
            )
            provider == LlmProvider.GEMINI -> listOf(
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-1.5-pro"
            )
            else -> emptyList()
        }
    }

    private fun fetchOpenAICompatModels(key: String, baseUrl: String): List<String> {
        val url = baseUrl.trimEnd('/') + "/v1/models"
        val req = Request.Builder()
            .url(url).addHeader("Authorization", "Bearer $key").get().build()
        val resp = http.newCall(req).execute()
        val json = JSONObject(resp.body!!.string())
        if (json.has("error")) throw RuntimeException(json.getJSONObject("error").getString("message"))
        val arr = json.getJSONArray("data")
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }.sorted()
    }

    private fun callClaude(prompt: String, key: String, modelOverride: String): String {
        val body = JSONObject().apply {
            put("model", modelOverride.ifBlank { "claude-sonnet-4-20250514" })
            put("max_tokens", 512)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON_MT)).build()
        val resp = http.newCall(req).execute()
        val json = JSONObject(resp.body!!.string())
        if (json.has("error")) throw RuntimeException(json.getJSONObject("error").getString("message"))
        return json.getJSONArray("content").getJSONObject(0).getString("text").trim()
    }

    private fun callOpenAICompat(prompt: String, key: String, baseUrl: String, model: String): String {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("max_tokens", 512)
        }
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(JSON_MT)).build()
        val resp = http.newCall(req).execute()
        val json = JSONObject(resp.body!!.string())
        if (json.has("error")) throw RuntimeException(json.getJSONObject("error").getString("message"))
        return json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }

    private fun callGemini(prompt: String, key: String, modelOverride: String): String {
        val m = modelOverride.ifBlank { "gemini-1.5-flash" }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().put("maxOutputTokens", 512))
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$key")
            .post(body.toString().toRequestBody(JSON_MT)).build()
        val resp = http.newCall(req).execute()
        val json = JSONObject(resp.body!!.string())
        if (json.has("error")) throw RuntimeException(json.getJSONObject("error").getString("message"))
        return json.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
            .getJSONObject(0).getString("text").trim()
    }
}
