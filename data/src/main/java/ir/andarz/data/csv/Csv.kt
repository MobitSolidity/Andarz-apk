package ir.andarz.data.csv

import ir.andarz.data.EngineMapping
import ir.andarz.data.db.LegRow
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.TradeEventEntity
import ir.andarz.engine.Fmt
import ir.andarz.engine.Jalali
import ir.andarz.engine.Params
import ir.andarz.engine.Planner
import ir.andarz.engine.Side
import ir.andarz.engine.XlMath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/** RFC 4180 writer and reader with the rules of 9.1 (BOM, CRLF, formula protection). */
object CsvFormat {
    const val BOM = "\uFEFF"
    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    fun quote(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    /** Free-text cells only: a leading formula character gets a single quote (9.1). */
    fun protect(text: String): String = if (text.isNotEmpty() && text[0] in FORMULA_START) "'$text" else text

    fun unprotect(text: String): String = if (text.length >= 2 && text[0] == '\'' && text[1] in FORMULA_START) text.substring(1) else text

    fun write(rows: List<List<String>>): String = BOM + rows.joinToString("\r\n") { r -> r.joinToString(",") { quote(it) } } + "\r\n"

    /** Parses RFC 4180 text (BOM stripped); returns rows of cells. */
    fun parse(text: String): List<List<String>> {
        val s = text.removePrefix(BOM)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var i = 0
        var quoted = false
        while (i < s.length) {
            val ch = s[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row.add(cell.toString()); cell.clear() }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** Numbers: `.` decimal, no grouping, shortest round trip (9.1). */
    fun num(x: Double?): String = x?.let { java.math.BigDecimal(XlMath.shortest(it)).toPlainString() } ?: ""

    fun bool(b: Boolean?): String = when (b) { true -> "1"; false -> "0"; null -> "" }

    /** Header normalization of 9.2 step 1: trim, remove ZWNJ, Arabic yeh/kaf to Persian. */
    fun normalizeHeader(h: String): String =
        h.trim().replace("\u200C", "").replace('\u064A', '\u06CC').replace('\u0643', '\u06A9')
}

/**
 * Persian vocabulary of the CSV profiles, read from content by `:app` (no Persian in code, law 3):
 * the excel headers by key, the `csv` label of each enum id, and the yes/no/dash words.
 */
data class CsvVocab(
    val excelHeaders: List<Pair<String, String>>,
    val fullColumns: List<String>,
    val labels: Map<String, Map<String, String>>,
    val yes: String,
    val no: String,
    val dash: String,
) {
    companion object {
        /** From the content document: `csv.*.columns`, `enums.*[].csv` (or label) and `ui.common.yes|no|dash`. */
        fun fromContent(content: JsonObject): CsvVocab {
            val csv = content["csv"]!!.jsonObject
            fun cols(p: String) = csv[p]!!.jsonObject["columns"]!!.jsonArray.map { it.jsonObject }
            val enums = content["enums"]!!.jsonObject
            val labels = enums.filterValues { it is JsonArray }.mapValues { (_, arr) ->
                arr.jsonArray.associate { e ->
                    val o = e.jsonObject
                    o["id"]!!.jsonPrimitive.content to (o["csv"]?.jsonPrimitive?.content ?: o["label"]?.jsonPrimitive?.content ?: o["id"]!!.jsonPrimitive.content)
                }
            }
            val ui = content["ui"]!!.jsonObject
            return CsvVocab(
                excelHeaders = cols("excel").map { it["key"]!!.jsonPrimitive.content to it["header"]!!.jsonPrimitive.content },
                fullColumns = cols("full").map { it["key"]!!.jsonPrimitive.content },
                labels = labels,
                yes = ui["common.yes"]!!.jsonPrimitive.content,
                no = ui["common.no"]!!.jsonPrimitive.content,
                dash = ui["common.dash"]!!.jsonPrimitive.content,
            )
        }
    }

    fun label(enum: String, id: String?): String = id?.let { labels[enum]?.get(it) } ?: ""
    fun idOf(enum: String, label: String): String? {
        val l = label.trim()
        return labels[enum]?.entries?.firstOrNull { it.value.trim() == l || it.key == l }?.key
    }
}

enum class CsvProfile { EXCEL, FULL }

data class CsvRowError(val row: Int, val why: String)

data class CsvImportPreview(
    val profile: CsvProfile,
    val trades: List<TradeEntity>,
    val newSetups: List<SetupEntity>,
    val errors: List<CsvRowError>,
    val duplicates: Int,
    /** 9.2 step 7: earliest imported day, when it precedes the BASELINE. */
    val earliest: LocalDate?,
)

/** CSV export and import (9.2, 9.3). Pure over its inputs; the repository writes the result. */
class CsvCodec(private val vocab: CsvVocab, private val zone: ZoneId) {

    private fun date(ms: Long?): String = ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() } ?: ""
    private fun iso(ms: Long?): String = ms?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toString() } ?: ""
    private fun exitAvg(t: TradeEntity): Double? = if (t.legs.isEmpty()) null else t.legs.sumOf { it.price * it.fraction } / t.legs.sumOf { it.fraction }
    private fun bit(mask: Int, i: Int) = mask and (1 shl i) != 0

    // ------------------------------------------------------------------ export

    fun exportExcel(trades: List<TradeEntity>, setupName: (String?) -> String, firstShot: (String) -> String): String {
        val header = vocab.excelHeaders.map { it.second }
        val rows = trades.filter { it.status == "OPEN" || it.status == "CLOSED" }.sortedBy { it.entryTime }.map { t ->
            vocab.excelHeaders.map { (key, _) ->
                when (key) {
                    "date" -> date(t.entryTime)
                    "sym" -> CsvFormat.protect(t.symbol)
                    "side" -> vocab.label("side", t.side)
                    "kind" -> vocab.label("kind", t.kind)
                    "setup" -> CsvFormat.protect(setupName(t.setupId))
                    "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8" -> if (bit(t.checklistMask, key.substring(1).toInt() - 1)) vocab.yes else vocab.no
                    "emo" -> vocab.label("emotion", t.emotion)
                    "eq" -> CsvFormat.num(t.equity)
                    "entry" -> CsvFormat.num(t.entry ?: t.plannedEntry)
                    "sl" -> CsvFormat.num(t.slInitial)
                    "tp" -> CsvFormat.num(t.tp)
                    "lev" -> CsvFormat.num(t.lev)
                    "notl" -> CsvFormat.num(t.notional)
                    "exd" -> if (t.status == "CLOSED") date(t.exitTime) else ""
                    "exit" -> CsvFormat.num(exitAvg(t))
                    "fee" -> CsvFormat.num(t.fee + t.legs.sumOf { it.fee })
                    "viol" -> t.violations.firstOrNull()?.let { vocab.label("violation", it) } ?: vocab.dash
                    "mist" -> vocab.label("mistake", t.mistake)
                    "lesson" -> CsvFormat.protect(t.lesson ?: "")
                    "shot" -> CsvFormat.protect(firstShot(t.id))
                    else -> ""
                }
            }
        }
        return CsvFormat.write(listOf(header) + rows)
    }

    fun exportFull(trades: List<TradeEntity>, setupName: (String?) -> String, events: (String) -> List<TradeEventEntity>): String {
        val cols = vocab.fullColumns
        val rows = trades.sortedBy { it.entryTime ?: it.createdAt }.map { t ->
            val ev = events(t.id)
            cols.map { key ->
                when (key) {
                    "id" -> t.id; "account" -> t.account; "status" -> t.status; "symbol" -> CsvFormat.protect(t.symbol)
                    "side" -> t.side; "kind" -> t.kind; "setupId" -> t.setupId ?: ""; "setupName" -> CsvFormat.protect(setupName(t.setupId))
                    "timeframe" -> t.timeframe; "htfTrend" -> t.htfTrend; "emotion" -> t.emotion
                    "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8" -> if (bit(t.checklistMask, key.substring(1).toInt() - 1)) "1" else "0"
                    "chk" -> t.chk.toString(); "equity" -> CsvFormat.num(t.equity); "plannedEntry" -> CsvFormat.num(t.plannedEntry)
                    "entry" -> CsvFormat.num(t.entry); "slInitial" -> CsvFormat.num(t.slInitial); "tp" -> CsvFormat.num(t.tp)
                    "lev" -> CsvFormat.num(t.lev); "riskPctCap" -> CsvFormat.num(t.riskPctCap); "gateAllowed" -> CsvFormat.bool(t.gateAllowed)
                    "gateReasons" -> t.gateReasons; "notional" -> CsvFormat.num(t.notional); "entryOrder" -> t.entryOrder
                    "entryTime" -> iso(t.entryTime); "exitTime" -> iso(t.exitTime)
                    "legs" -> JsonArray(t.legs.map { JsonObject(mapOf("price" to JsonPrimitive(it.price), "fraction" to JsonPrimitive(it.fraction), "fee" to JsonPrimitive(it.fee))) }).toString()
                    "fee" -> CsvFormat.num(t.fee); "exitReason" -> t.exitReason ?: ""; "gross" -> CsvFormat.num(t.gross); "net" -> CsvFormat.num(t.net)
                    "R" -> CsvFormat.num(t.r); "verdict" -> t.verdict ?: ""; "ok" -> CsvFormat.bool(t.ok)
                    "violations" -> t.violations.joinToString("|"); "violationAt" -> iso(t.violationAt)
                    "violationReport" -> CsvFormat.protect(t.violationReport ?: ""); "mistake" -> t.mistake ?: ""
                    "emotionAfter" -> t.emotionAfter ?: ""; "lesson" -> CsvFormat.protect(t.lesson ?: "")
                    "slMoves" -> JsonArray(ev.filter { it.type == "SL_MOVE" }.map { Json.parseToJsonElement(it.payload) }).toString()
                    "events" -> JsonArray(ev.map { JsonObject(mapOf("time" to JsonPrimitive(it.time), "type" to JsonPrimitive(it.type), "payload" to Json.parseToJsonElement(it.payload))) }).toString()
                    "imported" -> CsvFormat.bool(t.imported)
                    else -> ""
                }
            }
        }
        return CsvFormat.write(listOf(cols) + rows)
    }

    // ------------------------------------------------------------------ import

    fun detect(header: List<String>): CsvProfile? {
        val h = header.map(CsvFormat::normalizeHeader).toSet()
        if (vocab.fullColumns.count { it in h } >= REQUIRED_FULL.size && REQUIRED_FULL.all { it in h }) return CsvProfile.FULL
        val excel = vocab.excelHeaders.associate { CsvFormat.normalizeHeader(it.second) to it.first }
        if (REQUIRED_EXCEL.all { k -> excel.entries.any { it.value == k && it.key in h } }) return CsvProfile.EXCEL
        return null
    }

    /**
     * Builds the import preview. [existing] are trades already stored (for duplicates), [setups] the known
     * setups (matched by name), [p] the parameters in force now, [baseline] the BASELINE time.
     */
    fun preview(text: String, existing: List<TradeEntity>, setups: List<SetupEntity>, p: Params, baseline: Instant?, now: Instant): CsvImportPreview {
        val rows = CsvFormat.parse(text)
        if (rows.isEmpty()) throw CsvHeaderException()
        val profile = detect(rows[0]) ?: throw CsvHeaderException()
        val header = rows[0].map(CsvFormat::normalizeHeader)
        val keyOf: Map<Int, String> = if (profile == CsvProfile.FULL) header.withIndex().associate { it.index to it.value }
        else {
            val byHeader = vocab.excelHeaders.associate { CsvFormat.normalizeHeader(it.second) to it.first }
            header.withIndex().mapNotNull { (i, h) -> byHeader[h]?.let { i to it } }.toMap()
        }
        val errors = ArrayList<CsvRowError>()
        val out = ArrayList<TradeEntity>()
        val newSetups = LinkedHashMap<String, SetupEntity>()
        var dups = 0
        val keys = existing.map(::dupKey).toMutableSet()
        for ((idx, cells) in rows.drop(1).withIndex()) {
            val rowNo = idx + 2
            val m = HashMap<String, String>()
            cells.forEachIndexed { i, c -> keyOf[i]?.let { m[it] = c.trim() } }
            val r = try {
                if (profile == CsvProfile.EXCEL) excelRow(m, idx, setups, newSetups, p, now) else fullRow(m, setups, newSetups, p, now)
            } catch (e: RowException) { errors += CsvRowError(rowNo, e.why); null } ?: continue
            val k = dupKey(r)
            if (!keys.add(k)) { dups++; continue }
            out += r
        }
        val earliest = out.mapNotNull { it.entryTime }.minOrNull()?.let { Instant.ofEpochMilli(it) }
        val before = if (earliest != null && baseline != null && earliest < baseline) earliest.atZone(zone).toLocalDate() else null
        return CsvImportPreview(profile, out, newSetups.values.toList(), errors, dups, before)
    }

    private fun dupKey(t: TradeEntity) = listOf(t.symbol, t.side, t.entry ?: t.plannedEntry, t.slInitial, t.tp, date(t.entryTime), t.notional).joinToString("|")

    private class RowException(val why: String) : Exception(why)

    private fun need(m: Map<String, String>, k: String): Double {
        val v = m[k]?.takeIf { it.isNotBlank() } ?: throw RowException("missing:$k")
        return Fmt.normalizeInput(v) ?: throw RowException("number:$k")
    }

    /** Gregorian `yyyy-mm-dd` or Jalali `yyyy/mm/dd` (year < 1700 means Jalali) (9.2 step 2). */
    fun parseDate(s: String): LocalDate? {
        val parts = Fmt.toLatinDigits(s.trim()).split('-', '/').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        val (y, mo, d) = parts
        return runCatching { if (y < 1700) Jalali.toGregorian(y, mo, d) else LocalDate.of(y, mo, d) }.getOrNull()
    }

    private fun setupId(name: String, setups: List<SetupEntity>, created: MutableMap<String, SetupEntity>, now: Instant): String? {
        val n = name.trim().takeIf { it.isNotEmpty() && it != vocab.dash } ?: return null
        setups.firstOrNull { it.name.trim() == n }?.let { return it.id }
        return created.getOrPut(n) { SetupEntity(UUID.randomUUID().toString(), n, "", "", "", "", "B", false, false, now.toEpochMilli(), now.toEpochMilli()) }.id
    }

    private fun excelRow(m: Map<String, String>, idx: Int, setups: List<SetupEntity>, created: MutableMap<String, SetupEntity>, p: Params, now: Instant): TradeEntity {
        val sym = CsvFormat.unprotect(m["sym"].orEmpty()).trim().uppercase(java.util.Locale.ROOT).takeIf { it.isNotEmpty() } ?: throw RowException("missing:sym")
        val side = vocab.idOf("side", m["side"].orEmpty()) ?: throw RowException("missing:side")
        val eq = need(m, "eq"); val entry = need(m, "entry"); val sl = need(m, "sl"); val tp = need(m, "tp"); val lev = need(m, "lev"); val notl = need(m, "notl")
        if (entry == sl) throw RowException("entryEqSl")
        if (listOf(eq, entry, sl, tp, lev, notl).any { it <= 0 }) throw RowException("positive")
        val day = m["date"]?.let(::parseDate) ?: throw RowException("date")
        val entryTime = day.atTime(12, 0).atZone(zone).toInstant().plusSeconds(idx.toLong())
        val exitPrice = m["exit"]?.takeIf { it.isNotBlank() }?.let { Fmt.normalizeInput(it) ?: throw RowException("number:exit") }
        var exitTime: Instant? = null
        if (exitPrice != null) {
            val exDay = m["exd"]?.takeIf { it.isNotBlank() }?.let { parseDate(it) ?: throw RowException("date") } ?: day
            exitTime = exDay.atTime(12, 0).atZone(zone).toInstant().plusSeconds(idx.toLong())
            if (exDay.isBefore(day)) throw RowException("dateOrder")
            if (exitTime < entryTime.plusSeconds(60)) exitTime = entryTime.plusSeconds(60)
        }
        var mask = 0
        for (i in 1..8) if (m["c$i"]?.trim() == vocab.yes) mask = mask or (1 shl (i - 1))
        val fee = m["fee"]?.takeIf { it.isNotBlank() }?.let { Fmt.normalizeInput(it) } ?: 0.0
        val violation = m["viol"]?.trim()?.takeIf { it.isNotEmpty() && it != vocab.dash }?.let { vocab.idOf("violation", it) }
        val mistLabel = m["mist"]?.trim().orEmpty()
        val mistake = if (mistLabel.isEmpty()) null else vocab.idOf("mistake", mistLabel) ?: "M00"
        val lessonBase = CsvFormat.unprotect(m["lesson"].orEmpty())
        val lesson = if (mistLabel.isNotEmpty() && vocab.idOf("mistake", mistLabel) == null) listOf(lessonBase, mistLabel).filter { it.isNotBlank() }.joinToString(" \u00B7 ") else lessonBase
        return build(
            sym, Side.valueOf(side), vocab.idOf("kind", m["kind"].orEmpty()) ?: "INTRADAY", setupId(m["setup"].orEmpty(), setups, created, now),
            vocab.idOf("emotion", m["emo"].orEmpty()) ?: "CALM", mask, eq, entry, sl, tp, lev, notl, entryTime, exitTime,
            exitPrice?.let { listOf(LegRow(it, 1.0)) }.orEmpty(), fee, listOfNotNull(violation), mistake, lesson.takeIf { it.isNotBlank() }, p, now,
            exitReason = exitPrice?.let { if (it == tp) "TAKE_PROFIT" else if (it == sl) "STOP_LOSS" else "MANUAL" },
        )
    }

    private fun fullRow(m: Map<String, String>, setups: List<SetupEntity>, created: MutableMap<String, SetupEntity>, p: Params, now: Instant): TradeEntity {
        val sym = CsvFormat.unprotect(m["symbol"].orEmpty()).trim().uppercase(java.util.Locale.ROOT).takeIf { it.isNotEmpty() } ?: throw RowException("missing:symbol")
        val side = m["side"]?.takeIf { it == "LONG" || it == "SHORT" } ?: throw RowException("missing:side")
        val eq = need(m, "equity"); val entry = m["entry"]?.takeIf { it.isNotBlank() }?.let { Fmt.normalizeInput(it) } ?: need(m, "plannedEntry")
        val sl = need(m, "slInitial"); val tp = need(m, "tp"); val lev = need(m, "lev"); val notl = need(m, "notional")
        if (entry == sl) throw RowException("entryEqSl")
        val entryTime = m["entryTime"]?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: throw RowException("date")
        val exitTime = m["exitTime"]?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: throw RowException("date") }
        if (exitTime != null && exitTime < entryTime) throw RowException("dateOrder")
        val legs = m["legs"]?.takeIf { it.isNotBlank() }?.let { j ->
            runCatching { Json.parseToJsonElement(j).jsonArray.map { o -> o.jsonObject.let { LegRow(it["price"]!!.jsonPrimitive.content.toDouble(), it["fraction"]!!.jsonPrimitive.content.toDouble(), null, it["fee"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0) } } }.getOrNull()
                ?: throw RowException("legs")
        }.orEmpty()
        var mask = 0
        for (i in 1..8) if (m["c$i"] == "1") mask = mask or (1 shl (i - 1))
        if (mask == 0 && m["chk"] != null) mask = (1 shl (m["chk"]!!.toIntOrNull() ?: 0).coerceIn(0, 8)) - 1
        val setup = m["setupName"]?.let { CsvFormat.unprotect(it) }?.takeIf { it.isNotBlank() }?.let { setupId(it, setups, created, now) }
            ?: m["setupId"]?.takeIf { id -> setups.any { it.id == id } }
        val t = build(
            sym, Side.valueOf(side), m["kind"]?.takeIf { it.isNotBlank() } ?: "INTRADAY", setup, m["emotion"]?.takeIf { it.isNotBlank() } ?: "CALM",
            mask, eq, entry, sl, tp, lev, notl, entryTime, exitTime, if (exitTime != null) legs else emptyList(), m["fee"]?.let { Fmt.normalizeInput(it) } ?: 0.0,
            m["violations"]?.split('|')?.filter { it.isNotBlank() }.orEmpty(), m["mistake"]?.takeIf { it.isNotBlank() },
            m["lesson"]?.let { CsvFormat.unprotect(it) }?.takeIf { it.isNotBlank() }, p, now,
            exitReason = m["exitReason"]?.takeIf { it.isNotBlank() },
        )
        // D-24: persisted derived columns win; recompute only what is missing.
        return t.copy(
            id = m["id"]?.takeIf { it.isNotBlank() } ?: t.id,
            account = m["account"]?.takeIf { it == "REAL" || it == "DEMO" } ?: t.account,
            timeframe = m["timeframe"]?.takeIf { it.isNotBlank() } ?: t.timeframe,
            htfTrend = m["htfTrend"]?.takeIf { it.isNotBlank() } ?: t.htfTrend,
            entryOrder = m["entryOrder"]?.takeIf { it.isNotBlank() } ?: t.entryOrder,
            plannedEntry = m["plannedEntry"]?.let { Fmt.normalizeInput(it) } ?: t.plannedEntry,
            riskPctCap = m["riskPctCap"]?.let { Fmt.normalizeInput(it) } ?: t.riskPctCap,
            gateAllowed = m["gateAllowed"]?.let { it == "1" } ?: t.gateAllowed,
            gateReasons = m["gateReasons"] ?: t.gateReasons,
            gross = m["gross"]?.let { Fmt.normalizeInput(it) } ?: t.gross,
            net = m["net"]?.let { Fmt.normalizeInput(it) } ?: t.net,
            r = m["R"]?.let { Fmt.normalizeInput(it) } ?: t.r,
            verdict = m["verdict"]?.takeIf { it.isNotBlank() } ?: t.verdict,
            ok = m["ok"]?.takeIf { it.isNotBlank() }?.let { it == "1" } ?: t.ok,
            violationAt = m["violationAt"]?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: t.violationAt,
            violationReport = m["violationReport"]?.let { CsvFormat.unprotect(it) }?.takeIf { it.isNotBlank() } ?: t.violationReport,
            emotionAfter = m["emotionAfter"]?.takeIf { it.isNotBlank() } ?: t.emotionAfter,
            imported = m["imported"]?.let { it != "0" } ?: true,
        )
    }

    /** A complete trade row from imported numbers; derived values computed once and persisted (9.2 step 4). */
    private fun build(
        sym: String, side: Side, kind: String, setupId: String?, emotion: String, mask: Int, eq: Double, entry: Double, sl: Double,
        tp: Double, lev: Double, notl: Double, entryTime: Instant, exitTime: Instant?, legs: List<LegRow>, fee: Double,
        violations: List<String>, mistake: String?, lesson: String?, p: Params, now: Instant, exitReason: String?,
    ): TradeEntity {
        val chk = Integer.bitCount(mask and 0xFF)
        val plan = Planner.plan(side, eq, entry, sl, tp, lev, p, p.riskPct)
        val reasons = Planner.gate(side, entry, sl, tp, lev, chk, plan, p)
        val gateOk = reasons.isEmpty()
        val sld = kotlin.math.abs(entry - sl) / entry
        val rrisk = notl * sld
        val rriskp = rrisk / eq
        val verdict = Planner.sizeVerdict(rriskp, p, p.riskPct, true)
        val closed = exitTime != null && legs.isNotEmpty()
        if (closed && kotlin.math.abs(legs.sumOf { it.fraction } - 1.0) > 1e-9) throw RowException("legs")
        val fill = if (closed) Planner.fill(side, entry, sl, eq, notl, legs.map { ir.andarz.engine.Leg(it.price, it.fraction) }, fee + legs.sumOf { it.fee }) else null
        val ok = if (closed) Planner.compliant(violations, gateOk, verdict) else null
        val nowMs = now.toEpochMilli()
        return TradeEntity(
            id = UUID.randomUUID().toString(), account = "REAL", status = if (closed) "CLOSED" else "OPEN", symbol = sym, side = side.name,
            kind = kind, setupId = setupId, timeframe = "15m", htfTrend = "RANGE", counterTrendReason = null, emotion = emotion,
            checklistMask = mask, chk = chk, equity = eq, plannedEntry = entry, slInitial = sl, tp = tp, lev = lev,
            riskPctChosen = p.riskPct, riskPctCap = p.riskPct, gradeA = true, entryOrder = "LIMIT", isolated = true,
            slTpOnExchange = true, canMonitor = true, plan = EngineMapping.snapshotOf(plan), gateReasons = reasons.joinToString("|") { it.name },
            gatePlanOk = gateOk, entryAllowed = gateOk, gateAllowed = gateOk, entry = entry, notional = notl,
            entryTime = entryTime.toEpochMilli(), slCurrent = sl, rrisk = rrisk, rriskp = rriskp, verdict = verdict.name,
            exitTime = if (closed) exitTime!!.toEpochMilli() else null, legs = if (closed) legs else emptyList(), fee = fee,
            exitReason = if (closed) exitReason else null, gross = fill?.gross, net = fill?.net, r = fill?.r,
            result = fill?.net?.let { if (it > 0) "WIN" else if (it < 0) "LOSS" else "EVEN" }, equityAfter = fill?.equityAfter, ok = ok,
            violations = violations, violationAt = if (violations.isNotEmpty()) (exitTime ?: entryTime).toEpochMilli() else null,
            mistake = mistake, lesson = lesson, reviewedAt = if (closed && mistake != null) nowMs else null,
            imported = true, createdAt = nowMs, updatedAt = nowMs,
        )
    }

    companion object {
        val REQUIRED_EXCEL = listOf("sym", "side", "eq", "entry", "sl", "tp", "lev", "notl")
        val REQUIRED_FULL = listOf("id", "symbol", "side", "equity", "slInitial", "tp", "lev", "notional", "entryTime")
    }
}

class CsvHeaderException : Exception("csv header")
