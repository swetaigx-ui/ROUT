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
        "phone" to listOf("com.google.android.dialer", "com.android.dialer")
    )

    fun openApp(context: Context, nameRaw: String): ActionResult {
        val name = nameRaw.trim().lowercase()
        val pm = context.packageManager
        appAliases[name]?.forEach { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { if (launch(context, it)) return ActionResult(true, "Opening $nameRaw") }
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
                    return ActionResult(true, "Opening $label")
                }
            }
        }
        return ActionResult(false, "I could not find an app called $nameRaw on this phone.")
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
