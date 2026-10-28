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
                else -> null
            } ?: return Result(speech, emptyList(), "step ${i + 1} not allowed: $type")
            steps.add(line)
        }
        return Result(speech, steps, null)
    }
}
