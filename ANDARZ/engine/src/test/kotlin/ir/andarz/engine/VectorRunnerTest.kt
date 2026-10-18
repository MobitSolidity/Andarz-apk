package ir.andarz.engine

import ir.andarz.engine.Vectors.arr
import ir.andarz.engine.Vectors.b
import ir.andarz.engine.Vectors.bOrNull
import ir.andarz.engine.Vectors.d
import ir.andarz.engine.Vectors.dOrNull
import ir.andarz.engine.Vectors.i
import ir.andarz.engine.Vectors.j
import ir.andarz.engine.Vectors.legs
import ir.andarz.engine.Vectors.obj
import ir.andarz.engine.Vectors.s
import ir.andarz.engine.Vectors.sOrNull
import ir.andarz.engine.Vectors.strings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The golden-vector runner of 10.1: one dynamic test per case, named by its id, so a failure names the
 * vector. M1 covers xlRound plan leverageAdvisor sizeVerdict fill curve kpi jalali format renderClause.
 */
class VectorRunnerTest {

    private val zone = ZoneOffset.ofHoursMinutes(3, 30)

    private fun check(id: String, got: JsonElement, exp: JsonElement) {
        val d = Vectors.diff(id, got, exp)
        if (d.isNotEmpty()) fail(d.joinToString("\n"))
    }

    private fun named(category: String, block: (JsonObject) -> Unit): List<DynamicTest> =
        Vectors.cases(category).map { c -> DynamicTest.dynamicTest(c["id"]!!.jsonPrimitive.content) { block(c) } }

    @TestFactory
    fun xlRound(): List<DynamicTest> = Vectors.cases("xlRound").mapIndexed { n, c ->
        DynamicTest.dynamicTest("xlRound#${n + 1} ${c.d("x")},${c.i("d")}") {
            val got = XlMath.round(c.d("x"), c.i("d"))
            assertTrue(Vectors.closeEnough(got, c.d("expected")), "xlRound(${c.d("x")}, ${c.i("d")}) = $got")
        }
    }

    @TestFactory
    fun plan(): List<DynamicTest> = named("plan") { c ->
        val i = c["input"]!!.jsonObject
        val p = Vectors.params(i)
        val side = Side.valueOf(i.s("side"))
        val pl = Planner.plan(side, i.d("equity"), i.d("entry"), i.d("sl"), i.d("tp"), i.d("lev"), p, i.dOrNull("riskPct"))
        val reasons = Planner.gate(side, i.d("entry"), i.d("sl"), i.d("tp"), i.d("lev"), i.i("chk"), pl, p)
        val got = obj(
            "sld" to j(pl.sld), "rr" to j(pl.rr), "riskUsd" to j(pl.riskUsd), "notionalMax" to j(pl.notionalMax),
            "margin" to j(pl.margin), "marginPct" to j(pl.marginPct), "liq" to j(pl.liq), "liqDist" to j(pl.liqDist),
            "liqX" to j(pl.liqX), "reasons" to arr(reasons.map { JsonPrimitive(it.name) }), "allowed" to j(reasons.isEmpty()),
        )
        check(c.s("id"), got, c["expected"]!!)
    }

    @TestFactory
    fun leverageAdvisor(): List<DynamicTest> = named("leverageAdvisor") { c ->
        val i = c["input"]!!.jsonObject
        val p = Vectors.params(i)
        val a = Planner.leverageAdvisor(
            Side.valueOf(i.s("side")), i.d("equity"), i.d("entry"), i.d("sl"), i.d("tp"), p.chkN.toInt(), p, i.dOrNull("riskPct"),
        )
        val got = obj(
            "lMinReal" to j(a.lMinReal), "lMaxLiqReal" to j(a.lMaxLiqReal), "feasibleMin" to j(a.feasibleMin),
            "feasibleMax" to j(a.feasibleMax), "recommended" to j(a.recommended),
        )
        check(c.s("id"), got, c["expected"]!!)
    }

    @TestFactory
    fun sizeVerdict(): List<DynamicTest> = named("sizeVerdict") { c ->
        val i = c["input"]!!.jsonObject
        val v = Planner.sizeVerdict(i.d("rriskp"), Vectors.params(i), i.dOrNull("riskCap"), i.bOrNull("gradeAAllowed") ?: true)
        assertEquals(c.s("expected"), v.name, c.s("id"))
    }

    @TestFactory
    fun fill(): List<DynamicTest> = named("fill") { c ->
        val i = c["input"]!!.jsonObject
        val p = Vectors.params(i)
        val ctx = i["ctx"] as? JsonObject
        val f = Planner.fill(Side.valueOf(i.s("side")), i.d("entry"), i.d("sl"), i.d("equity"), i.d("notional"), i.legs(), i.d("fee"))
        val verdict = Planner.sizeVerdict(f.rriskp!!, p, ctx?.dOrNull("riskCap"), ctx?.bOrNull("gradeA") ?: true)
        val ok = Planner.compliant(i.strings("violations"), i.b("gateAllowed"), verdict)
        val got = obj(
            "rrisk" to j(f.rrisk), "rriskp" to j(f.rriskp), "verdict" to j(verdict.name), "gross" to j(f.gross),
            "net" to j(f.net), "R" to j(f.r), "result" to j(f.result?.name), "ok" to j(ok), "equityAfter" to j(f.equityAfter),
        )
        check(c.s("id"), got, c["expected"]!!)
    }

    @TestFactory
    fun curve(): List<DynamicTest> = named("curve") { c ->
        val i = c["input"]!!.jsonObject
        val cv = Stats.curve(i["nets"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }, i.d("start"))
        check(c.s("id"), arr(cv.map { obj("cum" to j(it.cum), "peak" to j(it.peak), "dd" to j(it.dd)) }), c["expected"]!!)
    }

    @TestFactory
    fun kpi(): List<DynamicTest> = named("kpi") { c ->
        val i = c["input"]!!.jsonObject
        val p = Vectors.params(i)
        val trades = i["trades"]!!.jsonArray.map { tradeOf(it.jsonObject) }
        val (k, rows) = Trades.kpis(trades, LocalDate.parse(i.s("from")), LocalDate.parse(i.s("to")), p, zone)
        val got = obj(
            "n" to j(k.n), "compliance" to j(k.compliance), "expectancy" to j(k.expectancy), "avgWinR" to j(k.avgWinR),
            "avgLossR" to j(k.avgLossR), "realRR" to j(k.realRR), "winRate" to j(k.winRate),
            "profitFactor" to j(k.profitFactor), "maxDD" to j(k.maxDD), "avgChk" to j(k.avgChk), "net" to j(k.net),
            "sumR" to j(k.sumR), "withdrawSuggest" to j(k.withdrawSuggest),
            "status" to JsonObject(k.status.mapValues { JsonPrimitive(it.value.name) }),
            "mistakeTally" to JsonObject(k.mistakeTally.mapValues { JsonPrimitive(it.value) }),
            "violationTally" to JsonObject(k.violationTally.mapValues { JsonPrimitive(it.value) }),
            "curve" to arr(rows.map { obj("id" to j(it.id), "cum" to j(it.cum), "peak" to j(it.peak), "dd" to j(it.dd)) }),
        )
        check(c.s("id"), got, c["expected"]!!)
    }

    @TestFactory
    fun jalali(): List<DynamicTest> {
        val o = Vectors.root["jalali"]!!.jsonObject
        val tests = ArrayList<DynamicTest>()
        o["pairs"]!!.jsonArray.map { it.jsonObject }.forEach { pr ->
            val g = LocalDate.parse(pr.s("gregorian"))
            val (jy, jm, jd) = pr.s("jalali").split("-").map { it.toInt() }
            tests += DynamicTest.dynamicTest("jalali.pair ${pr.s("gregorian")}") {
                assertEquals(JDate(jy, jm, jd), Jalali.toJalali(g))
                assertEquals(g, Jalali.toGregorian(jy, jm, jd))
            }
        }
        o["leapYears"]!!.jsonObject.forEach { (y, v) ->
            tests += DynamicTest.dynamicTest("jalali.leap $y") { assertEquals(v.jsonPrimitive.content.toBoolean(), Jalali.isLeap(y.toInt())) }
        }
        o["monthLengths"]!!.jsonObject.forEach { (y, v) ->
            tests += DynamicTest.dynamicTest("jalali.months $y") {
                assertEquals(v.jsonArray.map { it.jsonPrimitive.content.toInt() }, (1..12).map { Jalali.monthLength(y.toInt(), it) })
            }
        }
        o["weekdaySaturdayZero"]!!.jsonArray.map { it.jsonObject }.forEach { w ->
            tests += DynamicTest.dynamicTest("jalali.weekday ${w.s("date")}") {
                assertEquals(w.i("index"), Jalali.weekdayIndexSat0(LocalDate.parse(w.s("date"))))
            }
        }
        o["weekStart"]!!.jsonArray.map { it.jsonObject }.forEach { w ->
            tests += DynamicTest.dynamicTest("jalali.weekStart ${w.s("date")}") {
                assertEquals(LocalDate.parse(w.s("weekStart")), Jalali.weekStart(LocalDate.parse(w.s("date"))))
            }
        }
        return tests
    }

    @TestFactory
    fun format(): List<DynamicTest> {
        val o = Vectors.root["format"]!!.jsonObject
        val t = ArrayList<DynamicTest>()
        fun list(k: String) = o[k]!!.jsonArray.map { it.jsonObject }
        list("number").forEachIndexed { n, c ->
            t += DynamicTest.dynamicTest("format.number#${n + 1}") {
                assertEquals(c.s("expected"), Fmt.number(c.d("x"), c.i("decimals"), c.b("fa"), c.b("group")))
            }
        }
        list("signed").forEachIndexed { n, c ->
            t += DynamicTest.dynamicTest("format.signed#${n + 1}") {
                assertEquals(c.s("expected"), Fmt.signed(c.d("x"), c.i("decimals"), c.b("fa")))
            }
        }
        list("percent").forEachIndexed { n, c ->
            t += DynamicTest.dynamicTest("format.percent#${n + 1}") {
                assertEquals(c.s("expected"), Fmt.percent(c.d("x"), c.i("decimals"), c.b("fa"), c.b("signed")))
            }
        }
        list("normalizeInput").forEachIndexed { n, c ->
            t += DynamicTest.dynamicTest("format.normalizeInput#${n + 1}") {
                val exp = c.dOrNull("expected")
                val got = Fmt.normalizeInput(c.s("text"))
                if (exp == null) assertEquals(null, got, c.s("text")) else assertTrue(got != null && Vectors.closeEnough(got, exp), c.s("text"))
            }
        }
        list("persianDigits").forEachIndexed { n, c ->
            t += DynamicTest.dynamicTest("format.persianDigits#${n + 1}") { assertEquals(c.s("expected"), Fmt.toFaDigits(c.s("text"))) }
        }
        return t
    }

    @TestFactory
    fun renderClause(): List<DynamicTest> = named("renderClause") { c ->
        val i = c["input"]!!.jsonObject
        val refs = i["refs"]!!.jsonArray.map { val r = it.jsonObject; Ref(r.s("param"), r.s("literal"), r.s("template"), r.s("fmt")) }
        assertEquals(c.s("expected"), Clause.render(i.s("text"), refs, Vectors.params(i), Vectors.numberWords), c.s("id"))
    }

    companion object {
        fun instant(s: String?) = s?.let { OffsetDateTime.parse(it).toInstant() }

        /** A raw vector trade → [TradeRaw]. */
        fun tradeOf(o: JsonObject): TradeRaw {
            val ctx = o["ctx"] as? JsonObject
            return TradeRaw(
                id = o.s("id"),
                account = Account.valueOf(o.sOrNull("account") ?: "REAL"),
                setupId = o.sOrNull("setupId"),
                side = Side.valueOf(o.s("side")),
                entry = o.d("entry"), sl = o.d("sl"), tp = o.d("tp"), lev = o.d("lev"), eq = o.d("eq"),
                chk = o.i("chk"), gateAllowed = o.b("gateAllowed"), notional = o.d("notional"),
                entryTime = instant(o.s("entryTime"))!!,
                exitTime = instant(o.sOrNull("exitTime")),
                legs = o.legs(),
                fee = o.dOrNull("fee") ?: 0.0,
                exitReason = o.sOrNull("exitReason"),
                violations = o.strings("violations"),
                violationAt = instant(o.sOrNull("violationAt")),
                violationReport = o.sOrNull("violationReport"),
                mistake = o.sOrNull("mistake"),
                ctxRiskCap = ctx?.dOrNull("riskCap"),
                ctxGradeA = ctx?.bOrNull("gradeA") ?: true,
            )
        }

        @Suppress("unused")
        private val keepImports: List<Any> = listOf(JsonArray(emptyList()), JsonNull)
    }
}
