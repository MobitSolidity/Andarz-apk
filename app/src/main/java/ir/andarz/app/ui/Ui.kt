package ir.andarz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import ir.andarz.app.content.ContentBundle
import ir.andarz.data.settings.CalendarMode
import ir.andarz.data.settings.Digits
import ir.andarz.data.settings.Settings
import ir.andarz.engine.Fmt
import ir.andarz.engine.Jalali
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Presentation helpers of the app layer (6.3): wording lookups, enum labels, money/R/percent chips and dates
 * in the user's digit and calendar setting. Every number is formatted by `Fmt`; nothing uses the default
 * `Locale` (trap 1). Money, R values and prices are LTR isolates (trap 9).
 */
class Ui(val content: ContentBundle, val settings: Settings) {
    val fa: Boolean get() = settings.digits == Digits.FA

    /** The wording reads left to right (English, D-90). */
    val ltr: Boolean get() = content.isLtr

    /** Equity and dollar P&L are masked on screen (D-91). Calculator inputs and results are never masked. */
    val hidden: Boolean get() = settings.hideAmounts

    /** The same helpers with the mask off: for tools whose numbers the user is typing (calculator, wizard, share). */
    fun unmasked(): Ui = if (hidden) Ui(content, settings.copy(hideAmounts = false)) else this
    val zone: ZoneId get() = settings.zone

    fun t(key: String, vararg args: Pair<String, String>): String = content.ui(key, args.toMap())

    /** `enums.<enum>[id].label` (or another field), falling back to the id. */
    fun enumLabel(enum: String, id: String?, field: String = "label"): String {
        if (id == null) return t("common.dash")
        val items = content.obj("enums")?.get(enum)?.jsonArray ?: return id
        val o = items.map { it.jsonObject }.firstOrNull { it["id"]?.jsonPrimitive?.content == id } ?: return id
        return o[field]?.jsonPrimitive?.content ?: id
    }

    fun enumIds(enum: String): List<String> =
        content.obj("enums")?.get(enum)?.jsonArray?.map { it.jsonObject["id"]!!.jsonPrimitive.content }.orEmpty()

    fun enumItem(enum: String, id: String): JsonObject? =
        content.obj("enums")?.get(enum)?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["id"]?.jsonPrimitive?.content == id }

    // ---- numbers (6.3)
    fun num(x: Double?, d: Int = 2, group: Boolean = true, trim: Boolean = false): String =
        if (x == null) t("common.dash") else Fmt.number(x, d, fa, group, trim)

    /**
     * A dollar amount. [mask] lets a screen opt out (the calculator and the wizard show the numbers the user is
     * typing); everything that shows the account's money follows the hide setting (D-91).
     */
    fun money(x: Double?, d: Int = 2, mask: Boolean = true): String {
        if (x == null) return t("common.dash")
        if (mask && hidden) return MASKED
        val r = ir.andarz.engine.XlMath.round(x, d)
        val sign = if (r > 0) "" else if (r < 0) Fmt.MINUS.toString() else ""
        return Fmt.isolate(sign + "$" + Fmt.number(kotlin.math.abs(r), d, fa))
    }

    fun signedMoney(x: Double?, d: Int = 2, mask: Boolean = true): String {
        if (x == null) return t("common.dash")
        if (mask && hidden) return MASKED
        val r = ir.andarz.engine.XlMath.round(x, d)
        val sign = if (r > 0) "+" else if (r < 0) Fmt.MINUS.toString() else ""
        return Fmt.isolate(sign + "$" + Fmt.number(kotlin.math.abs(r), d, fa))
    }

    fun rChip(r: Double?): String = if (r == null) t("common.dash") else Fmt.isolate(Fmt.signed(r, 2, fa) + "R")

    fun pct(x: Double?, d: Int = 2, signed: Boolean = false): String =
        if (x == null) t("common.dash") else Fmt.isolate(Fmt.percent(x, d, fa, signed))

    fun price(x: Double?): String = if (x == null) t("common.dash") else Fmt.isolate(Fmt.number(x, priceDecimals(x), fa, group = true, trim = false))

    fun mult(x: Double?): String = if (x == null) t("common.dash") else Fmt.isolate(Fmt.number(x, 2, fa, group = false, trim = true) + "\u00D7")

    fun int(n: Int): String = Fmt.number(n.toDouble(), 0, fa)

    fun symbol(s: String): String = Fmt.isolate(s)

    /** Digits inside static text follow the setting (3.10). */
    fun digits(s: String): String = if (fa) Fmt.toFaDigits(s) else Fmt.toLatinDigits(s)

    // ---- dates (3.12)
    fun date(d: LocalDate, withWeekday: Boolean = true): String {
        val cal = content.obj("calendar")
        val wd = cal?.get("weekdaysSaturdayFirst")?.jsonArray?.get(Jalali.weekdayIndexSat0(d))?.jsonPrimitive?.content
        val body = if (settings.calendar == CalendarMode.JALALI) {
            val j = Jalali.toJalali(d)
            val m = cal?.get("jalaliMonths")?.jsonArray?.get(j.month - 1)?.jsonPrimitive?.content ?: j.month.toString()
            digits("${j.day}") + " " + m + " " + digits("${j.year}")
        } else {
            val m = cal?.get("gregorianMonths")?.jsonArray?.get(d.monthValue - 1)?.jsonPrimitive?.content ?: d.monthValue.toString()
            digits("${d.dayOfMonth}") + " " + m + " " + digits("${d.year}")
        }
        return if (withWeekday && wd != null) "$wd $body" else body
    }

    fun time(t: Instant): String {
        val z = t.atZone(zone)
        return Fmt.isolate(digits("%02d:%02d".format(java.util.Locale.ROOT, z.hour, z.minute)))
    }

    fun dateTime(t: Instant): String = date(t.atZone(zone).toLocalDate(), withWeekday = false) + " " + time(t)

    fun localDate(t: Instant): LocalDate = t.atZone(zone).toLocalDate()

    /** "1h 05m" style remaining time with digits per setting, isolated. */
    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return Fmt.isolate(digits(if (h > 0) "%d:%02d".format(java.util.Locale.ROOT, h, m) else m.toString()))
    }

    companion object {
        /** What a hidden amount shows: a dollar sign and four bullets, isolated so it sits right in RTL. */
        val MASKED: String = Fmt.isolate("$\u2022\u2022\u2022\u2022")

        /** March's price tiers (K7), reused for journal prices. */
        fun priceDecimals(x: Double): Int {
            val a = kotlin.math.abs(x)
            return when {
                a >= 1000 -> 2
                a >= 1 -> 2
                a >= 0.01 -> 4
                a == 0.0 -> 2
                else -> minOf(10, 2 - kotlin.math.floor(kotlin.math.log10(a)).toInt())
            }
        }
    }
}

val LocalUi = staticCompositionLocalOf<Ui> { error("Ui not provided") }

@Composable
fun ui(): Ui = LocalUi.current
