package com.rout.aiassistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenTargetTest {
    @Test fun romanizedSuffix() { assertEquals("youtube", extractOpenTarget("YouTube kholo")) }
    @Test fun romanizedSuffixDoNa() { assertEquals("whatsapp", extractOpenTarget("whatsapp khol do na")) }
    @Test fun romanizedPrefix() { assertEquals("chrome", extractOpenTarget("kholo chrome")) }
    @Test fun chalao() { assertEquals("translate", extractOpenTarget("translate chalao")) }
    @Test fun devanagariSuffix() { assertEquals("यूट्यूब", extractOpenTarget("यूट्यूब ओपन करो")) }
    @Test fun devanagariQuestionForm() { assertEquals("यूट्यूब", extractOpenTarget("क्या तुम यूट्यूब ओपन करो ओके")) }
    @Test fun devanagariHello() { assertEquals("यूट्यूब", extractOpenTarget("हैलो यूट्यूब ओपन करो")) }
    @Test fun devanagariKholDo() { assertEquals("व्हाट्सएप", extractOpenTarget("व्हाट्सएप खोल दो")) }
    @Test fun devanagariPrefix() { assertEquals("कैमरा", extractOpenTarget("ओपन करो कैमरा")) }
    @Test fun englishQuestion() { assertEquals("youtube", extractOpenTarget("can you youtube open karo please")) }
    @Test fun searchIsNotOpen() { assertNull(extractOpenTarget("youtube search karo")) }
    @Test fun youtubeVideoQueryIsNotOpen() { assertNull(extractOpenTarget("youtube par cat videos")) }
    @Test fun plainSentenceIsNotOpen() { assertNull(extractOpenTarget("aaj mausam kaisa hai")) }

    @Test fun devanagariNameMapsToAlias() { assertEquals("youtube", DeviceActions.normalizeAppName("यूट्यूब")) }
    @Test fun devanagariWhatsappMaps() { assertEquals("whatsapp", DeviceActions.normalizeAppName("व्हाट्सएप")) }
    @Test fun latinPassesThrough() { assertEquals("spotify", DeviceActions.normalizeAppName("spotify")) }
    @Test fun unknownDevanagariPassesThrough() { assertEquals("रामायण", DeviceActions.normalizeAppName("रामायण")) }
}
