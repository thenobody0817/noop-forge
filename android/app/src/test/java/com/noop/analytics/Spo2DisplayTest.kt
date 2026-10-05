package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kotlin port of `Spo2DisplayTests.swift`. Test names mirror the Swift suite so the two read side by
 * side, and each carries the same reason it exists.
 *
 * The parity half is [Spo2DisplayParityOracleTest], which pins all 1,458 cases against the Swift build's
 * own stdout. These are the readable ones: the decision rule in the order a person would state it.
 */
class Spo2DisplayTest {

    private fun resolve(
        today: Double? = null,
        candidate: Double? = null,
        enabled: Boolean = true,
        carried: Double? = null,
    ): Spo2Display.Resolved? =
        Spo2Display.resolve(today, candidate, enabled, carried)

    /**
     * THE regression. An import that ended months ago left a calibrated value in the carry, and the old
     * rule let it beat every fresh estimate - so the tile showed 1 June until August and hid the reading
     * the strap had produced that night.
     */
    @Test
    fun `tonight's estimate beats a months-old measurement`() {
        val out = resolve(candidate = 95.0, carried = 97.0)
        assertEquals(95.0, out!!.percent, 0.0)
        assertEquals(Spo2Display.Provenance.CANDIDATE, out.provenance)
    }

    /**
     * The other side of the same rule: within ONE day a measurement still beats an estimate. The change
     * is about recency across days, not about trusting the candidate more.
     */
    @Test
    fun `today's measurement beats today's estimate`() {
        val out = resolve(today = 96.0, candidate = 93.0)
        assertEquals(96.0, out!!.percent, 0.0)
        assertEquals(Spo2Display.Provenance.MEASURED, out.provenance)
    }

    /**
     * With the toggle off the candidate does not exist for this decision - the carry is then the only
     * thing left, exactly as before the experiment shipped.
     */
    @Test
    fun `with the toggle off the carry is used`() {
        val out = resolve(candidate = 95.0, enabled = false, carried = 97.0)
        assertEquals(97.0, out!!.percent, 0.0)
        assertEquals(Spo2Display.Provenance.MEASURED_CARRIED, out.provenance)
    }

    /** A carry with no candidate still shows, and says it is a carry so the surface can date it. */
    @Test
    fun `a carry alone is still shown and marked`() {
        assertEquals(Spo2Display.Provenance.MEASURED_CARRIED, resolve(carried = 97.0)!!.provenance)
    }

    @Test
    fun `nothing at all resolves to nil`() {
        assertNull(resolve())
        assertNull(resolve(candidate = 95.0, enabled = false))
    }

    /**
     * Zero and NaN are "no reading", not a reading of zero percent - a 0 % blood oxygen would be a claim
     * about a corpse, and it reaches these fields from empty rows and failed decodes.
     */
    @Test
    fun `a zero or NaN reading is not a reading`() {
        assertEquals(
            Spo2Display.Provenance.MEASURED_CARRIED,
            resolve(today = 0.0, carried = 97.0)!!.provenance,
        )
        assertEquals(
            Spo2Display.Provenance.CANDIDATE,
            resolve(today = Double.NaN, candidate = 95.0)!!.provenance,
        )
        assertNull(resolve(today = 0.0, candidate = 0.0, carried = 0.0))
    }
}