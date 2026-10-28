package com.rout.aiassistant.commands

import org.json.JSONObject

/**
 * Validates a model-proposed action chain ({speech, actions:[{type,...}]}) and turns it into
 * ordinary router command lines. The chain has NO authority: every step still goes through the
 * normal router paths, so send/call confirmations stay, and execution halts at the first confirmation.
 * Anything not on the whitelist (CLICK_XY, DRAG, CAPTURE_SCREEN_VISION, PRESS_ENTER, unknown types)
 * rejects the WHOLE chain.
 */
object ActionChain {
    const val MAX_STEPS = 6
    data class Result(val speech: String, val steps: List<String>, val error: String?)

    private fun clean(s: String, max: Int = 120): String =
        s.replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ").trim().take(max)

    fun parse(json: String): Result {
        val o = try { JSONObject(json) } catch (e: Exception) { return Result("", emptyList(), "bad json") }
        val speech = clean(o.optString("speech"), 400)
        val arr = o.optJSONArray("actions") ?: return Result(speech, emptyList(), null)
        if (arr.length() > MAX_STEPS) return Result(speech, emptyList(), "too many steps")
        val steps = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: return Result(speech, emptyList(), "bad step")
            val type = a.optString("type").uppercase()
            val line = when (type) {
                "OPEN_APP" -> clean(a.optString("app"), 40).takeIf { it.isNotEmpty() }?.let { "open $it" }
                "GLOBAL_NAV" -> when (a.optString("target").lowercase()) {
                    "back" -> "back"; "home" -> "home"; "recents" -> "recents"; else -> null }
                "SCROLL" -> when (a.optString("direction").lowercase()) {
                    "up" -> "scroll up"; "down" -> "scroll down"; else -> null }
                "CLICK_NODE" -> clean(a.optString("label"), 40).takeIf { it.isNotEmpty() }?.let { "tap $it" }
                "SEND_WHATSAPP" -> {
                    val n = clean(a.optString("contact"), 40); val m = clean(a.optString("message"), 300)
                    if (n.isNotEmpty() && m.isNotEmpty()) "$n ko whatsapp bhejo $m" else null
                }
                "MAKE_CALL" -> clean(a.optString("contact"), 40).takeIf { it.isNotEmpty() }?.let { "$it ko whatsapp call karo" }
                // Numbers and enums only (validated here); no free text reaches the router for these four.
                "SET_ALARM" -> {
                    val h = a.optInt("hour", -1); val m = a.optInt("minute", -1)
                    if (a.opt("hour") is Number && a.opt("minute") is Number && h in 0..23 && m in 0..59) "alarm %d:%02d".format(h, m) else null
                }
                "SET_TIMER" -> {
                    val n = a.optInt("minutes", -1)
                    if (a.opt("minutes") is Number && n in 1..720) "timer for $n minute" else null
                }
                "MEDIA_CONTROL" -> when (a.optString("action").lowercase()) {
                    "play_pause" -> "play music"; "next" -> "next song"; "previous" -> "previous song"; else -> null }
                "FLASHLIGHT" -> when (a.optString("state").lowercase()) {
                    "on" -> "torch on"; "off" -> "torch off"; else -> null }
                else -> null
            } ?: return Result(speech, emptyList(), "step ${i + 1} not allowed: $type")
            steps.add(line)
        }
        return Result(speech, steps, null)
    }

    const val HINT = "\n\n[ROUT note, not from the user: only if the user asked for a multi-step phone action, you may add ONE final JSON object {\"speech\":\"short reply\",\"actions\":[{\"type\":\"OPEN_APP\",\"app\":\"youtube\"}]} using only OPEN_APP(app), GLOBAL_NAV(target back|home|recents), SCROLL(direction up|down), CLICK_NODE(label), SEND_WHATSAPP(contact,message), MAKE_CALL(contact), SET_ALARM(hour 0-23, minute 0-59), SET_TIMER(minutes 1-720), MEDIA_CONTROL(action play_pause|next|previous), FLASHLIGHT(state on|off). Max 6 actions. Otherwise answer normally with no JSON.]"

    /** Splits a model reply into visible text and an optional chain. The chain is untrusted: it only proposes steps. */
    fun extract(reply: String): Pair<String, Result?> {
        if (!reply.contains("\"actions\"")) return reply to null
        val first = Regex("\\{\\s*\"(speech|actions)\"").find(reply)?.range?.first ?: return reply to null
        val end = reply.lastIndexOf("}")
        if (end < first) return reply.substring(0, first).replace("```json", "").replace("```", "").trim() to Result("", emptyList(), "reply cut off")
        val r = parse(reply.substring(first, end + 1))
        val before = reply.substring(0, first).replace("```json", "").replace("```", "").trim()
        return (before.ifEmpty { r.speech }) to r
    }
}
