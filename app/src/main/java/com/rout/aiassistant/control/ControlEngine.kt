package com.rout.aiassistant.control

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

enum class ControlState { IDLE, PLANNING, WAITING_FOR_CONFIRMATION, EXECUTING, VERIFYING, COMPLETED, FAILED, CANCELLED }
enum class Verify { VERIFIED, DISPATCHED, FAILED }
data class StepResult(val ok: Boolean, val message: String, val verify: Verify = if (ok) Verify.DISPATCHED else Verify.FAILED)

interface ActionExecutor {
    suspend fun execute(a: ControlAction): StepResult
    /** Observe the phone after the action. Default: whatever execute() already knows. */
    suspend fun verify(a: ControlAction, r: StepResult): Verify = r.verify
    /** Human line for the confirmation prompt: exact recipient/number/words where relevant. */
    suspend fun describe(a: ControlAction): String = a.type + " " + a.args.values.joinToString(" ")
}

data class ControlSnapshot(
    val state: ControlState = ControlState.IDLE,
    val requestId: String = "",
    val step: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val message: String = "",
    val log: List<String> = emptyList()
)

sealed class ControlOutcome {
    data class Done(val state: ControlState, val message: String, val verified: Int, val dispatched: Int, val failed: Int) : ControlOutcome()
    data class Rejected(val reason: String) : ControlOutcome()
}

/**
 * Runs a validated plan one action at a time: confirm (when needed) -> execute -> verify -> next. The phone side stays the authority:
 * the plan can ADD confirmation but never remove it, HIGH-risk actions are confirmed individually, Stop prevents every later step,
 * a request id runs at most once, and every state change lands in [snapshot] for the UI.
 */
class ControlEngine(
    private val executor: ActionExecutor,
    /** Ask the user; true = confirmed. Must return false on cancel/timeout. */
    private val confirm: suspend (String) -> Boolean,
    private val stepTimeoutMs: Long = 15_000L,
    private val verifyTimeoutMs: Long = 4_000L
) {
    private val _snap = MutableStateFlow(ControlSnapshot())
    val snapshot: StateFlow<ControlSnapshot> get() = _snap
    private val seen = LinkedHashSet<String>()
    @Volatile private var running = false
    @Volatile private var stopped = false
    @Volatile private var stepJob: Deferred<*>? = null
    @Volatile private var pendingConfirm: CompletableDeferred<Boolean>? = null

    private fun log(line: String): List<String> = (_snap.value.log + line).takeLast(30)
    private fun set(state: ControlState, msg: String = _snap.value.message, step: Int = _snap.value.step, cur: String = _snap.value.current, line: String? = null) {
        _snap.value = _snap.value.copy(state = state, message = msg, step = step, current = cur, log = if (line != null) log(line) else _snap.value.log)
    }

    fun isRunning() = running
    fun planning(requestId: String) {
        if (running) return
        _snap.value = ControlSnapshot(ControlState.PLANNING, requestId, message = "Command samajh rahi hoon", log = listOf("Planning"))
    }
    fun planningFailed(reason: String) { if (!running) _snap.value = _snap.value.copy(state = ControlState.FAILED, message = reason, log = log("Plan: $reason")) }
    fun reset() { if (!running) _snap.value = ControlSnapshot() }

    /** Stop Automation: no later step runs, the current one is cancelled, a waiting confirmation is declined. */
    fun stop() {
        if (!running) { if (_snap.value.state == ControlState.PLANNING) _snap.value = ControlSnapshot(ControlState.CANCELLED, _snap.value.requestId, message = "Roka gaya.", log = _snap.value.log + "Stop"); return }
        stopped = true
        pendingConfirm?.complete(false)
        stepJob?.cancel()
    }

    suspend fun run(plan: ControlPlan): ControlOutcome {
        synchronized(this) {
            if (running) return ControlOutcome.Rejected("Pehla automation abhi chal raha hai. Pehle Stop karo.")
            if (plan.requestId in seen) return ControlOutcome.Rejected("Ye request pehle chal chuki hai (duplicate), isliye dobara nahi chalayi.")
            seen.add(plan.requestId); if (seen.size > 128) seen.remove(seen.first())
            running = true; stopped = false
        }
        _snap.value = ControlSnapshot(ControlState.EXECUTING, plan.requestId, 0, plan.actions.size, "", "Shuru", listOf("Plan ${plan.requestId}: ${plan.actions.size} action"))
        var verified = 0; var dispatched = 0; var failed = 0
        var halted: String? = null
        try {
            var planConfirmed = !plan.requiresConfirmation
            for ((i, a) in plan.actions.withIndex()) {
                if (stopped) { halted = "Stop"; break }
                val n = i + 1
                val risk = ActionRegistry.effectiveRisk(a)
                if (!planConfirmed || risk == Risk.HIGH) {
                    val desc = executor.describe(a)
                    set(ControlState.WAITING_FOR_CONFIRMATION, "Confirm karo: $desc", n, a.type, "Step $n confirm: $desc")
                    val d = CompletableDeferred<Boolean>(); pendingConfirm = d
                    val ok = if (stopped) false else coroutineScope {
                        val c = async { confirm(desc) }
                        val r = try { select(c, d) } finally { pendingConfirm = null }
                        r
                    }
                    if (!ok) { halted = if (stopped) "Stop" else "Confirm nahi hua"; set(ControlState.CANCELLED, "Confirm nahi hua, kuch nahi chala.", n, a.type, "Step $n declined"); break }
                    if (risk != Risk.HIGH) planConfirmed = true
                }
                if (stopped) { halted = "Stop"; break }
                set(ControlState.EXECUTING, "Chal raha hai: ${a.type}", n, a.type, "Step $n ${a.type}")
                val res: StepResult? = try {
                    coroutineScope {
                        val job = async { withTimeoutOrNull(stepTimeoutMs) { executor.execute(a) } }
                        stepJob = job
                        try { job.await() ?: StepResult(false, "Time out: ${a.type} 15 second mein nahi hua.") } finally { stepJob = null }
                    }
                } catch (e: CancellationException) { if (stopped) null else throw e }
                if (res == null || stopped) { halted = "Stop"; break }
                set(ControlState.VERIFYING, "Check kar rahi hoon", n, a.type)
                val v = if (!res.ok) Verify.FAILED else (withTimeoutOrNull(verifyTimeoutMs) { executor.verify(a, res) } ?: res.verify)
                when (v) { Verify.VERIFIED -> verified++; Verify.DISPATCHED -> dispatched++; Verify.FAILED -> failed++ }
                set(ControlState.EXECUTING, res.message, n, a.type, "Step $n ${v.name.lowercase()}: ${res.message}")
                if (v == Verify.FAILED) { halted = "Step $n fail: ${res.message}"; break }
            }
        } finally { running = false }
        val total = plan.actions.size
        return if (stopped) {
            val m = "Roka gaya. Baaki steps nahi chale (${verified + dispatched} of $total ho chuke the)."
            set(ControlState.CANCELLED, m, line = "Cancelled"); ControlOutcome.Done(ControlState.CANCELLED, m, verified, dispatched, failed)
        } else if (halted != null && _snap.value.state == ControlState.CANCELLED) {
            ControlOutcome.Done(ControlState.CANCELLED, _snap.value.message, verified, dispatched, failed)
        } else if (halted != null) {
            val m = "$halted. ${verified + dispatched} of $total steps hue, baaki nahi chale."
            set(ControlState.FAILED, m, line = "Failed"); ControlOutcome.Done(ControlState.FAILED, m, verified, dispatched, failed)
        } else {
            val m = "$total of $total steps chale: $verified screen/system se check hue, $dispatched sirf bheje gaye (phone ne kya kiya, tum dekh lena)."
            set(ControlState.COMPLETED, m, line = "Completed"); ControlOutcome.Done(ControlState.COMPLETED, m, verified, dispatched, failed)
        }
    }

    private suspend fun select(c: Deferred<Boolean>, d: CompletableDeferred<Boolean>): Boolean = coroutineScope {
        val winner = CompletableDeferred<Boolean>()
        val j1 = async { winner.complete(c.await()) }
        val j2 = async { winner.complete(d.await()) }
        try { winner.await() } finally { j1.cancel(); j2.cancel(); c.cancel() }
    }
}
