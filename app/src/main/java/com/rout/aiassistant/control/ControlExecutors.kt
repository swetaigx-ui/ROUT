package com.rout.aiassistant.control

import android.content.Context
import android.content.Intent
import com.rout.aiassistant.commands.ContactResolver
import com.rout.aiassistant.commands.DeviceActions
import com.rout.aiassistant.commands.MediaControl
import com.rout.aiassistant.service.RoutAccessibilityService
import kotlinx.coroutines.delay

/** Real handlers for the implemented registry entries. Each one reports what it actually did; unsupported types never reach here (validator rejects them). */
class AndroidExecutor(
    private val context: Context,
    private val svc: () -> RoutAccessibilityService? = { RoutAccessibilityService.instance },
    private val contactRows: (String) -> List<ContactResolver.Row> = { DeviceActions.queryContacts(context, it) },
    private val onCancelRequested: () -> Unit = {},
    private val routineRunner: (suspend (String) -> Pair<Boolean, String>)? = null,
    private val appNotifications: () -> Map<String, Pair<String, List<String>>>? = { com.rout.aiassistant.service.RoutMediaListenerService.instance?.clearableByApp() },
    private val dismissKeys: (List<String>) -> Unit = { com.rout.aiassistant.service.RoutMediaListenerService.instance?.dismissKeys(it) }
) : ActionExecutor {
    private var fgBefore: String? = null
    private var fpBefore: Int? = null
    private val OFF = "Screen control off hai. ROUT Settings > Screen control pe jakar accessibility service on karo."

    private fun fail(m: String) = StepResult(false, m)
    private fun ar(r: com.rout.aiassistant.commands.ActionResult) = StepResult(r.ok, r.message)
    private fun contact(name: String): Pair<ContactResolver.Result.Unique?, String?> =
        when (val r = ContactResolver.resolve(name, contactRows(name))) {
            is ContactResolver.Result.Unique -> r to null
            is ContactResolver.Result.Ambiguous -> null to ContactResolver.ambiguousMessage(name, r.options)
            ContactResolver.Result.None -> null to "\"$name\" naam ka contact nahi mila."
        }

    override suspend fun describe(a: ControlAction): String = when (a.type) {
        "message.send" -> contact(a.arg("target")).let { (c, e) -> if (c != null) "WhatsApp ${c.name} (${c.number}): \"${a.arg("text")}\"" else (e ?: "Contact nahi mila") }
        "call.end" -> "Chalu call khatam karun?"
        "notification.dismiss" -> "SAARE dismiss hone wale notifications saaf karun? (sab ek saath)"
        "notification.dismiss_app" -> appMatch(a.arg("target")).let { (m, e) -> if (m != null) "${m.first} ke ${m.second.size} notifications saaf karun?" else (e ?: "App nahi mila") }
        "workflow.execute" -> "Saved routine '${a.arg("target")}' chalau? (har step ki apni confirmation aayegi)"
        "call.initiate" -> contact(a.arg("target")).let { (c, e) -> if (c != null) "Call: ${c.name} (${c.number})" else (e ?: "Contact nahi mila") }
        "ui.tap", "ui.long_press" -> "Screen par '${a.arg("text")}' dabana hai"
        else -> a.type + " " + a.args.values.joinToString(" ")
    }

    /** Unique app (by label) among dismissible notifications, else an honest error. */
    private fun appMatch(name: String): Pair<Pair<String, List<String>>?, String?> {
        val all = appNotifications() ?: return null to "Notification access on nahi hai, isliye kuch saaf nahi kiya."
        val q = name.trim().lowercase()
        val hits = all.values.filter { it.first.lowercase() == q }.ifEmpty { all.values.filter { it.first.lowercase().contains(q) } }
        return when {
            q.isEmpty() -> null to "App ka naam chahiye."
            hits.isEmpty() -> null to "\"$name\" ka koi dismiss hone wala notification nahi mila."
            hits.size > 1 -> null to "\"$name\" ke naam se ${hits.size} apps mile (${hits.joinToString(", ") { it.first }}) - kaunsa wala, batao."
            else -> hits[0] to null
        }
    }

    private fun fingerprint(): Int? = svc()?.screenTextOnDevice()?.hashCode()

    override suspend fun execute(a: ControlAction): StepResult {
        val s = svc()
        fgBefore = s?.foregroundPackage(); fpBefore = null
        return when (a.type) {
            "screen.home" -> if (s == null) fail(OFF) else if (s.home()) StepResult(true, "Home gaya.") else fail("Home nahi ho paya.")
            "screen.back" -> if (s == null) fail(OFF) else if (s.back()) StepResult(true, "Back gaya.") else fail("Back nahi ho paya (ya screen sensitive hai).")
            "screen.scroll" -> if (s == null) fail(OFF) else if (s.scroll(a.arg("direction") == "down")) StepResult(true, "Scroll ${a.arg("direction")}.") else fail("Is screen par kuch scroll nahi hua.")
            "screen.swipe" -> if (s == null) fail(OFF) else if (!s.readableAndSafe()) fail("Screen padhne layak/safe nahi.") else if (s.swipe(a.arg("direction"))) StepResult(true, "Swipe ${a.arg("direction")}.") else fail("Swipe nahi chala.")
            "ui.find" -> if (s == null) fail(OFF) else if (!s.readableAndSafe()) fail("Screen padhne layak/safe nahi.") else {
                val n = s.countMatching(a.arg("text"))
                if (n == 0) fail("'${a.arg("text")}' screen par nahi dikha.")
                else if (n > 1) StepResult(true, "'${a.arg("text")}' ke $n match mile - kaunsa wala, batao.", Verify.VERIFIED)
                else StepResult(true, "'${a.arg("text")}' screen par mila.", Verify.VERIFIED)
            }
            "ui.tap" -> if (s == null) fail(OFF) else if (s.isSensitiveScreen()) fail("Screen sensitive hai, kuch nahi dabaya.") else {
                val n = s.countMatching(a.arg("text"))
                if (n > 1) fail("'${a.arg("text")}' ke $n match mile - guess karke nahi dabaya. Kaunsa wala, batao.")
                else { fpBefore = fingerprint(); if (s.tapNamedElement(a.arg("text"))) StepResult(true, "'${a.arg("text")}' dabaya.") else fail("'${a.arg("text")}' dabane layak nahi mila.") }
            }
            "ui.long_press" -> if (s == null) fail(OFF) else if (s.longPressNamed(a.arg("text"))) StepResult(true, "'${a.arg("text")}' long press.") else fail("'${a.arg("text")}' long press ke liye nahi mila.")
            "ui.type" -> if (s == null) fail(OFF) else if (s.typeIntoFocused(a.arg("text"))) StepResult(true, "Type kar diya. Send nahi kiya.") else fail("Koi normal text box focus mein nahi mila.")
            "ui.clear" -> if (s == null) fail(OFF) else if (s.clearFocused()) StepResult(true, "Text box saaf kiya.") else fail("Koi normal text box focus mein nahi mila.")
            "ui.select_all" -> if (s == null) fail(OFF) else if (s.selectAllFocused()) StepResult(true, "Poora text select kiya.") else fail("Koi normal text box focus mein nahi mila.")
            "app.open" -> ar(DeviceActions.openApp(context, a.arg("target")))
            "app.switch" -> if (s == null) fail(OFF) else if (s.recents()) StepResult(true, "Recent apps khule.") else fail("Recent apps nahi khule.")
            "web.search" -> ar(DeviceActions.webSearch(context, a.arg("target")))
            "web.open_url" -> ar(DeviceActions.openWebsite(context, a.arg("target")))
            "device.flashlight_on" -> ar(DeviceActions.flashlight(context, true))
            "device.flashlight_off" -> ar(DeviceActions.flashlight(context, false))
            "device.volume_adjust" -> ar(DeviceActions.volume(context, a.arg("direction")))
            "settings.open" -> ar(DeviceActions.openSystemPanel(context, a.arg("target")))
            "contacts.search" -> { val rows = contactRows(a.arg("target")); val names = rows.map { it.name }.distinct()
                if (names.isEmpty()) fail("Koi contact nahi mila.") else StepResult(true, "${names.size} contact mila: ${names.take(5).joinToString(", ")}", Verify.VERIFIED) }
            "call.initiate" -> contact(a.arg("target")).let { (c, e) -> if (c == null) fail(e ?: "Contact nahi mila.") else StepResult(true, DeviceActions.placeCall(context, c.number)) }
            "message.draft" -> contact(a.arg("target")).let { (c, e) -> if (c == null) fail(e ?: "Contact nahi mila.") else ar(DeviceActions.whatsappMessage(context, c.number, a.arg("text"))) }
            "message.send" -> contact(a.arg("target")).let { (c, e) ->
                if (c == null) fail(e ?: "Contact nahi mila.") else {
                    val r = DeviceActions.whatsappMessage(context, c.number, a.arg("text"))
                    if (!r.ok) fail(r.message)
                    else if (s?.tapExact(listOf("send", "\u092d\u0947\u091c\u0947\u0902"), "com.whatsapp", 6000) == true) StepResult(true, "${c.name} (${c.number}) ko send button dabaya. Phone ne bheja ya nahi, WhatsApp mein check kar lena.")
                    else StepResult(true, "Chat khul gayi, message ready hai - send button nahi mila, bas send dabao.")
                }
            }
            "share.prepare" -> {
                val i = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.arg("text")), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(i); StepResult(true, "Share sheet khuli - target tum chuno.") } catch (e: Exception) { fail("Share sheet nahi khuli.") }
            }
            "media.play_pause" -> ar(MediaControl.playPause(context))
            "media.next" -> ar(MediaControl.next(context))
            "media.previous" -> ar(MediaControl.previous(context))
            "notification.open" -> if (s == null) fail(OFF) else if (s.notifications()) StepResult(true, "Notifications khul gaye (padhe nahi).") else fail("Notifications nahi khule.")
            "call.end" -> endCall()
            "notification.dismiss" -> { val l = com.rout.aiassistant.service.RoutMediaListenerService.instance
                if (l == null) fail("Notification access on nahi hai, isliye kuch saaf nahi kiya.") else { l.cancelAllNotifications(); StepResult(true, "Dismiss hone wale notifications saaf karne ka command diya (padhe nahi).") } }
            "notification.dismiss_app" -> appMatch(a.arg("target")).let { (m, e) -> if (m == null) fail(e ?: "App nahi mila.") else { dismissKeys(m.second); StepResult(true, "${m.first} ke ${m.second.size} notifications saaf karne ka command diya.") } }
            "workflow.execute" -> { val r = routineRunner; if (r == null) fail("Routines abhi available nahi.") else { val (ok, msg) = r(a.arg("target")); StepResult(ok, msg) } }
            "automation.cancel" -> { onCancelRequested(); StepResult(true, "Automation rokne ka command diya.", Verify.VERIFIED) }
            else -> fail("'${a.type}' supported nahi hai.")
        }
    }

    @Suppress("DEPRECATION")
    private fun endCall(): StepResult {
        if (android.os.Build.VERSION.SDK_INT < 28) return fail("Is Android version par call end nahi ho sakta (Android 9+ chahiye).")
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return fail("Call end karne ke liye 'Phone calls answer/manage' permission chahiye - abhi nahi hai, kuch nahi kiya.")
        val tm = context.getSystemService(android.telecom.TelecomManager::class.java)
        return try { if (tm.endCall()) StepResult(true, "Chalu call end karne ka command diya.") else fail("Koi chalu call nahi mili ya end nahi hui.") }
        catch (e: SecurityException) { fail("Android ne call end karne se roka (permission).") }
    }

    override suspend fun verify(a: ControlAction, r: StepResult): Verify {
        val s = svc()
        return when (a.type) {
            "app.open" -> { var v = r.verify
                if (s != null) for (i in 0 until 6) { delay(400); val now = s.foregroundPackage(); if (now != null && now != fgBefore && now != context.packageName) { v = Verify.VERIFIED; break } }
                v }
            "ui.tap" -> { delay(600); val now = fingerprint(); if (fpBefore != null && now != null && now != fpBefore) Verify.VERIFIED else r.verify }
            else -> r.verify
        }
    }
}
