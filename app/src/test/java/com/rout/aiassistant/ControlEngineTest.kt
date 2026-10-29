package com.rout.aiassistant

import com.rout.aiassistant.control.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControlEngineTest {
    private fun plan(id: String, vararg a: String) = """{"requestId":"$id","actions":[${a.joinToString(",")}]}"""
    private fun act(t: String, target: String? = null, params: String = "{}") =
        """{"type":"$t"${if (target != null) ",\"target\":\"$target\"" else ""},"parameters":$params}"""
    private fun valid(j: String) = (PlanValidator.parse(j) as PlanResult.Valid).plan
    private fun invalid(j: String) = (PlanValidator.parse(j) as PlanResult.Invalid).reason

    class Fake(val log: MutableList<String> = ArrayList(), val verdict: Verify = Verify.DISPATCHED, val failOn: String? = null, val slowOn: String? = null) : ActionExecutor {
        override suspend fun execute(a: ControlAction): StepResult {
            log.add(a.type)
            if (a.type == slowOn) delay(60_000)
            return if (a.type == failOn) StepResult(false, "fail") else StepResult(true, "ok " + a.type, verdict)
        }
    }

    @Test fun validatesExampleFromSpec() {
        val p = valid("""{"requestId":"r-1","actions":[{"type":"app.open","target":"YouTube","parameters":{}},{"type":"web.search","target":"phonk music","parameters":{}}],"requiresConfirmation":false}""")
        assertEquals(listOf("app.open", "web.search"), p.actions.map { it.type })
        assertEquals("YouTube", p.actions[0].arg("target"))
    }

    @Test fun rejectsUnknownMalformedAndUnsupported() {
        assertTrue(invalid("not json").isNotEmpty())
        assertTrue(invalid(plan("r1", act("shell.exec", "rm -rf /"))).contains("registered nahi"))
        assertTrue(invalid(plan("r2", act("file.delete", "/sdcard/a"))).contains("supported nahi"))
        assertTrue(invalid(plan("r3", act("file.rename", "/a", """{"text":"b"}"""))).contains("supported nahi"))
        assertTrue(invalid("""{"requestId":"r4","actions":[{"type":"screen.home","code":"x"}]}""").contains("unknown field"))
        assertTrue(invalid("""{"requestId":"r5","actions":[{"type":"screen.home"}],"extra":1}""").contains("Unknown field"))
        assertTrue(invalid("""{"requestId":"bad id!","actions":[{"type":"screen.home"}]}""").contains("requestId"))
        assertTrue(invalid("""{"requestId":"r6","actions":[]}""").isNotEmpty())
        assertTrue(invalid(plan("r7", act("screen.scroll", params = """{"direction":"sideways"}"""))).contains("allowed nahi"))
        assertTrue(invalid(plan("r8", act("web.open_url", "javascript:alert(1)"))).contains("http"))
        assertTrue(invalid(plan("r9", act("settings.open", "security"))).contains("allowed nahi"))
        assertTrue(invalid(plan("r10", act("app.open", "x".repeat(100)))).contains("lamba"))
        assertTrue(invalid(plan("r11", act("screen.home", params = """{"x":"y"}"""))).contains("allowed nahi"))
        assertTrue(invalid("""{"requestId":"r12","requiresConfirmation":"no","actions":[{"type":"screen.home"}]}""").contains("boolean"))
        val nine = (1..9).map { act("screen.home") }.toTypedArray()
        assertTrue(invalid(plan("r13", *nine)).contains("1 se"))
    }

    @Test fun notificationDismissNeedsExplicitAll() {
        assertTrue(invalid(plan("n1", act("notification.dismiss"))).contains("chahiye"))
        assertTrue(invalid(plan("n2", act("notification.dismiss", params = """{"scope":"one"}"""))).contains("allowed nahi"))
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(valid(plan("n3", act("notification.dismiss", params = """{"scope":"all"}"""))).actions[0]))
    }

    @Test fun riskClassification() {
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(valid(plan("a", act("message.send", "rohan", """{"text":"hi"}"""))).actions[0]))
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(valid(plan("b", act("call.initiate", "rohan"))).actions[0]))
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(valid(plan("c", act("ui.tap", params = """{"text":"Send"}"""))).actions[0]))
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(valid(plan("d", act("ui.tap", params = """{"text":"Pay now"}"""))).actions[0]))
        assertEquals(Risk.MEDIUM, ActionRegistry.effectiveRisk(valid(plan("e", act("ui.tap", params = """{"text":"Reels"}"""))).actions[0]))
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(valid(plan("g", act("ui.tap", params = """{"text":"Like"}"""))).actions[0]))
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(valid(plan("h", act("call.end"))).actions[0]))
        assertEquals(Risk.LOW, ActionRegistry.effectiveRisk(valid(plan("f", act("device.flashlight_on"))).actions[0]))
        assertTrue(ActionRegistry.unsupportedTypes.containsAll(listOf("file.delete", "file.rename", "file.open")))
    }

    @Test fun runsOneAtATimeAndReportsHonestly() = runBlocking {
        val f = Fake()
        val e = ControlEngine(f, { true })
        val o = e.run(valid(plan("p1", act("screen.home"), act("device.flashlight_on")))) as ControlOutcome.Done
        assertEquals(ControlState.COMPLETED, o.state); assertEquals(listOf("screen.home", "device.flashlight_on"), f.log)
        assertEquals(2, o.dispatched); assertEquals(0, o.verified)
        assertTrue(o.message, o.message.contains("sirf bheje gaye"))
        assertEquals(ControlState.COMPLETED, e.snapshot.value.state)
    }

    @Test fun highRiskNeedsConfirmEvenIfPlanSaysNo() = runBlocking {
        val f = Fake(); val asked = ArrayList<String>()
        val e = ControlEngine(f, { asked.add(it); false })
        val o = e.run(valid("""{"requestId":"p2","requiresConfirmation":false,"actions":[{"type":"message.send","target":"rohan","parameters":{"text":"hi"}}]}""")) as ControlOutcome.Done
        assertEquals(ControlState.CANCELLED, o.state); assertTrue(f.log.isEmpty()); assertEquals(1, asked.size)
    }

    @Test fun planCanAddConfirmationButLowRiskStillAsksOnce() = runBlocking {
        val f = Fake(); var n = 0
        val e = ControlEngine(f, { n++; true })
        e.run(valid("""{"requestId":"p3","requiresConfirmation":true,"actions":[{"type":"screen.home"},{"type":"screen.back"}]}"""))
        assertEquals(1, n); assertEquals(2, f.log.size)
    }

    @Test fun failureHaltsLaterSteps() = runBlocking {
        val f = Fake(failOn = "screen.back")
        val o = ControlEngine(f, { true }).run(valid(plan("p4", act("screen.home"), act("screen.back"), act("device.flashlight_on")))) as ControlOutcome.Done
        assertEquals(ControlState.FAILED, o.state); assertEquals(listOf("screen.home", "screen.back"), f.log)
    }

    @Test fun duplicateRequestIdRunsOnce() = runBlocking {
        val f = Fake(); val e = ControlEngine(f, { true })
        e.run(valid(plan("dup", act("screen.home"))))
        val second = e.run(valid(plan("dup", act("screen.home"))))
        assertTrue(second is ControlOutcome.Rejected); assertEquals(1, f.log.size)
    }

    @Test fun stopCancelsCurrentAndPreventsLaterSteps() = runBlocking {
        val f = Fake(slowOn = "screen.back"); val e = ControlEngine(f, { true })
        val job = async { e.run(valid(plan("p5", act("screen.home"), act("screen.back"), act("device.flashlight_on")))) }
        while (!f.log.contains("screen.back")) delay(20)
        e.stop()
        val o = job.await() as ControlOutcome.Done
        assertEquals(ControlState.CANCELLED, o.state); assertEquals(listOf("screen.home", "screen.back"), f.log)
        assertFalse(e.isRunning())
    }

    @Test fun stopWhileWaitingForConfirmationDeclines() = runBlocking {
        val f = Fake(); val gate = CompletableDeferred<Boolean>()
        val e = ControlEngine(f, { gate.await() })
        val job = async { e.run(valid(plan("p6", act("message.send", "rohan", """{"text":"hi"}""")))) }
        while (e.snapshot.value.state != ControlState.WAITING_FOR_CONFIRMATION) delay(20)
        e.stop()
        val o = job.await() as ControlOutcome.Done
        assertEquals(ControlState.CANCELLED, o.state); assertTrue(f.log.isEmpty())
    }

    @Test fun timeoutIsAFailureNotAHang() = runBlocking {
        val f = Fake(slowOn = "screen.home")
        val o = ControlEngine(f, { true }, stepTimeoutMs = 200).run(valid(plan("p7", act("screen.home"), act("screen.back")))) as ControlOutcome.Done
        assertEquals(ControlState.FAILED, o.state); assertEquals(listOf("screen.home"), f.log)
    }

    @Test fun verifiedCountedSeparately() = runBlocking {
        val o = ControlEngine(Fake(verdict = Verify.VERIFIED), { true }).run(valid(plan("p8", act("app.open", "YouTube")))) as ControlOutcome.Done
        assertEquals(1, o.verified); assertEquals(0, o.dispatched)
    }

    @Test fun planningStateAndSecondRunRejectedWhileRunning() = runBlocking {
        val f = Fake(slowOn = "screen.home"); val e = ControlEngine(f, { true })
        e.planning("x1"); assertEquals(ControlState.PLANNING, e.snapshot.value.state)
        val job = async { e.run(valid(plan("p9", act("screen.home")))) }
        while (!f.log.contains("screen.home")) delay(20)
        assertTrue(e.run(valid(plan("p10", act("screen.back")))) is ControlOutcome.Rejected)
        e.stop(); job.await()
        Unit
    }

    private fun exec(routine: (suspend (String) -> Pair<Boolean, String>)? = null,
                     notes: Map<String, Pair<String, List<String>>>? = emptyMap(), dismissed: MutableList<String> = ArrayList()) =
        AndroidExecutor(androidx.test.core.app.ApplicationProvider.getApplicationContext(), svc = { null }, contactRows = { emptyList() },
            routineRunner = routine, appNotifications = { notes }, dismissKeys = { dismissed.addAll(it) })

    @Test fun dismissAppNeedsUniqueAppAndConfirmation() = runBlocking {
        val notes = mapOf("com.a" to ("WhatsApp" to listOf("k1", "k2")), "com.b" to ("WhatsApp Business" to listOf("k3")), "com.c" to ("Gmail" to listOf("k4")))
        val gone = ArrayList<String>()
        val ex = exec(notes = notes, dismissed = gone)
        val one = valid(plan("d1", act("notification.dismiss_app", "gmail"))).actions[0]
        assertEquals(Risk.HIGH, ActionRegistry.effectiveRisk(one))
        assertTrue(ex.describe(one).contains("Gmail ke 1 notifications"))
        assertTrue(ex.execute(one).ok); assertEquals(listOf("k4"), gone)
        // exact label wins over a prefix match
        gone.clear(); assertTrue(ex.execute(valid(plan("d2", act("notification.dismiss_app", "whatsapp"))).actions[0]).ok); assertEquals(listOf("k1", "k2"), gone)
        // ambiguous partial match: nothing dismissed
        gone.clear(); val amb = ex.execute(valid(plan("d3", act("notification.dismiss_app", "what"))).actions[0])
        assertFalse(amb.ok); assertTrue(gone.isEmpty())
        // unknown app and missing access
        assertFalse(ex.execute(valid(plan("d4", act("notification.dismiss_app", "tiktok"))).actions[0]).ok)
        assertFalse(exec(notes = null).execute(one).ok)
    }

    @Test fun workflowExecuteRunsExistingRoutineOnly() = runBlocking {
        val seen = ArrayList<String>()
        val ok = exec(routine = { n -> seen.add(n); (n == "good morning") to (if (n == "good morning") "Routine: good morning. ok" else "nahi mila") })
        val a = valid(plan("w1", act("workflow.execute", "good morning"))).actions[0]
        assertTrue(ok.execute(a).ok); assertEquals(listOf("good morning"), seen)
        assertFalse(ok.execute(valid(plan("w2", act("workflow.execute", "nonexistent"))).actions[0]).ok)
        assertFalse(exec(routine = null).execute(a).ok)
    }
}
