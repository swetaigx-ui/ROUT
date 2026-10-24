package com.rout.aiassistant.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.rout.aiassistant.ai.AiMode
import com.rout.aiassistant.ai.AiResult
import com.rout.aiassistant.ai.ChatTurn
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import com.rout.aiassistant.data.TaskItem
import com.rout.aiassistant.data.Note
import com.rout.aiassistant.data.MemoryFact
import kotlinx.coroutines.flow.first
import java.util.Calendar

data class ConfirmRequest(
    val id: String,
    val description: String,
    val action: suspend () -> String
)

class CommandRouter(
    private val context: Context,
    private val db: RoutDatabase,
    private val prefs: Prefs,
    private val providerManager: () -> ProviderManager,
    private val history: () -> List<ChatTurn>,
    private val requestConfirmation: (ConfirmRequest) -> Unit
) {
    private fun has(perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    /** Entry point. Supports multi-step "A aur B" plans with per-step results. */
    suspend fun handle(rawInput: String): String {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return "Kuch sunai nahi diya. Please try again."
        val steps = splitSteps(raw)
        if (steps.size > 1) {
            val results = steps.map { step -> singleStep(step) }
            val ok = results.count { it.first }
            val sb = StringBuilder()
            results.forEachIndexed { i, r -> sb.append("${i + 1}. ${r.second}") }
            sb.append("\n$ok of ${results.size} steps completed.")
            return sb.toString().trim()
        }
        return singleStep(raw).second
    }

    private fun splitSteps(raw: String): List<String> {
        val parts = raw.split(Regex("\\s+(?:aur|and then|phir|fir)\\s+|\\s+and then\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size > 1) parts else listOf(raw)
    }

    private suspend fun singleStep(raw: String): Pair<Boolean, String> {
        val t = raw.lowercase().trim()

        // --- App launching ---
        val openMatch = Regex("^(?:open|launch|start|kholo|khol do|chalao)\\s+(.+)$").find(t)
        if (openMatch != null) {
            val target = openMatch.groupValues[1].trim()
            if (target.contains("search") || target.startsWith("youtube par")) {
                // fall through to search handlers
            } else {
                val r = DeviceActions.openApp(context, target)
                return r.ok to r.message
            }
        }

        // --- YouTube search ---
        Regex("(?:youtube\\s*(?:par|pe|mein|on)?\\s*)(?:search\\s*karo|search|dhundho)[:\\s]+(.+)$").find(t)?.let {
            val r = DeviceActions.youtubeSearch(context, it.groupValues[1].trim()); return r.ok to r.message
        }
        Regex("^(?:youtube|yt)\\s+(.+)$").find(t)?.let {
            val r = DeviceActions.youtubeSearch(context, it.groupValues[1].trim()); return r.ok to r.message
        }

        // --- Web search ---
        Regex("(?:google\\s*(?:par|pe)?\\s*)?(?:search\\s*karo|search|google\\s*karo|dhundho)[:\\s]+(.+)$").find(t)?.let {
            val r = DeviceActions.webSearch(context, it.groupValues[1].trim()); return r.ok to r.message
        }

        // --- Open website ---
        Regex("^(?:website|site|open website)\\s+(.+)$").find(t)?.let {
            val r = DeviceActions.openWebsite(context, it.groupValues[1]); return r.ok to r.message
        }

        // --- Flashlight ---
        if (Regex("(torch|flashlight|flash|batti)").containsMatchIn(t)) {
            val on = !(t.contains("off") || t.contains("band") || t.contains("bujha"))
            val r = DeviceActions.flashlight(context, on); return r.ok to r.message
        }

        // --- Volume ---
        when {
            Regex("volume (up|badha|high|tez)").containsMatchIn(t) -> { val r = DeviceActions.volume(context, "up"); return r.ok to r.message }
            Regex("volume (down|kam|dheere)").containsMatchIn(t) -> { val r = DeviceActions.volume(context, "down"); return r.ok to r.message }
            Regex("volume (max|full)").containsMatchIn(t) -> { val r = DeviceActions.volume(context, "max"); return r.ok to r.message }
            t.contains("unmute") -> { val r = DeviceActions.volume(context, "unmute"); return r.ok to r.message }
            t.contains("mute") -> { val r = DeviceActions.volume(context, "mute"); return r.ok to r.message }
        }

        // --- Media ---
        when {
            Regex("(play|pause)").containsMatchIn(t) && t.contains("media|music|song|gaana".toRegex()) -> {
                val r = MediaControl.playPause(context); return r.ok to r.message
            }
            Regex("next (song|track|gaana)").containsMatchIn(t) -> { val r = MediaControl.next(context); return r.ok to r.message }
            Regex("(previous|pichla) (song|track|gaana)").containsMatchIn(t) -> { val r = MediaControl.previous(context); return r.ok to r.message }
        }

        // --- Brightness ---
        Regex("brightness\\s*(?:set\\s*)?(?:to\\s*)?(\\d{1,3})").find(t)?.let {
            val r = DeviceActions.setBrightness(context, it.groupValues[1].toInt())
            if (!r.ok) DeviceActions.openWriteSettingsPage(context)
            return r.ok to r.message
        }
        if (t.contains("brightness")) { val r = DeviceActions.openBrightnessSettings(context); return r.ok to r.message }

        // --- Time / date / battery / network ---
        if (Regex("(time|samay|kitne baje|kya time)").containsMatchIn(t)) {
            val r = DeviceActions.dateTime(); return r.ok to r.message
        }
        if (Regex("(date|tarikh|tareekh|kaun sa din)").containsMatchIn(t)) {
            val r = DeviceActions.dateTime(); return r.ok to r.message
        }
        if (t.contains("battery")) { val r = DeviceActions.battery(context); return r.ok to r.message }
        if (Regex("(network|internet|wifi|wi-fi)").containsMatchIn(t) && Regex("(status|check|batao|hai)").containsMatchIn(t)) {
            val r = DeviceActions.network(context); return r.ok to r.message
        }

        // --- Alarm / timer ---
        Regex("alarm\\s*(?:set\\s*karo|lagao)?\\s*(?:for\\s*|ke liye\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?").find(t)?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (m.groupValues[3] == "pm" && hour < 12) hour += 12
            if (m.groupValues[3] == "am" && hour == 12) hour = 0
            val r = DeviceActions.setAlarm(context, hour, minute); return r.ok to r.message
        }
        Regex("timer\\s*(?:for\\s*)?(\\d+)\\s*(minute|min|second)").find(t)?.let { m ->
            val secs = if (m.groupValues[2].startsWith("min")) m.groupValues[1].toInt() * 60 else m.groupValues[1].toInt()
            val r = DeviceActions.setTimer(context, secs); return r.ok to r.message
        }

        // --- Calendar / navigation ---
        if (t.contains("calendar")) { val r = DeviceActions.openCalendar(context); return r.ok to r.message }
        Regex("(?:navigate|directions?|rasta)\\s*(?:to|ka|tak)?\\s*(.+)$").find(t)?.let {
            val dest = it.groupValues[1].trim()
            if (dest.isNotEmpty()) { val r = DeviceActions.navigate(context, dest); return r.ok to r.message }
        }

        // --- Calls and messages: confirmation required ---
        Regex("(?:call|dial|phone)\\s+(.+)$").find(t)?.let { m ->
            val name = m.groupValues[1].trim()
            if (!has(Manifest.permission.READ_CONTACTS)) {
                return false to "I need contacts permission to find $name. Grant it from the prompt and try again."
            }
            val contact = DeviceActions.findContact(context, name)
                ?: return false to "I could not find a contact matching $name."
            requestConfirmation(ConfirmRequest("call", "Call ${contact.first} (${contact.second})?") {
                if (DeviceActions.dialIntent(context, contact.second)) "Opening the dialer for ${contact.first}. Tap call to connect."
                else "Could not open the dialer."
            })
            return true to "Confirm the call on screen."
        }
        Regex("(?:message|sms|text)\\s+(?:to\\s+)?(\\w+)\\s*(?:saying|ki|that)?\\s*(.*)$").find(t)?.let { m ->
            val name = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            if (!has(Manifest.permission.READ_CONTACTS)) {
                return false to "I need contacts permission to find $name. Grant it from the prompt and try again."
            }
            val contact = DeviceActions.findContact(context, name)
                ?: return false to "I could not find a contact matching $name."
            requestConfirmation(ConfirmRequest("sms", "Send SMS to ${contact.first}: \"$body\"?") {
                if (DeviceActions.smsIntent(context, contact.second, body)) "Opening your messaging app with the draft. Review and send."
                else "Could not open the messaging app."
            })
            return true to "Confirm the message on screen."
        }

        // --- Notes ---
        Regex("(?:note\\s*banao|make\\s*a\\s*note|note\\s*karo|likh\\s*lo)[:\\s]+(.+)$").find(t)?.let {
            db.notes().insert(Note(title = it.groupValues[1].take(40), body = it.groupValues[1]))
            return true to "Note saved."
        }
        if (Regex("(notes\\s*dikhao|show\\s*notes|my\\s*notes|mere\\s*notes)").containsMatchIn(t)) {
            val notes = db.notes().latest()
            return if (notes.isEmpty()) true to "You have no saved notes yet."
            else true to "Your latest notes: " + notes.take(5).joinToString("; ") { it.body.take(50) }
        }

        // --- Tasks and reminders ---
        Regex("(?:task\\s*(?:add\\s*karo|banao)|add\\s*(?:a\\s*)?task)[:\\s]+(.+)$").find(t)?.let {
            db.tasks().insert(TaskItem(title = it.groupValues[1]))
            return true to "Task added."
        }
        Regex("(?:remind\\s*me|yaad\\s*dilao|yaad\\s*dilana)(?:\\s*to)?\\s+(.+?)\\s+(?:at|ko)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$").find(t)?.let { m ->
            val title = m.groupValues[1]
            var hour = m.groupValues[2].toInt()
            val minute = m.groupValues[3].ifEmpty { "0" }.toInt()
            if (m.groupValues[4] == "pm" && hour < 12) hour += 12
            if (m.groupValues[4] == "am" && hour == 12) hour = 0
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val id = db.tasks().insert(TaskItem(title = title, dueAt = cal.timeInMillis))
            DeviceActions.scheduleReminder(context, id, title, cal.timeInMillis)
            return true to "Reminder saved for ${"%d:%02d".format(hour, minute)}. It fires as an inexact alarm, so it may arrive within a few minutes."
        }
        Regex("(?:task\\s*(?:complete|done|ho\\s*gaya|pura)|complete\\s*task|mark\\s*done)[:\\s]+(.+)$").find(t)?.let { m ->
            val q = m.groupValues[1].trim().lowercase()
            val match = db.tasks().tasks().first().firstOrNull { !it.done && it.title.lowercase().contains(q) }
            return if (match != null) {
                db.tasks().setDone(match.id, true)
                true to "Marked done: ${match.title}"
            } else true to "I could not find a pending task matching \"$q\"."
        }
        Regex("(?:note\\s*(?:delete|hatao|hata\\s*do)|delete\\s*note)[:\\s]+(.+)$").find(t)?.let { m ->
            val q = m.groupValues[1].trim().lowercase()
            val match = db.notes().latest().firstOrNull { it.body.lowercase().contains(q) || it.title.lowercase().contains(q) }
            return if (match != null) {
                db.notes().delete(match.id)
                true to "Note deleted: ${match.title}"
            } else true to "I could not find a note matching \"$q\"."
        }
        Regex("(?:history\\s*(?:search|dikhao)|search\\s*history|pichla\\s*chat)[:\\s]+(.+)$").find(t)?.let { m ->
            val hits = db.conversations().searchUserMessages(m.groupValues[1].trim())
            return if (hits.isEmpty()) true to "Nothing found in history for \"${m.groupValues[1].trim()}\"."
            else true to "Found in your history: " + hits.take(3).joinToString("; ") { it.take(60) }
        }
        if (Regex("(tasks?\\s*dikhao|show\\s*tasks|my\\s*tasks)").containsMatchIn(t)) {
            val tasks = db.tasks().tasks().first().filter { !it.done }
            return if (tasks.isEmpty()) true to "No pending tasks."
            else true to "Pending tasks: " + tasks.take(6).joinToString("; ") { it.title }
        }

        // --- Memory ---
        Regex("(?:yaad\\s*rakho|remember)[:\\s]+(.+)$").find(t)?.let {
            db.memory().insert(MemoryFact(text = it.groupValues[1]))
            return true to "Saved to memory. You can review or delete it in the Memory screen."
        }
        if (Regex("(kya\\s*yaad\\s*hai|what\\s*do\\s*you\\s*remember)").containsMatchIn(t)) {
            val facts = db.memory().all()
            return if (facts.isEmpty()) true to "I have nothing saved in memory yet."
            else true to "I remember: " + facts.take(6).joinToString("; ") { it.text }
        }

        // --- Weather (real data via Open-Meteo, no key needed) ---
        if (Regex("(weather|mausam|temperature)").containsMatchIn(t)) {
            return weather()
        }

        // --- Settings shortcuts ---
        if (t.contains("settings")) { val r = DeviceActions.openSettings(context); return r.ok to r.message }
        if (t.contains("sound settings")) { val r = DeviceActions.openSoundSettings(context); return r.ok to r.message }

        // --- AI fallback ---
        return aiFallback(raw)
    }

    private suspend fun weather(): Pair<Boolean, String> {
        var lat: Double? = null
        var lon: Double? = null
        var label = "your location"
        if (has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            try {
                val lm = context.getSystemService(LocationManager::class.java)
                val loc = lm.getProviders(true).firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                if (loc != null) { lat = loc.latitude; lon = loc.longitude }
            } catch (_: SecurityException) {}
        }
        if (lat == null) {
            val city = prefs.weatherCity.first()
            if (city.isBlank()) {
                return false to "For weather I need location permission or your city. Set your city in Settings, then ask again."
            }
            val geo = WeatherService.geocode(city) ?: return false to "I could not look up $city right now."
            lat = geo.first; lon = geo.second; label = geo.third
        }
        val w = WeatherService.current(lat!!, lon!!, label)
            ?: return false to "The weather service is unreachable right now."
        return true to "In ${w.place}: ${w.tempC}°C, ${w.summary}, humidity ${w.humidity} percent, wind ${w.windKmh} km/h."
    }

    private suspend fun aiFallback(raw: String): Pair<Boolean, String> {
        val pm = providerManager()
        return when (pm.mode()) {
            AiMode.SETUP_REQUIRED -> false to
                "That needs ROUT AI, which is not configured yet. Local commands (apps, torch, notes, tasks, weather) still work. " +
                "To enable AI answers, set the ROUT backend URL in Settings and connect this device."
            AiMode.BACKEND_NEEDS_ENROLLMENT -> false to
                "The ROUT backend is set but this phone is not connected yet. Open Settings, tap Connect this device, and approve the link once."
            else -> when (val r = pm.provider()!!.chat(history(), raw)) {
                is AiResult.Reply -> true to r.text
                is AiResult.SetupRequired -> false to r.message
                is AiResult.Failure -> false to "ROUT AI could not answer: ${r.reason}"
            }
        }
    }
}
