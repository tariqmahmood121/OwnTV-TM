package tv.own.owntv.features.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XpCinemaTest {
    @Test fun typingUsesCursorAndKeepsOrder() {
        val start = TextFieldValue("Mision", TextRange(3))
        val inserted = xpCinemaEdit(start, "s")
        assertEquals("Mission", inserted.text)
        assertEquals(TextRange(4), inserted.selection)
        assertEquals("Mission Impossible", " Impossible".fold(inserted.copy(selection = TextRange(7))) { value, c ->
            xpCinemaEdit(value, c.toString())
        }.text)
    }

    @Test fun deleteHandlesSelectionAndSupplementaryCharacters() {
        assertEquals("AC", xpCinemaEdit(TextFieldValue("ABC", TextRange(1, 2)), null).text)
        assertEquals("A", xpCinemaEdit(TextFieldValue("A🌟", TextRange(3)), null).text)
        val empty = TextFieldValue()
        assertEquals(empty, xpCinemaEdit(empty, null))
    }

    @Test fun onlyRecognisedUkCertificatesAreDisplayed() {
        assertEquals("12A", xpCinemaCertificate(" 12a "))
        assertEquals("PG", xpCinemaCertificate("PG"))
        assertNull(xpCinemaCertificate("PG-13"))
        assertNull(xpCinemaCertificate("TV-MA"))
        assertNull(xpCinemaCertificate(null))
    }
}
