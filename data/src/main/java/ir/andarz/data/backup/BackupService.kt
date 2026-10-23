package ir.andarz.data.backup

import androidx.room.withTransaction
import ir.andarz.data.Audit.log
import ir.andarz.data.AuditType
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.db.AttachmentEntity
import ir.andarz.data.db.AuditLogEntity
import ir.andarz.data.db.LedgerEntryEntity
import ir.andarz.data.db.ParamChangeEntity
import ir.andarz.data.db.PledgeEntity
import ir.andarz.data.db.ReviewEntity
import ir.andarz.data.db.ReviewSessionEntity
import ir.andarz.data.db.SecurityCheckEntity
import ir.andarz.data.db.SetupEntity
import ir.andarz.data.db.SpotAssetEntity
import ir.andarz.data.db.TradeEntity
import ir.andarz.data.db.TradeEventEntity
import ir.andarz.data.db.WatchSymbolEntity
import ir.andarz.engine.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId

/** The tables of the backup payload (8.3), by table name. Every list defaults to empty (forward compatible). */
@Serializable
data class BackupTables(
    val trade: List<TradeEntity> = emptyList(),
    val trade_event: List<TradeEventEntity> = emptyList(),
    val attachment: List<AttachmentEntity> = emptyList(),
    val setup: List<SetupEntity> = emptyList(),
    val watch_symbol: List<WatchSymbolEntity> = emptyList(),
    val ledger_entry: List<LedgerEntryEntity> = emptyList(),
    val review: List<ReviewEntity> = emptyList(),
    val param_change: List<ParamChangeEntity> = emptyList(),
    val review_session: List<ReviewSessionEntity> = emptyList(),
    val pledge: List<PledgeEntity> = emptyList(),
    val security_check: List<SecurityCheckEntity> = emptyList(),
    val spot_asset: List<SpotAssetEntity> = emptyList(),
    val audit_log: List<AuditLogEntity> = emptyList(),
    val trade_alert: List<ir.andarz.data.db.TradeAlertEntity> = emptyList(),
)

/**
 * The payload of 8.3 and the JSON export of 9.4. `settings` is an opaque object owned by `:app` (it excludes
 * the PIN, the lock state and the permission state); `market` is the `:market` settings document (MK3); `files`
 * maps attachment ids to base64 JPEG and is present only with screenshots (D-13).
 */
@Serializable
data class BackupDocument(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long = 0,
    val appVersion: String = "",
    val zone: String = "",
    val tables: BackupTables = BackupTables(),
    val settings: JsonObject = JsonObject(emptyMap()),
    val market: JsonElement? = null,
    val files: Map<String, String> = emptyMap(),
) {
    companion object { const val FORMAT = "andarz-backup" }
}

/** Counts and date range of a backup, shown before the typed confirmation (8.3). */
data class BackupSummary(val trades: Int, val ledger: Int, val reviews: Int, val firstEntry: Instant?, val lastEntry: Instant?, val createdAt: Instant, val withShots: Boolean)

/** Validation failures of a decrypted payload (8.3 "validate"). */
class InvalidBackup(val why: String) : Exception(why)

/**
 * Builds and restores the backup document. Encryption is [BackupCrypto]; file IO (SAF, screenshots) is done
 * by `:app`, which passes bytes in and out. Restore replaces all tables in one Room transaction (8.3).
 */
class BackupService(private val db: AndarzDatabase, private val clock: Clock) {

    suspend fun snapshot(appVersion: String, zone: ZoneId, settings: JsonObject, market: JsonElement?, files: Map<String, String> = emptyMap()): BackupDocument =
        BackupDocument(
            createdAt = clock.now().toEpochMilli(), appVersion = appVersion, zone = zone.id,
            tables = BackupTables(
                db.trades().all(), db.events().all(), db.attachments().all(), db.setups().all(), db.watch().all(),
                db.ledger().all(), db.reviews().all(), db.paramChanges().all(), db.sessions().all(), db.pledges().all(),
                db.security().all(), db.spot().all(), db.audit().all(), db.tradeAlerts().all(),
            ),
            settings = settings, market = market, files = files,
        )

    fun encode(doc: BackupDocument, pretty: Boolean = false): String = (if (pretty) PRETTY else JSON).encodeToString(BackupDocument.serializer(), doc)

    fun decode(json: String): BackupDocument {
        val doc = try { JSON.decodeFromString(BackupDocument.serializer(), json) } catch (e: Exception) { throw InvalidBackup("json") }
        if (doc.format != BackupDocument.FORMAT) throw BackupError.NotAndarz()
        if (doc.version > 1) throw BackupError.Version()
        return doc
    }

    /** Encrypted container bytes of the current data. */
    suspend fun export(password: String, appVersion: String, zone: ZoneId, settings: JsonObject, market: JsonElement?, files: Map<String, String>, iterations: Int = BackupCrypto.ITERATIONS): ByteArray {
        val doc = snapshot(appVersion, zone, settings, market, files)
        val bytes = BackupCrypto.seal(encode(doc).toByteArray(Charsets.UTF_8), password, files.isNotEmpty(), iterations)
        db.audit().log(clock.now().toEpochMilli(), AuditType.BACKUP_EXPORT, null, mapOf("trades" to doc.tables.trade.size, "shots" to files.size))
        return bytes
    }

    /** Decrypts and validates; nothing is written. */
    fun open(file: ByteArray, password: String): BackupDocument {
        val opened = BackupCrypto.open(file, password)
        val doc = decode(opened.payloadJson.toString(Charsets.UTF_8))
        validate(doc)
        return doc
    }

    fun summary(doc: BackupDocument): BackupSummary {
        val entries = doc.tables.trade.mapNotNull { it.entryTime }
        return BackupSummary(
            doc.tables.trade.size, doc.tables.ledger_entry.size, doc.tables.review.size,
            entries.minOrNull()?.let(Instant::ofEpochMilli), entries.maxOrNull()?.let(Instant::ofEpochMilli),
            Instant.ofEpochMilli(doc.createdAt), doc.files.isNotEmpty(),
        )
    }

    /** One BASELINE, unique ids, legs summing to 1, known enums (8.3). */
    fun validate(doc: BackupDocument) {
        val t = doc.tables
        if (t.ledger_entry.count { it.type == "BASELINE" } != 1) throw InvalidBackup("baseline")
        fun <T> unique(rows: List<T>, id: (T) -> String, name: String) { if (rows.map(id).toSet().size != rows.size) throw InvalidBackup("ids:$name") }
        unique(t.trade, { it.id }, "trade"); unique(t.trade_event, { it.id }, "trade_event"); unique(t.setup, { it.id }, "setup")
        unique(t.ledger_entry, { it.id }, "ledger"); unique(t.review, { it.id }, "review"); unique(t.param_change, { it.id }, "param_change")
        unique(t.attachment, { it.id }, "attachment"); unique(t.audit_log, { it.id }, "audit")
        val ids = t.trade.map { it.id }.toSet()
        for (tr in t.trade) {
            if (tr.status !in STATUSES || tr.side !in SIDES || tr.account !in ACCOUNTS) throw InvalidBackup("enum:${tr.id}")
            if (tr.status == "CLOSED" && kotlin.math.abs(tr.legs.sumOf { it.fraction } - 1.0) > 1e-9) throw InvalidBackup("legs:${tr.id}")
        }
        if (t.ledger_entry.any { it.type !in LEDGER }) throw InvalidBackup("enum:ledger")
        if (t.review.any { it.type !in REVIEWS }) throw InvalidBackup("enum:review")
        if (t.trade_event.any { it.tradeId !in ids } || t.attachment.any { it.tradeId !in ids }) throw InvalidBackup("orphans")
    }

    /** Replace-all in one transaction; a failure leaves the database unchanged (8.3). */
    suspend fun restore(doc: BackupDocument, beforeCommit: suspend () -> Unit = {}) {
        validate(doc)
        val t = doc.tables
        db.withTransaction {
            clearAll()
            db.trades().insertAll(t.trade)
            db.events().insertAll(t.trade_event)
            db.attachments().insertAll(t.attachment)
            db.setups().insertAll(t.setup)
            db.watch().insertAll(t.watch_symbol)
            db.ledger().insertAll(t.ledger_entry)
            db.reviews().insertAll(t.review)
            db.paramChanges().insertAll(t.param_change)
            db.sessions().insertAll(t.review_session)
            db.pledges().insertAll(t.pledge)
            db.security().insertAll(t.security_check)
            db.spot().insertAll(t.spot_asset)
            db.audit().insertAll(t.audit_log)
            db.tradeAlerts().insertAll(t.trade_alert.filter { a -> t.trade.any { it.id == a.tradeId && it.status == "OPEN" } })
            beforeCommit()
            db.audit().log(clock.now().toEpochMilli(), AuditType.BACKUP_RESTORE, null, mapOf("trades" to t.trade.size, "createdAt" to doc.createdAt))
        }
    }

    /** Wipe (5.15): every table emptied in one transaction; one DATA_WIPE row remains. */
    suspend fun wipe() {
        db.withTransaction {
            clearAll()
            db.audit().log(clock.now().toEpochMilli(), AuditType.DATA_WIPE, null)
        }
    }

    private suspend fun clearAll() {
        db.tradeAlerts().deleteAll(); db.events().deleteAll(); db.attachments().deleteAll(); db.trades().deleteAll()
        db.setups().deleteAll(); db.watch().deleteAll(); db.ledger().deleteAll(); db.reviews().deleteAll()
        db.paramChanges().deleteAll(); db.sessions().deleteAll(); db.pledges().deleteAll(); db.security().deleteAll()
        db.spot().deleteAll(); db.audit().deleteAll()
    }

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }
        val PRETTY = Json(JSON) { prettyPrint = true }
        private val STATUSES = setOf("PLANNED", "OPEN", "CLOSED", "REJECTED", "CANCELLED")
        private val SIDES = setOf("LONG", "SHORT")
        private val ACCOUNTS = setOf("REAL", "DEMO")
        private val LEDGER = setOf("BASELINE", "DEPOSIT", "WITHDRAWAL", "ADJUSTMENT")
        private val REVIEWS = setOf("DAILY", "WEEKLY", "MONTHLY", "DRAWDOWN")

        /** `andarz-backup-YYYYMMDD-HHMM.andz` (Gregorian, Latin digits) (8.3). */
        fun fileName(t: Instant, zone: ZoneId): String {
            val z = t.atZone(zone)
            return "andarz-backup-%04d%02d%02d-%02d%02d.andz".format(java.util.Locale.ROOT, z.year, z.monthValue, z.dayOfMonth, z.hour, z.minute)
        }
    }
}
