package com.noop.analytics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * BYTE-IDENTICAL PARITY ORACLE for `Spo2Display.resolve`, against the compiled Swift twin.
 *
 * The expected block is not a re-derivation of the rule. `Tools/oracle/gen_spo2_display_oracle.sh`
 * compiles `Packages/StrandAnalytics/Sources/StrandAnalytics/Spo2Display.swift` STANDALONE (it imports
 * Foundation and nothing else), drives it over the full cross product of its four inputs, and commits
 * its own answers as `src/test/resources/spo2_display_oracle.json`. That fixture is committed to BOTH
 * trees, so `Spo2DisplayTests.swift` reads the same file this test does. Two independent
 * reimplementations agreeing is weaker evidence than one implementation pinned by a committed
 * expectation, which is why the fixture is the Swift build's stdout and not a set of hand-written
 * numbers.
 *
 * COVERAGE, and why this test guards its own coverage. The grid is exhaustive over nine named values
 * (`nil`, `0`, `-1`, three realistic readings, `nan`, `inf`, `-inf`) crossed with the toggle, so every
 * branch of the three-way decision is exercised for every combination of the other two inputs. The
 * guard at the bottom asserts the fixture still has that full cross product and still covers all three
 * provenance values, so deleting a case cannot quietly shrink what is pinned.
 *
 * NAMED INPUTS. JSON has no NaN or Infinity literal, so the grid names its values and BOTH platforms map
 * the same names to the same doubles. The names are the shared input contract; the mapping lives in
 * [gridValue] on this side and in `gen_spo2_display_oracle.swift` on the other.
 */
class Spo2DisplayParityOracleTest {

    private fun gridValue(name: String): Double? = when (name) {
        "nil" -> null
        "0" -> 0.0
        "neg" -> -1.0
        "90" -> 90.0
        "96" -> 96.0
        "97" -> 97.0
        "nan" -> Double.NaN
        "inf" -> Double.POSITIVE_INFINITY
        "neginf" -> Double.NEGATIVE_INFINITY
        else -> throw AssertionError("oracle names an input the grid does not define: $name")
    }

    private fun provenance(name: String): Spo2Display.Provenance = when (name) {
        "measured" -> Spo2Display.Provenance.MEASURED
        "measuredCarried" -> Spo2Display.Provenance.MEASURED_CARRIED
        "candidate" -> Spo2Display.Provenance.CANDIDATE
        else -> throw AssertionError("oracle names a provenance this platform does not define: $name")
    }

    @Test
    fun `every case matches the compiled Swift twin`() {
        val root = JSONObject(javaClass.classLoader!!.getResource("spo2_display_oracle.json")!!.readText())
        val cases = root.getJSONArray("cases")
        assertTrue("the fixture must not be empty", cases.length() > 0)

        val seen = mutableSetOf<String>()
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val t = c.getString("t")
            val cand = c.getString("c")
            val k = c.getString("k")
            val enabled = c.getBoolean("e")
            val label = "t=$t c=$cand k=$k e=$enabled"

            val out = Spo2Display.resolve(
                todayPct = gridValue(t),
                candidatePct = gridValue(cand),
                candidateEnabled = enabled,
                carriedPct = gridValue(k),
            )

            val expectedProvenance = if (c.isNull("provenance")) null else c.getString("provenance")
            if (expectedProvenance == null) {
                assertEquals("$label should not resolve", null, out)
                continue
            }
            if (out == null) {
                fail("$label should have resolved to $expectedProvenance but resolved to null")
            }
            assertEquals("$label provenance", provenance(expectedProvenance), out!!.provenance)
            val expectedPercent = c.getDouble("percent")
            assertTrue(
                "$label percent: expected $expectedPercent, got ${out.percent}",
                abs(out.percent - expectedPercent) < 1e-9,
            )
            seen.add(expectedProvenance)
        }

        assertEquals("every provenance value must be pinned", setOf("measured", "measuredCarried", "candidate"), seen)
    }

    /**
     * The self-defence guard. A fixture that silently loses cases still passes the loop above, so assert
     * the cross product is whole: grid size cubed, crossed with the toggle.
     */
    @Test
    fun `the fixture still covers the whole cross product`() {
        val root = JSONObject(javaClass.classLoader!!.getResource("spo2_display_oracle.json")!!.readText())
        val values = root.getJSONObject("grid").getJSONArray("values")
        val enabled = root.getJSONObject("grid").getJSONArray("enabled")
        val n = values.length()
        val expected = n * n * n * enabled.length()
        assertEquals(
            "case count must be the full cross product of the grid",
            expected,
            root.getJSONArray("cases").length(),
        )
    }
}