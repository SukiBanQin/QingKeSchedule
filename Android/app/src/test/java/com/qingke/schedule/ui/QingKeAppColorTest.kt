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

    @Test fun colorPickerGridSpectrumAndRgbMappingsAreBoundedAndDeterministic() {
        assertEquals(36, CourseColorVisualSpec.grid.size)
        listOf("#FFFFFF", "#000000", "#E11D48", "#22C55E", "#3B82F6", "#8B5CF6").forEach { require(it in CourseColorVisualSpec.grid) }
        assertEquals("#FFFFFF", spectrumHexAt(0f, 0f, 0))
        assertEquals("#000000", spectrumHexAt(1f, 1f, 0))
        assertEquals("#FF0000", spectrumHexAt(1f, 0f, 0))
        assertEquals(spectrumHexAt(1f, 0f, 120), spectrumHexAt(2f, -1f, 480))
        assertEquals("#00FF00", hsvHex(120, 100, 100))
        assertEquals("#FF0000", hsvHex(360, 100, 100))
        assertEquals("#00FF00", rgbHex(-1, 300, 0))
        assertEquals(intArrayOf(0, 255, 0).toList(), rgbParts("#00FF00").toList())
    }
}
