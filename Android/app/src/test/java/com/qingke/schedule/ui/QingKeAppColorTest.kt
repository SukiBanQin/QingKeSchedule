package com.qingke.schedule.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class QingKeAppColorTest {
    @Test fun courseColorAcceptsOnlySixDigitRgbAndFallsBackToQingKeCyan() {
        assertEquals(Color(0xFF287B74), courseColor("#287b74"))
        listOf("#287B7", "#287B744", "287B74", "#GG7B74", " #287B74", "#FFF").forEach { malformed ->
            assertEquals(Color(0xFF28B9D6), courseColor(malformed))
            assertEquals("#28B9D6", courseColorLabel(malformed))
        }
        assertEquals("#287B74", courseColorLabel("#287b74"))
    }
}
