package ir.andarz.engine

import java.time.DayOfWeek
import java.time.LocalDate

/** A Jalali date: year, month 1–12, day 1–31. */
data class JDate(val year: Int, val month: Int, val day: Int)

/**
 * Jalali ↔ Gregorian (3.12), the jalaali-js algorithm of `jalali_ref.py`. Division is *truncating*, as in
 * the JavaScript original (Appendix C trap 6); Kotlin's `/` on Int already truncates toward zero, and `%`
 * is the matching remainder, so `div` and `mod` below are exactly the reference's `_div` and `_mod`.
 * Valid for Jalali years −61 … 3177.
 */
object Jalali {
    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Int, b: Int): Int = a - div(a, b) * b

    private class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int, withoutLeap: Boolean = false): Cal {
        val bl = BREAKS.size
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        require(jy >= jp && jy < BREAKS[bl - 1]) { "Invalid Jalaali year $jy" }
        var jump = 0
        for (i in 1 until bl) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        var leap = -1
        if (!withoutLeap) {
            if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
            leap = mod(mod(n + 1, 33) - 1, 4)
            if (leap == -1) leap = 4
        }
        return Cal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        val d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) + div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        return d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
    }

    private fun d2g(jdn: Int): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return Triple(gy, gm, gd)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy, true)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    private fun d2j(jdn: Int): JDate {
        val gy = d2g(jdn).first
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return JDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    fun toJalali(date: LocalDate): JDate = d2j(g2d(date.year, date.monthValue, date.dayOfMonth))

    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        val (y, m, d) = d2g(j2d(jy, jm, jd))
        return LocalDate.of(y, m, d)
    }

    fun toGregorian(j: JDate): LocalDate = toGregorian(j.year, j.month, j.day)

    fun isLeap(jy: Int): Boolean = jalCal(jy).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeap(jy)) 30 else 29
    }

    /** 0 = Saturday … 6 = Friday. */
    fun weekdayIndexSat0(date: LocalDate): Int = (date.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7

    /** The Saturday on or before [date] (Appendix C trap 5). */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays(weekdayIndexSat0(date).toLong())

    /** First Gregorian day of the Jalali month containing [date] (D-31). */
    fun monthStart(date: LocalDate): LocalDate {
        val j = toJalali(date)
        return toGregorian(j.year, j.month, 1)
    }
}
