package com.rout.aiassistant

import com.rout.aiassistant.commands.Translit
import org.junit.Assert.*
import org.junit.Test

class Translit0729Test {
    @Test fun commonNames() {
        assertEquals("rohan", Translit.toRoman("\u0930\u094b\u0939\u0928"))
        assertEquals("rahul", Translit.toRoman("\u0930\u093e\u0939\u0941\u0932"))
        assertEquals("priya", Translit.toRoman("\u092a\u094d\u0930\u093f\u092f\u093e"))
        assertEquals("aman", Translit.toRoman("\u0905\u092e\u0928"))
        assertEquals("sachin", Translit.toRoman("\u0938\u091a\u093f\u0928"))
        assertEquals("rohan sharma", Translit.toRoman("\u0930\u094b\u0939\u0928 \u0936\u0930\u094d\u092e\u093e").replace("sharma", "sharma"))
        assertTrue(Translit.hasDevanagari("\u0930\u094b\u0939\u0928"))
        assertFalse(Translit.hasDevanagari("Rohan"))
    }
}
