package com.akhielesh.datum.core.units

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class UnitSystem { METRIC, IMPERIAL }

/** A number and its unit, kept separate so the UI can typeset them at different sizes. */
data class Formatted(val value: String, val unit: String) {
    override fun toString() = if (unit.isEmpty()) value else "$value $unit"
}

enum class Quantity { LENGTH, AREA, VOLUME, ANGLE, PRESSURE, LUX, DECIBEL, ACCEL, HEADING, MASS, FIELD, PLAIN }

object Fmt {
    private const val M_PER_IN = 0.0254
    private const val M_PER_FT = 0.3048

    fun length(m: Double, system: UnitSystem, fractions: Boolean = true): Formatted {
        if (!m.isFinite()) return Formatted("—", "")
        val a = abs(m)
        val sign = if (m < 0) "−" else ""
        return when (system) {
            UnitSystem.METRIC -> when {
                a < 0.01 -> Formatted(sign + "%.1f".format(a * 1000), "mm")
                a < 1.0 -> Formatted(sign + "%.1f".format(a * 100), "cm")
                a < 1000 -> Formatted(sign + "%.2f".format(a), "m")
                else -> Formatted(sign + "%.2f".format(a / 1000), "km")
            }
            UnitSystem.IMPERIAL -> {
                val inches = a / M_PER_IN
                when {
                    inches < 12 -> Formatted(sign + if (fractions) fractionInches(inches) else "%.2f".format(inches), "in")
                    a < 30.48 -> {
                        // feet + inches, e.g. 3′ 4½″
                        var ft = floor(inches / 12).toLong()
                        var rem = inches - ft * 12
                        var remStr = if (fractions) fractionInches(rem) else "%.1f".format(rem)
                        if (remStr.startsWith("12")) {
                            ft += 1; rem = 0.0; remStr = "0"
                        }
                        Formatted("$sign$ft′ $remStr″", "")
                    }
                    a < 1609.344 -> Formatted(sign + "%.1f".format(a / M_PER_FT), "ft")
                    else -> Formatted(sign + "%.2f".format(a / 1609.344), "mi")
                }
            }
        }
    }

    /** Compact inline length, e.g. for labels on a 3D drawing. */
    fun lengthShort(m: Double, system: UnitSystem): String = length(m, system).toString()

    private val vulgar = mapOf(
        Pair(1, 2) to "½", Pair(1, 4) to "¼", Pair(3, 4) to "¾",
        Pair(1, 8) to "⅛", Pair(3, 8) to "⅜", Pair(5, 8) to "⅝", Pair(7, 8) to "⅞",
    )
    private val sup = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private val sub = "₀₁₂₃₄₅₆₇₈₉"

    /** Inches to the nearest 1/16, rendered with proper typographic fractions. */
    fun fractionInches(inches: Double): String {
        val sixteenths = (inches * 16).roundToLong()
        val whole = sixteenths / 16
        var num = (sixteenths % 16).toInt()
        var den = 16
        if (num == 0) return whole.toString()
        while (num % 2 == 0) {
            num /= 2; den /= 2
        }
        val frac = vulgar[num to den] ?: (num.toString().map { sup[it - '0'] }.joinToString("") + "⁄" +
            den.toString().map { sub[it - '0'] }.joinToString(""))
        return if (whole == 0L) frac else "$whole$frac"
    }

    fun area(m2: Double, system: UnitSystem): Formatted = when (system) {
        UnitSystem.METRIC -> if (m2 < 1.0) Formatted(trim(m2 * 1e4, if (m2 * 1e4 < 100) 1 else 0), "cm²")
        else Formatted("%.2f".format(m2), "m²")
        UnitSystem.IMPERIAL -> {
            val ft2 = m2 / (M_PER_FT * M_PER_FT)
            if (ft2 < 1.0) Formatted(trim(m2 / (M_PER_IN * M_PER_IN), 1), "in²") else Formatted("%.2f".format(ft2), "ft²")
        }
    }

    fun volume(m3: Double, system: UnitSystem): Formatted = when (system) {
        UnitSystem.METRIC -> when {
            m3 < 0.001 -> Formatted(trim(m3 * 1e6, if (m3 * 1e6 < 100) 1 else 0), "cm³")
            m3 < 1.0 -> Formatted("%.2f".format(m3 * 1000), "L")
            else -> Formatted("%.3f".format(m3), "m³")
        }
        UnitSystem.IMPERIAL -> {
            val ft3 = m3 / (M_PER_FT * M_PER_FT * M_PER_FT)
            if (ft3 < 1.0) Formatted(trim(m3 / (M_PER_IN * M_PER_IN * M_PER_IN), 1), "in³")
            else Formatted("%.2f".format(ft3), "ft³")
        }
    }

    fun mass(kg: Double, system: UnitSystem): Formatted = when (system) {
        UnitSystem.METRIC -> if (kg < 1) Formatted(trim(kg * 1000, if (kg < 0.1) 1 else 0), "g") else Formatted("%.2f".format(kg), "kg")
        UnitSystem.IMPERIAL -> {
            val lb = kg / 0.45359237
            if (lb < 1) Formatted("%.1f".format(lb * 16), "oz") else Formatted("%.2f".format(lb), "lb")
        }
    }

    fun angle(deg: Double, decimals: Int = 1): Formatted =
        Formatted(if (decimals == 0) deg.roundToInt().toString() else "%.${decimals}f".format(deg), "°")

    fun pressure(hPa: Double, system: UnitSystem): Formatted = when (system) {
        UnitSystem.METRIC -> Formatted("%.1f".format(hPa), "hPa")
        UnitSystem.IMPERIAL -> Formatted("%.2f".format(hPa * 0.0295299830714), "inHg")
    }

    fun lux(lx: Double): Formatted = when {
        lx < 10 -> Formatted("%.1f".format(lx), "lx")
        lx < 10_000 -> Formatted("%,d".format(lx.roundToLong()), "lx")
        else -> Formatted("%.1fk".format(lx / 1000), "lx")
    }

    fun format(value: Double, quantity: Quantity, system: UnitSystem): Formatted = when (quantity) {
        Quantity.LENGTH -> length(value, system)
        Quantity.AREA -> area(value, system)
        Quantity.VOLUME -> volume(value, system)
        Quantity.ANGLE -> angle(value)
        Quantity.PRESSURE -> pressure(value, system)
        Quantity.LUX -> lux(value)
        Quantity.DECIBEL -> Formatted("%.0f".format(value), "dB")
        Quantity.ACCEL -> Formatted("%.3f".format(value), "m/s²")
        Quantity.HEADING -> Formatted("%.0f".format(value), "°")
        Quantity.MASS -> mass(value, system)
        Quantity.FIELD -> Formatted("%.1f".format(value), "µT")
        Quantity.PLAIN -> Formatted(trim(value, 2), "")
    }

    private fun trim(v: Double, decimals: Int): String =
        if (decimals == 0) "%,d".format(v.roundToLong()) else "%.${decimals}f".format(v)
}
