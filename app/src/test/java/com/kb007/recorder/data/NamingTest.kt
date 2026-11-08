package com.kb007.recorder.data

import com.kb007.recorder.data.db.Recording
import com.kb007.recorder.data.prefs.AudioFormatType
import com.kb007.recorder.data.prefs.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class NamingTest {
    @Test fun patternTokens() {
        val d = Calendar.getInstance().apply { set(2026, 10, 8, 9, 5, 3) }.time
        assertEquals("KB007_2026-11-08_09-05-03", StorageManager.formatName("KB007_{fecha}_{hora}", TriggerType.MANUAL, 1, d))
        assertEquals("clave-007", StorageManager.formatName("{modo}-{n}", TriggerType.KEYWORD, 7, d))
        assertEquals("a_b_c", StorageManager.formatName("a/b:c", TriggerType.MANUAL, 1, d))
    }

    @Test fun qualityLabel() {
        val r = Recording(title = "x", format = AudioFormatType.AAC_M4A, sampleRate = 44_100, channels = 1, bitrateKbps = 128,
            createdAt = 0, trigger = TriggerType.MANUAL)
        assertEquals("M4A · 44,1 kHz · 128 kbps · mono", r.qualityLabel)
        assertEquals("WAV · 48 kHz · estéreo", r.copy(format = AudioFormatType.WAV_PCM, sampleRate = 48_000, channels = 2).qualityLabel)
        assertEquals("OGG · 22,05 kHz · 128 kbps · mono", r.copy(format = AudioFormatType.OPUS_OGG, sampleRate = 22_050).qualityLabel)
    }
}
