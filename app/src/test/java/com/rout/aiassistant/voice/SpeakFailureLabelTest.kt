package com.rout.aiassistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakFailureLabelTest {
    @Test fun quota429IsLabelledNotGeneric502() {
        val body = """{"error":"upstream_error","upstream_status":429,"upstream_reason":"RESOURCE_EXHAUSTED"}"""
        val label = BackendSpeaker.describeServerFailure(502, body)
        assertTrue(label, label.contains("quota") && label.contains("429"))
        assertTrue(BackendSpeaker.isQuota(label))
    }
    @Test fun otherUpstreamStatus() {
        val label = BackendSpeaker.describeServerFailure(502, """{"error":"upstream_error","upstream_status":500}""")
        assertTrue(label.contains("500")); assertTrue(!BackendSpeaker.isQuota(label))
    }
    @Test fun oldServerBodyFallsBackToHttpCode() {
        assertEquals("server HTTP 502", BackendSpeaker.describeServerFailure(502, """{"error":"upstream_error"}"""))
        assertEquals("device not authorised (HTTP 401)", BackendSpeaker.describeServerFailure(401, ""))
    }
}
