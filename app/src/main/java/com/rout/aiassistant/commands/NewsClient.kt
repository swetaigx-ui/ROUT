package com.rout.aiassistant.commands

import com.rout.aiassistant.ai.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Asks the ROUT backend /v1/news for ONE current-news query. The backend tries
 * Gemini Google-Search grounding first and falls back to public RSS headlines
 * (mode "rss-headlines") - the answer is always derived from named sources and
 * the mode is reported, never silently substituted. Only the query text leaves
 * the device. Any failure returns Failure with an honest reason.
 */
object NewsClient {
    sealed class Result {
        data class Answer(val text: String, val sources: List<String>, val mode: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    suspend fun fetch(backendUrl: String, deviceToken: String, query: String): Result = withContext(Dispatchers.IO) {
        if (backendUrl.isBlank() || deviceToken.isBlank()) return@withContext Result.Failure("Backend enroll nahi hai.")
        try {
            val body = JSONObject().put("query", query.take(400))
            val (code, text) = post(backendUrl.trimEnd('/') + "/v1/news", body, mapOf("Authorization" to "Bearer $deviceToken"), timeoutMs = 25000)
            when {
                code == 401 -> Result.Failure("Backend ne device ko authorize nahi kiya (401).")
                code == 429 -> Result.Failure("News service abhi busy hai (rate limit).")
                code !in 200..299 -> Result.Failure("Backend error HTTP $code.")
                else -> {
                    val j = try { JSONObject(text) } catch (e: Exception) { return@withContext Result.Failure("Backend ka jawab parse nahi hua.") }
                    val answer = j.optString("answer").trim()
                    if (answer.isEmpty()) return@withContext Result.Failure("News jawab khaali aaya.")
                    val srcs = mutableListOf<String>()
                    val arr = j.optJSONArray("sources")
                    if (arr != null) for (i in 0 until arr.length()) {
                        val t = arr.optJSONObject(i)?.optString("title").orEmpty().trim()
                        if (t.isNotEmpty()) srcs.add(t)
                    }
                    Result.Answer(answer, srcs.take(5), j.optString("mode", "unknown"))
                }
            }
        } catch (e: Exception) { Result.Failure("Backend unreachable: ${e.javaClass.simpleName}") }
    }
}
