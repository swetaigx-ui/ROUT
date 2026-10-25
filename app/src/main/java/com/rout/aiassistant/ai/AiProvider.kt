package com.rout.aiassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ChatTurn(val role: String, val text: String)

sealed class AiResult {
    data class Reply(val text: String, val provider: String) : AiResult()
    /** AI is not configured yet. The app must show an honest setup-required state. */
    data class SetupRequired(val message: String) : AiResult()
    data class Failure(val reason: String) : AiResult()
}

interface AiProvider {
    val name: String
    suspend fun chat(history: List<ChatTurn>, userText: String): AiResult
}

private fun post(url: String, body: JSONObject, headers: Map<String, String>, timeoutMs: Int = 20000): Pair<Int, String> {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
    conn.disconnect()
    return code to text
}

/**
 * Secure default: ROUT backend proxy. The AI key lives on the server only.
 * Deploy backend/ from the source tree; set the deployed URL in Settings.
 */
class BackendProxyProvider(private val baseUrl: String, private val deviceToken: String) : AiProvider {
    override val name = "ROUT backend"
    override suspend fun chat(history: List<ChatTurn>, userText: String): AiResult = withContext(Dispatchers.IO) {
        fun attempt(): AiResult = try {
            val msgs = JSONArray()
            history.takeLast(20).forEach { t ->
                msgs.put(JSONObject().put("role", if (t.role == "user") "user" else "assistant").put("text", t.text))
            }
            val body = JSONObject().put("messages", msgs).put("input", userText)
            val (code, text) = post(baseUrl.trimEnd('/') + "/v1/chat", body,
                mapOf("Authorization" to "Bearer $deviceToken"))
            if (code in 200..299) {
                val reply = JSONObject(text).optString("reply")
                if (reply.isNotBlank()) AiResult.Reply(reply, name) else AiResult.Failure("Backend returned an empty reply")
            } else {
                AiResult.Failure("Backend error HTTP $code")
            }
        } catch (e: Exception) {
            AiResult.Failure("Backend unreachable: ${e.javaClass.simpleName}")
        }
        val first = attempt()
        // One automatic retry on network failure (cold starts, transients) before showing an error.
        if (first is AiResult.Failure && first.reason.startsWith("Backend unreachable")) attempt() else first
    }
}

/**
 * Advanced/developer mode: user enters their own provider key on-device (stored encrypted).
 * This is NOT the recommended secure setup - prefer the backend proxy. The key is never
 * shipped inside the app or committed to the repository.
 * Model: verified against Google AI docs at build time; see README.
 */
class GeminiDirectProvider(private val apiKey: String) : AiProvider {
    override val name = "Gemini (developer key)"
    override suspend fun chat(history: List<ChatTurn>, userText: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val contents = JSONArray()
            history.takeLast(10).forEach { t ->
                contents.put(JSONObject()
                    .put("role", if (t.role == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text))))
            }
            contents.put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userText))))
            val body = JSONObject().put("contents", contents)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                    "You are ROUT, a concise personal assistant inside an Android app that can itself open any " +
                    "installed app by name and place phone calls after on-screen confirmation. The user speaks Hindi, " +
                    "Hinglish and English. Reply in the same language the user used. Keep answers short and spoken-friendly. " +
                    "Never say you cannot open apps or make calls - if asked, tell the user ROUT can do it directly, " +
                    "e.g. just say \"YouTube kholo\" or \"Rahul ko call karo\"."))))
            val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-3.6-flash:generateContent"
            val (code, text) = post(url, body, mapOf("x-goog-api-key" to apiKey))
            if (code in 200..299) {
                val root = JSONObject(text)
                val parts = root.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text") ?: "")
                if (sb.isNotBlank()) AiResult.Reply(sb.toString().trim(), name) else AiResult.Failure("Gemini returned no text")
            } else {
                AiResult.Failure("Gemini error HTTP $code")
            }
        } catch (e: Exception) {
            AiResult.Failure("Gemini request failed: ${e.javaClass.simpleName}")
        }
    }
}

enum class AiMode { BACKEND, BACKEND_NEEDS_ENROLLMENT, DEV_KEY, SETUP_REQUIRED }

class ProviderManager(
    private val backendUrl: String,
    private val devKey: String,
    private val devEnabled: Boolean,
    private val enrollment: Enrollment?
) {
    fun mode(): AiMode = when {
        backendUrl.isNotBlank() && enrollment != null && enrollment.isEnrolledFor(backendUrl) -> AiMode.BACKEND
        backendUrl.isNotBlank() -> AiMode.BACKEND_NEEDS_ENROLLMENT
        devEnabled && devKey.isNotBlank() -> AiMode.DEV_KEY
        else -> AiMode.SETUP_REQUIRED
    }

    fun provider(): AiProvider? = when (mode()) {
        AiMode.BACKEND -> BackendProxyProvider(backendUrl, enrollment!!.deviceToken)
        AiMode.DEV_KEY -> GeminiDirectProvider(devKey)
        else -> null
    }

    fun statusLabel(): String = when (mode()) {
        AiMode.BACKEND -> "Connected via ROUT backend"
        AiMode.BACKEND_NEEDS_ENROLLMENT -> "Backend set - connect this device"
        AiMode.DEV_KEY -> "Developer key mode (advanced)"
        AiMode.SETUP_REQUIRED -> "AI setup required"
    }
}
