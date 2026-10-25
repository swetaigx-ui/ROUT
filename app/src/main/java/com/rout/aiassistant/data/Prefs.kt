package com.rout.aiassistant.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore(name = "rout_settings")

class Prefs(private val context: Context) {
    companion object {
        val LANGUAGE = stringPreferencesKey("language")            // "hi-IN" | "en-IN"
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val WAKE_ENABLED = booleanPreferencesKey("wake_enabled")
        val ORB_ENABLED = booleanPreferencesKey("orb_enabled")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        const val DEFAULT_BACKEND_URL = "https://rout-backend.rout-1ed.workers.dev"
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val DEV_AI_ENABLED = booleanPreferencesKey("dev_ai_enabled")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val TRANSLATE_MODE = booleanPreferencesKey("translate_mode")
        val BRIEFING_COMMAND = stringPreferencesKey("briefing_command")
        val BRIEFING_HOUR = intPreferencesKey("briefing_hour")
        val BRIEFING_MINUTE = intPreferencesKey("briefing_minute")
    }

    val language: Flow<String> = context.ds.data.map { it[LANGUAGE] ?: "hi-IN" }
    val speechRate: Flow<Float> = context.ds.data.map { it[SPEECH_RATE] ?: 1.0f }
    val wakeEnabled: Flow<Boolean> = context.ds.data.map { it[WAKE_ENABLED] ?: false }
    val orbEnabled: Flow<Boolean> = context.ds.data.map { it[ORB_ENABLED] ?: false }
    val backendUrl: Flow<String> = context.ds.data.map { it[BACKEND_URL] ?: DEFAULT_BACKEND_URL }
    val weatherCity: Flow<String> = context.ds.data.map { it[WEATHER_CITY] ?: "" }
    val onboarded: Flow<Boolean> = context.ds.data.map { it[ONBOARDED] ?: false }
    val reducedMotion: Flow<Boolean> = context.ds.data.map { it[REDUCED_MOTION] ?: false }
    val devAiEnabled: Flow<Boolean> = context.ds.data.map { it[DEV_AI_ENABLED] ?: false }
    val voiceName: Flow<String> = context.ds.data.map { it[VOICE_NAME] ?: "Achernar" }
    val translateMode: Flow<Boolean> = context.ds.data.map { it[TRANSLATE_MODE] ?: false }
    val briefingCommand: Flow<String> = context.ds.data.map { it[BRIEFING_COMMAND] ?: "" }
    val briefingTime: Flow<Pair<Int, Int>?> = context.ds.data.map { p ->
        val h = p[BRIEFING_HOUR]; val m = p[BRIEFING_MINUTE]
        if (h != null && m != null) h to m else null
    }

    suspend fun setLanguage(v: String) = context.ds.edit { it[LANGUAGE] = v }
    suspend fun setSpeechRate(v: Float) = context.ds.edit { it[SPEECH_RATE] = v }
    suspend fun setWakeEnabled(v: Boolean) = context.ds.edit { it[WAKE_ENABLED] = v }
    suspend fun setOrbEnabled(v: Boolean) = context.ds.edit { it[ORB_ENABLED] = v }
    suspend fun setBackendUrl(v: String) = context.ds.edit { it[BACKEND_URL] = v }
    suspend fun setWeatherCity(v: String) = context.ds.edit { it[WEATHER_CITY] = v }
    suspend fun setOnboarded(v: Boolean) = context.ds.edit { it[ONBOARDED] = v }
    suspend fun setReducedMotion(v: Boolean) = context.ds.edit { it[REDUCED_MOTION] = v }
    suspend fun setDevAiEnabled(v: Boolean) = context.ds.edit { it[DEV_AI_ENABLED] = v }
    suspend fun setVoiceName(v: String) = context.ds.edit { it[VOICE_NAME] = v }
    suspend fun setTranslateMode(v: Boolean) = context.ds.edit { it[TRANSLATE_MODE] = v }
    suspend fun setBriefing(command: String, hour: Int, minute: Int) = context.ds.edit {
        it[BRIEFING_COMMAND] = command; it[BRIEFING_HOUR] = hour; it[BRIEFING_MINUTE] = minute
    }
    suspend fun clearBriefing() = context.ds.edit {
        it.remove(BRIEFING_COMMAND); it.remove(BRIEFING_HOUR); it.remove(BRIEFING_MINUTE)
    }

    // Developer/advanced AI key: stored encrypted on-device, entered by the user at runtime.
    // Never shipped in the app, never committed to source control.
    private val securePrefs by lazy {
        try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "rout_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // AndroidKeyStore exists only on real Android runtimes; fall back so
            // host-side tests can run. On-device this branch is never taken.
            context.getSharedPreferences("rout_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    var devApiKey: String
        get() = securePrefs.getString("dev_api_key", "") ?: ""
        set(v) { securePrefs.edit().putString("dev_api_key", v).apply() }
}
