package com.rout.aiassistant.commands

import com.rout.aiassistant.service.RoutAccessibilityService
import com.rout.aiassistant.service.ScreenSafety

/**
 * Screen-control intent parsing and execution for CommandRouter.
 *
 * Guarantees:
 * - Commands come only from the user's own typed/spoken input. Screen text
 *   itself can never issue commands (it is untrusted content).
 * - Sensitive screens (ScreenSafety heuristics) block every action and read.
 * - Actions that could send/share content to third parties (send/submit/pay
 *   style targets) always go through user confirmation first.
 * - "Ask AI about my screen" is the only path where screen text leaves the
 *   device: it is redacted, the exact redacted text sent is shown in the
 *   reply (visible disclosure), and sensitive screens refuse outright.
 */
object ScreenControl {

    sealed class Intent {
        data class Tap(val target: String) : Intent()
        data class Scroll(val forward: Boolean) : Intent()
        object Back : Intent()
        object Home : Intent()
        object Recents : Intent()
        object ReadScreenLocal : Intent()
        data class AskAiAboutScreen(val question: String) : Intent()
    }

    /** Targets whose tap could send/share/commit something to a third party. */
    private val SEND_LIKE = listOf("send", "bhej", "submit", "pay", "transfer", "share", "post", "upload")

    fun parse(t: String): Intent? {
        Regex("^(?:back dabao|back karo|piche jao|peeche jao|wapas jao|go back|back)\$").find(t)?.let { return Intent.Back }
        Regex("^(?:home jao|home screen|home dabao|go home)\$").find(t)?.let { return Intent.Home }
        Regex("^(?:recents|recent apps|recent apps kholo|app switcher kholo|open recents)\$").find(t)?.let { return Intent.Recents }
        Regex("^(?:scroll )?(?:neeche|niche|down)(?: karo| scroll)?\$").find(t)?.let { return Intent.Scroll(true) }
        Regex("^(?:scroll )?(?:upar|up)(?: karo| scroll)?\$").find(t)?.let { return Intent.Scroll(false) }
        Regex("^scroll (?:down|up)\$").find(t)?.let { return Intent.Scroll(t.endsWith("down")) }
        Regex("^(?:tap|click|press|dabao|daba do) (.+)\$").find(t)?.let { return Intent.Tap(it.groupValues[1].trim()) }
        Regex("^(.+) (?:dabao|daba do|click karo)\$").find(t)?.let { return Intent.Tap(it.groupValues[1].trim()) }
        Regex("^(?:screen pe kya hai|ye screen padho|screen padho|what.?s on (?:my |the )screen|read (?:my |the )screen)\$").find(t)
            ?.let { return Intent.ReadScreenLocal }
        Regex("^(?:screen ke baare mein (?:ai se )?poochho|ask ai about (?:my |the )screen|ye screen samjhao|screen samjhao)[:\\s]*(.+)?\$")
            .find(t)?.let { return Intent.AskAiAboutScreen(it.groupValues.getOrNull(1)?.trim() ?: "Summarize this screen") }
        return null
    }

    fun isSendLike(target: String): Boolean {
        val l = target.lowercase()
        return SEND_LIKE.any { l.contains(it) }
    }

    /**
     * Execute a parsed intent. askAi is invoked only for AskAiAboutScreen and
     * receives (question, redactedScreenText); its answer is returned inside a
     * visible disclosure that includes exactly what was sent.
     */
    suspend fun execute(
        intent: Intent,
        askAi: suspend (String, String) -> String
    ): String {
        val svc = RoutAccessibilityService.instance
            ?: return "Screen control off hai. ROUT Settings > Screen control pe jakar accessibility service on karo."
        return when (intent) {
            is Intent.Back -> if (svc.back()) "Done - back." else blockedOrFail(svc)
            is Intent.Home -> if (svc.home()) "Done - home." else "Home nahi ho paya."
            is Intent.Recents -> if (svc.recents()) "Done - recent apps." else "Recent apps nahi khul paye."
            is Intent.Scroll -> when {
                svc.isSensitiveScreen() -> BLOCKED
                svc.scroll(intent.forward) -> "Done - scroll ${if (intent.forward) "neeche" else "upar"}."
                else -> "Is screen pe kuch scroll nahi hua."
            }
            is Intent.Tap -> {
                if (svc.isSensitiveScreen()) return BLOCKED
                // send-like targets never reach here unconfirmed - CommandRouter
                // intercepts them through its confirmation flow before execute().
                if (svc.tapNamedElement(intent.target)) "Done - '${intent.target}' tap kiya."
                else "Screen pe '${intent.target}' nahi mila. Thoda alag naam bolo ya screen pe kya hai poochho."
            }
            is Intent.ReadScreenLocal -> {
                val text = svc.screenTextOnDevice() ?: return blockedOrEmpty(svc)
                "Screen par (sirf is device pe padha gaya, kahin nahi bheja):\n" + text.take(600)
            }
            is Intent.AskAiAboutScreen -> {
                val raw = svc.screenTextOnDevice() ?: return blockedOrEmpty(svc)
                val redacted = ScreenSafety.redact(raw).take(3000)
                val answer = askAi(intent.question, redacted)
                "Screen ka text AI ko bheja gaya (numbers/codes/contact details chhupa kar). Jo text bheja gaya:\n---\n" +
                    redacted.take(800) + "\n---\nJawab: " + answer
            }
        }
    }

    private const val BLOCKED =
        "Ye screen sensitive lag rahi hai (password, OTP ya payment). Main is screen ko na padhunga na is pe kuch karunga."

    private fun blockedOrFail(svc: RoutAccessibilityService) =
        if (svc.isSensitiveScreen()) BLOCKED else "Action nahi ho paya."

    private fun blockedOrEmpty(svc: RoutAccessibilityService) =
        if (svc.isSensitiveScreen()) BLOCKED else "Screen ka text nahi mil paya."
}
