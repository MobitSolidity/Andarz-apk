package ir.andarz.engine

import ir.andarz.engine.Vectors.arr
import ir.andarz.engine.Vectors.d
import ir.andarz.engine.Vectors.dOrNull
import ir.andarz.engine.Vectors.j
import ir.andarz.engine.Vectors.obj
import ir.andarz.engine.Vectors.s
import ir.andarz.engine.Vectors.sOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.fail

/** Vector runner for the M2 categories: scenarios reviewSession paramChange paramsAt gradeA (10.1). */
class GovernorVectorTest {
    private val zone = ZoneOffset.ofHoursMinutes(3, 30)

    private fun iso(t: Instant?): JsonElement = j(t?.atOffset(zone)?.toString())

    private fun check(id: String, got: JsonElement, exp: JsonElement) {
        val d = Vectors.diff(id, got, exp)
        if (d.isNotEmpty()) fail(d.joinToString("\n"))
    }

    private fun named(category: String, block: (JsonObject) -> Unit): List<DynamicTest> =
        Vectors.cases(category).map { c -> DynamicTest.dynamicTest(c["id"]!!.jsonPrimitive.content) { block(c) } }

    @TestFactory
    fun scenarios(): List<DynamicTest> = named("scenarios") { c ->
        val i = c["input"]!!.jsonObject
        val s = Governor.snapshot(
            now = instant(i.s("now")), zone = zone, p = Vectors.params(i),
            ledger = ledgerOf(i), tradesRaw = i["trades"]!!.jsonArray.map { VectorRunnerTest.tradeOf(it.jsonObject) },
            reviews = reviewsOf(i), setups = setupsOf(i), setupId = i.sOrNull("setupId"),
        )
        check(c.s("id"), snapshotJson(s), c["expected"]!!)
    }

    @TestFactory
    fun reviewSession(): List<DynamicTest> = named("reviewSession") { c ->
        val i = c["input"]!!.jsonObject
        val p = Vectors.params(i)
        val trades = i["trades"]!!.jsonArray.map { Trades.resolve(VectorRunnerTest.tradeOf(it.jsonObject), p) }
        val r = Governor.canStartSession(instant(i.s("now")), trades, p)
        check(c.s("id"), obj("allowed" to j(r.allowed), "reason" to j(r.reason?.name), "until" to iso(r.until)), c["expected"]!!)
    }

    @TestFactory
    fun paramChange(): List<DynamicTest> = named("paramChange") { c ->
        val i = c["input"]!!.jsonObject
        val (at, kind) = Governor.changeEffectiveAt(i.s("key"), i.d("old"), i.d("new"), instant(i.s("appliedAt")), Vectors.params(i))
        check(c.s("id"), obj("kind" to j(kind.name), "effectiveAt" to iso(at)), c["expected"]!!)
    }

    @TestFactory
    fun paramsAt(): List<DynamicTest> = named("paramsAt") { c ->
        val i = c["input"]!!.jsonObject
        val changes = i["changes"]!!.jsonArray.map {
            val o = it.jsonObject
            ParamChange(o.s("key"), o.d("value"), instant(o.s("appliedAt")), instant(o.s("effectiveAt")))
        }
        val p = Governor.paramsAt(Params.DEFAULTS, changes, instant(i.s("at")))
        val exp = c["expected"]!!.jsonObject
        check(c.s("id"), JsonObject(exp.keys.associateWith { JsonPrimitive(p[it]) }), exp)
    }

    @TestFactory
    fun gradeA(): List<DynamicTest> = named("gradeA") { c ->
        val i = c["input"]!!.jsonObject
        val g = Governor.gradeAEligible(i["closedRs"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }, Vectors.params(i))
        check(c.s("id"), obj("eligible" to j(g.eligible), "n" to j(g.n), "expectancy" to j(g.expectancy)), c["expected"]!!)
    }

    private fun snapshotJson(s: Snapshot): JsonObject {
        val d = s.dashboard
        val g = s.governor
        return obj(
            "equity" to obj("now" to j(s.equity.now), "startOfDay" to j(s.equity.startOfDay), "startOfWeek" to j(s.equity.startOfWeek)),
            "dashboard" to obj(
                "tradesToday" to j(d.tradesToday), "dailyPct" to j(d.dailyPct), "weeklyPct" to j(d.weeklyPct),
                "drawdown" to j(d.drawdown), "openPositions" to j(d.openPositions), "openRiskPct" to j(d.openRiskPct),
                "status" to obj(
                    "trades" to j(d.status.trades.name), "daily" to j(d.status.daily.name), "weekly" to j(d.status.weekly.name),
                    "drawdown" to j(d.status.drawdown.name), "positions" to j(d.status.positions.name), "openRisk" to j(d.status.openRisk.name),
                ),
                "allowed" to j(d.allowed),
            ),
            "governor" to obj(
                "canEnter" to j(g.canEnter),
                "locks" to arr(g.locks.map { l ->
                    obj("code" to j(l.code.name), "until" to iso(l.until), "needs" to arr(l.needs.map { it.name }.sorted().map { JsonPrimitive(it) }))
                }),
                "riskCap" to j(g.riskCap),
                "reductions" to arr(g.reductions.sortedBy { it.code.name }.map {
                    obj("code" to j(it.code.name), "riskPct" to j(it.riskPct), "remaining" to j(it.remaining))
                }),
                "gradeAAllowed" to j(g.gradeAAllowed), "bigWinHoldUntil" to iso(g.bigWinHoldUntil),
                "tradesToday" to j(g.tradesToday), "openPositions" to j(g.openPositions),
            ),
        )
    }

    companion object {
        fun instant(s: String): Instant = java.time.OffsetDateTime.parse(s).toInstant()

        fun ledgerOf(i: JsonObject) = i["ledger"]!!.jsonArray.map {
            val o = it.jsonObject
            LedgerEntry(instant(o.s("time")), LedgerType.valueOf(o.s("type")), o.d("amount"))
        }

        fun reviewsOf(i: JsonObject) = (i["reviews"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())).map {
            val o = it.jsonObject
            Review(ReviewType.valueOf(o.s("type")), instant(o.s("completedAt")), o.sOrNull("weekStart")?.let(LocalDate::parse))
        }

        fun setupsOf(i: JsonObject) = (i["setups"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())).map {
            val o = it.jsonObject
            Setup(o.s("id"), o.s("grade"))
        }

        @Suppress("unused")
        private val keep = listOf<(JsonObject, String) -> Double?>({ o, k -> o.dOrNull(k) })
    }
}
