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
        object Notifications : Intent()
        object QuickSettings : Intent()
        object LockScreen : Intent()
        object SystemScreenshot : Intent()
        data class LongPress(val target: String) : Intent()
        data class TypeText(val text: String) : Intent()
        data class WaitFor(val text: String) : Intent()
        data class Swipe(val dir: String) : Intent()
        data class TapXY(val x: Float, val y: Float, val double: Boolean) : Intent()
        data class DragXY(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Intent()
        data class Pinch(val zoomIn: Boolean) : Intent()
        data class AskAiAboutScreen(val question: String) : Intent()
    }

    /** Targets whose tap could send/share/commit something to a third party. */
    private val SEND_LIKE = listOf("send", "bhej", "submit", "pay", "transfer", "share", "post", "upload",
        "delete", "remove", "hata", "buy", "purchase", "order", "confirm", "checkout", "book", "call", "dial", "uninstall", "erase", "reset", "clear", "sign out", "logout", "log out", "unlock", "install", "allow", "accept")

    fun parse(t: String): Intent? {
        Regex("^(double tap|tap) at (\\d{1,4}) (\\d{1,4})\$").find(t)?.let {
            return Intent.TapXY(it.groupValues[2].toFloat(), it.groupValues[3].toFloat(), it.groupValues[1].startsWith("double"))
        }
        Regex("^drag from (\\d{1,4}) (\\d{1,4}) to (\\d{1,4}) (\\d{1,4})\$").find(t)?.let {
            val v = it.groupValues.drop(1).map { n -> n.toFloat() }
            return Intent.DragXY(v[0], v[1], v[2], v[3])
        }
        Regex("^(?:pinch|zoom) (in|out)\$").find(t)?.let { return Intent.Pinch(it.groupValues[1] == "in") }
        Regex("^(?:notifications kholo|notification panel kholo|notification dikhao|open notifications)\$").find(t)?.let { return Intent.Notifications }
        Regex("^(?:quick settings kholo|quick settings|open quick settings)\$").find(t)?.let { return Intent.QuickSettings }
        Regex("^(?:screen lock karo|phone lock karo|lock screen|lock the phone)\$").find(t)?.let { return Intent.LockScreen }
        Regex("^(?:screenshot lo|screenshot le lo|take a screenshot|screenshot)\$").find(t)?.let { return Intent.SystemScreenshot }
        Regex("^(?:long press|lamba dabao|long dabao) (.+)\$").find(t)?.let { return Intent.LongPress(it.groupValues[1].trim()) }
        Regex("^(?:type karo|screen par type karo) (.+)\$").find(t)?.let { return Intent.TypeText(it.groupValues[1].trim()) }
        Regex("^(?:wait for|intezaar karo|ruko jab tak) (.+?)(?: dikhe| aaye)?\$").find(t)?.let { return Intent.WaitFor(it.groupValues[1].trim()) }
        Regex("^swipe (left|right)\$").find(t)?.let { return Intent.Swipe(it.groupValues[1]) }
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
        askAi: suspend (String, String) -> String,
        cloudAllowed: Boolean = false,
        /** Per-request approval: gets (question, exactly the sanitized text that would be sent). Null = refuse (fail closed). */
        previewForApproval: ((String, String) -> Unit)? = null
    ): String {
        val svc = RoutAccessibilityService.instance
            ?: return "Screen control off hai. ROUT Settings > Screen control pe jakar accessibility service on karo."
        return when (intent) {
            is Intent.Back -> if (svc.back()) "Done - back." else blockedOrFail(svc)
            is Intent.Home -> if (svc.home()) "Done - home." else "Home nahi ho paya."
            is Intent.Recents -> if (svc.recents()) "Done - recent apps." else "Recent apps nahi khul paye."
            is Intent.Notifications -> if (svc.notifications()) "Done - notifications khol diye." else "Notifications nahi khul paye."
            is Intent.QuickSettings -> if (svc.quickSettings()) "Done - quick settings khul gayi." else "Quick settings nahi khul payi."
            is Intent.LockScreen -> if (svc.lockScreen()) "Done - screen lock." else "Is phone par lock action nahi chala (Android 9+ chahiye ya phone ne roka)."
            is Intent.SystemScreenshot -> if (svc.systemScreenshot()) "Screenshot ka command diya. Phone ne save kiya ya nahi, gallery mein check karna." else "Screenshot command nahi chala."
            is Intent.LongPress -> if (!svc.readableAndSafe()) refusal(svc)
                else if (svc.longPressNamed(intent.target)) "Done - '${intent.target}' long press kiya." else "'${intent.target}' long press ke liye nahi mila."
            is Intent.TypeText -> if (!svc.readableAndSafe()) refusal(svc)
                else if (svc.typeIntoFocused(intent.text)) "Done - type kar diya. Send nahi kiya." else "Koi normal text box focus mein nahi mila."
            is Intent.WaitFor -> if (!svc.readableAndSafe()) refusal(svc)
                else if (svc.waitForText(intent.text, 8000)) "'${intent.text}' screen par aa gaya." else "8 second mein '${intent.text}' nahi dikha."
            is Intent.Swipe -> if (!svc.readableAndSafe()) refusal(svc)
                else if (svc.swipe(intent.dir)) "Done - swipe ${intent.dir}." else "Swipe nahi chala." 
            is Intent.TapXY -> if (!svc.readableAndSafe()) refusal(svc)
                else if (!svc.pointIsResolvedAndHarmless(intent.x, intent.y)) UNRESOLVED
                else if (svc.tapXY(intent.x, intent.y, intent.double)) "Gesture bheja (${intent.x.toInt()}, ${intent.y.toInt()}). App ne kya kiya wo screen par dekho." else "Gesture nahi chala."
            is Intent.DragXY -> if (!svc.readableAndSafe()) refusal(svc)
                else if (!svc.pointIsResolvedAndHarmless(intent.x1, intent.y1) || !svc.pointIsResolvedAndHarmless(intent.x2, intent.y2)) UNRESOLVED
                else if (svc.dragXY(intent.x1, intent.y1, intent.x2, intent.y2)) "Drag gesture bheja. Screen par dekho." else "Drag nahi chala."
            is Intent.Pinch -> if (!svc.readableAndSafe()) refusal(svc)
                else if (svc.pinch(intent.zoomIn)) "Pinch ${if (intent.zoomIn) "in" else "out"} gesture bheja. Zoom hua ya nahi screen par dekho." else "Pinch nahi chala."
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
                if (!cloudAllowed) return CLOUD_OFF
                val raw = svc.screenTextOnDevice() ?: return blockedOrEmpty(svc)
                val redacted = ScreenSafety.redact(raw).take(CLOUD_TEXT_CAP)
                // Even with the saved permission, nothing leaves until the user approves THIS exact text.
                val preview = previewForApproval ?: return NO_PREVIEW
                preview(intent.question, redacted)
                "Confirm on screen - AI ko bhejne se pehle wo exact text dikhaunga jo jaayega."
            }
        }
    }

    /** "screen dekh kar batao: ..." / "look at my screen ..." -> the question. Separate from the text path and its own approval. */
    fun parseImageQuestion(raw: String): String? {
        val t = raw.trim().lowercase().trimEnd('.', '?', '!')
        val m = Regex("^(?:meri |meri wali )?(?:screen|screenshot) (?:ko )?dekh(?: ?kar| ?ke)? (?:batao|bata do|samjhao)[:,\\s]*(.*)$").find(t)
            ?: Regex("^look at (?:my|the) screen(?: and (?:tell me|explain)(?: what| about)?)?[:,\\s]*(.*)$").find(t) ?: return null
        return m.groupValues[1].trim().ifBlank { "Describe what is on this screen" }
    }
    const val IMAGE_OFF = "Screen ki image AI ko bhejna abhi band hai (uski alag permission baaki hai, text wali permission isme kaam nahi aati), isliye maine screenshot liya hi nahi."
    const val IMAGE_NO_CAPTURE = "Screenshot nahi mila - screen sensitive lag rahi hai, readable nahi hai, Android ne roka (secure screen), ya is phone par Accessibility ko screenshot permission ke saath dobara on karna hoga. Kuch nahi bheja."
    const val CLOUD_TEXT_CAP = 1200
    const val NO_PREVIEW = "Screen ka text AI ko bhejne se pehle har baar exact text dikhana zaroori hai, wo abhi nahi ho paya - isliye kuch nahi bheja."

    /** Reply after the user approved sending this exact text. Starts with the private-reply marker. */
    fun cloudAnswer(sent: String, answer: String) =
        "Screen ka text AI ko bheja gaya (tumhari approval ke baad, common patterns chhupa kar). Jo text bheja gaya:\n---\n" +
            sent + "\n---\nJawab: " + answer

    const val CLOUD_OFF =
        "Screen ka text AI ko bhejna abhi band hai - uski permission abhi baaki hai, isliye maine screen padhi hi nahi. 'Screen padho' bologe to main sirf is phone par padhunga, kahin bhejunga nahi."
    const val UNRESOLVED =
        "Is jagah par kya hai ye screen se pakka nahi hua (ya wo send/pay/delete jaisa ho sakta hai), isliye gesture nahi chalaya. Button ka naam bolo, jaise 'tap Settings'."
    const val UNREADABLE =
        "Abhi screen padhi nahi ja rahi (koi readable window nahi), isliye main kuch type ya gesture nahi karunga."
    private fun refusal(svc: RoutAccessibilityService) = if (svc.isSensitiveScreen()) BLOCKED else UNREADABLE

    private const val BLOCKED =
        "Ye screen sensitive lag rahi hai (password, OTP ya payment). Main is screen ko na padhunga na is pe kuch karunga."

    private fun blockedOrFail(svc: RoutAccessibilityService) =
        if (svc.isSensitiveScreen()) BLOCKED else "Action nahi ho paya."

    private fun blockedOrEmpty(svc: RoutAccessibilityService) =
        if (svc.isSensitiveScreen()) BLOCKED else "Screen ka text nahi mil paya."
}
