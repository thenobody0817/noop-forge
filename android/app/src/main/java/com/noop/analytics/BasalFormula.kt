package com.noop.analytics

/*
 * BasalFormula.kt - which published basal-metabolic-rate formula the wearer's daily rate comes from.
 * Kotlin twin of `StrandAnalytics.BasalFormula`.
 *
 * ONE RATE, CHOSEN ONCE. Before this, the basal formula was a property of whichever screen asked, so two
 * screens could quote different daily rates for the same body. The choice now lives on `UserProfile`, and
 * every energy path reads the rate through `Calories.bmrKcalPerDay(profile)`, so one selection reaches all
 * of them.
 *
 * NONE OF THESE IS A MEASUREMENT. Each is a population regression on age/sex/height/weight (Katch–McArdle
 * on lean mass), not a reading of this person's metabolism. That is why the number is always presented with
 * what it rests on rather than as a fact about the body.
 *
 * KATCH–MCARDLE IS NOT AUTOMATICALLY BETTER. It works from lean mass, so it beats a height/weight
 * regression only when the body-fat number is good. Fed a Navy circumference estimate carrying ±4
 * percentage points, its error can be comparable to what it replaced. The switch is therefore offered,
 * never applied on the app's initiative, and the surface that offers it names what the number rests on —
 * DEXA, scale, or tape.
 */
enum class BasalFormula(val rawValue: String) {
    /**
     * Roza & Shizgal's 1984 revision of Harris–Benedict. What the app has always used, and the seed of
     * every log, so an untouched install behaves exactly as before.
     */
    REVISED_HARRIS_BENEDICT("revisedHarrisBenedict"),

    /**
     * Mifflin–St Jeor (1990). Generally the better height/weight regression for modern populations, and
     * the one most calorie calculators quote.
     */
    MIFFLIN_ST_JEOR("mifflinStJeor"),

    /**
     * Katch–McArdle. Works from LEAN mass, so it needs a body-fat figure and is sex-independent — the same
     * lean kilogram costs the same regardless of whose body it is on.
     */
    KATCH_MCARDLE("katchMcArdle");

    /** Whether this formula needs a body-fat percentage. Only Katch–McArdle does. */
    val needsBodyFat: Boolean get() = this == KATCH_MCARDLE
}

/**
 * The basal rate itself, in kcal per 24 h, for a chosen formula.
 *
 * Kotlin twin of `StrandAnalytics.BasalRate`.
 */
object BasalRate {

    /**
     * Basal rate in kcal/24 h, or null when the inputs cannot support the chosen formula.
     *
     * `bodyFatPercent` is only read by Katch–McArdle, and callers resolve it through the measurement
     * store's `asOf(day)` so the rate tracks new readings forward without ever reaching backwards. Null
     * from Katch–McArdle means no body-fat reading was in force on that day, which the caller must handle
     * deliberately — silently substituting another formula would put an unexplained step in the curve,
     * which is precisely what the epoch log exists to prevent.
     *
     * The Swift twin is `BasalRate.kcalPerDay(_:weightKg:heightCm:age:sex:bodyFatPercent:)`.
     */
    @JvmStatic
    fun kcalPerDay(
        formula: BasalFormula,
        weightKg: Double,
        heightCm: Double,
        age: Double,
        sex: String,
        bodyFatPercent: Double? = null,
    ): Double? {
        if (!(weightKg > 0) || !(heightCm > 0) || !(age > 0)) return null
        if (!weightKg.isFinite() || !heightCm.isFinite() || !age.isFinite()) return null

        val value: Double = when (formula) {
            BasalFormula.REVISED_HARRIS_BENEDICT ->
                Calories.bmrKcalPerDay(Calories.resolveCoeffs(sex), weightKg, heightCm, age)
            BasalFormula.MIFFLIN_ST_JEOR ->
                10.0 * weightKg + 6.25 * heightCm - 5.0 * age + mifflinConstant(sex)
            BasalFormula.KATCH_MCARDLE -> {
                val bf = bodyFatPercent
                if (bf == null || !bf.isFinite() ||
                    bf < NavyBodyFat.PLAUSIBLE_RANGE.start ||
                    bf > NavyBodyFat.PLAUSIBLE_RANGE.endInclusive
                ) {
                    return null
                }
                val leanKg = weightKg * (1.0 - bf / 100.0)
                if (!(leanKg > 0)) return null
                370.0 + 21.6 * leanKg
            }
        }
        return if (value > 0) value else null
    }

    /**
     * Mifflin–St Jeor's sex constant. Nonbinary takes the male/female midpoint — the same convention
     * `Calories` already applies to its own coefficients, so the two formulas treat one wearer alike.
     *
     * The Swift twin is `BasalRate.mifflinConstant(_:)`.
     */
    @JvmStatic
    fun mifflinConstant(sex: String): Double = when (sex.lowercase()) {
        "male" -> 5.0
        "female" -> -161.0
        else -> -78.0
    }

    /**
     * How much a switch moves the daily basal rate, in kcal/day, signed toward `to`.
     *
     * The chart marks the switch date and the sheet states this number. A step of this size with no
     * explanation beside it reads as a bug, and the wearer is right to read it that way.
     *
     * The Swift twin is `BasalRate.switchDelta(from:to:weightKg:heightCm:age:sex:bodyFatPercent:)`.
     */
    @JvmStatic
    fun switchDelta(
        from: BasalFormula,
        to: BasalFormula,
        weightKg: Double,
        heightCm: Double,
        age: Double,
        sex: String,
        bodyFatPercent: Double? = null,
    ): Double? {
        val before = kcalPerDay(from, weightKg, heightCm, age, sex, bodyFatPercent) ?: return null
        val after = kcalPerDay(to, weightKg, heightCm, age, sex, bodyFatPercent) ?: return null
        return after - before
    }
}