package com.rout.aiassistant.commands

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The single pending-confirmation slot. Policy lives here (not in the ViewModel) so it can be tested with a fake clock:
 *  - arming replaces any older request, so an old preview can never be confirmed;
 *  - an image or screen-text preview needs the on-screen Confirm button: a spoken "haan" is refused and the request stays;
 *  - such previews expire after [IMAGE_TTL_MS]; expiry drops the reference (and the image bytes), it does not just refuse later;
 *  - cancel/STOP clear the slot.
 */
class ConfirmSlot(private val now: () -> Long = { System.currentTimeMillis() }) {
    val state = MutableStateFlow<ConfirmRequest?>(null)
    private var armedAt = 0L

    sealed class Take {
        object None : Take()
        object NeedsButton : Take()
        object Expired : Take()
        data class Run(val req: ConfirmRequest) : Take()
    }

    companion object { const val IMAGE_TTL_MS = 90_000L }

    fun requiresButton(r: ConfirmRequest? = state.value): Boolean = r != null && (r.imageJpeg != null || r.id == "screen-cloud")

    fun arm(req: ConfirmRequest) { state.value = req; armedAt = now() }

    /** Drops an overdue preview. Returns true if something was dropped. */
    fun expireIfDue(): Boolean {
        val r = state.value ?: return false
        if (requiresButton(r) && now() - armedAt >= IMAGE_TTL_MS) { state.value = null; return true }
        return false
    }

    fun take(viaVoice: Boolean): Take {
        if (expireIfDue()) return Take.Expired
        val r = state.value ?: return Take.None
        if (viaVoice && requiresButton(r)) return Take.NeedsButton
        state.value = null
        return Take.Run(r)
    }

    fun clear() { state.value = null }
}
