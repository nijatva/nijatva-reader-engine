package dev.nijatva.reader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageReportTest {

    @Test
    fun anEmptyReportSaysSo() {
        assertTrue(CoverageReport().isEmpty)
    }

    @Test
    fun countingSomethingMakesItNotEmpty() {
        assertFalse(CoverageReport(ignoredNodes = 1).isEmpty)
    }

    @Test
    fun mergingAddsCountsRatherThanReplacingThem() {
        val a = CoverageReport(unsupportedHtml = mapOf("svg" to 2), ignoredNodes = 1)
        val b = CoverageReport(unsupportedHtml = mapOf("svg" to 3, "iframe" to 1), ignoredNodes = 4)
        val m = a.merge(b)
        assertEquals(5, m.unsupportedHtml["svg"])
        assertEquals(1, m.unsupportedHtml["iframe"])
        assertEquals(5, m.ignoredNodes)
    }
}
