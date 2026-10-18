package ir.andarz.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The journal database (4.1). Schemas are exported to `data/schemas/`; every version bump gets a migration
 * and a migration test. Destructive migration is never used.
 */
@Database(
    entities = [
        TradeEntity::class, TradeEventEntity::class, AttachmentEntity::class, SetupEntity::class,
        WatchSymbolEntity::class, LedgerEntryEntity::class, ReviewEntity::class, ParamChangeEntity::class,
        ReviewSessionEntity::class, PledgeEntity::class, SecurityCheckEntity::class, SpotAssetEntity::class,
        AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AndarzDatabase : RoomDatabase() {
    abstract fun trades(): TradeDao
    abstract fun events(): TradeEventDao
    abstract fun attachments(): AttachmentDao
    abstract fun setups(): SetupDao
    abstract fun watch(): WatchSymbolDao
    abstract fun ledger(): LedgerDao
    abstract fun reviews(): ReviewDao
    abstract fun paramChanges(): ParamChangeDao
    abstract fun sessions(): ReviewSessionDao
    abstract fun pledges(): PledgeDao
    abstract fun security(): SecurityCheckDao
    abstract fun spot(): SpotAssetDao
    abstract fun audit(): AuditDao

    companion object {
        const val NAME = "andarz.db"

        /** Every migration, in order. Version 1 has none; the list exists so a bump cannot forget it. */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}
