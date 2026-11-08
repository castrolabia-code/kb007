package com.kb007.recorder

import com.kb007.recorder.wakeword.HeardWord
import com.kb007.recorder.wakeword.KeywordMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {
    private val json = """{"result":[{"conf":0.62,"end":1.2,"start":0.9,"word":"[unk]"},
        {"conf":0.97,"end":1.8,"start":1.3,"word":"atención"},{"conf":0.91,"end":2.4,"start":1.9,"word":"grabadora"}],"text":"[unk] atención grabadora"}"""

    @Test fun parsesWordsWithConfidence() {
        val w = KeywordMatcher.parse(json)
        assertEquals(3, w.size)
        assertEquals("atención", w[1].word)
        assertEquals(0.97f, w[1].conf, 1e-4f)
    }

    @Test fun findsPhraseIgnoringAccentsAndCase() {
        val w = KeywordMatcher.parse(json)
        assertEquals(0.94f, KeywordMatcher.find(w, "Atencion  GRABADORA")!!, 1e-3f)
        assertEquals(0.91f, KeywordMatcher.find(w, "grabadora")!!, 1e-3f)
        assertNull(KeywordMatcher.find(w, "grabadora atención")) // orden importa
        assertNull(KeywordMatcher.find(w, "basta"))
    }

    @Test fun partialAndTextOnlyResults() {
        val p = KeywordMatcher.parse("""{"partial":"hola grabadora"}""")
        assertEquals(listOf(HeardWord("hola", 1f), HeardWord("grabadora", 1f)), p)
        assertTrue(KeywordMatcher.parse("""{"text":""}""").isEmpty())
        assertTrue(KeywordMatcher.parse("no es json").isEmpty())
    }

    @Test fun grammarJsonIncludesUnknownToken() {
        assertEquals("""["atención grabadora","basta","[unk]"]""", KeywordMatcher.grammarJson("¡Atención, Grabadora!", "basta", null))
        assertEquals("""["grabadora","[unk]"]""", KeywordMatcher.grammarJson("grabadora", "  "))
    }
}
