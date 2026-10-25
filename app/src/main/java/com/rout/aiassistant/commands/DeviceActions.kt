package com.rout.aiassistant.commands

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import com.rout.aiassistant.service.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionResult(val ok: Boolean, val message: String, val needsConfirmation: Boolean = false)

object DeviceActions {

    private fun launch(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    private val appAliases = mapOf(
        "youtube" to listOf("com.google.android.youtube"),
        "google" to listOf("com.google.android.googlequicksearchbox"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome"),
        "settings" to listOf("com.android.settings"),
        "setting" to listOf("com.android.settings"),
        "maps" to listOf("com.google.android.apps.maps"),
        "map" to listOf("com.google.android.apps.maps"),
        "whatsapp" to listOf("com.whatsapp"),
        "instagram" to listOf("com.instagram.android"),
        "gmail" to listOf("com.google.android.gm"),
        "camera" to listOf("com.android.camera2", "com.android.camera"),
        "photos" to listOf("com.google.android.apps.photos"),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "spotify" to listOf("com.spotify.music"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "files" to listOf("com.google.android.documentsui"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
        "phone" to listOf("com.google.android.dialer", "com.android.dialer"),
        "translate" to listOf("com.google.android.apps.translate"),
        "drive" to listOf("com.google.android.apps.docs")
    )

    // Devanagari app names -> latin alias keys. First contained key wins.
    private val devanagariAliases = listOf(
        "\u092f\u0942\u091f\u094d\u092f\u0942\u092c" to "youtube",
        "\u0935\u094d\u0939\u093e\u091f\u094d\u0938\u090f\u092a" to "whatsapp",
        "\u0935\u094d\u0939\u093e\u091f\u094d\u0938\u0910\u092a" to "whatsapp",
        "\u0935\u093e\u091f\u094d\u0938\u090f\u092a" to "whatsapp",
        "\u0907\u0902\u0938\u094d\u091f\u093e\u0917\u094d\u0930\u093e\u092e" to "instagram",
        "\u0907\u0928\u094d\u0938\u094d\u091f\u093e\u0917\u094d\u0930\u093e\u092e" to "instagram",
        "\u0907\u0902\u0938\u094d\u091f\u093e" to "instagram",
        "\u092b\u0947\u0938\u092c\u0941\u0915" to "facebook",
        "\u0915\u094d\u0930\u094b\u092e" to "chrome",
        "\u091c\u0940\u092e\u0947\u0932" to "gmail",
        "\u092e\u0948\u092a\u094d\u0938" to "maps",
        "\u0928\u0915\u094d\u0936\u093e" to "maps",
        "\u0915\u0948\u092e\u0930\u093e" to "camera",
        "\u092b\u094b\u091f\u094b\u091c\u093c" to "photos",
        "\u092b\u094b\u091f\u094b" to "photos",
        "\u0917\u0948\u0932\u0930\u0940" to "photos",
        "\u0938\u0947\u091f\u093f\u0902\u0917\u094d\u0938" to "settings",
        "\u0938\u0947\u091f\u093f\u0902\u0917" to "settings",
        "\u092a\u094d\u0932\u0947 \u0938\u094d\u091f\u094b\u0930" to "play store",
        "\u0915\u0948\u0932\u0915\u0941\u0932\u0947\u091f\u0930" to "calculator",
        "\u0915\u094d\u0932\u0949\u0915" to "clock",
        "\u0918\u0921\u093c\u0940" to "clock",
        "\u092b\u093e\u0907\u0932\u0947\u0902" to "files",
        "\u092b\u093e\u0907\u0932" to "files",
        "\u0938\u0902\u0926\u0947\u0936" to "messages",
        "\u092e\u0948\u0938\u0947\u091c" to "messages",
        "\u0921\u093e\u092f\u0932\u0930" to "phone",
        "\u092b\u094b\u0928" to "phone",
        "\u0938\u094d\u092a\u0949\u091f\u093f\u092b\u093e\u0908" to "spotify",
        "\u091f\u0947\u0932\u0940\u0917\u094d\u0930\u093e\u092e" to "telegram",
        "\u091f\u094d\u0935\u093f\u091f\u0930" to "twitter",
        "\u0938\u094d\u0928\u0948\u092a\u091a\u0948\u091f" to "snapchat",
        "\u091f\u094d\u0930\u093e\u0902\u0938\u0932\u0947\u091f" to "translate",
        "\u0905\u0928\u0941\u0935\u093e\u0926" to "translate",
        "\u0917\u0942\u0917\u0932" to "google",
        "\u0921\u094d\u0930\u093e\u0907\u0935" to "drive"
    )

    /** Map a spoken Devanagari app name to its latin alias key, if known. */
    fun normalizeAppName(name: String): String {
        if (name.none { it in '\u0900'..'\u097F' }) return name
        return devanagariAliases.firstOrNull { name.contains(it.first) }?.second ?: name
    }

    fun openApp(context: Context, nameRaw: String): ActionResult {
        val name = normalizeAppName(nameRaw.trim().lowercase())
        val pm = context.packageManager
        appAliases[name]?.forEach { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { if (launch(context, it)) return ActionResult(true, "$nameRaw khol diya.") }
        }
        // Fallback: fuzzy search installed launchable apps
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        val match = apps.firstOrNull { resolve ->
            val label = resolve.loadLabel(pm).toString().lowercase()
            label.contains(name) || name.contains(label)
        }
        if (match != null) {
            val pkg = match.activityInfo.packageName
            pm.getLaunchIntentForPackage(pkg)?.let {
                if (launch(context, it)) {
                    val label = match.loadLabel(pm).toString()
                    return ActionResult(true, "$label khol diya.")
                }
            }
        }
        return ActionResult(false, "\"$nameRaw\" naam ka app is phone par nahi mila. Naam saaf bolke dobara try karo.")
    }

    fun webSearch(context: Context, query: String): ActionResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (launch(context, intent)) return ActionResult(true, "Searching Google for $query")
        val url = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        return if (launch(context, url)) ActionResult(true, "Searching Google for $query")
        else ActionResult(false, "No browser available to search.")
    }

    fun youtubeSearch(context: Context, query: String): ActionResult {
        val url = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
        return if (launch(context, url)) ActionResult(true, "Searching YouTube for $query")
        else ActionResult(false, "Could not open YouTube search.")
    }

    fun openWebsite(context: Context, raw: String): ActionResult {
        var url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (!url.contains(".")) return ActionResult(false, "That does not look like a website address.")
            url = "https://$url"
        }
        return if (launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) ActionResult(true, "Opening $url")
        else ActionResult(false, "Could not open that website.")
    }

    fun flashlight(context: Context, on: Boolean): ActionResult {
        return try {
            val cm = context.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull { camId ->
                runCatching { cm.getCameraCharacteristics(camId).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }.getOrDefault(false)
            } ?: return ActionResult(false, "This phone reports no flashlight.")
            cm.setTorchMode(id, on)
            ActionResult(true, if (on) "Flashlight on" else "Flashlight off")
        } catch (e: Exception) {
            ActionResult(false, "Flashlight is not available right now.")
        }
    }

    fun volume(context: Context, direction: String): ActionResult {
        val am = context.getSystemService(AudioManager::class.java)
        when (direction) {
            "up" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "unmute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            "max" -> am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
        }
        return ActionResult(true, "Volume $direction")
    }

    fun openSettings(context: Context): ActionResult =
        ActionResult(launch(context, Intent(Settings.ACTION_SETTINGS)), "Opening settings")

    fun openSoundSettings(context: Context): ActionResult =
        ActionResult(launch(context, Intent(Settings.ACTION_SOUND_SETTINGS)), "Opening sound settings")

    fun openBrightnessSettings(context: Context): ActionResult =
        ActionResult(launch(context, Intent(Settings.ACTION_DISPLAY_SETTINGS)), "Opening display settings to change brightness")

    fun setBrightness(context: Context, percent: Int): ActionResult {
        return if (Settings.System.canWrite(context)) {
            val value = (percent.coerceIn(1, 100) * 255) / 100
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            ActionResult(true, "Brightness set to $percent percent")
        } else ActionResult(false, "ROUT needs the Modify system settings permission to change brightness. Opening that settings page.")
    }

    fun openWriteSettingsPage(context: Context) {
        launch(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:com.rout.aiassistant")))
    }

    fun dateTime(): ActionResult {
        val now = Date()
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now)
        return ActionResult(true, "It's $time, $date")
    }

    fun battery(context: Context): ActionResult {
        val i = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ActionResult(false, "Battery status unavailable")
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return ActionResult(false, "Battery status unavailable")
        val pct = (level * 100) / scale
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return ActionResult(true, "Battery is at $pct percent" + if (charging) ", charging" else "")
    }

    fun network(context: Context): ActionResult {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?: return ActionResult(true, "No internet connection right now")
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "a network"
        }
        return ActionResult(true, "You are online on $kind")
    }

    fun setAlarm(context: Context, hour: Int, minute: Int): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "ROUT alarm")
        return if (launch(context, intent)) ActionResult(true, "Opening the clock app to set an alarm for $hour:${"%02d".format(minute)}")
        else ActionResult(false, "No clock app found to set the alarm.")
    }

    fun setTimer(context: Context, seconds: Int): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "ROUT timer")
        return if (launch(context, intent)) ActionResult(true, "Opening the clock app to start a ${seconds / 60} minute timer")
        else ActionResult(false, "No clock app found to start a timer.")
    }

    fun openCalendar(context: Context): ActionResult {
        val uri = Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis())
        return ActionResult(launch(context, Intent(Intent.ACTION_VIEW).setData(uri)), "Opening calendar")
    }

    fun navigate(context: Context, destination: String): ActionResult {
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        return if (launch(context, intent)) ActionResult(true, "Starting navigation to $destination")
        else ActionResult(false, "Google Maps is not available for navigation.")
    }

    fun findContact(context: Context, name: String): Pair<String, String>? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = resolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) return it.getString(0) to it.getString(1)
        }
        return null
    }

    /**
     * Place a call. With CALL_PHONE granted this is a direct call (ACTION_CALL);
     * without it we open the dialer pre-filled (honest fallback, spoken to the user).
     */
    fun placeCall(context: Context, number: String): String {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (granted) {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch(context, i)) "Call lagai jaa rahi hai $number ko."
            else "Call nahi lag payi - dialer khol raha hoon."
        } else {
            val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launch(context, i)) "Direct call ki permission nahi hai - dialer mein number daal diya, call button dabao."
            else "Dialer nahi khul paya."
        }
    }

    fun dialIntent(context: Context, number: String): Boolean =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

    fun smsIntent(context: Context, number: String, body: String): Boolean =
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", body))

    fun scheduleReminder(context: Context, taskId: Long, title: String, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("title", title)
            .putExtra("taskId", taskId)
        val pi = PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Inexact alarm: delivered reliably but not at an exact wall-clock second.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }
}
