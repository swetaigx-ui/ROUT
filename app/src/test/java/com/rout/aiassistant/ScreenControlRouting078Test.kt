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

    @Test fun coordinateGesturesParseAndAreConfirmGated() = runBlocking {
        assertTrue(com.rout.aiassistant.commands.ScreenControl.parse("tap at 540 1200") is com.rout.aiassistant.commands.ScreenControl.Intent.TapXY)
        assertTrue((com.rout.aiassistant.commands.ScreenControl.parse("double tap at 10 20") as com.rout.aiassistant.commands.ScreenControl.Intent.TapXY).double)
        assertTrue(com.rout.aiassistant.commands.ScreenControl.parse("drag from 1 2 to 3 4") is com.rout.aiassistant.commands.ScreenControl.Intent.DragXY)
        val svc = Robolectric.setupService(RoutAccessibilityService::class.java)
        svc.connectForTest()
        try {
            for (c in listOf("tap at 540 1200", "drag from 100 100 to 300 300")) {
                val r = router.handle(c, localOnly = true)
                assertTrue("$c must ask confirmation, got $r", r.contains("permission") || r.contains("Confirm"))
                assertFalse(r.startsWith("Gesture") || r.startsWith("Drag"))
            }
            svc.sensitiveOverrideForTest = { true }
            assertFalse(svc.tapXY(5f, 5f)); assertFalse(svc.dragXY(1f, 1f, 2f, 2f)); assertFalse(svc.pinch(true))
            val r = router.handle("pinch in", localOnly = true)
            assertTrue(r, r.contains("sensitive"))
        } finally { svc.onDestroy() }
    }
}
