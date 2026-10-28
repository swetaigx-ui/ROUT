package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.data.Prefs
import com.rout.aiassistant.data.RoutDatabase
import com.rout.aiassistant.service.RoutAccessibilityService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 0.7.8: new screen commands really route to ScreenControl.execute, fail closed, and stay confirm-gated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenControlRouting078Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val router by lazy {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, {})
    }
    private val cmds = listOf("notifications kholo", "quick settings kholo", "screen lock karo", "screenshot lo",
        "long press message", "type karo hello", "wait for inbox", "swipe left")

    @Test fun serviceOffReportsHonestlyForEveryNewCommand() = runBlocking {
        Robolectric.setupService(RoutAccessibilityService::class.java).onDestroy()
        for (c in cmds) {
            val r = router.handle(c, localOnly = true)
            assertTrue("$c -> $r", r.contains("Screen control off"))
        }
    }

    @Test fun sensitiveScreenBlocksTextAndGestureCommands() = runBlocking {
        val svc = Robolectric.setupService(RoutAccessibilityService::class.java)
        svc.sensitiveOverrideForTest = { true }
        svc.connectForTest()
        try {
            for (c in listOf("long press message", "type karo hello", "wait for inbox", "swipe left")) {
                val r = router.handle(c, localOnly = true)
                assertTrue("$c must be blocked on sensitive screen, got $r", r.contains("sensitive"))
            }
            assertFalse(svc.typeIntoFocused("x"))
            assertFalse(svc.longPressNamed("send"))
        } finally { svc.onDestroy() }
    }

    @Test fun sendLikeLongPressNeedsConfirmationAndDoesNotRun() = runBlocking {
        val svc = Robolectric.setupService(RoutAccessibilityService::class.java)
        svc.connectForTest()
        try {
            val r = router.handle("long press send", localOnly = true)
            assertTrue("expected confirm prompt, got $r", r.contains("permission") || r.contains("Confirm"))
            assertFalse(r.startsWith("Done"))
        } finally { svc.onDestroy() }
    }

    private fun svcWith(readable: Boolean?, labels: ((Float, Float) -> List<String>?)?): RoutAccessibilityService {
        val svc = Robolectric.setupService(RoutAccessibilityService::class.java)
        svc.connectForTest(); svc.readableForTest = readable; svc.labelsAtForTest = labels
        return svc
    }

    @Test fun noReadableWindowFailsClosedForEverythingThatActs() = runBlocking {
        val svc = svcWith(false, { _, _ -> listOf("Settings") })
        try {
            assertFalse(svc.typeIntoFocused("x")); assertFalse(svc.longPressNamed("a")); assertFalse(svc.swipe("left"))
            assertFalse(svc.tapXY(5f, 5f)); assertFalse(svc.dragXY(1f, 1f, 2f, 2f)); assertFalse(svc.pinch(true))
            for (c in listOf("type karo hello", "long press message", "swipe left", "pinch in", "tap at 5 5", "drag from 1 1 to 2 2", "wait for inbox")) {
                val r = router.handle(c, localOnly = true)
                assertTrue("$c must refuse when unreadable, got $r", r.contains("readable window"))
                assertFalse("$c must not confirm/act: $r", r.contains("Confirm on screen") || r.startsWith("Done") || r.startsWith("Gesture"))
            }
        } finally { svc.onDestroy() }
    }

    @Test fun coordinateTargetUnresolvedOrConsequentialIsRefusedWithoutConfirm() = runBlocking {
        val cases = listOf<List<String>?>(null, emptyList(), listOf("Send"), listOf("Pay now"), listOf("btn_delete"), listOf("Inbox", "Send message"))
        for (labels in cases) {
            val svc = svcWith(true, { _, _ -> labels })
            try {
                for (c in listOf("tap at 540 1200", "double tap at 10 20", "drag from 100 100 to 300 300")) {
                    val r = router.handle(c, localOnly = true)
                    assertFalse("$c with $labels must not ask to confirm: $r", r.contains("Confirm on screen"))
                    assertTrue("$c with $labels must refuse: $r", r.contains("pakka nahi hua"))
                }
                assertFalse(svc.tapXY(5f, 5f)); assertFalse(svc.dragXY(1f, 1f, 2f, 2f))
            } finally { svc.onDestroy() }
        }
    }

    @Test fun coordinateTargetResolvedHarmlessAsksConfirmNamingTarget() = runBlocking {
        val svc = svcWith(true, { _, _ -> listOf("", "Settings") })
        try {
            val r = router.handle("tap at 540 1200", localOnly = true)
            assertTrue(r, r.contains("Confirm on screen"))
            assertFalse(r.startsWith("Gesture"))
            val sensitive = router.handle("pinch in", localOnly = true)
            assertFalse(sensitive.startsWith("Done"))
        } finally { svc.onDestroy() }
    }

    @Test fun askAiAboutScreenIsOffByDefaultAndNeverReadsScreen() = runBlocking {
        var asked = false
        val svc = svcWith(true, null)
        try {
            val r = com.rout.aiassistant.commands.ScreenControl.execute(
                com.rout.aiassistant.commands.ScreenControl.Intent.AskAiAboutScreen("kya hai"),
                { _, _ -> asked = true; "x" })
            assertFalse("AI was called while consent off", asked)
            assertEquals(com.rout.aiassistant.commands.ScreenControl.CLOUD_OFF, r)
            val viaRouter = router.handle("ask ai about my screen: summary", localOnly = true)
            assertEquals(com.rout.aiassistant.commands.ScreenControl.CLOUD_OFF, viaRouter)
            assertFalse(com.rout.aiassistant.data.Prefs(ctx).screenCloudAllowed())
        } finally { svc.onDestroy() }
    }
}
