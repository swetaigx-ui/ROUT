package com.rout.aiassistant.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Per-device enrollment for the ROUT backend.
 * No shared secret is embedded in the app. Each device enrolls once:
 * the user approves a one-use link (10 min expiry), the backend returns a
 * device credential exactly once, and ROUT stores it encrypted on-device.
 * The credential is revocable from Settings.
 */
class Enrollment(context: Context) {

    private val secure by lazy {
        try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "rout_enroll", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // AndroidKeyStore exists only on real Android runtimes; fall back so
            // host-side tests can run. On-device this branch is never taken.
            context.getSharedPreferences("rout_enroll_fallback", Context.MODE_PRIVATE)
        }
    }

    var deviceToken: String
        get() = secure.getString("device_token", "") ?: ""
        private set(v) = secure.edit().putString("device_token", v).apply()

    var enrolledBackend: String
        get() = secure.getString("enrolled_backend", "") ?: ""
        private set(v) = secure.edit().putString("enrolled_backend", v).apply()

    fun isEnrolledFor(backendUrl: String): Boolean =
        deviceToken.isNotBlank() && enrolledBackend == backendUrl.trimEnd('/')

    fun clear() {
        secure.edit().clear().apply()
    }

    data class EnrollStart(val enrollId: String, val approveUrl: String, val clientSecret: String)

    private fun randomSecret(): String {
        val b = ByteArray(32)
        java.security.SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(text: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private suspend fun post(url: String, body: JSONObject, bearer: String? = null): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000; readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()
        code to text
    }

    suspend fun start(backendUrl: String): EnrollStart? {
        return try {
            // Per-attempt secret: only this device can complete the enrollment,
            // even if someone else sees the approval URL.
            val clientSecret = randomSecret()
            val body = JSONObject().put("client_hash", sha256Hex(clientSecret))
            val (code, text) = post(backendUrl.trimEnd('/') + "/v1/enroll/start", body)
            if (code != 200) return null
            val o = JSONObject(text)
            EnrollStart(o.getString("enroll_id"), o.getString("approve_url"), clientSecret)
        } catch (e: Exception) { null }
    }

    /** Polls until the user approves (or attempts run out). Returns true when enrolled. */
    suspend fun complete(backendUrl: String, enrollId: String, clientSecret: String, attempts: Int = 40): Boolean {
        repeat(attempts) {
            try {
                val (code, text) = post(backendUrl.trimEnd('/') + "/v1/enroll/complete",
                    JSONObject().put("enroll_id", enrollId).put("client_secret", clientSecret))
                when (code) {
                    200 -> {
                        val token = JSONObject(text).getString("device_token")
                        deviceToken = token
                        enrolledBackend = backendUrl.trimEnd('/')
                        return true
                    }
                    202 -> delay(3000)       // still pending approval
                    else -> return false     // expired or error
                }
            } catch (e: Exception) { return false }
        }
        return false
    }

    suspend fun revoke(backendUrl: String): Boolean {
        val token = deviceToken
        if (token.isBlank()) return false
        return try {
            val (code, _) = post(backendUrl.trimEnd('/') + "/v1/revoke", JSONObject(), bearer = token)
            if (code == 200) { clear(); true } else false
        } catch (e: Exception) { false }
    }
}
