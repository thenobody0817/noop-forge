package com.noop.analytics

/*
 * Spo2Display.kt - which blood-oxygen number a surface shows, out of up to three. Kotlin twin of
 * `StrandAnalytics.Spo2Display`, pinned by `Spo2DisplayParityOracleTest`, whose expected values are
 * the Swift build's own stdout over a 1024-case grid.
 *
 * A wearer can hold all of these at once:
 *
 *   - a calibrated `spo2Pct` for the day on screen - a real measurement of that day;
 *   - a calibrated `spo2Pct` CARRIED from an earlier day, deliberately with no staleness bound
 *     (SpO2 is sparse and often imported, so "last known, of any age" is the honest carry - the
 *     bound `lastRespDay` applies exists because respiratory is nightly and SpO2 is not);
 *   - the strap's own nightly CANDIDATE mean for the day on screen, behind the default-off
 *     experimental toggle, which is never written to `spo2Pct` and always labelled.
 *
 * The old rule was "any calibrated value beats the candidate". That is right between a measurement
 * and an estimate OF THE SAME DAY and wrong across days, and it was reported as such: an import
 * ending on 1 June kept the tile pinned to a 1 June figure for three and a half months while the
 * strap produced a fresh estimate every night - which the rule then suppressed, because a
 * calibrated value existed somewhere in the past.
 *
 * So the order is by RECENCY first and strength second: today's measurement, else today's estimate,
 * else the old measurement. The estimate only ever displaces a reading from an EARLIER day, never
 * one from the day being shown, and it never loses its label on the way.
 */
object Spo2Display {

    /**
     * What the shown number is, so a surface can caption it. The distinction between the two
     * measured cases is not cosmetic: one is the day on screen, the other is a carry that the
     * surface stamps with its own date.
     */
    enum class Provenance {
        /** A calibrated reading for the day being shown. */
        MEASURED,

        /** A calibrated reading carried from an earlier day. */
        MEASURED_CARRIED,

        /**
         * The strap's nightly candidate for the day being shown. Always labelled "strap estimate
         * (unverified)" - it is not a validated calibration on either WHOOP or Oura.
         */
        CANDIDATE,
    }

    data class Resolved(val percent: Double, val provenance: Provenance)

    /**
     * @param todayPct calibrated `spo2Pct` for the day on screen.
     * @param candidatePct the strap candidate for that same day, or null when there is none.
     * @param candidateEnabled the experimental toggle. Off means the candidate does not exist as far
     *   as this decision is concerned - not that it loses a tie.
     * @param carriedPct the freshest calibrated reading from an EARLIER day.
     *
     * The Swift twin is `Spo2Display.resolve`.
     */
    @JvmStatic
    fun resolve(
        todayPct: Double?,
        candidatePct: Double?,
        candidateEnabled: Boolean,
        carriedPct: Double?,
    ): Resolved? {
        if (todayPct != null && todayPct.isFinite() && todayPct > 0) {
            return Resolved(todayPct, Provenance.MEASURED)
        }
        if (candidateEnabled && candidatePct != null && candidatePct.isFinite() && candidatePct > 0) {
            return Resolved(candidatePct, Provenance.CANDIDATE)
        }
        if (carriedPct != null && carriedPct.isFinite() && carriedPct > 0) {
            return Resolved(carriedPct, Provenance.MEASURED_CARRIED)
        }
        return null
    }
}