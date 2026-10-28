package com.rout.aiassistant.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        val DEVICE_VOICE_ENABLED = booleanPreferencesKey("device_voice_enabled")
        val PERSONA_MODE = stringPreferencesKey("persona_mode")
        val USER_NAME = stringPreferencesKey("user_name")
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val WARBY_EMOTIONS = booleanPreferencesKey("warby_emotions")
        val TRANSLATE_MODE = booleanPreferencesKey("translate_mode")
        val BATTERY_ALERT = booleanPreferencesKey("battery_alert")
        val FLIP_SILENT = booleanPreferencesKey("flip_silent")
        val BEDTIME_HOUR = intPreferencesKey("bedtime_hour")
        val BEDTIME_MINUTE = intPreferencesKey("bedtime_minute")
        val BRIEFING_COMMAND = stringPreferencesKey("briefing_command")
        val BRIEFING_HOUR = intPreferencesKey("briefing_hour")
        val BRIEFING_MINUTE = intPreferencesKey("briefing_minute")
        /** Cloud transfer of screen content. New key on purpose: no older toggle can enable it. Default OFF. */
        val SCREEN_CLOUD_CONSENT_V2 = booleanPreferencesKey("screen_cloud_consent_v2")
    }

    suspend fun screenCloudAllowed(): Boolean = context.ds.data.first()[SCREEN_CLOUD_CONSENT_V2] == true
    suspend fun setScreenCloudAllowed(v: Boolean) = context.ds.edit { it[SCREEN_CLOUD_CONSENT_V2] = v }

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
    val userName: Flow<String> = context.ds.data.map { it[USER_NAME] ?: "" }
    /** Spoken name for the listening session. Blank = default "ROUT". Separate from [userName] (the user's own name). */
    val assistantName: Flow<String> = context.ds.data.map { it[ASSISTANT_NAME] ?: "" }
    /** Blank clears (back to default ROUT). An invalid name is rejected and the previous value is KEPT. Returns true when stored/cleared. */
    suspend fun setAssistantName(v: String): Boolean {
        val s = com.rout.aiassistant.voice.WakeName.sanitize(v)
        if (s.isBlank()) { context.ds.edit { it.remove(ASSISTANT_NAME) }; return true }
        if (!com.rout.aiassistant.voice.WakeName.isValid(s)) return false
        context.ds.edit { it[ASSISTANT_NAME] = s }
        return true
    }
    suspend fun setUserName(v: String) = context.ds.edit { it[USER_NAME] = v.take(30) }
    val personaMode: Flow<String> = context.ds.data.map { it[PERSONA_MODE] ?: "" }
    val warbyEmotions: Flow<Boolean> = context.ds.data.map { it[WARBY_EMOTIONS] ?: true }
    val translateMode: Flow<Boolean> = context.ds.data.map { it[TRANSLATE_MODE] ?: false }
    val batteryAlert: Flow<Boolean> = context.ds.data.map { it[BATTERY_ALERT] ?: false }
    val flipSilent: Flow<Boolean> = context.ds.data.map { it[FLIP_SILENT] ?: false }
    val bedtime: Flow<Pair<Int, Int>?> = context.ds.data.map { p ->
        val h = p[BEDTIME_HOUR]; val m = p[BEDTIME_MINUTE]
        if (h != null && m != null) h to m else null
    }
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
    // Robotic on-device voice. Off by default: Gyan rejected the robotic voice
    // (2026-10-27 voice note), so it plays only when he explicitly turns this on.
    val deviceVoiceEnabled: Flow<Boolean> = context.ds.data.map { it[DEVICE_VOICE_ENABLED] ?: false }
    suspend fun setDeviceVoiceEnabled(v: Boolean) = context.ds.edit { it[DEVICE_VOICE_ENABLED] = v }
    suspend fun setPersonaMode(v: String) = context.ds.edit { it[PERSONA_MODE] = v }
    suspend fun setWarbyEmotions(v: Boolean) = context.ds.edit { it[WARBY_EMOTIONS] = v }
    suspend fun setTranslateMode(v: Boolean) = context.ds.edit { it[TRANSLATE_MODE] = v }
    suspend fun setBatteryAlert(v: Boolean) = context.ds.edit { it[BATTERY_ALERT] = v }
    suspend fun setFlipSilent(v: Boolean) = context.ds.edit { it[FLIP_SILENT] = v }
    suspend fun setBedtime(hour: Int, minute: Int) = context.ds.edit {
        it[BEDTIME_HOUR] = hour; it[BEDTIME_MINUTE] = minute
    }
    suspend fun clearBedtime() = context.ds.edit { it.remove(BEDTIME_HOUR); it.remove(BEDTIME_MINUTE) }
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
