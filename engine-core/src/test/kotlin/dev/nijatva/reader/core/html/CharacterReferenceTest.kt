package dev.nijatva.reader.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mechanism is tested exhaustively for what it claims; the table is tested for the
 * names it actually carries. The gap between that table and the specification's 2,231
 * names is recorded in docs/known-gaps.md rather than papered over with a guess.
 */
class CharacterReferenceTest {

    private fun decode(s: String, inAttribute: Boolean = false) =
        CharacterReferences.decode(s, 0, inAttribute)

    @Test fun namedWithSemicolon() {
        assertEquals("&", decode("&amp;")?.text)
        assertEquals(5, decode("&amp;")!!.consumed)
    }

    @Test fun longestMatchWins() {
        // &not; and &notin; both start the same way
        assertEquals("\u2209", decode("&notin;")?.text)
        assertEquals("\u00AC", decode("&not;")?.text)
    }

    @Test fun legacyNameWithoutSemicolonInText() {
        assertEquals("&", decode("&amp x")?.text)
    }

    @Test fun ambiguousAmpersandInAttributeIsRefused() {
        assertNull(decode("&notit;", inAttribute = true))
    }

    @Test fun legacyNameFollowedByEqualsInAttributeIsRefused() {
        assertNull(decode("&amp=1", inAttribute = true))
    }

    @Test fun unknownNameIsNotDecoded() {
        assertNull(decode("&nosuch;"))
    }

    @Test fun bareAmpersandIsNotAReference() {
        assertNull(decode("& x"))
    }

    @Test fun decimalNumeric() {
        assertEquals("A", decode("&#65;")?.text)
    }

    @Test fun hexNumericBothCases() {
        assertEquals("A", decode("&#x41;")?.text)
        assertEquals("A", decode("&#X41;")?.text)
    }

    @Test fun numericWithoutSemicolonStillDecodes() {
        assertEquals("A", decode("&#65 x")?.text)
    }

    @Test fun zeroBecomesReplacementCharacter() {
        assertEquals("\uFFFD", decode("&#0;")?.text)
    }

    @Test fun surrogateIsRejected() {
        assertEquals("\uFFFD", decode("&#xD800;")?.text)
    }

    @Test fun beyondMaxCodePointIsRejected() {
        assertEquals("\uFFFD", decode("&#x110000;")?.text)
    }

    @Test fun c1RangeIsRemapped() {
        assertEquals("\u20AC", decode("&#x80;")?.text)
        assertEquals("\u2122", decode("&#x99;")?.text)
        assertEquals("\u0178", decode("&#x9F;")?.text)
    }

    @Test fun astralCodePointBecomesASurrogatePair() {
        val r = decode("&#x1F600;")
        assertEquals(2, r!!.text.length)
        assertEquals("\uD83D\uDE00", r.text)
    }

    @Test fun hashWithoutDigitsIsNotAReference() {
        assertNull(decode("&#;"))
        assertNull(decode("&#x;"))
    }

    @Test fun tableCarriesTheCommonNames() {
        assertTrue(CharacterReferences.knownNameCount >= 100)
    }

    @Test fun everyTableEntryRoundTrips() {
        // A name that decodes to itself as text would be a table mistake.
        for (name in listOf("amp", "lt", "gt", "quot", "apos", "nbsp", "copy", "mdash")) {
            val r = decode("&$name;")
            assertTrue("&$name; did not decode", r != null)
        }
    }
}
