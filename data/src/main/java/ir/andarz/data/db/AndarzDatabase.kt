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
        AuditLogEntity::class, TradeAlertEntity::class,
    ],
    version = 2,
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
    abstract fun tradeAlerts(): TradeAlertDao

    companion object {
        const val NAME = "andarz.db"

        /** 1 -> 2: the `trade_alert` table of MK3 (D-M56). Nothing else changes. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trade_alert` (`tradeId` TEXT NOT NULL, `level` TEXT NOT NULL, `price` REAL NOT NULL, " +
                        "`armedAt` INTEGER NOT NULL, `firedAt` INTEGER, PRIMARY KEY(`tradeId`, `level`), " +
                        "FOREIGN KEY(`tradeId`) REFERENCES `trade`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
            }
        }

        /** Every migration, in order. */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_1_2)
    }
}
