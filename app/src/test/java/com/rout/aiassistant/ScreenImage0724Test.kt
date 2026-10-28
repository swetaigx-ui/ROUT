package com.rout.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.ProviderManager
import com.rout.aiassistant.commands.CommandRouter
import com.rout.aiassistant.commands.ConfirmRequest
import com.rout.aiassistant.commands.ScreenControl
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenImage0724Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val armed = ArrayList<ConfirmRequest>()
    private var captures = 0
    private var hist: List<com.rout.aiassistant.ai.ChatTurn> = emptyList()
    private var now = 1_000L
    private var fp: String? = "a"
    private var pkg: String? = "com.example.app"
    private var image: ByteArray? = byteArrayOf(1, 2, 3)
    private val router by lazy {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { hist }, { armed.add(it) },
            screenImage = { captures++; image }, foregroundPkg = { pkg }, nowMs = { now }, contentFingerprint = { fp })
    }
    @org.junit.After fun reset() { runBlocking { Prefs(ctx).setScreenImageAllowed(false); Prefs(ctx).setScreenCloudAllowed(false) } }
    private fun svc(sensitive: Boolean = false): RoutAccessibilityService {
        val s = Robolectric.setupService(RoutAccessibilityService::class.java)
        s.connectForTest(); s.readableForTest = true; s.sensitiveOverrideForTest = { sensitive }
        return s
    }

    @Test fun parse() {
        assertEquals("ye kya hai", ScreenControl.parseImageQuestion("screen dekh kar batao: ye kya hai"))
        assertEquals("Describe what is on this screen", ScreenControl.parseImageQuestion("screen dekh ke batao"))
        assertNotNull(ScreenControl.parseImageQuestion("look at my screen and tell me what this is"))
        assertNull(ScreenControl.parseImageQuestion("screenshot lo"))
        assertNull(ScreenControl.parseImageQuestion("screen padho"))
    }

    @Test fun offByDefaultTakesNoScreenshot() = runBlocking {
        val s = svc()
        try {
            assertEquals(ScreenControl.IMAGE_OFF, router.handle("screen dekh kar batao: kya hai", localOnly = true))
            assertEquals(0, captures); assertTrue(armed.isEmpty())
        } finally { s.onDestroy() }
    }

    @Test fun textApprovalDoesNotImplyImageApproval() = runBlocking {
        Prefs(ctx).setScreenCloudAllowed(true)
        val s = svc()
        try {
            assertEquals(ScreenControl.IMAGE_OFF, router.handle("screen dekh kar batao: kya hai", localOnly = true))
            assertEquals(0, captures)
        } finally { s.onDestroy() }
    }

    @Test fun onArmsPreviewWithTheActualImageAndSendsNothingYet() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true)
        val s = svc()
        try {
            val r = router.handle("screen dekh kar batao: kya hai", localOnly = true)
            assertTrue(r, r.contains("Confirm on screen"))
            assertEquals(1, armed.size)
            assertArrayEquals(byteArrayOf(1, 2, 3), armed[0].imageJpeg)
            assertTrue(armed[0].description.contains("password") && armed[0].description.contains("Cancel"))
        } finally { s.onDestroy() }
    }

    @Test fun noCaptureFailsClosed() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true); image = null
        val s = svc()
        try {
            assertEquals(ScreenControl.IMAGE_NO_CAPTURE, router.handle("screen dekh kar batao: x", localOnly = true))
            assertTrue(armed.isEmpty())
        } finally { s.onDestroy() }
    }

    @Test fun sensitiveScreenGivesNoImage() = runBlocking {
        val s = svc(sensitive = true)
        try {
            assertNull(s.screenshotJpeg())
        } finally { s.onDestroy() }
    }

    @Test fun switchedOffBeforeConfirmRefuses() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true)
        val s = svc()
        try {
            router.handle("screen dekh kar batao: x", localOnly = true)
            Prefs(ctx).setScreenImageAllowed(false)
            assertEquals(ScreenControl.IMAGE_OFF, armed[0].action())
        } finally { s.onDestroy() }
    }

    @Test fun screenChangedBeforeConfirmRefusesAndSendsNothing() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true)
        val s = svc()
        try {
            router.handle("screen dekh kar batao: x", localOnly = true)
            assertTrue(armed[0].description.contains("com.example.app"))
            pkg = "com.other.app"
            assertTrue(armed[0].action().contains("purani image nahi bheji"))
            pkg = ctx.packageName                               // confirming inside ROUT itself: previewed bytes are what is sent
            assertFalse(armed[0].action().contains("purani image nahi bheji"))
        } finally { s.onDestroy() }
    }

    @Test fun sensitiveAtConfirmRefuses() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true)
        var sens = false
        val s = Robolectric.setupService(RoutAccessibilityService::class.java)
        s.connectForTest(); s.readableForTest = true; s.sensitiveOverrideForTest = { sens }
        try {
            router.handle("screen dekh kar batao: x", localOnly = true)
            sens = true
            assertTrue(armed[0].action().contains("purani image nahi bheji"))
        } finally { s.onDestroy() }
    }

    @Test fun unreadableForegroundAtCaptureFailsClosed() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true); pkg = null
        val s = svc()
        try {
            assertEquals(ScreenControl.IMAGE_NO_CAPTURE, router.handle("screen dekh kar batao: x", localOnly = true))
            assertTrue(armed.isEmpty())
        } finally { s.onDestroy() }
    }

    @Test fun imageReplyNeverReachesLiveContextHistoryOrRecap() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true)
        val s = svc()
        try {
            router.handle("screen dekh kar batao: x", localOnly = true)
            val reply = armed[0].action()
            assertTrue(reply, reply.startsWith("Screenshot AI ko bheja gaya"))
            assertEquals("(private on-device result, not shared)", router.liveSafeResult(reply))
            hist = listOf(com.rout.aiassistant.ai.ChatTurn("user", "screen dekh kar batao: x"), com.rout.aiassistant.ai.ChatTurn("rout", reply))
            assertEquals("(private on-device result, not shared)", router.safeHistory().last().text)
            // marker alone (e.g. after an app restart, empty in-memory set) still classifies it as private
            assertTrue(router.isPrivateReply("Screenshot AI ko bheja gaya (tumhari approval ke baad). Jawab: koi bhi"))
        } finally { s.onDestroy() }
    }

    @Test fun sameAppContentChangeRefuses() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true)
        val s = svc()
        try {
            router.handle("screen dekh kar batao: x", localOnly = true)
            fp = "b"                                            // same package, different content
            assertTrue(armed[0].action().contains("purani image nahi bheji"))
        } finally { s.onDestroy() }
    }

    @Test fun expiredImageIsRefused() = runBlocking {
        Prefs(ctx).setScreenImageAllowed(true)
        val s = svc()
        try {
            router.handle("screen dekh kar batao: x", localOnly = true)
            now += CommandRouter.IMAGE_CONFIRM_TTL_MS + 1
            assertTrue(armed[0].action().contains("90 second"))
        } finally { s.onDestroy() }
    }
}
