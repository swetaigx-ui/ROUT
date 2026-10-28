package com.rout.aiassistant

import androidx.test.core.app.ApplicationProvider
import com.rout.aiassistant.ai.PersonalitySettings
import com.rout.aiassistant.commands.ConfirmRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalityVm0729Test {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun newVm(): MainViewModel {
        com.rout.aiassistant.data.RoutDatabase.testInstance =
            androidx.room.Room.inMemoryDatabaseBuilder(app, com.rout.aiassistant.data.RoutDatabase::class.java).allowMainThreadQueries().build()
        val vm = MainViewModel(app)
        @Suppress("UNCHECKED_CAST")
        val cid = vm.javaClass.getDeclaredField("conversationId").apply { isAccessible = true }.get(vm) as MutableStateFlow<Long>
        repeat(50) { Thread.sleep(100); org.robolectric.shadows.ShadowLooper.idleMainLooper(); if (cid.value != 0L) return vm }
        return vm
    }
    private fun rows(): List<Pair<String, String>> {
        val c = com.rout.aiassistant.data.RoutDatabase.testInstance!!.openHelper.readableDatabase.query("SELECT role, text FROM messages ORDER BY id")
        val o = mutableListOf<Pair<String, String>>(); while (c.moveToNext()) o.add(c.getString(0) to c.getString(1)); c.close(); return o
    }
    private fun drain(until: () -> Boolean) { repeat(60) { if (until()) return; Thread.sleep(200); org.robolectric.shadows.ShadowLooper.idleMainLooper() } }

    @Test fun stopPersistsSuspendedClearsPendingConfirmAndResumeIsExplicit() = runBlocking {
        val vm = newVm()
        vm.setPersonality(PersonalitySettings(romanticOn = true, jealousy = 2))
        drain { vm.personality.value.romanticOn }
        val ran = booleanArrayOf(false)
        val f = vm.javaClass.getDeclaredField("confirmSlot").apply { isAccessible = true }
        (f.get(vm) as com.rout.aiassistant.commands.ConfirmSlot).arm(ConfirmRequest(id = "t", description = "Call?", action = { ran[0] = true; "x" }))
        vm.onUserText("OK OK, ruko", fromVoice = true)
        drain { vm.personality.value.suspended }
        assertTrue(vm.personality.value.suspended)
        assertEquals(null, vm.confirm.value)
        assertFalse("stop must not run the pending action", ran[0])
        assertTrue(rows().any { it.first == "rout" && it.second.contains("Ruk gayi") })
        // still suspended after a fresh read; only an explicit request lifts it
        vm.onUserText("kuch bhi", fromVoice = false)
        assertTrue(vm.personality.value.suspended)
        vm.onUserText("phir se romantic mode chalu karo", fromVoice = true)
        drain { !vm.personality.value.suspended }
        assertFalse(vm.personality.value.suspended)
        assertTrue(vm.personality.value.romanticEff)
    }

    @Test fun liveTurnStopSuspendsWithoutNameGate() = runBlocking {
        val vm = newVm()
        vm.setPersonality(PersonalitySettings(playfulAnger = true))
        drain { vm.personality.value.playfulAnger }
        vm.onLiveUserTurn("bas ab ruko")
        drain { vm.personality.value.suspended }
        assertTrue(vm.personality.value.suspended)
    }

    @Test fun calmPersonaNeverOverwritesStoredPersonalityChoices() = runBlocking {
        val vm = newVm()
        vm.setPersonality(PersonalitySettings(mode = com.rout.aiassistant.ai.PMode.WAIFU, romanticOn = true))
        vm.setPersona("professional")
        drain { vm.personality.value.romanticOn && vm.personaMode.value == "professional" }
        vm.onUserText("bas ab ruko", fromVoice = true)
        drain { vm.personality.value.suspended }
        assertTrue(vm.personality.value.suspended)
        assertEquals(com.rout.aiassistant.ai.PMode.WAIFU, vm.personality.value.mode)
        vm.onUserText("aur dramatic bano", fromVoice = true)
        drain { vm.personality.value.intensity == 2 || rows().any { it.second.contains("dramatic nahi ho sakti") } }
        assertEquals("stored mode must stay Waifu", com.rout.aiassistant.ai.PMode.WAIFU, vm.personality.value.mode)
        // a local romantic reply must not fire under the Professional persona
        vm.onUserText("romantic mode off", fromVoice = true)
        vm.onUserText("tum mujhse pyaar nahi karte", fromVoice = false)
        drain { false }
        assertFalse(rows().any { it.first == "rout" && it.second.contains("ilzaam") })
    }
}
