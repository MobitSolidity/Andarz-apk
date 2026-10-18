package ir.andarz.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.andarz.app.content.ContentRepository
import ir.andarz.data.EngineFacade
import ir.andarz.data.JournalRepository
import ir.andarz.data.LedgerRepository
import ir.andarz.data.ParamBounds
import ir.andarz.data.ParamsRepository
import ir.andarz.data.ReviewRepository
import ir.andarz.data.SetupRepository
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Clock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The journal's data layer (M3). Nothing here is reachable from `:market` (K4). */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun clock(): Clock = Clock.System

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AndarzDatabase =
        Room.databaseBuilder(ctx, AndarzDatabase::class.java, AndarzDatabase.NAME)
            .addMigrations(*AndarzDatabase.MIGRATIONS)
            .build()

    @Provides @Singleton
    fun settings(@ApplicationContext ctx: Context): SettingsRepository = SettingsRepository(ctx.settingsStore)

    @Provides @Singleton
    fun facade(db: AndarzDatabase, clock: Clock, settings: SettingsRepository): EngineFacade =
        EngineFacade(db, clock) { settings.current().zone }

    /** `min`, `max` and `locked` of every parameter, from `params[]` of the content JSON (3.9). */
    @Provides @Singleton
    fun bounds(content: ContentRepository): Map<String, ParamBounds> =
        content.bundle.arr("params").orEmpty().associate {
            val o = it.jsonObject
            o["key"]!!.jsonPrimitive.content to ParamBounds(
                min = o["min"]!!.jsonPrimitive.content.toDouble(),
                max = o["max"]!!.jsonPrimitive.content.toDouble(),
                locked = o["locked"]?.jsonPrimitive?.content == "true",
            )
        }

    @Provides @Singleton
    fun params(db: AndarzDatabase, clock: Clock, bounds: Map<String, @JvmSuppressWildcards ParamBounds>) = ParamsRepository(db, clock, bounds)

    @Provides @Singleton
    fun journal(db: AndarzDatabase, clock: Clock, p: ParamsRepository, f: EngineFacade) = JournalRepository(db, clock, p, f)

    @Provides @Singleton
    fun ledger(db: AndarzDatabase, clock: Clock, p: ParamsRepository, f: EngineFacade) = LedgerRepository(db, clock, p, f)

    @Provides @Singleton fun setups(db: AndarzDatabase, clock: Clock) = SetupRepository(db, clock)

    @Provides @Singleton fun reviews(db: AndarzDatabase, clock: Clock) = ReviewRepository(db, clock)
}
