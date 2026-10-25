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
import android.provider.CalendarContract
import android.provider.Settings
import android.app.NotificationManager
import android.content.ContentValues
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


    // --- WhatsApp draft send: opens the chat with the message prefilled; the user taps send. ---
    fun whatsappMessage(context: Context, number: String, body: String): ActionResult {
        val digits = number.filter { it.isDigit() }
        val url = "https://wa.me/$digits?text=" + Uri.encode(body)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp")
        if (launch(context, intent)) return ActionResult(true, "WhatsApp khul gaya - message ready hai, bas send dabao.")
        // WhatsApp not installed - fall back to the generic handler
        val generic = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return if (launch(context, generic)) ActionResult(true, "Chat khul gayi - message ready hai, bas send dabao.")
        else ActionResult(false, "WhatsApp is phone par nahi mila.")
    }


    /** WhatsApp voice/video call via the contact's WhatsApp data row; falls back to opening the chat. */
    fun whatsappCall(context: Context, contactId: Long, number: String, video: Boolean): ActionResult {
        val mime = if (video) "vnd.android.cursor.item/vnd.com.whatsapp.video.call" else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                arrayOf(contactId.toString(), mime), null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val uri = android.content.ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, c.getLong(0))
                    val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
                    if (launch(context, intent)) return ActionResult(true, if (video) "WhatsApp video call lag raha hai." else "WhatsApp call lag raha hai.")
                }
            }
        } catch (_: Exception) {}
        // Fallback: open the chat so the user taps the call button
        val digits = number.filter { it.isDigit() }
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).setPackage("com.whatsapp")
        return if (launch(context, fallback)) ActionResult(true, "WhatsApp chat khul gayi - upar call ka button dabao.")
        else ActionResult(false, "WhatsApp is phone par nahi mila.")
    }

    // --- Do Not Disturb (needs Notification Policy Access - a one-time special grant) ---
    private fun hasPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun openPolicyAccessSettings(context: Context) {
        launch(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    fun setDnd(context: Context, on: Boolean): ActionResult {
        if (!hasPolicyAccess(context)) {
            openPolicyAccessSettings(context)
            return ActionResult(false, "DND ke liye ek one-time permission chahiye. Settings kholi hai - list mein ROUT ko allow karo, fir dobara bolo.")
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
        return ActionResult(true, if (on) "Do Not Disturb on kar diya. Ab koi notification disturb nahi karega." else "Do Not Disturb off kar diya.")
    }

    fun setRingerMode(context: Context, mode: String): ActionResult {
        if (!hasPolicyAccess(context)) {
            openPolicyAccessSettings(context)
            return ActionResult(false, "Silent/vibrate mode ke liye ek one-time permission chahiye. Settings kholi hai - ROUT ko allow karo, fir dobara bolo.")
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            when (mode) {
                "silent" -> { am.ringerMode = AudioManager.RINGER_MODE_SILENT; ActionResult(true, "Phone silent mode mein daal diya.") }
                "vibrate" -> { am.ringerMode = AudioManager.RINGER_MODE_VIBRATE; ActionResult(true, "Phone vibrate mode mein daal diya.") }
                else -> { am.ringerMode = AudioManager.RINGER_MODE_NORMAL; ActionResult(true, "Phone normal ringer mode mein aa gaya.") }
            }
        } catch (e: SecurityException) {
            openPolicyAccessSettings(context)
            ActionResult(false, "Ye change karne ke liye ROUT ko Do Not Disturb access chahiye - settings kholi hai, allow karke dobara bolo.")
        }
    }

    // --- Calendar event: silent insert when permission granted, prefilled Calendar screen otherwise ---
    fun insertCalendarEvent(context: Context, title: String, startMillis: Long, endMillis: Long): ActionResult {
        val hasWrite = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val hasRead = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (hasWrite && hasRead) {
            try {
                var calId: Long = -1
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    CalendarContract.Calendars.VISIBLE + " = 1", null,
                    CalendarContract.Calendars.IS_PRIMARY + " DESC"
                )?.use { c -> if (c.moveToFirst()) calId = c.getLong(0) }
                if (calId > 0) {
                    val v = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calId)
                        put(CalendarContract.Events.TITLE, title)
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, endMillis)
                        put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    }
                    context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
                    val when_ = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(startMillis))
                    return ActionResult(true, "Event save kar diya: $title - $when_.")
                }
            } catch (_: Exception) { /* fall through to prefilled intent */ }
        }
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DTSTART, startMillis)
            .putExtra(CalendarContract.Events.DTEND, endMillis)
        return if (launch(context, intent)) ActionResult(true, "Calendar khul raha hai - event ready hai, bas Save dabao.")
        else ActionResult(false, "Calendar app nahi mila is phone par.")
    }


    // --- One-tap system panels (Android does not allow any app to toggle these silently) ---
    fun openSystemPanel(context: Context, kind: String): ActionResult {
        val (action, label) = when (kind) {
            "wifi" -> Pair(Settings.Panel.ACTION_WIFI, "WiFi")
            "bluetooth" -> Pair(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            "internet" -> Pair(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, "Internet")
            "batterysaver" -> Pair(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery saver")
            "display" -> Pair(Settings.ACTION_DISPLAY_SETTINGS, "Display")
            "wireless" -> Pair(Settings.ACTION_WIRELESS_SETTINGS, "Hotspot")
            "battery" -> Pair(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery")
            "storage" -> Pair(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Storage")
            "apps" -> Pair(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "Apps")
            "sound" -> Pair(Settings.ACTION_SOUND_SETTINGS, "Sound")
            "privacy" -> Pair(Settings.ACTION_PRIVACY_SETTINGS, "Privacy")
            "location" -> Pair(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location")
            "accessibility" -> Pair(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility")
            "security" -> Pair(Settings.ACTION_SECURITY_SETTINGS, "Security")
            "airplane" -> Pair(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode")
            else -> return ActionResult(false, "Ye settings panel nahi mila.")
        }
        return if (launch(context, Intent(action))) ActionResult(true, "$label settings khul gayi - ek tap mein badal lo.")
        else ActionResult(false, "$label settings nahi khul payi.")
    }

    fun spotifyPlay(context: Context, query: String): ActionResult {
        val uri = Uri.parse("spotify:search:" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.spotify.music")
        if (launch(context, intent)) return ActionResult(true, "Spotify mein '$query' dhoondh raha hoon - play dabao.")
        return ActionResult(false, "Spotify is phone par installed nahi hai.")
    }

    fun openCamera(context: Context, selfie: Boolean): ActionResult {
        val intent = Intent("android.media.action.IMAGE_CAPTURE")
        if (selfie) {
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        }
        return if (launch(context, intent)) ActionResult(true, if (selfie) "Selfie camera khul gaya - smile!" else "Camera khul gaya - photo lo.")
        else ActionResult(false, "Camera nahi khul paya.")
    }



    // --- Daily spoken briefing: exact alarm -> notification -> tap runs the command in-app ---
    fun scheduleBriefing(context: Context, command: String, hour: Int, minute: Int): ActionResult {
        val am = context.getSystemService(AlarmManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            launch(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return ActionResult(false, "Roz exact waqt pe briefing ke liye one-time permission chahiye. Settings kholi hai - ROUT ko allow karo, fir dobara bolo.")
        }
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("title", "ROUT briefing - tap karke suno: $command")
            .putExtra("run_command", command)
            .putExtra("hour", hour)
            .putExtra("minute", minute)
        val pi = PendingIntent.getBroadcast(context, 987654321, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        // ReminderReceiver re-arms this daily when run_command is present.
        return ActionResult(true, "Briefing set: roz ${"%d:%02d".format(hour, minute)} pe '$command'. Notification aayegi - tap karte hi ROUT bol dega. ('briefing band karo' se rukega.)")
    }


    fun cancelBriefing(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, 987654321, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }

    fun exactAlarmStatus(context: Context): ActionResult {
        if (android.os.Build.VERSION.SDK_INT < 31) return ActionResult(true, "Is Android version par alarms hamesha exact hain.")
        val am = context.getSystemService(AlarmManager::class.java)
        if (am.canScheduleExactAlarms()) return ActionResult(true, "Exact alarms already on hain.")
        launch(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        return ActionResult(false, "Exact alarms ke liye one-time permission chahiye. Settings kholi hai - ROUT ko allow karo. Uske baad har reminder bilkul sahi waqt pe aayega.")
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

    fun setTimer(context: Context, seconds: Int, label: String = "ROUT timer"): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (launch(context, intent)) ActionResult(true, "Opening the clock app to start '$label' (${seconds / 60} minute)")
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

    fun findContact(context: Context, name: String): Pair<String, String>? =
        findContactWithId(context, name)?.let { it.first to it.second }

    /** (display name, number, contact id) for the first fuzzy name match. */
    fun findContactWithId(context: Context, name: String): Triple<String, String, Long>? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = resolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) return Triple(it.getString(0), it.getString(1), it.getLong(2))
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
        // Exact when the user granted SCHEDULE_EXACT_ALARM (Android 12+); inexact otherwise.
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    /** Open an email draft (mailto) prefilled; user reviews and sends in their mail app. */
    fun emailIntent(context: Context, to: String, body: String): Boolean {
        val uri = Uri.parse("mailto:" + Uri.encode(to)).buildUpon()
            .appendQueryParameter("body", body).build()
        return launch(context, Intent(Intent.ACTION_SENDTO, uri))
    }

    /** Open Google Maps directions (with live ETA, no auto-navigation) to a place. */
    fun directionsEta(context: Context, destination: String): ActionResult {
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination) + "&travelmode=driving")
        return if (launch(context, Intent(Intent.ACTION_VIEW, uri)))
            ActionResult(true, "Maps khol raha hoon - $destination ke routes aur live ETA screen par hain.")
        else ActionResult(false, "Google Maps nahi khul paya.")
    }

    /** Recent missed calls (needs READ_CALL_LOG): "name/number (time)" lines. */
    fun recentMissedCalls(context: Context, limit: Int = 5): List<String> {
        val out = mutableListOf<String>()
        try {
            val cur = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.CACHED_NAME, android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE),
                android.provider.CallLog.Calls.TYPE + " = ?",
                arrayOf(android.provider.CallLog.Calls.MISSED_TYPE.toString()),
                android.provider.CallLog.Calls.DATE + " DESC"
            ) ?: return out
            cur.use {
                val nameI = it.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                val numI = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                val dateI = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                while (it.moveToNext() && out.size < limit) {
                    val name = it.getString(nameI)?.takeIf { n -> n.isNotBlank() } ?: it.getString(numI) ?: "Unknown"
                    val when_ = java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.getLong(dateI)))
                    out.add("$name ($when_)")
                }
            }
        } catch (_: Exception) {}
        return out
    }

    /** Recent inbox SMS (needs READ_SMS): "sender: preview" lines. */
    fun recentSms(context: Context, limit: Int = 5): List<String> {
        val out = mutableListOf<String>()
        try {
            val cur = context.contentResolver.query(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(android.provider.Telephony.Sms.ADDRESS, android.provider.Telephony.Sms.BODY),
                null, null,
                android.provider.Telephony.Sms.DATE + " DESC"
            ) ?: return out
            cur.use {
                val addrI = it.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                val bodyI = it.getColumnIndex(android.provider.Telephony.Sms.BODY)
                while (it.moveToNext() && out.size < limit) {
                    val from = it.getString(addrI) ?: "Unknown"
                    val body = (it.getString(bodyI) ?: "").replace(Regex("\\s+"), " ").trim().take(80)
                    if (body.isNotEmpty()) out.add("$from: $body")
                }
            }
        } catch (_: Exception) {}
        return out
    }

    /** First email address of the first fuzzy name-matched contact, or null. */
    fun findContactEmail(context: Context, name: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
        val cur = resolver.query(uri,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS, ContactsContract.CommonDataKinds.Email.DISPLAY_NAME),
            null, null, null) ?: return null
        val target = name.trim().lowercase()
        cur.use {
            val addrI = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val nameI = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
            while (it.moveToNext()) {
                val dn = it.getString(nameI)?.lowercase() ?: continue
                if (dn.contains(target) || target.contains(dn)) {
                    val addr = it.getString(addrI)
                    if (!addr.isNullOrBlank()) return addr
                }
            }
        }
        return null
    }

    /** Song recognition: Google's sound search -> Shazam -> Google app (honest 1-tap path). */
    fun songSearch(context: Context): ActionResult {
        val gsa = Intent().setClassName("com.google.android.googlequicksearchbox",
            "com.google.android.apps.gsa.staticplugins.soundsearch.SoundSearchActivity")
        if (launch(context, gsa)) return ActionResult(true, "Google ka song search khul gaya - gaana suna do.")
        if (openApp(context, "shazam").ok) return ActionResult(true, "Shazam khol diya - gaana suna do.")
        if (openApp(context, "google").ok) return ActionResult(true, "Google app kholi hai - mic dabakar 'search a song' chuno. (On-device gaana pehchanna sirf Google/Shazam kar sakte hain.)")
        return ActionResult(false, "Na Google app na Shazam mila. Play Store se Shazam install karo, fir dobara bolo.")
    }

    /** Last known location as (lat, lon) or null. */
    fun lastLocation(context: Context): Pair<Double, Double>? {
        return try {
            val lm = context.getSystemService(android.location.LocationManager::class.java)
            lm.getProviders(true).firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                ?.let { it.latitude to it.longitude }
        } catch (_: Exception) { null }
    }
}
