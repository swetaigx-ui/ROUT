package com.rout.aiassistant.features.protect

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Settings for device-protection features. All OFF by default (explicit opt-in). */
object ProtectPrefs {
    private const val FILE = "rout_protect"

    @Volatile private var cached: android.content.SharedPreferences? = null

    private fun prefs(ctx: Context): android.content.SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: run {
                val app = ctx.applicationContext
                val p = try {
                    EncryptedSharedPreferences.create(
                        app, FILE,
                        MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (t: Throwable) {
                    // No AndroidKeyStore (e.g. Robolectric tests): fall back to plain prefs
                    app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                }
                p.also { cached = it }
            }
        }

    private const val K_ANTITHEFT_ON = "antitheft_on"
    private const val K_TRUSTED_NUMBER = "trusted_number"
    private const val K_SECRET_WORD = "secret_word"
    private const val K_DHUNDO_ON = "dhundo_on"
    private const val K_INTRUDER_ON = "intruder_on"
    private const val K_FAILED_COUNT = "failed_count"
    private const val K_APPLOCK_ON = "applock_on"
    private const val K_APPLOCK_PIN = "applock_pin"

    var Context.antiTheftOn: Boolean
        get() = prefs(this).getBoolean(K_ANTITHEFT_ON, false)
        set(v) = prefs(this).edit().putBoolean(K_ANTITHEFT_ON, v).apply()

    var Context.trustedNumber: String
        get() = prefs(this).getString(K_TRUSTED_NUMBER, "") ?: ""
        set(v) = prefs(this).edit().putString(K_TRUSTED_NUMBER, v.filter { it.isDigit() || it == '+' }).apply()

    var Context.secretWord: String
        get() = prefs(this).getString(K_SECRET_WORD, "") ?: ""
        set(v) = prefs(this).edit().putString(K_SECRET_WORD, v.trim().lowercase()).apply()

    var Context.dhundoOn: Boolean
        get() = prefs(this).getBoolean(K_DHUNDO_ON, false)
        set(v) = prefs(this).edit().putBoolean(K_DHUNDO_ON, v).apply()

    var Context.intruderOn: Boolean
        get() = prefs(this).getBoolean(K_INTRUDER_ON, false)
        set(v) = prefs(this).edit().putBoolean(K_INTRUDER_ON, v).apply()

    var Context.failedUnlocks: Int
        get() = prefs(this).getInt(K_FAILED_COUNT, 0)
        set(v) = prefs(this).edit().putInt(K_FAILED_COUNT, v).apply()

    var Context.appLockOn: Boolean
        get() = prefs(this).getBoolean(K_APPLOCK_ON, false)
        set(v) = prefs(this).edit().putBoolean(K_APPLOCK_ON, v).apply()

    var Context.appLockPin: String
        get() = prefs(this).getString(K_APPLOCK_PIN, "") ?: ""
        set(v) = prefs(this).edit().putString(K_APPLOCK_PIN, v).apply()

    fun Context.antiTheftConfigured() = antiTheftOn && trustedNumber.isNotBlank() && secretWord.isNotBlank()
}
