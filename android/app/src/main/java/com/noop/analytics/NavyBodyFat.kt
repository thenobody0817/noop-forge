package com.noop.analytics

import kotlin.math.log10

/*
 * NavyBodyFat.kt - body fat from circumferences (Hodgdon & Beckett). Kotlin twin of
 * `StrandAnalytics.NavyBodyFat`, so both platforms read the same measurements the same way.
 *
 * The US Navy circumference method, published by Hodgdon & Beckett (1984) and still the standard
 * tape-measure estimate. It is citable the way Epley is - a named formula with known behaviour, not
 * something invented here.
 *
 * WHAT IT IS GOOD AT, and what it is not. Against DEXA it carries an error band of roughly +/-3-4
 * PERCENTAGE POINTS, and - this is the part that matters - the error is largely a per-person BIAS
 * rather than random noise: someone reading three points high tends to keep reading three points high.
 * So the level is weak and the TREND is strong. A wearer watching their own number fall over two months
 * is looking at something real; a wearer comparing their number to a friend's, or to a published
 * category, is not. Every surface that shows this figure has to say so.
 *
 * TWO EQUATIONS, CHOSEN, NOT ASSUMED. Hodgdon & Beckett fitted separate equations on male and female
 * cohorts, and they are not interchangeable - the female equation takes a hip measurement the male one
 * does not. So the equation is an explicit argument rather than being derived from a profile's sex field.
 * A person whose sex is recorded as nonbinary has no published variant to fall back on, and quietly
 * picking one for them would be both a modelling error and a claim the app has no business making.
 * The choice belongs to the person; this code only computes what it is asked.
 *
 * The equations, with every measurement in centimetres:
 *
 *   male:   495 / (1.0324  - 0.19077 * log10(waist - neck)       + 0.15456 * log10(height)) - 450
 *   female: 495 / (1.29579 - 0.35004 * log10(waist + hip - neck) + 0.22100 * log10(height)) - 450
 *
 * Nothing is clamped. A tape reading that produces an impossible result is a measurement problem - most
 * often neck and waist swapped, or inches entered as centimetres - and returning a number anyway would
 * hide it behind a plausible-looking figure.
 */
object NavyBodyFat {

    /** Which published Hodgdon & Beckett equation to apply. Chosen by the wearer, never inferred. */
    enum class NavyEquation(val needsHip: Boolean) {
        /** Takes neck, waist and height. */
        MALE(false),

        /** Takes neck, waist, hip and height. */
        FEMALE(true),
    }

    /**
     * The published error band against DEXA, in percentage points. Exposed so every surface that shows an
     * estimate can state it rather than restating a number from memory.
     *
     * Twin of Swift `NavyBodyFat.errorBandPercentagePoints`.
     */
    const val ERROR_BAND_PERCENTAGE_POINTS = 4.0

    /**
     * What a human body can actually carry. A result outside this came from the tape, not the body:
     * swapped neck and waist, or inches typed into a centimetre field. Returning null surfaces that;
     * clamping would bury it.
     *
     * Twin of Swift `NavyBodyFat.plausibleRange`.
     */
    val PLAUSIBLE_RANGE = 3.0..70.0

    /**
     * The estimate, or null when the measurements cannot produce a meaningful one.
     *
     * Null means one of three things, all of which are the caller's cue to question the input rather than
     * to substitute a default: a measurement is missing or non-positive, the equation's circumference
     * difference is not positive (so its logarithm is undefined), or the result falls outside
     * [PLAUSIBLE_RANGE].
     *
     * The Swift twin is `NavyBodyFat.percent`.
     */
    @JvmStatic
    fun percent(
        equation: NavyEquation,
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double? = null,
    ): Double? {
        if (!(heightCm > 0) || !(neckCm > 0) || !(waistCm > 0)) return null
        if (!heightCm.isFinite() || !neckCm.isFinite() || !waistCm.isFinite()) return null

        val result: Double = when (equation) {
            NavyEquation.MALE -> {
                val girth = waistCm - neckCm
                if (!(girth > 0)) return null
                495.0 / (1.0324 - 0.19077 * log10(girth) + 0.15456 * log10(heightCm)) - 450.0
            }
            NavyEquation.FEMALE -> {
                val hip = hipCm
                if (hip == null || !(hip > 0) || !hip.isFinite()) return null
                val girth = waistCm + hip - neckCm
                if (!(girth > 0)) return null
                495.0 / (1.29579 - 0.35004 * log10(girth) + 0.22100 * log10(heightCm)) - 450.0
            }
        }

        if (!result.isFinite() || result < PLAUSIBLE_RANGE.start || result > PLAUSIBLE_RANGE.endInclusive) {
            return null
        }
        return result
    }
}