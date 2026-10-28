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

/** Per-request approval of the exact sanitized text, even after the saved permission is on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenCloudPreview0724Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    @org.junit.After fun reset() { runBlocking { Prefs(ctx).setScreenCloudAllowed(false) } }
    private val armed = ArrayList<ConfirmRequest>()
    private val router by lazy {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoutDatabase::class.java).allowMainThreadQueries().build()
        CommandRouter(ctx, db, Prefs(ctx), { ProviderManager("", "", false, null) }, { emptyList() }, { armed.add(it) })
    }
    private fun svc(text: String?, sensitive: Boolean = false): RoutAccessibilityService {
        val s = Robolectric.setupService(RoutAccessibilityService::class.java)
        s.connectForTest(); s.readableForTest = true; s.screenTextForTest = text
        s.sensitiveOverrideForTest = { sensitive }
        return s
    }

    @Test fun savedPermissionStillOnlyArmsAPreviewAndSendsNothing() = runBlocking {
        Prefs(ctx).setScreenCloudAllowed(true)
        val s = svc("Rohan: mera code 482913 hai, call 9876543210, mail a@b.com, password hunter two")
        try {
            val r = router.handle("ask ai about my screen: summary", localOnly = true)
            assertTrue(r, r.contains("Confirm on screen"))
            assertEquals(1, armed.size)
            val d = armed[0].description
            assertTrue(d.contains("[code]") && d.contains("[phone]") && d.contains("[email]"))
            assertFalse(d.contains("482913") || d.contains("9876543210") || d.contains("a@b.com"))
            assertTrue("honest caveat about letters", d.contains("password") && d.contains("Cancel"))
        } finally { s.onDestroy() }
    }

    @Test fun sensitiveScreenArmsNothingAndLeavesNothing() = runBlocking {
        Prefs(ctx).setScreenCloudAllowed(true)
        val s = svc("Enter your password", sensitive = true)
        try {
            val r = router.handle("ask ai about my screen: summary", localOnly = true)
            assertTrue(r, r.contains("sensitive"))
            assertTrue(armed.isEmpty())
        } finally { s.onDestroy() }
    }

    @Test fun noPreviewCallbackFailsClosed() = runBlocking {
        val s = svc("hello")
        try {
            var asked = false
            val r = ScreenControl.execute(ScreenControl.Intent.AskAiAboutScreen("q"), { _, _ -> asked = true; "x" }, true)
            assertFalse(asked); assertEquals(ScreenControl.NO_PREVIEW, r)
        } finally { s.onDestroy() }
    }

    @Test fun permissionOffStillRefusesBeforeReadingScreen() = runBlocking {
        Prefs(ctx).setScreenCloudAllowed(false)
        val s = svc("hello")
        try {
            assertEquals(ScreenControl.CLOUD_OFF, router.handle("ask ai about my screen: x", localOnly = true))
            assertTrue(armed.isEmpty())
        } finally { s.onDestroy() }
    }
}
