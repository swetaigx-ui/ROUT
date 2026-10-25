package com.rout.aiassistant

import com.rout.aiassistant.ai.SharedImage
import com.rout.aiassistant.commands.VisionDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionDocTest {
    private val img = SharedImage("AAAA", "image/jpeg")

    @Test fun partialPdfDisclosesShownOfTotal() {
        val d = VisionDoc("pdf", "lease.pdf", totalPages = 12, shownPages = 5, images = listOf(img))
        assertEquals("[Pehle 5/12 panne dekh ke]", d.disclosure())
    }

    @Test fun fullPdfDisclosesFullCoverage() {
        val d = VisionDoc("pdf", "short.pdf", totalPages = 3, shownPages = 3, images = listOf(img))
        assertEquals("[Poore 3 panne dekh ke]", d.disclosure())
    }

    @Test fun photoHasNoPageDisclosure() {
        val d = VisionDoc("photo", "photo", 1, 1, listOf(img))
        assertTrue(d.disclosure().isEmpty())
    }
}
