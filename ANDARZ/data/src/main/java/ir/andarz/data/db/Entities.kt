package ir.andarz.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Tables of 4.2. Ids are random UUID strings, instants are epoch milliseconds (UTC), enumerations are the
 * string ids of `content.enums`. No Persian text is stored except what the user typed (4.1).
 * Field names of TradeEntity equal the `csv.full` columns.
 */

/** One exit leg: price, fraction of the original position, optional time and fee (4.2). */
@Serializable
data class LegRow(val price: Double, val fraction: Double, val time: Long? = null, val fee: Double = 0.0)

/** The engine plan, frozen when the plan is saved (4.4). */
@Serializable
data class PlanSnapshot(
    val sld: Double?,
    val rr: Double?,
    val riskUsd: Double,
    val notionalMax: Double?,
    val margin: Double?,
    val marginPct: Double?,
    val liq: Double?,
    val liqDist: Double?,
    val liqX: Double?,
)

@Serializable
@Entity(
    tableName = "trade",
    indices = [Index("status"), Index("entryTime"), Index("exitTime"), Index("setupId"), Index("account")],
)
data class TradeEntity(
    @PrimaryKey val id: String,
    val account: String,
    val status: String,
    val symbol: String,
    val side: String,
    val kind: String,
    val setupId: String?,
    val timeframe: String,
    val htfTrend: String,
    val counterTrendReason: String?,
    val emotion: String,
    val checklistMask: Int,
    val chk: Int,
    // ---- plan
    val equity: Double,
    val plannedEntry: Double,
    val slInitial: Double,
    val tp: Double,
    val lev: Double,
    val riskPctChosen: Double,
    val riskPctCap: Double,
    val gradeA: Boolean,
    val entryOrder: String,
    val isolated: Boolean,
    val slTpOnExchange: Boolean,
    val canMonitor: Boolean,
    @Embedded(prefix = "plan_") val plan: PlanSnapshot,
    val gateReasons: String,
    val gatePlanOk: Boolean,
    val entryAllowed: Boolean,
    val gateAllowed: Boolean,
    // ---- execution
    val entry: Double? = null,
    val notional: Double? = null,
    val entryTime: Long? = null,
    val slCurrent: Double? = null,
    val rrisk: Double? = null,
    val rriskp: Double? = null,
    val verdict: String? = null,
    // ---- exit
    val exitTime: Long? = null,
    val legs: List<LegRow> = emptyList(),
    val fee: Double = 0.0,
    val exitReason: String? = null,
    val gross: Double? = null,
    val net: Double? = null,
    val r: Double? = null,
    val result: String? = null,
    val equityAfter: Double? = null,
    val ok: Boolean? = null,
    // ---- review
    val violations: List<String> = emptyList(),
    val violationAt: Long? = null,
    val violationReport: String? = null,
    val mistake: String? = null,
    val emotionAfter: String? = null,
    val lesson: String? = null,
    val reviewedAt: Long? = null,
    // ---- bookkeeping
    val imported: Boolean = false,
    val loggedLate: Boolean = false,
    val monitorChecks: List<Long> = emptyList(),
    val editedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "trade_event",
    foreignKeys = [ForeignKey(entity = TradeEntity::class, parentColumns = ["id"], childColumns = ["tradeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tradeId")],
)
data class TradeEventEntity(
    @PrimaryKey val id: String,
    val tradeId: String,
    val time: Long,
    val type: String,
    /** JSON object, shape per type (4.2). */
    val payload: String,
)

@Serializable
@Entity(
    tableName = "attachment",
    foreignKeys = [ForeignKey(entity = TradeEntity::class, parentColumns = ["id"], childColumns = ["tradeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tradeId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val tradeId: String,
    val fileName: String,
    val mime: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "setup")
data class SetupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val entryRule: String,
    val invalidation: String,
    val target: String,
    val notes: String,
    val grade: String,
    val archived: Boolean,
    val testWaived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "watch_symbol", indices = [Index(value = ["symbol"], unique = true)])
data class WatchSymbolEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val volume24hUsd: Double?,
    val relVolume: Double?,
    val levels: String,
    val notes: String,
    val checkedAt: Long?,
    val addedAt: Long,
)

@Serializable
@Entity(tableName = "ledger_entry", indices = [Index("time")])
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val time: Long,
    val type: String,
    /** Positive, except ADJUSTMENT which is signed (3.8). */
    val amount: Double,
    val note: String,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "review", indices = [Index("type"), Index("periodStart")])
data class ReviewEntity(
    @PrimaryKey val id: String,
    val type: String,
    val completedAt: Long,
    /** ISO date: the day, the week's Saturday, the month start or the breach date (4.2). */
    val periodStart: String,
    val periodEnd: String,
    val payload: String,
    /** KPIs frozen at the time (law 6). */
    val kpiSnapshot: String,
)

@Serializable
@Entity(tableName = "param_change", indices = [Index("key"), Index("effectiveAt")])
data class ParamChangeEntity(
    @PrimaryKey val id: String,
    val key: String,
    val oldValue: Double,
    val newValue: Double,
    val kind: String,
    val appliedAt: Long,
    val effectiveAt: Long,
    val reason: String,
    val sessionId: String?,
    val cancelledAt: Long?,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "review_session")
data class ReviewSessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    /** USER | TIMEOUT | TRADE_OPENED */
    val endReason: String?,
)

@Serializable
@Entity(tableName = "pledge")
data class PledgeEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val name: String,
    val signedAt: Long,
    val startEquity: Double,
    val riskPct: Double,
    val textSnapshot: String,
)

@Serializable
@Entity(tableName = "security_check")
data class SecurityCheckEntity(
    @PrimaryKey val clauseId: String,
    val checked: Boolean,
    val checkedAt: Long?,
)

@Serializable
@Entity(tableName = "spot_asset")
data class SpotAssetEntity(
    @PrimaryKey val id: String,
    val asset: String,
    val thesis: String,
    val invalidation: String,
    val weightCap: Double,
    val weight: Double,
    val stagedNotes: String,
    val lastReviewAt: Long?,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "audit_log", indices = [Index("time"), Index("type")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val time: Long,
    val type: String,
    val refId: String?,
    @ColumnInfo(defaultValue = "{}") val payload: String,
)
