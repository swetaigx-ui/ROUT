package com.rout.aiassistant.commands

import org.junit.Assert.assertEquals
import org.junit.Test

class HindiNormalTest {
    @Test fun weather() { assertEquals("mausam batao", HindiNormal.latinize("\u092e\u094c\u0938\u092e \u092c\u0924\u093e\u0913")) }
    @Test fun kitneBaje() { assertEquals("kitne baje", HindiNormal.latinize("\u0915\u093f\u0924\u0928\u0947 \u092c\u091c\u0947")) }
    @Test fun alarm() { assertEquals("alarm lagao 7 baje", HindiNormal.latinize("\u0905\u0932\u093e\u0930\u094d\u092e \u0932\u0917\u093e\u0913 7 \u092c\u091c\u0947")) }
    @Test fun torch() { assertEquals("batti chalu karo", HindiNormal.latinize("\u092c\u0924\u094d\u0924\u0940 \u091a\u093e\u0932\u0942 \u0915\u0930\u094b")) }
    @Test fun news() { assertEquals("news sunao", HindiNormal.latinize("\u0916\u092c\u0930 \u0938\u0941\u0928\u093e\u0913")) }
    @Test fun callKeepsName() {
        val out = HindiNormal.latinize("\u0930\u093e\u0939\u0941\u0932 \u0915\u094b \u0915\u0949\u0932 \u0915\u0930\u094b")
        assertEquals("\u0930\u093e\u0939\u0941\u0932 ko call karo", out)
    }
    @Test fun latinUntouched() { assertEquals("youtube kholo", HindiNormal.latinize("youtube kholo")) }
    @Test fun freeTextUntouched() { assertEquals("\u0930\u093e\u092e\u093e\u092f\u0923 \u0915\u0939\u093e\u0928\u0940", HindiNormal.latinize("\u0930\u093e\u092e\u093e\u092f\u0923 \u0915\u0939\u093e\u0928\u0940")) }
}
