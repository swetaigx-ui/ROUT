package com.rout.aiassistant.control

import com.rout.aiassistant.ai.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Asks the ROUT backend for an action plan for ONE command. Only the command text and a request id are sent: never screen text,
 * images or notifications. Any failure (no endpoint deployed, 4xx/5xx, bad JSON, requestId mismatch, invalid plan) is returned as a
 * Failure the user can see. There is no silent fallback to "success".
 */
object PlanClient {
    sealed class Result { data class Plan(val plan: ControlPlan) : Result(); data class Failure(val reason: String) : Result() }

    fun newRequestId(): String = "r-" + java.util.UUID.randomUUID().toString().replace("-", "").take(24)

    suspend fun fetch(backendUrl: String, deviceToken: String, requestId: String, command: String): Result = withContext(Dispatchers.IO) {
        if (backendUrl.isBlank() || deviceToken.isBlank()) return@withContext Result.Failure("Backend enroll nahi hai, plan nahi ban paya.")
        try {
            val body = JSONObject().put("requestId", requestId).put("input", command.take(1000))
            val (code, text) = post(backendUrl.trimEnd('/') + "/v1/control/plan", body, mapOf("Authorization" to "Bearer $deviceToken"), timeoutMs = 25000)
            when {
                code == 404 -> Result.Failure("Backend mein plan endpoint abhi deploy nahi hua (404). Kuch nahi chala.")
                code == 401 -> Result.Failure("Backend ne device ko authorize nahi kiya (401).")
                code == 422 -> Result.Failure("Is command ka valid plan nahi bana.")
                code !in 200..299 -> Result.Failure("Backend error HTTP $code.")
                else -> when (val v = PlanValidator.parse(text, requestId)) {
                    is PlanResult.Valid -> Result.Plan(v.plan)
                    is PlanResult.Invalid -> Result.Failure("Backend ka plan app ke rules par fail hua: ${v.reason}")
                }
            }
        } catch (e: Exception) { Result.Failure("Backend unreachable: ${e.javaClass.simpleName}") }
    }
}
