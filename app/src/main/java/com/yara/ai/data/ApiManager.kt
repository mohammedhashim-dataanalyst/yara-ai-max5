package com.yara.ai.data

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Multi-provider brain with adaptive auto-switching + manual primary.
 * Falls back to local learned brain then persona fallback — never lags.
 */
class ApiManager(private val store: MemoryStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    @Volatile var manualPrimary: String = ""

    suspend fun ask(systemPrompt: String, userText: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val providers = store.providers.filter { it.enabled && it.apiKey.isNotBlank() }
            val now = System.currentTimeMillis()

            val manual = providers.firstOrNull { it.name.equals(manualPrimary, true) && now > it.rateLimitedUntil }
            val others = providers.filter { it != manual && now > it.rateLimitedUntil }
                .sortedBy { it.lastLatencyMs }
            val order = listOfNotNull(manual) + others

            for (p in order) {
                try {
                    val t0 = System.currentTimeMillis()
                    val reply = callProvider(p, systemPrompt, userText)
                    val latency = System.currentTimeMillis() - t0
                    if (!reply.isNullOrBlank()) {
                        p.lastLatencyMs = if (p.lastLatencyMs == Long.MAX_VALUE) latency
                            else (p.lastLatencyMs + latency) / 2
                        p.failures = 0
                        store.saveProviders()
                        return@withContext reply.trim() to p.name
                    }
                } catch (e: RateLimit) {
                    p.rateLimitedUntil = now + RATE_LIMIT_COOLDOWN_MS
                    p.failures++
                    store.saveProviders()
                } catch (e: Exception) {
                    Log.w("ApiManager", "${p.name} failed: ${e.message}")
                    p.failures++
                    store.saveProviders()
                }
            }

            val learned = store.offlineReply(userText)
            if (learned != null) return@withContext learned to "local_brain"

            val emo = com.yara.ai.brain.EmotionEngine.detect(userText)
            return@withContext com.yara.ai.brain.PersonaEngine.offlineFallback(userText, emo) to "local_persona"
        }

    private fun callProvider(p: ProviderState, sys: String, user: String): String? =
        when (p.name.lowercase()) {
            "gemini" -> callGemini(p.apiKey, sys, user)
            "grok" -> callOpenAiStyle("https://api.x.ai/v1/chat/completions", p.apiKey, "grok-2-latest", sys, user)
            "openrouter" -> callOpenAiStyle("https://openrouter.ai/api/v1/chat/completions", p.apiKey, "openai/gpt-4o-mini", sys, user)
            "huggingface" -> callHuggingFace(p.apiKey, sys, user)
            else -> null
        }

    private fun callGemini(key: String, sys: String, user: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
        val body = JsonObject().apply {
            add("system_instruction", JsonObject().apply {
                add("parts", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", sys) })
                })
            })
            add("contents", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", com.google.gson.JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", user) })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.9)
                addProperty("maxOutputTokens", 300)
            })
        }.toString().toRequestBody(json)
        val req = Request.Builder().url(url).post(body).build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.code == 429 -> throw RateLimit()
                !resp.isSuccessful -> throw Exception("gemini ${resp.code}")
                else -> {
                    val j = JsonParser.parseString(resp.body?.string() ?: return null).asJsonObject
                    return j["candidates"]?.asJsonArray?.get(0)?.asJsonObject
                        ?.get("content")?.asJsonObject
                        ?.get("parts")?.asJsonArray?.get(0)?.asJsonObject
                        ?.get("text")?.asString
                }
            }
        }
    }

    private fun callOpenAiStyle(url: String, key: String, model: String, sys: String, user: String): String? {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 300)
            addProperty("temperature", 0.9)
            add("messages", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("role", "system"); addProperty("content", sys) })
                add(JsonObject().apply { addProperty("role", "user"); addProperty("content", user) })
            })
        }.toString().toRequestBody(json)
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $key").post(body).build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.code == 429 -> throw RateLimit()
                !resp.isSuccessful -> throw Exception("${resp.code}")
                else -> {
                    val j = JsonParser.parseString(resp.body?.string() ?: return null).asJsonObject
                    return j["choices"]?.asJsonArray?.get(0)?.asJsonObject
                        ?.get("message")?.asJsonObject?.get("content")?.asString
                }
            }
        }
    }

    private fun callHuggingFace(key: String, sys: String, user: String): String? {
        val url = "https://api-inference.huggingface.co/models/mistralai/Mistral-7B-Instruct-v0.2"
        val prompt = "<s>[INST] $sys\n\n$user [/INST]"
        val body = JsonObject().apply {
            addProperty("inputs", prompt)
            add("parameters", JsonObject().apply {
                addProperty("max_new_tokens", 200)
                addProperty("temperature", 0.9)
            })
        }.toString().toRequestBody(json)
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $key").post(body).build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.code == 429 -> throw RateLimit()
                !resp.isSuccessful -> throw Exception("hf ${resp.code}")
                else -> {
                    val txt = resp.body?.string() ?: return null
                    val arr = JsonParser.parseString(txt).asJsonArray
                    return arr[0].asJsonObject["generated_text"]?.asString?.substringAfter("[/INST]")?.trim()
                }
            }
        }
    }

    class RateLimit : Exception("rate limited")

    companion object { private const val RATE_LIMIT_COOLDOWN_MS = 60_000L }
}
