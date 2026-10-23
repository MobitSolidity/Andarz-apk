package ir.andarz.data.csv

import androidx.room.withTransaction
import ir.andarz.data.Audit.log
import ir.andarz.data.AuditType
import ir.andarz.data.ParamsRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.engine.Clock
import java.time.Instant
import java.time.ZoneId

/** CSV export and the two-step import of 9.2/9.3: preview first, then one transaction. */
class CsvRepository(private val db: AndarzDatabase, private val clock: Clock, private val params: ParamsRepository) {

    suspend fun exportExcel(vocab: CsvVocab, zone: ZoneId, realOnly: Boolean = true): String {
        val names = db.setups().all().associate { it.id to it.name }
        val shots = db.attachments().all().groupBy { it.tradeId }.mapValues { (_, l) -> l.minByOrNull { it.createdAt }?.fileName ?: "" }
        val trades = db.trades().all().filter { !realOnly || it.account == "REAL" }
        val out = CsvCodec(vocab, zone).exportExcel(trades, { names[it] ?: "" }, { shots[it] ?: "" })
        db.audit().log(clock.now().toEpochMilli(), AuditType.CSV_EXPORT, null, mapOf("profile" to "EXCEL", "rows" to trades.size))
        return out
    }

    suspend fun exportFull(vocab: CsvVocab, zone: ZoneId): String {
        val names = db.setups().all().associate { it.id to it.name }
        val events = db.events().all().groupBy { it.tradeId }
        val trades = db.trades().all()
        val out = CsvCodec(vocab, zone).exportFull(trades, { names[it] ?: "" }, { events[it].orEmpty() })
        db.audit().log(clock.now().toEpochMilli(), AuditType.CSV_EXPORT, null, mapOf("profile" to "FULL", "rows" to trades.size))
        return out
    }

    suspend fun preview(text: String, vocab: CsvVocab, zone: ZoneId): CsvImportPreview {
        val baseline = db.ledger().baselines().firstOrNull()?.time?.let(Instant::ofEpochMilli)
        return CsvCodec(vocab, zone).preview(text, db.trades().all(), db.setups().all(), params.current(), baseline, clock.now())
    }

    /**
     * Writes a preview in one transaction. [moveBaseline] is the 9.2 step 7 choice the user confirmed:
     * the new BASELINE time and amount; never applied silently.
     */
    suspend fun commit(p: CsvImportPreview, moveBaseline: Pair<Instant, Double>? = null) {
        val now = clock.now().toEpochMilli()
        db.withTransaction {
            p.newSetups.forEach { db.setups().upsert(it) }
            db.trades().insertAll(p.trades)
            if (moveBaseline != null) {
                val b = db.ledger().baselines().single()
                db.ledger().update(b.copy(time = moveBaseline.first.toEpochMilli(), amount = moveBaseline.second))
                db.audit().log(now, AuditType.LEDGER_EDIT, b.id, mapOf("op" to "BASELINE_IMPORT", "old" to b.amount, "new" to moveBaseline.second))
            }
            db.audit().log(now, AuditType.CSV_IMPORT, null, mapOf("profile" to p.profile.name, "rows" to p.trades.size, "skipped" to p.errors.size, "dups" to p.duplicates))
        }
    }
}
