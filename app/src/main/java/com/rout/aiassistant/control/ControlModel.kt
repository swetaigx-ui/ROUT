package com.rout.aiassistant.control

import org.json.JSONArray
import org.json.JSONObject

enum class Risk { LOW, MEDIUM, HIGH }

/** HIGH risk = consequential (send, call, publish, delete, purchase): always needs the user's confirmation, whatever the plan says. */
enum class ParamType { STRING, INT, BOOL, ENUM }

data class ParamSpec(val name: String, val type: ParamType, val required: Boolean = true, val max: Int = 200, val values: Set<String> = emptySet())

data class ActionSpec(
    val type: String,
    val params: List<ParamSpec>,
    val risk: Risk,
    /** Android permission or access this action needs (documentation + permission check), "" when none. */
    val permission: String,
    /** How the result is checked after execution. */
    val verification: String,
    /** False = registered but NOT implemented: validation rejects it and the report lists it as unsupported. */
    val implemented: Boolean,
    val note: String = ""
)

/** A single validated action. Only the registry can produce one; there is no generic "run this code" action. */
data class ControlAction(val type: String, val args: Map<String, String>) {
    val spec: ActionSpec get() = ActionRegistry.spec(type)!!
    val risk: Risk get() = spec.risk
    fun arg(n: String) = args[n].orEmpty()
}

data class ControlPlan(val requestId: String, val actions: List<ControlAction>, val requiresConfirmation: Boolean)

sealed class PlanResult {
    data class Valid(val plan: ControlPlan) : PlanResult()
    data class Invalid(val reason: String) : PlanResult()
}

object ActionRegistry {
    private val ST = ParamType.STRING
    private val PANELS = setOf("wifi", "bluetooth", "internet", "display", "sound", "wireless", "battery", "batterysaver", "storage", "apps", "airplane")
    private fun s(vararg p: ParamSpec) = p.toList()
    private fun p(n: String, max: Int = 200, req: Boolean = true) = ParamSpec(n, ST, req, max)

    val all: List<ActionSpec> = listOf(
        ActionSpec("screen.home", s(), Risk.LOW, "accessibility", "dispatched", true),
        ActionSpec("screen.back", s(), Risk.LOW, "accessibility", "dispatched", true),
        ActionSpec("screen.scroll", s(ParamSpec("direction", ParamType.ENUM, true, 8, setOf("down", "up"))), Risk.LOW, "accessibility", "dispatched", true),
        ActionSpec("screen.swipe", s(ParamSpec("direction", ParamType.ENUM, true, 8, setOf("left", "right"))), Risk.LOW, "accessibility", "dispatched", true),
        ActionSpec("ui.find", s(p("text", 80)), Risk.LOW, "accessibility", "element_seen_on_screen", true),
        ActionSpec("ui.tap", s(p("text", 80)), Risk.MEDIUM, "accessibility", "screen_changed", true, "A send/pay/delete/post-like target is escalated to HIGH and needs confirmation."),
        ActionSpec("ui.long_press", s(p("text", 80)), Risk.MEDIUM, "accessibility", "dispatched", true),
        ActionSpec("ui.type", s(p("text", 300)), Risk.MEDIUM, "accessibility", "dispatched", true, "Types into the focused normal text box only. Never password fields. Never sends."),
        ActionSpec("ui.clear", s(), Risk.MEDIUM, "accessibility", "dispatched", true),
        ActionSpec("ui.select_all", s(), Risk.LOW, "accessibility", "dispatched", true),
        ActionSpec("app.open", s(p("target", 60)), Risk.LOW, "", "foreground_app_changed", true),
        ActionSpec("app.switch", s(), Risk.LOW, "accessibility", "dispatched", true, "Opens recent apps."),
        ActionSpec("web.search", s(p("target", 200)), Risk.LOW, "", "dispatched", true),
        ActionSpec("web.open_url", s(p("target", 500)), Risk.LOW, "", "dispatched", true, "http/https only."),
        ActionSpec("device.flashlight_on", s(), Risk.LOW, "", "dispatched", true),
        ActionSpec("device.flashlight_off", s(), Risk.LOW, "", "dispatched", true),
        ActionSpec("device.volume_adjust", s(ParamSpec("direction", ParamType.ENUM, true, 8, setOf("up", "down", "mute", "unmute", "max"))), Risk.LOW, "", "dispatched", true),
        ActionSpec("settings.open", s(ParamSpec("target", ParamType.ENUM, true, 16, PANELS)), Risk.LOW, "", "dispatched", true, "Opens a panel only. Security, privacy, location and accessibility panels are not allowed from a plan."),
        ActionSpec("contacts.search", s(p("target", 80)), Risk.LOW, "READ_CONTACTS", "match_count", true),
        ActionSpec("call.initiate", s(p("target", 80)), Risk.HIGH, "READ_CONTACTS,CALL_PHONE", "dispatched", true, "Single clear contact only; shows name and number; opens the dialer when CALL_PHONE is missing."),
        ActionSpec("call.end", s(), Risk.HIGH, "ANSWER_PHONE_CALLS", "dispatched", true, "TelecomManager.endCall (API 28+) needs the ANSWER_PHONE_CALLS runtime permission; without it the step fails honestly."),
        ActionSpec("message.draft", s(p("target", 80), p("text", 500)), Risk.MEDIUM, "READ_CONTACTS", "chat_opened", true, "WhatsApp chat opens with the text prefilled. Nothing is sent."),
        ActionSpec("message.send", s(p("target", 80), p("text", 500)), Risk.HIGH, "READ_CONTACTS,accessibility", "dispatched", true, "Shows recipient, number and exact words; sends only after confirmation."),
        ActionSpec("share.prepare", s(p("text", 500)), Risk.MEDIUM, "", "dispatched", true, "Opens the Android share sheet; the user picks the target."),
        ActionSpec("media.play_pause", s(), Risk.LOW, "notification_listener", "dispatched", true),
        ActionSpec("media.next", s(), Risk.LOW, "notification_listener", "dispatched", true),
        ActionSpec("media.previous", s(), Risk.LOW, "notification_listener", "dispatched", true),
        ActionSpec("notification.open", s(), Risk.LOW, "accessibility", "dispatched", true, "Opens the notification shade. Does not read it."),
        ActionSpec("notification.dismiss", s(ParamSpec("scope", ParamType.ENUM, true, 4, setOf("all"))), Risk.HIGH, "notification_listener", "dispatched", true, "Bulk clear only: scope must be explicitly \"all\". Clears ALL dismissible notifications (needs notification access), confirmed first, never reads them. Dismissing one named notification is not implemented."),
        ActionSpec("notification.dismiss_app", s(p("target", 60)), Risk.HIGH, "notification_listener", "dispatched", true, "Dismisses the dismissible notifications of ONE named app (unique match, else it asks). Confirmed first; never reads content."),
        ActionSpec("file.open", s(p("target", 300)), Risk.LOW, "system_file_picker", "dispatched", true, "Opens the Android file picker; the USER picks the file, then it opens in a viewer app. ROUT never searches storage by itself."),
        ActionSpec("file.rename", s(p("target", 300), p("text", 120)), Risk.HIGH, "system_file_picker", "renamed", true, "User picks the file in the system picker, then a dialog shows the real name and the new name; only Yes renames. Storage must support rename."),
        ActionSpec("file.delete", s(p("target", 300)), Risk.HIGH, "system_file_picker", "deleted", true, "User picks the file, then a dialog shows its real name; only Yes deletes permanently. Storage must support delete."),
        ActionSpec("workflow.execute", s(p("target", 80)), Risk.MEDIUM, "", "routine_ran", true, "Runs an EXISTING saved routine by exact name. Every step goes through the normal router, so each send/call/like step still asks its own confirmation and the routine halts at the first one."),
        ActionSpec("automation.cancel", s(), Risk.LOW, "", "state_cancelled", true, "Same as Stop Automation.")
    )
    private val byType = all.associateBy { it.type }
    fun spec(type: String): ActionSpec? = byType[type]
    val implementedTypes: List<String> get() = all.filter { it.implemented }.map { it.type }
    val unsupportedTypes: List<String> get() = all.filter { !it.implemented }.map { it.type }

    private val SEND_LIKE = Regex("\\b(send|bhej|submit|pay|transfer|share|post|upload|like|follow|unfollow|comment|react|subscribe|repost|reply|delete|remove|buy|purchase|order|confirm|checkout|book|uninstall|erase|reset|sign out|logout|log out|unlock|install|allow|accept|call)\\b", RegexOption.IGNORE_CASE)
    /** Effective risk: the registry's risk, raised to HIGH for a tap/long-press whose target looks consequential. The backend can never lower it. */
    fun effectiveRisk(a: ControlAction): Risk =
        if ((a.type == "ui.tap" || a.type == "ui.long_press") && SEND_LIKE.containsMatchIn(a.arg("text"))) Risk.HIGH else a.risk
}

/** Strict plan validation: exact key set, allowlisted implemented types, typed and length-capped parameters. Never executes anything. */
object PlanValidator {
    const val MAX_ACTIONS = 8
    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val TOP = setOf("requestId", "actions", "requiresConfirmation")
    private val ACT = setOf("type", "target", "parameters")

    fun parse(json: String, expectedRequestId: String? = null): PlanResult {
        val o = try { JSONObject(json) } catch (e: Exception) { return PlanResult.Invalid("Plan JSON nahi hai.") }
        for (k in o.keys()) if (k !in TOP) return PlanResult.Invalid("Unknown field '$k'.")
        val id = o.opt("requestId") as? String ?: return PlanResult.Invalid("requestId missing.")
        if (!ID.matches(id)) return PlanResult.Invalid("requestId galat format.")
        if (expectedRequestId != null && id != expectedRequestId) return PlanResult.Invalid("requestId match nahi hua.")
        val rc = if (o.has("requiresConfirmation")) (o.opt("requiresConfirmation") as? Boolean ?: return PlanResult.Invalid("requiresConfirmation boolean hona chahiye.")) else false
        val arr = o.opt("actions") as? JSONArray ?: return PlanResult.Invalid("actions array missing.")
        if (arr.length() == 0 || arr.length() > MAX_ACTIONS) return PlanResult.Invalid("actions 1 se $MAX_ACTIONS tak hone chahiye.")
        val out = ArrayList<ControlAction>()
        for (i in 0 until arr.length()) {
            val a = arr.opt(i) as? JSONObject ?: return PlanResult.Invalid("Action ${i + 1} object nahi hai.")
            for (k in a.keys()) if (k !in ACT) return PlanResult.Invalid("Action ${i + 1}: unknown field '$k'.")
            val type = a.opt("type") as? String ?: return PlanResult.Invalid("Action ${i + 1}: type missing.")
            val spec = ActionRegistry.spec(type) ?: return PlanResult.Invalid("Action '$type' registered nahi hai.")
            if (!spec.implemented) return PlanResult.Invalid("Action '$type' abhi supported nahi hai.")
            val params = a.opt("parameters")
            val pobj = when (params) { null, JSONObject.NULL -> JSONObject(); is JSONObject -> params; else -> return PlanResult.Invalid("Action '$type': parameters object hona chahiye.") }
            val merged = LinkedHashMap<String, Any>()
            for (k in pobj.keys()) merged[k] = pobj.get(k)
            val target = a.opt("target")
            if (target != null && target != JSONObject.NULL) {
                if (target !is String) return PlanResult.Invalid("Action '$type': target string hona chahiye.")
                if (target.isNotEmpty()) merged["target"] = target
            }
            val allowed = spec.params.map { it.name }.toSet()
            for (k in merged.keys) if (k !in allowed) return PlanResult.Invalid("Action '$type': parameter '$k' allowed nahi.")
            val args = LinkedHashMap<String, String>()
            for (ps in spec.params) {
                val v = merged[ps.name]
                if (v == null) { if (ps.required) return PlanResult.Invalid("Action '$type': '${ps.name}' chahiye."); continue }
                if (v !is String) return PlanResult.Invalid("Action '$type': '${ps.name}' string hona chahiye.")
                val x = v.trim()
                if (x.isEmpty() && ps.required) return PlanResult.Invalid("Action '$type': '${ps.name}' khali hai.")
                if (x.length > ps.max) return PlanResult.Invalid("Action '$type': '${ps.name}' bahut lamba.")
                if (x.any { it.code < 32 && it != '\n' }) return PlanResult.Invalid("Action '$type': '${ps.name}' mein control characters.")
                if (ps.type == ParamType.ENUM && x !in ps.values) return PlanResult.Invalid("Action '$type': '${ps.name}' value allowed nahi.")
                args[ps.name] = x
            }
            if (type == "web.open_url" && !Regex("^https?://[^\\s]+$", RegexOption.IGNORE_CASE).matches(args["target"].orEmpty())) return PlanResult.Invalid("Sirf http/https URL allowed hai.")
            out.add(ControlAction(type, args))
        }
        return PlanResult.Valid(ControlPlan(id, out, rc))
    }
}
