package ir.andarz.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Insert suspend fun insertAll(rows: List<TradeEntity>)
    @Insert suspend fun insert(t: TradeEntity)
    @Update suspend fun update(t: TradeEntity)
    @Delete suspend fun delete(t: TradeEntity)
    @Query("SELECT * FROM trade WHERE id = :id") suspend fun get(id: String): TradeEntity?
    @Query("SELECT * FROM trade WHERE id = :id") fun observe(id: String): Flow<TradeEntity?>
    @Query("SELECT * FROM trade ORDER BY COALESCE(entryTime, createdAt) DESC") fun observeAll(): Flow<List<TradeEntity>>
    @Query("SELECT * FROM trade") suspend fun all(): List<TradeEntity>
    /** Only OPEN and CLOSED rows reach the engine (4.2). */
    @Query("SELECT * FROM trade WHERE status IN ('OPEN','CLOSED')") suspend fun engineRows(): List<TradeEntity>
    @Query("SELECT * FROM trade WHERE status IN ('OPEN','CLOSED')") fun observeEngineRows(): Flow<List<TradeEntity>>
    @Query("SELECT * FROM trade WHERE status = 'PLANNED' AND createdAt < :before") suspend fun plannedBefore(before: Long): List<TradeEntity>
    @Query("DELETE FROM trade") suspend fun deleteAll()
}

@Dao
interface TradeEventDao {
    @Insert suspend fun insertAll(rows: List<TradeEventEntity>)
    @Query("DELETE FROM trade_event") suspend fun deleteAll()
    @Insert suspend fun insert(e: TradeEventEntity)
    @Query("SELECT * FROM trade_event WHERE tradeId = :tradeId ORDER BY time") suspend fun forTrade(tradeId: String): List<TradeEventEntity>
    @Query("SELECT * FROM trade_event WHERE tradeId = :tradeId ORDER BY time DESC") fun observeForTrade(tradeId: String): Flow<List<TradeEventEntity>>
    @Query("SELECT * FROM trade_event") suspend fun all(): List<TradeEventEntity>
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insertAll(rows: List<AttachmentEntity>)
    @Query("DELETE FROM attachment") suspend fun deleteAll()
    @Insert suspend fun insert(a: AttachmentEntity)
    @Delete suspend fun delete(a: AttachmentEntity)
    @Query("SELECT * FROM attachment WHERE tradeId = :tradeId ORDER BY createdAt") suspend fun forTrade(tradeId: String): List<AttachmentEntity>
    @Query("SELECT * FROM attachment") suspend fun all(): List<AttachmentEntity>
}

@Dao
interface SetupDao {
    @Insert suspend fun insertAll(rows: List<SetupEntity>)
    @Query("DELETE FROM setup") suspend fun deleteAll()
    @Upsert suspend fun upsert(s: SetupEntity)
    @Query("SELECT * FROM setup WHERE id = :id") suspend fun get(id: String): SetupEntity?
    @Query("SELECT * FROM setup ORDER BY archived, name") fun observeAll(): Flow<List<SetupEntity>>
    @Query("SELECT * FROM setup") suspend fun all(): List<SetupEntity>
}

@Dao
interface WatchSymbolDao {
    @Insert suspend fun insertAll(rows: List<WatchSymbolEntity>)
    @Query("DELETE FROM watch_symbol") suspend fun deleteAll()
    @Upsert suspend fun upsert(w: WatchSymbolEntity)
    @Delete suspend fun delete(w: WatchSymbolEntity)
    @Query("SELECT * FROM watch_symbol ORDER BY addedAt") fun observeAll(): Flow<List<WatchSymbolEntity>>
    @Query("SELECT * FROM watch_symbol") suspend fun all(): List<WatchSymbolEntity>
    @Query("SELECT COUNT(*) FROM watch_symbol") suspend fun count(): Int
}

@Dao
interface LedgerDao {
    @Insert suspend fun insertAll(rows: List<LedgerEntryEntity>)
    @Query("DELETE FROM ledger_entry") suspend fun deleteAll()
    @Insert suspend fun insert(e: LedgerEntryEntity)
    @Update suspend fun update(e: LedgerEntryEntity)
    @Delete suspend fun delete(e: LedgerEntryEntity)
    @Query("SELECT * FROM ledger_entry WHERE id = :id") suspend fun get(id: String): LedgerEntryEntity?
    @Query("SELECT * FROM ledger_entry ORDER BY time") suspend fun all(): List<LedgerEntryEntity>
    @Query("SELECT * FROM ledger_entry ORDER BY time DESC") fun observeAll(): Flow<List<LedgerEntryEntity>>
    @Query("SELECT * FROM ledger_entry WHERE type = 'BASELINE'") suspend fun baselines(): List<LedgerEntryEntity>
}

@Dao
interface ReviewDao {
    @Insert suspend fun insertAll(rows: List<ReviewEntity>)
    @Query("DELETE FROM review") suspend fun deleteAll()
    @Insert suspend fun insert(r: ReviewEntity)
    @Query("SELECT * FROM review ORDER BY completedAt DESC") fun observeAll(): Flow<List<ReviewEntity>>
    @Query("SELECT * FROM review") suspend fun all(): List<ReviewEntity>
}

@Dao
interface ParamChangeDao {
    @Insert suspend fun insertAll(rows: List<ParamChangeEntity>)
    @Query("DELETE FROM param_change") suspend fun deleteAll()
    @Insert suspend fun insert(c: ParamChangeEntity)
    @Update suspend fun update(c: ParamChangeEntity)
    @Query("SELECT * FROM param_change WHERE id = :id") suspend fun get(id: String): ParamChangeEntity?
    @Query("SELECT * FROM param_change ORDER BY appliedAt") suspend fun all(): List<ParamChangeEntity>
    @Query("SELECT * FROM param_change ORDER BY appliedAt DESC") fun observeAll(): Flow<List<ParamChangeEntity>>
}

@Dao
interface ReviewSessionDao {
    @Insert suspend fun insertAll(rows: List<ReviewSessionEntity>)
    @Query("DELETE FROM review_session") suspend fun deleteAll()
    @Insert suspend fun insert(s: ReviewSessionEntity)
    @Update suspend fun update(s: ReviewSessionEntity)
    @Query("SELECT * FROM review_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun open(): ReviewSessionEntity?
    @Query("SELECT * FROM review_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1") fun observeOpen(): Flow<ReviewSessionEntity?>
    @Query("SELECT * FROM review_session") suspend fun all(): List<ReviewSessionEntity>
}

@Dao
interface PledgeDao {
    @Insert suspend fun insertAll(rows: List<PledgeEntity>)
    @Query("DELETE FROM pledge") suspend fun deleteAll()
    @Insert suspend fun insert(p: PledgeEntity)
    @Query("SELECT * FROM pledge ORDER BY signedAt DESC LIMIT 1") fun observeLatest(): Flow<PledgeEntity?>
    @Query("SELECT * FROM pledge") suspend fun all(): List<PledgeEntity>
}

@Dao
interface SecurityCheckDao {
    @Insert suspend fun insertAll(rows: List<SecurityCheckEntity>)
    @Query("DELETE FROM security_check") suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(c: SecurityCheckEntity)
    @Query("SELECT * FROM security_check") fun observeAll(): Flow<List<SecurityCheckEntity>>
    @Query("SELECT * FROM security_check") suspend fun all(): List<SecurityCheckEntity>
}

@Dao
interface SpotAssetDao {
    @Insert suspend fun insertAll(rows: List<SpotAssetEntity>)
    @Query("DELETE FROM spot_asset") suspend fun deleteAll()
    @Upsert suspend fun upsert(a: SpotAssetEntity)
    @Query("SELECT * FROM spot_asset") suspend fun all(): List<SpotAssetEntity>
}

@Dao
interface AuditDao {
    @Insert suspend fun insertAll(rows: List<AuditLogEntity>)
    @Query("DELETE FROM audit_log") suspend fun deleteAll()
    @Insert suspend fun insert(a: AuditLogEntity)
    @Query("SELECT * FROM audit_log ORDER BY time DESC") fun observeAll(): Flow<List<AuditLogEntity>>
    @Query("SELECT * FROM audit_log ORDER BY time") suspend fun all(): List<AuditLogEntity>
}

@Dao
interface TradeAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(a: TradeAlertEntity)
    @Query("DELETE FROM trade_alert WHERE tradeId = :tradeId AND level = :level") suspend fun remove(tradeId: String, level: String)
    @Query("DELETE FROM trade_alert WHERE tradeId = :tradeId") suspend fun removeAll(tradeId: String)
    @Query("UPDATE trade_alert SET firedAt = :at WHERE tradeId = :tradeId AND level = :level AND firedAt IS NULL") suspend fun markFired(tradeId: String, level: String, at: Long): Int
    @Query("SELECT * FROM trade_alert") suspend fun all(): List<TradeAlertEntity>
    @Query("SELECT * FROM trade_alert") fun observeAll(): Flow<List<TradeAlertEntity>>
    @Query("SELECT * FROM trade_alert WHERE tradeId = :tradeId") fun observeFor(tradeId: String): Flow<List<TradeAlertEntity>>
    @Insert suspend fun insertAll(rows: List<TradeAlertEntity>)
    @Query("DELETE FROM trade_alert") suspend fun deleteAll()
}
