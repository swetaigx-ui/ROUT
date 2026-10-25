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
import com.rout.aiassistant.data.Routine
import com.rout.aiassistant.data.MemoryFact
import kotlinx.coroutines.flow.first
import java.util.Calendar

internal val OPEN_VERBS = listOf(
    "khol do na", "khol dijiye", "open karo na", "open kar do", "kholo", "khol do", "kholna",
    "kholiye", "open karo", "chalao", "chalu karo", "shuru karo",
    "\u0916\u094b\u0932 \u0926\u0940\u091c\u093f\u090f", "\u0916\u094b\u0932 \u0926\u094b", "\u0916\u094b\u0932\u094b", "\u0916\u094b\u0932\u093f\u090f",
    "\u0913\u092a\u0928 \u0915\u0930\u094b \u0928\u093e", "\u0913\u092a\u0928 \u0915\u0930 \u0926\u094b", "\u0913\u092a\u0928 \u0915\u0940\u091c\u093f\u090f", "\u0913\u092a\u0928 \u0915\u0930\u094b",
    "\u091a\u093e\u0932\u0942 \u0915\u0930 \u0926\u094b", "\u091a\u093e\u0932\u0942 \u0915\u0930\u094b",
    "\u0936\u0941\u0930\u0942 \u0915\u0930\u094b"
).sortedByDescending { it.length }

private val POLITE_PREFIX = Regex(
    "^(?:(?:hello|hii?|hey|ok(?:ay)?|please|pl[sz]|can you|could you|would you|kya tum|kya aap|" +
    "\u0939\u0947\u0932\u094b|\u0939\u0948\u0932\u094b|\u0939\u093e\u092f|\u0939\u0947|\u0913\u0915\u0947|" +
    "\u092a\u094d\u0932\u0940\u091c\u093c|\u0915\u094d\u092f\u093e \u0924\u0941\u092e|\u0915\u094d\u092f\u093e \u0906\u092a|\u0915\u094d\u092f\u093e)\\s+)+"
)
private val POLITE_SUFFIX = Regex(
    "(?:\\s+(?:na|ji|bhai|yaar|please|pl[sz]|ok(?:ay)?|\u0928\u093e|\u091c\u0940|\u092d\u093e\u0908|\u092f\u093e\u0930|\u092a\u094d\u0932\u0940\u091c\u093c|\u0913\u0915\u0947))+$"
)

/** Pure: extract the app name from any open-intent phrasing (Hindi, Devanagari, English, question forms). */
internal fun extractOpenTarget(text: String): String? {
    var t = POLITE_PREFIX.replace(text.trim().lowercase(), "")
    t = POLITE_SUFFIX.replace(t, "").trim()
    if (t.isEmpty()) return null
    for (v in OPEN_VERBS) {
        if (t.startsWith("$v ")) {
            val target = t.removePrefix("$v ").trim()
            if (target.isNotEmpty()) return target
        }
    }
    for (v in OPEN_VERBS) {
        if (t.endsWith(" $v")) {
            val target = t.removeSuffix(" $v").trim()
            if (target.isNotEmpty()) return target
        }
    }
    return null
}


/** Pure: extract (contact name, message) from a WhatsApp-send phrasing, any language mix. */
internal fun parseWhatsapp(text: String): Pair<String, String>? {
    val t = text.trim()
    val wa = "(?:whats?app|\u0935\u094d\u0939\u093e\u091f\u094d\u0938\u090f\u092a|\u0935\u094d\u0939\u093e\u091f\u094d\u0938\u0910\u092a)"
    val a = Regex("^(.+?)\\s+(?:ko|\u0915\u094b)\\s+$wa(?:\\s+(?:par|pe|par message|\u092a\u0930|\u092a\u0947))?\\s+(?:karo|kar do|message karo|bhejo|bhej do|likho|likh do|message|\u0915\u0930\u094b|\u0915\u0930 \u0926\u094b|\u092d\u0947\u091c\u094b|\u0932\u093f\u0916\u094b)\\s*:?\\s*(?:ki|that|saying|ki bolo|\u0915\u093f)?\\s*(.+)$").find(t)
    if (a != null) return a.groupValues[1].trim() to a.groupValues[2].trim()
    val b = Regex("^$wa\\s+(?:message\\s+|msg\\s+)?(.+?)\\s+(?:ko\\s+)?(?:ki|that|saying)\\s+(.+)$").find(t)
    if (b != null) return b.groupValues[1].trim() to b.groupValues[2].trim()
    return null
}

/** Pure: extract (title, startMillis, endMillis) from a calendar phrasing, or null. */
internal fun parseCalendar(text: String, now: java.util.Calendar = java.util.Calendar.getInstance()): Triple<String, Long, Long>? {
    val t = text.trim().lowercase()
    val dayOffset = when {
        Regex("\\b(aaj|today)\\b").containsMatchIn(t) -> 0
        Regex("\\b(kal|tomorrow)\\b").containsMatchIn(t) -> 1
        Regex("\\b(parso)\\b").containsMatchIn(t) -> 2
        else -> return null
    }
    val timeM = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)").find(t) ?: return null
    var hour = timeM.groupValues[1].toInt()
    val minute = timeM.groupValues[2].ifEmpty { "0" }.toInt()
    if (hour > 24 || minute > 59) return null
    val marker = timeM.groupValues[3]
    val pm = marker == "pm" || (marker == "baje" && Regex("\\b(shaam|sham|dopahar|raat|rat)\\b").containsMatchIn(t))
    val am = marker == "am" || (marker == "baje" && Regex("\\b(subah|saverey)\\b").containsMatchIn(t))
    if (pm && hour < 12) hour += 12
    if (am && hour == 12) hour = 0
    if (marker == "baje" && !pm && !am && hour < 7) hour += 12 // "4 baje" without context usually means evening
    val cue = Regex("(calendar|event|meeting|appointment|schedule|rakh do|banao|add karo|fix karo|set karo|yaad dilao)").containsMatchIn(t)
    if (!cue) return null
    var title = t.replace(timeM.value, " ")
    listOf("calendar mein", "calendar", "event", "appointment", "schedule karo", "schedule", "rakh do", "add karo", "fix karo", "set karo", "banao",
        "aaj", "today", "kal", "tomorrow", "parso", "subah", "shaam", "sham", "dopahar", "raat", "rat", "saverey", "ko ", " ka ", " ki ", " ke ", " mein ", "please", " meeting").forEach { w -> title = title.replace(w, " ") }
    title = title.replace(Regex("\\s+"), " ").trim()
    if (title.isEmpty()) title = if (t.contains("meeting")) "Meeting" else "Event"
    val start = (now.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.DAY_OF_YEAR, dayOffset)
        set(java.util.Calendar.HOUR_OF_DAY, hour); set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as java.util.Calendar).apply { add(java.util.Calendar.HOUR_OF_DAY, 1) }
    return Triple(title.replaceFirstChar { it.uppercase() }, start.timeInMillis, end.timeInMillis)
}

/** Pure: parse a daily-briefing phrasing into (command, hour, minute), or null. */
internal fun parseBriefing(text: String, now: java.util.Calendar = java.util.Calendar.getInstance()): Triple<String, Int, Int>? {
    val t = text.trim().lowercase()
    if (!Regex("\\b(roz|har roz|every day|daily)\\b").containsMatchIn(t)) return null
    val timeM = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)").find(t) ?: return null
    var hour = timeM.groupValues[1].toInt()
    val minute = timeM.groupValues[2].ifEmpty { "0" }.toInt()
    if (hour > 24 || minute > 59) return null
    val marker = timeM.groupValues[3]
    val pm = marker == "pm" || (marker == "baje" && Regex("\\b(shaam|sham|dopahar|raat|rat)\\b").containsMatchIn(t))
    val am = marker == "am" || (marker == "baje" && Regex("\\b(subah|saverey|morning)\\b").containsMatchIn(t))
    if (pm && hour < 12) hour += 12
    if (am && hour == 12) hour = 0
    if (marker == "baje" && !pm && !am && hour < 7) hour += 12
    var cmd = t.replace(timeM.value, " ")
    listOf("har roz", "roz", "every day", "daily", "subah", "shaam", "sham", "dopahar", "raat", "rat", "saverey",
        "mujhe", "briefing", "brief", "bata dena", "bata do", "batao", "sunao", "suna dena", "kar do", "karo", "dena", "do").forEach { w -> cmd = cmd.replace(w, " ") }
    cmd = cmd.replace(Regex("\\s+"), " ").trim()
    if (cmd.isEmpty()) return null
    return Triple(cmd, hour, minute)
}


internal object QuickAnswersEval {
    fun fmtPub(v: Double): String {
        val r = kotlin.math.round(v * 100) / 100.0
        return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
    }
}

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

        // --- App launching by trailing verb: "youtube kholo", "whatsapp khol do", "translate chalao" ---
        // Checked before every search rule so a named app opens instead of being searched.
        val openTarget = extractOpenTarget(t)
        if (openTarget != null &&
            !openTarget.contains("search") && !openTarget.startsWith("youtube par") && !openTarget.startsWith("youtube pe") &&
            !openTarget.contains("\u0938\u0930\u094d\u091a")) {
            val r = DeviceActions.openApp(context, openTarget)
            if (r.ok) return true to r.message
            // Not an installed app - try it as a website instead of failing silently (latin names only).
            if (openTarget.none { it in '\u0900'..'\u097F' }) {
                val w = DeviceActions.openWebsite(context, openTarget)
                return w.ok to if (w.ok) w.message else r.message
            }
            return false to r.message
        }

        // --- Routines: user-defined trigger phrases that run a sequence of commands ---
        if (!inRoutine) {
            if (db.routines().count() == 0) seedDefaultRoutines()
            val norm = t.replace(Regex("[!?.]+$"), "").trim()
            db.routines().enabled().firstOrNull { it.trigger == norm }?.let { r -> return runRoutine(r) }
        }
        Regex("^routine banao\\s+(.+?)\\s*:\\s*(.+)$").find(t)?.let { m ->
            val trigger = m.groupValues[1].trim()
            val steps = m.groupValues[2].split(Regex("\\s*(?:,|\\s+aur\\s+)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
            if (trigger.isEmpty() || steps.isEmpty()) return false to "Routine banane ke liye: \"routine banao <naam>: <step1>, <step2>\" bolo."
            db.routines().deleteByTrigger(trigger)
            db.routines().insert(Routine(trigger = trigger, steps = steps.joinToString("\n")))
            return true to "Routine '$trigger' ban gaya. Jab bhi '$trigger' bologe, ye ${steps.size} steps chalenge: " + steps.joinToString(", ") + "."
        }
        Regex("^routine hatao\\s+(.+)$").find(t)?.let { m ->
            db.routines().deleteByTrigger(m.groupValues[1].trim())
            return true to "Routine '${m.groupValues[1].trim()}' hata diya."
        }

        // --- Do Not Disturb / ringer modes ---
        if (Regex("^(?:dnd|do not disturb)(?:\\s+(?:on|off|on karo|off karo|lagao|hatao|kar do))?$").matches(t)) {
            val on = !(t.contains("off") || t.contains("hatao"))
            val r = DeviceActions.setDnd(context, on); return r.ok to r.message
        }
        if (Regex("^(?:silent mode|phone silent|silent karo)(?:\\s+(?:on|karo|kar do|lagao))?$").matches(t)) {
            val r = DeviceActions.setRingerMode(context, "silent"); return r.ok to r.message
        }
        if (Regex("^(?:vibrate mode|vibration mode|phone vibrate)(?:\\s+(?:on|karo|kar do|lagao))?$").matches(t)) {
            val r = DeviceActions.setRingerMode(context, "vibrate"); return r.ok to r.message
        }
        if (Regex("^(?:normal mode|ringer on|sound on|unmute phone)(?:\\s+(?:karo|kar do))?$").matches(t)) {
            val r = DeviceActions.setRingerMode(context, "normal"); return r.ok to r.message
        }

        // --- WhatsApp draft-send: contact + message, show-then-confirm, chat opens prefilled ---
        parseWhatsapp(t)?.let { (name, body) ->
            if (!has(Manifest.permission.READ_CONTACTS)) {
                return false to "Contact dhundhne ke liye contacts permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val contact = DeviceActions.findContact(context, name)
                ?: return false to "\"$name\" naam ka contact nahi mila. Naam saaf bolo ya number bata do."
            requestConfirmation(ConfirmRequest("whatsapp", "WhatsApp ${contact.first}: \"$body\"") {
                DeviceActions.whatsappMessage(context, contact.second, body).message
            })
            return true to "${contact.first} ko WhatsApp: \"$body\" - screen par confirm karo ya 'haan' bolo."
        }

        // --- WhatsApp voice/video call: contact shown, confirm, then deep-link into the call ---
        Regex("^(.+?)\\s+ko\\s+(?:whatsapp|\\u0935\\u094d\\u0939\\u093e\\u091f\\u094d\\u0938\\u090f\\u092a)\\s+(?:(video)\\s+)?call\\s+(?:karo|kar do|lagao|marao)$").find(t)?.let { m ->
            val name = m.groupValues[1].trim()
            val video = m.groupValues[2].isNotEmpty()
            if (!has(Manifest.permission.READ_CONTACTS)) {
                return false to "Contact dhundhne ke liye contacts permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val contact = DeviceActions.findContactWithId(context, name)
                ?: return false to "\"$name\" naam ka contact nahi mila. Naam saaf bolo ya number bata do."
            val kind = if (video) "video call" else "call"
            requestConfirmation(ConfirmRequest("wa-call", "WhatsApp $kind: ${contact.first} (${contact.second})?") {
                DeviceActions.whatsappCall(context, contact.third, contact.second, video).message
            })
            return true to "${contact.first} ko WhatsApp $kind karna hai - screen par confirm karo ya 'haan' bolo."
        }

        // --- Read notifications aloud (user-enabled notification access) ---
        if (Regex("^(?:(?:mere|my)\\s+)?(?:messages?|notifications?)\\s+(?:padho|padh do|batao|sunao)|what are my notifications$").matches(t)) {
            val svc = com.rout.aiassistant.service.RoutMediaListenerService.instance
            if (svc == null) {
                launchSettingsListener(context)
                return false to "Notifications padhne ke liye one-time notification access chahiye. Settings kholi hai - ROUT ko allow karo, fir dobara bolo."
            }
            val items = svc.recentSummaries(5)
            return if (items.isEmpty()) true to "Abhi koi notification nahi hai."
            else true to "Tumhari latest notifications: " + items.joinToString(". ") + "."
        }

        // --- Daily spoken briefing ---
        if (Regex("^(?:briefing|roz briefing)\\s+(?:band|off|hatao|cancel)(?:\\s+(?:karo|kar do))?$").matches(t)) {
            DeviceActions.cancelBriefing(context)
            prefs.clearBriefing()
            return true to "Daily briefing band kar di."
        }
        parseBriefing(t)?.let { (cmd, hour, minute) ->
            val r = DeviceActions.scheduleBriefing(context, cmd, hour, minute)
            if (r.ok) prefs.setBriefing(cmd, hour, minute)
            return r.ok to r.message
        }

        // --- Live translate mode ---
        if (Regex("^(?:translate|translation|anuvad)(?:\\s+mode)?\\s+(?:on|chalu|shuru)(?:\\s+(?:karo|kar do))?$").matches(t)) {
            prefs.setTranslateMode(true)
            return true to "Translate mode on. Ab jo bhi bologe, main usse doosri bhasha mein bolunga (Hindi <-> English). Band karne ke liye 'translate mode off' bolo."
        }
        if (Regex("^(?:translate|translation|anuvad)(?:\\s+mode)?\\s+(?:off|band)(?:\\s+(?:karo|kar do))?$").matches(t)) {
            prefs.setTranslateMode(false)
            return true to "Translate mode off. Normal mode mein wapas."
        }

        // --- Translate: one-shot via backend brain ---
        Regex("^(?:translate|anuvad|\\u0905\\u0928\\u0941\\u0935\\u093e\\u0926)\\s+(.+?)\\s+(?:in|to|into|mein)\\s+(english|hindi|hinglish)$").find(t)?.let { m ->
            val pm = providerManager()
            if (pm.provider() == null) return false to "Translate ke liye ROUT AI (backend) connect hona chahiye. Settings mein device connect karo."
            val lang = m.groupValues[2].replaceFirstChar { it.uppercase() }
            return when (val r = pm.provider()!!.chat(emptyList(),
                "Translate the following into $lang. Reply with ONLY the translation, no explanation.\n\n" + m.groupValues[1])) {
                is AiResult.Reply -> true to r.text
                is AiResult.SetupRequired -> false to r.message
                is AiResult.Failure -> false to friendlyFailure(r.reason)
            }
        }

        // ===== Siri parity: quick answers (math, random, units, currency, dates, moon) =====
        QuickAnswers.evalMath(t)?.let { return true to it }
        QuickAnswers.randomAnswer(t)?.let { return true to it }
        QuickAnswers.convertLocalUnits(t)?.let { return true to it }
        QuickAnswers.parseCurrency(t)?.let { (amount, from, to) ->
            val out = QuickAnswers.fx(amount, from, to)
            return if (out != null) true to "${QuickAnswersEval.fmtPub(amount)} $from = ${QuickAnswersEval.fmtPub(out)} $to"
            else false to "Currency rates abhi mil nahi rahe. Net check karke dobara try karo."
        }
        QuickAnswers.dateFacts(t)?.let { return true to it }
        QuickAnswers.moonPhaseAnswer(t)?.let { return true to it }

        // --- Meaning / spelling via backend brain ---
        QuickAnswers.parseMeaning(raw.trim())?.let { (mode, word) ->
            val pm = providerManager()
            if (pm.provider() != null) {
                val prompt = if (mode == "spell")
                    "Spell the word \"$word\" letter by letter, then give it in one line. Keep it very short."
                else
                    "Give the meaning of \"$word\" in one short simple line, with one example. If the user asked in Hindi/Hinglish, answer in Hinglish."
                when (val r = pm.provider()!!.chat(emptyList(), prompt)) {
                    is AiResult.Reply -> return true to r.text
                    else -> {} // fall through to AI fallback
                }
            }
        }

        // --- Emergency call: 112, always show-then-confirm ---
        if (QuickAnswers.isEmergencyCall(t)) {
            requestConfirmation(ConfirmRequest("call", "Emergency number 112 par call karein?") {
                DeviceActions.placeCall(context, "112")
            })
            return true to "Emergency number 112 par call karna hai - screen par confirm karo ya 'haan' bolo."
        }

        // --- Voice email: contact/address + body, show-then-confirm ---
        QuickAnswers.parseEmail(raw.trim())?.let { (toRaw, body) ->
            var to = toRaw
            if (!to.contains("@")) {
                if (!has(Manifest.permission.READ_CONTACTS)) {
                    return false to "Contact dhundhne ke liye contacts permission chahiye. Prompt mein allow karo, fir dobara bolo."
                }
                to = DeviceActions.findContactEmail(context, to)
                    ?: return false to "\"$toRaw\" ka email address nahi mila. Poora email address bata do, jaise \"email to name@gmail.com saying ...\"."
            }
            val toFinal = to
            requestConfirmation(ConfirmRequest("email", "Email $toFinal: \"$body\"") {
                if (DeviceActions.emailIntent(context, toFinal, body)) "Email app khul gayi - draft review karke send kar do."
                else "Koi email app nahi mili."
            })
            return true to "$toFinal ko email: \"$body\" - screen par confirm karo ya 'haan' bolo."
        }

        // --- ETA / travel time: Maps directions with live ETA ---
        QuickAnswers.parseEta(t)?.let { dest ->
            if (dest.isNotEmpty()) {
                val r = DeviceActions.directionsEta(context, dest)
                return r.ok to r.message
            }
        }

        // --- Missed calls + SMS readout ---
        if (Regex("(?:missed calls?|missed call)").containsMatchIn(t) && Regex("(dikhao|batao|kya hai|check|kaun|list|show)").containsMatchIn(t)) {
            if (!has(Manifest.permission.READ_CALL_LOG)) {
                return false to "Missed calls ke liye call log permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val items = DeviceActions.recentMissedCalls(context)
            return if (items.isEmpty()) true to "Koi missed call nahi hai."
            else true to "Tumhari missed calls: " + items.joinToString(". ") + "."
        }
        if (Regex("^(?:sms|messages?|texts?)\\s+(?:padho|batao|dikhao|check\\s*karo|sunao)$|^(?:mere|my)\\s+(?:sms|texts?)\\s+(?:padho|batao|dikhao)$").matches(t)) {
            if (!has(Manifest.permission.READ_SMS)) {
                return false to "SMS padhne ke liye SMS permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val items = DeviceActions.recentSms(context)
            return if (items.isEmpty()) true to "Koi recent SMS nahi hai."
            else true to "Tumhare latest SMS: " + items.joinToString(". ") + "."
        }

        // --- Send my location to a contact (WhatsApp maps link, confirm) ---
        QuickAnswers.parseSendLocation(t)?.let { nameRaw ->
            if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                return false to "Location ke liye permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val loc = DeviceActions.lastLocation(context)
                ?: return false to "Location abhi nahi mil rahi. Maps ek baar khol lo, fir dobara bolo."
            val link = "https://maps.google.com/?q=${loc.first},${loc.second}"
            if (nameRaw.isEmpty()) {
                return true to "Tumhari location: ${"%.5f".format(loc.first)}, ${"%.5f".format(loc.second)} - $link"
            }
            if (!has(Manifest.permission.READ_CONTACTS)) {
                return false to "Contact dhundhne ke liye contacts permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val contact = DeviceActions.findContact(context, nameRaw)
                ?: return false to "\"$nameRaw\" naam ka contact nahi mila. Naam saaf bolo ya number bata do."
            val body = "Meri location: $link"
            requestConfirmation(ConfirmRequest("whatsapp", "Location WhatsApp ${contact.first}?") {
                DeviceActions.whatsappMessage(context, contact.second, body).message
            })
            return true to "${contact.first} ko apni location WhatsApp karni hai - screen par confirm karo ya 'haan' bolo."
        }

        // --- Song recognition (Google sound search / Shazam, honest deep link) ---
        if (QuickAnswers.isSongIdQ(t)) {
            val r = DeviceActions.songSearch(context); return r.ok to r.message
        }

                // --- News briefing (public RSS, honest on availability) ---
        if (QuickAnswers.isNewsQ(t)) {
            val hindi = Regex("(hindi|hindi mein)").containsMatchIn(t) || raw.any { it in '\u0900'..'\u097F' }
            val items = NewsService.headlines(hindi)
            return if (items.isEmpty()) false to "News feed abhi nahi mil raha. Net check karke dobara try karo."
            else true to "Aaj ki top khabrein: " + items.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString(" ")
        }

        // --- Voice memo ---
        if (Regex("^(?:voice memo|memo|recording)\\s*(?:record|shuru|start|banao|karo|start karo)?$").matches(t)) {
            if (!has(Manifest.permission.RECORD_AUDIO)) {
                return false to "Voice memo ke liye mic permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val f = com.rout.aiassistant.service.MemoRecorder.start(context)
            return if (f != null) true to "Memo record ho raha hai. Rokne ke liye 'memo stop karo' bolo."
            else false to "Recording shuru nahi ho payi. Ek baar aur try karo."
        }
        if (Regex("^(?:memo|voice memo|recording)\\s+(?:stop|band|rok)(?:\\s+karo|\\s+kar do|\\s+do)?$").matches(t)) {
            val f = com.rout.aiassistant.service.MemoRecorder.stop()
            return if (f != null) true to "Memo save ho gaya (${f.name}). Sunne ke liye 'memo sunao' bolo."
            else false to "Koi recording chal nahi rahi thi."
        }
        if (Regex("^(?:memo|memos|voice memos?)\\s+(?:sunao|play\\s*karo|chalao)$").matches(t)) {
            val memos = com.rout.aiassistant.service.MemoRecorder.list(context)
            if (memos.isEmpty()) return true to "Koi memo nahi mila. 'Voice memo record karo' se banao."
            val ok = com.rout.aiassistant.service.MemoRecorder.play(context, memos.first())
            return if (ok) true to "Latest memo chala raha hoon."
            else false to "Memo play nahi ho paya."
        }
        if (Regex("^(?:memos|voice memos?)\\s+(?:dikhao|batao|list)$").matches(t)) {
            val memos = com.rout.aiassistant.service.MemoRecorder.list(context)
            if (memos.isEmpty()) return true to "Koi memo nahi mila."
            val fmt = java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault())
            return true to "Tumhare memos: " + memos.take(5).joinToString("; ") { fmt.format(java.util.Date(it.lastModified())) } + "."
        }

        // --- Battery alert toggle ---
        if (Regex("^battery (?:full )?alert\\s+(?:on|chalu)(?:\\s+karo)?$").matches(t)) {
            prefs.setBatteryAlert(true)
            return true to "Battery alert on. Jab ROUT khula hoga, battery full ya low pe bata dunga. (App band hone pe alert nahi bajta - Android ki seema.)"
        }
        if (Regex("^battery (?:full )?alert\\s+(?:off|band)(?:\\s+karo)?$").matches(t)) {
            prefs.setBatteryAlert(false)
            return true to "Battery alert band kar diya."
        }

        // --- Flip-to-silent toggle ---
        if (Regex("^(?:flip to silent|ulta silent|phone ulta)(?:\\s+mode)?\\s+(?:on|chalu)(?:\\s+karo)?$").matches(t)) {
            prefs.setFlipSilent(true)
            return true to "Flip-to-silent on. Jab ROUT khula hoga, phone ko screen-niche rakhne se silent ho jaayega. (App band hone pe kaam nahi karta - Android ki seema.)"
        }
        if (Regex("^(?:flip to silent|ulta silent|phone ulta)(?:\\s+mode)?\\s+(?:off|band)(?:\\s+karo)?$").matches(t)) {
            prefs.setFlipSilent(false)
            return true to "Flip-to-silent band kar diya."
        }

        // --- Bedtime mode: nightly DND ---
        QuickAnswers.parseBedtime(t)?.let { (hour, minute) ->
            if (hour == -1) {
                DeviceActions.cancelBedtime(context)
                prefs.clearBedtime()
                return true to "Bedtime band kar diya."
            }
            val r = DeviceActions.scheduleBedtime(context, hour, minute)
            if (r.ok) prefs.setBedtime(hour, minute)
            return r.ok to r.message
        }

                // --- Extra one-tap settings panels ---
        when {
            Regex("^airplane mode(?:\\s+(?:on|off|karo|kar do|chalu|band))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "airplane"); return r.ok to r.message + " (Silent toggle Android kisi bhi app ko nahi deta.)"
            }
            Regex("^battery settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "battery"); return r.ok to r.message
            }
            Regex("^storage settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "storage"); return r.ok to r.message
            }
            Regex("^(?:app|apps) settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "apps"); return r.ok to r.message
            }
            Regex("^sound settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "sound"); return r.ok to r.message
            }
            Regex("^display settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "display"); return r.ok to r.message
            }
            Regex("^privacy settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "privacy"); return r.ok to r.message
            }
            Regex("^location settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "location"); return r.ok to r.message
            }
            Regex("^accessibility settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "accessibility"); return r.ok to r.message
            }
            Regex("^security settings(?:\\s+(?:kholo|khol do|open karo))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "security"); return r.ok to r.message
            }
        }

        // --- Weather scoped: city / AQI / sunrise-sunset, else current location ---
        if (Regex("(weather|mausam|temperature|aqi|air quality|sunrise|sunset|suryoday|suryast)").containsMatchIn(t)) {
            return weatherScoped(t)
        }

                // --- Calendar event by voice: day + time + title, show-then-confirm ---
        parseCalendar(t)?.let { (title, start, end) ->
            val when_ = java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(start))
            requestConfirmation(ConfirmRequest("calendar", "Event: $title - $when_") {
                DeviceActions.insertCalendarEvent(context, title, start, end).message
            })
            return true to "Event: $title - $when_. Save karoon? Screen par confirm karo ya 'haan' bolo."
        }

        // --- One-tap system panels (WiFi/Bluetooth/data/battery saver/display/hotspot) ---
        when {
            Regex("^(?:wifi|wi-fi)(?:\\s+(?:on|off|karo|kar do|chalu|band))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "wifi"); return r.ok to r.message + " (Silent toggle Android kisi bhi app ko nahi deta.)"
            }
            Regex("^(?:bluetooth|bt)(?:\\s+(?:on|off|karo|kar do|chalu|band))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "bluetooth"); return r.ok to r.message + " (Silent toggle Android kisi bhi app ko nahi deta.)"
            }
            Regex("^(?:mobile data|internet)(?:\\s+(?:on|off|karo|kar do))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "internet"); return r.ok to r.message + " (Silent toggle Android kisi bhi app ko nahi deta.)"
            }
            Regex("^(?:battery saver|power saving)(?:\\s+(?:on|off|karo|kar do|lagao))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "batterysaver"); return r.ok to r.message
            }
            Regex("^(?:dark mode|dark theme)(?:\\s+(?:on|off|karo|kar do))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "display"); return r.ok to r.message + " Dark mode wahi display settings mein hai - ek tap."
            }
            Regex("^(?:hotspot)(?:\\s+(?:on|off|karo|kar do|chalu))?$").matches(t) -> {
                val r = DeviceActions.openSystemPanel(context, "wireless"); return r.ok to r.message + " Hotspot wahi 'Hotspot & tethering' mein hai."
            }
        }

        // --- Voice screenshot (accessibility service) ---
        if (Regex("(screenshot|screen shot|स्क्रीनशॉट)").containsMatchIn(t) && Regex("(lo|le lo|karo|kar do|lena|le|capture)").containsMatchIn(t)) {
            val svc = com.rout.aiassistant.service.RoutAccessibilityService.instance
            if (svc == null) {
                return false to "Screenshot ke liye ROUT ka screen control (accessibility) on karna hoga. Settings > Screen control se enable karo, fir dobara bolo."
            }
            if (svc.isSensitiveScreen()) {
                return false to "Ye screen sensitive lag rahi hai (password/payment) - privacy ke liye screenshot nahi liya."
            }
            var ok = false
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                svc.captureScreenshot { done -> ok = done; if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
            }
            return ok to if (ok) "Screenshot le liya - gallery (Pictures/ROUT) mein save ho gaya." else "Screenshot nahi le paya. Ek baar aur try karo."
        }

        // --- Exact alarms permission ---
        if (Regex("(exact alarm|exact reminder|sahi waqt)").containsMatchIn(t)) {
            val r = DeviceActions.exactAlarmStatus(context); return r.ok to r.message
        }

        // --- Spotify ---
        Regex("^spotify\\s+(?:par|pe|mein)\\s+(.+?)\\s+(?:chalao|bajao|sunao|play\\s*karo|play)$").find(t)?.let {
            val r = DeviceActions.spotifyPlay(context, it.groupValues[1]); return r.ok to r.message
        }
        Regex("^spotify\\s+(?:play\\s+)?(.+?)\\s+(?:chalao|bajao|sunao)$").find(t)?.let {
            val r = DeviceActions.spotifyPlay(context, it.groupValues[1]); return r.ok to r.message
        }

        // --- Camera / selfie ---
        if (Regex("^(?:selfie|selfi)(?:\\s+(?:lo|le lo|karo|kholo))?$").matches(t)) {
            val r = DeviceActions.openCamera(context, true); return r.ok to r.message
        }
        if (Regex("^(?:photo|picture|tasveer)(?:\\s+(?:lo|le lo|karo|khicho))?$").matches(t)) {
            val r = DeviceActions.openCamera(context, false); return r.ok to r.message
        }

        // --- Help ---
        if (t == "help" || t == "madad" || t.contains("what can you do") || t.contains("kya kar sakte")) {
            return true to "Main ye sab kar sakta hoon: koi bhi app kholo - kisi bhi bhasha mein bolo (\"YouTube kholo\", \"\u092f\u0942\u091f\u094d\u092f\u0942\u092c \u0913\u092a\u0928 \u0915\u0930\u094b\"), kisi ko call karo (\"Rahul ko call karo\"), weather, time, alarm, timer, torch, notes, tasks, reminders, Google/YouTube/Spotify search, maps navigation, WhatsApp message, calendar events, routines (\"good morning\"), DND/silent mode, WiFi/Bluetooth panels, screenshot - math (\"45 guna 12\"), unit/currency conversion, coin toss, dice, days-until, moon phase, kisi city ka weather/AQI/sunrise, meaning/spelling, missed calls, SMS padhna, email, ETA - aur kuch bhi poochho, main AI se jawab dunga."
        }

        // --- App launching ---
        val tPolite = POLITE_PREFIX.replace(t, "")
        val openMatch = Regex("^(?:open|launch|start)\\s+(.+)$").find(tPolite)
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
        QuickAnswers.parseNamedTimer(t)?.let { (label, secs) ->
            val r = DeviceActions.setTimer(context, secs, label); return r.ok to r.message
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

        // --- Calls: by contact name or spoken number; always show-then-confirm ---
        val callTarget = Regex("^(?:call|dial|phone)\\s+(.+)$").find(t)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+ko\\s+call\\s+(?:karo|kar do|karna hai|karni hai|lagao|marao)$").find(t)?.groupValues?.get(1)
        if (callTarget != null) {
            val name = callTarget.trim()
            val digits = name.filter { it.isDigit() || it == '+' }
            if (digits.length >= 5) {
                // Spoken number - no contact lookup needed.
                requestConfirmation(ConfirmRequest("call", "Call $digits?") {
                    DeviceActions.placeCall(context, digits)
                })
                return true to "$digits par call karna hai - screen par confirm karo ya 'haan' bolo."
            }
            if (!has(Manifest.permission.READ_CONTACTS)) {
                return false to "Contact dhundhne ke liye contacts permission chahiye. Prompt mein allow karo, fir dobara bolo."
            }
            val contact = DeviceActions.findContact(context, name)
                ?: return false to "\"$name\" naam ka contact nahi mila. Naam thoda aur saaf bolo, ya number bata do."
            requestConfirmation(ConfirmRequest("call", "Call ${contact.first} (${contact.second})?") {
                DeviceActions.placeCall(context, contact.second)
            })
            return true to "${contact.first} (${contact.second}) ko call karoon? Screen par confirm karo ya 'haan' bolo."
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


        // --- Settings shortcuts ---
        if (t.contains("settings")) { val r = DeviceActions.openSettings(context); return r.ok to r.message }
        if (t.contains("sound settings")) { val r = DeviceActions.openSoundSettings(context); return r.ok to r.message }

        // --- Screen control (user-enabled accessibility service; approved scope) ---
        ScreenControl.parse(t)?.let { intent ->
            if (intent is ScreenControl.Intent.Tap && ScreenControl.isSendLike(intent.target)) {
                requestConfirmation(ConfirmRequest("screen-tap",
                    "'${intent.target}' tap karne se kuch bheja ya post ho sakta hai. Karoon?") {
                    ScreenControl.execute(intent, ::askAiAboutScreen)
                })
                return true to "Confirm on screen - tap karne se pehle aapki permission chahiye."
            }
            return true to ScreenControl.execute(intent, ::askAiAboutScreen)
        }

        // --- AI fallback ---
        return aiFallback(raw)
    }

    private suspend fun askAiAboutScreen(question: String, redactedScreenText: String): String {
        val pm = providerManager()
        return when (pm.mode()) {
            AiMode.SETUP_REQUIRED -> "ROUT AI is not configured yet - screen ka jawab dene ke liye backend connect karo."
            AiMode.BACKEND_NEEDS_ENROLLMENT -> "Is phone ko pehle backend se connect karo (Settings > Connect this device)."
            else -> when (val r = pm.provider()!!.chat(emptyList(),
                "The user is asking about their phone screen. Answer their question using the screen text below. Screen text:\n---\n" +
                    redactedScreenText + "\n---\nQuestion: " + question)) {
                is AiResult.Reply -> r.text
                is AiResult.SetupRequired -> r.message
                is AiResult.Failure -> friendlyFailure(r.reason)
            }
        }
    }

    private fun launchSettingsListener(context: Context) {
        try {
            val i = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (_: Exception) {}
    }

    private var inRoutine = false

    private suspend fun seedDefaultRoutines() {
        db.routines().insert(Routine(trigger = "good morning", steps = "time\nweather\nbattery\nshow tasks"))
        db.routines().insert(Routine(trigger = "good night", steps = "battery\ndnd on"))
    }

    private suspend fun runRoutine(r: Routine): Pair<Boolean, String> {
        inRoutine = true
        try {
            val results = r.steps.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                .map { step -> singleStep(step).second }
            return true to "Routine: ${r.trigger}. " + results.joinToString(" ")
        } finally {
            inRoutine = false
        }
    }

    private suspend fun weatherScoped(t: String): Pair<Boolean, String> {
        var lat: Double? = null
        var lon: Double? = null
        var label = "your location"
        val city = QuickAnswers.parseWeatherCity(t)
        if (city != null) {
            val geo = WeatherService.geocode(city) ?: return false to "I could not look up $city right now."
            lat = geo.first; lon = geo.second; label = geo.third
        } else if (has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            try {
                val lm = context.getSystemService(LocationManager::class.java)
                val loc = lm.getProviders(true).firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                if (loc != null) { lat = loc.latitude; lon = loc.longitude }
            } catch (_: SecurityException) {}
        }
        if (lat == null) {
            val prefCity = prefs.weatherCity.first()
            if (prefCity.isBlank()) {
                return false to "For weather I need location permission or your city. Set your city in Settings, then ask again."
            }
            val geo = WeatherService.geocode(prefCity) ?: return false to "I could not look up $prefCity right now."
            lat = geo.first; lon = geo.second; label = geo.third
        }
        if (QuickAnswers.isAqiQ(t)) {
            val aqi = WeatherService.aqi(lat!!, lon!!) ?: return false to "Air quality service abhi jawab nahi de raha."
            val band = when {
                aqi <= 50 -> "acchi (good)"
                aqi <= 100 -> "theek-thaak (moderate)"
                aqi <= 150 -> "sensitive logon ke liye kharab"
                aqi <= 200 -> "kharab (unhealthy)"
                else -> "bahut kharab (very unhealthy)"
            }
            return true to "$label mein abhi AQI $aqi hai - hawa $band."
        }
        if (QuickAnswers.isSunQ(t)) {
            val ss = WeatherService.sunriseSunset(lat!!, lon!!) ?: return false to "Sunrise/sunset data abhi nahi mil raha."
            return true to "$label mein aaj sunrise ${ss.first} par aur sunset ${ss.second} par hai."
        }
        val w = WeatherService.current(lat!!, lon!!, label)
            ?: return false to "The weather service is unreachable right now."
        return true to "In ${w.place}: ${w.tempC}\u00b0C, ${w.summary}, humidity ${w.humidity} percent, wind ${w.windKmh} km/h."
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

    private fun friendlyFailure(reason: String): String = when {
        reason.contains("unreachable", true) || reason.contains("timeout", true) ->
            "ROUT AI ka server abhi jawab nahi de raha. Net check karke ek baar aur try karo - aksar doosri baar ho jaata hai."
        reason.contains("HTTP 401") || reason.contains("HTTP 403") ->
            "ROUT AI ka connection expire ho gaya lagta hai. Settings mein device dobara connect karo."
        reason.contains("HTTP 429") ->
            "ROUT AI thoda busy hai. Kuch second ruk kar dobara try karo."
        else -> "ROUT AI abhi jawab nahi de paya. Thodi der baad dobara try karo."
    }

    private suspend fun aiFallback(raw: String): Pair<Boolean, String> {
        val pm = providerManager()
        return when (pm.mode()) {
            AiMode.SETUP_REQUIRED -> false to
                "That needs ROUT AI, which is not configured yet. Local commands (apps, torch, notes, tasks, weather) still work. " +
                "To enable AI answers, set the ROUT backend URL in Settings and connect this device."
            AiMode.BACKEND_NEEDS_ENROLLMENT -> false to
                "The ROUT backend is set but this phone is not connected yet. Open Settings, tap Connect this device, and approve the link once."
            else -> {
                val translateOn = prefs.translateMode.first()
                val effectiveRaw = if (!translateOn) raw else {
                    val hasDeva = raw.any { it in '\u0900'..'\u097F' }
                    val target = if (hasDeva) "English" else "Hindi"
                    "Translate the following into $target. Reply with ONLY the translation, no explanation.\n\n$raw"
                }
                when (val r = pm.provider()!!.chat(history(), effectiveRaw)) {
                    is AiResult.Reply -> true to r.text
                    is AiResult.SetupRequired -> false to r.message
                    is AiResult.Failure -> false to friendlyFailure(r.reason)
                }
            }
        }
    }
}
