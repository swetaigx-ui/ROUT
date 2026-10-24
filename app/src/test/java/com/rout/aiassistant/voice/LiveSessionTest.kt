package com.rout.aiassistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSessionTest {
    @Test fun httpsBecomesWss() =
        assertEquals("wss://x.workers.dev/v1/live", LiveSession.wsUrl("https://x.workers.dev/"))
    @Test fun httpBecomesWs() =
        assertEquals("ws://10.0.2.2:8787/v1/live", LiveSession.wsUrl("http://10.0.2.2:8787"))
}
