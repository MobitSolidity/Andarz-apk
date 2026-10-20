package ir.andarz.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.andarz.data.backup.BackupCrypto
import ir.andarz.data.backup.BackupError
import ir.andarz.data.backup.BackupService
import ir.andarz.data.backup.InvalidBackup
import ir.andarz.data.csv.CsvFormat
import ir.andarz.data.csv.CsvHeaderException
import ir.andarz.data.csv.CsvProfile
import ir.andarz.data.csv.CsvRepository
import ir.andarz.data.csv.CsvVocab
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.settings.PinLock
import ir.andarz.engine.Clock
import ir.andarz.engine.Side
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** M6 data layer: backup payload and restore, CSV round trips, wipe and the PIN policy (10.5). */
@RunWith(RobolectricTestRunner::class)
class PlatformDataTest {
    private class FakeClock(var t: Instant) : Clock { override fun now() = t; fun plus(d: Duration) { t = t.plus(d) } }

    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(3, 30)
    private val clock = FakeClock(OffsetDateTime.parse("2026-10-14T09:00:00+03:30").toInstant())
    private lateinit var db: AndarzDatabase
    private lateinit var facade: EngineFacade
    private lateinit var params: ParamsRepository
    private lateinit var journal: JournalRepository
    private lateinit var ledger: LedgerRepository
    private lateinit var setups: SetupRepository
    private lateinit var backup: BackupService
    private lateinit var csv: CsvRepository
    private val vocab: CsvVocab by lazy {
        CsvVocab.fromContent(Json.parseToJsonElement(File(System.getProperty("andarz.content")!!).readText(Charsets.UTF_8)).jsonObject)
    }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AndarzDatabase::class.java).allowMainThreadQueries().build()
        facade = EngineFacade(db, clock) { zone }
        params = ParamsRepository(db, clock, mapOf("Risk_Pct" to ParamBounds(0.001, 0.02), "Start_Equity" to ParamBounds(1.0, 1e9)))
        journal = JournalRepository(db, clock, params, facade)
        ledger = LedgerRepository(db, clock, params, facade)
        setups = SetupRepository(db, clock)
        backup = BackupService(db, clock)
        csv = CsvRepository(db, clock, params)
    }

    @After fun tearDown() = db.close()

    /** Onboard and run the worked example to CLOSED (net +39.4). */
    private fun workedExample(): String = runBlocking {
        assertNull(ledger.onboard(2000.0, 0.01, zone))
        val s = setups.create("retest").id
        val t = journal.savePlan(
            PlanDraft("REAL", "btcusdt", Side.LONG, "INTRADAY", s, "15m", "UP", null, "CALM", 0xFF, 2000.0, 100.0, 96.0, 108.0, 5.0, 0.01, "LIMIT", true, true, true),
        )
        clock.plus(Duration.ofMinutes(5))
        journal.confirmEntry(t.id, EntryConfirm(100.0, 500.0, clock.now()))
        clock.plus(Duration.ofHours(2))
        journal.close(t.id, listOf(ExitLeg(108.0, 1.0)), 0.6, "TAKE_PROFIT", clock.now())
        t.id
    }

    @Test
    fun backupRoundTripRestoresEveryTableAndIsAtomic() = runBlocking {
        val id = workedExample()
        val settings = JsonObject(mapOf("theme" to JsonPrimitive("DARK")))
        val bytes = backup.export("correct horse", "1.0", zone, settings, null, emptyMap(), iterations = BackupCrypto.MIN_ITERATIONS)
        assertTrue(db.audit().all().any { it.type == "BACKUP_EXPORT" })

        assertFailsWith<BackupError.WrongPassword> { backup.open(bytes, "wrong password") }
        val doc = backup.open(bytes, "correct horse")
        assertEquals(1, doc.tables.trade.size)
        assertEquals("DARK", (doc.settings["theme"] as JsonPrimitive).content)
        val sum = backup.summary(doc)
        assertEquals(1, sum.trades); assertFalse(sum.withShots)

        backup.wipe()
        assertTrue(db.trades().all().isEmpty())
        assertEquals(listOf("DATA_WIPE"), db.audit().all().map { it.type })

        // A failure halfway leaves the database as it was (8.3 atomicity).
        assertFailsWith<IllegalStateException> { backup.restore(doc) { throw IllegalStateException("halfway") } }
        assertTrue(db.trades().all().isEmpty())

        backup.restore(doc)
        val back = db.trades().get(id)!!
        assertEquals(39.4, back.net!!, 1e-9)
        assertEquals(2039.4, facade.snapshot().equity.now, 1e-9)
        assertTrue(db.audit().all().any { it.type == "BACKUP_RESTORE" })
    }

    @Test
    fun payloadIsForwardCompatibleAndValidated() = runBlocking {
        workedExample()
        val doc = backup.snapshot("1.0", zone, JsonObject(emptyMap()), null)
        // unknown fields are ignored; a missing optional field takes its default
        val json = backup.encode(doc).replaceFirst("\"format\"", "\"futureField\":1,\"format\"").replace(",\"loggedLate\":false", "")
        assertEquals(doc.tables.trade.single().id, backup.decode(json).tables.trade.single().id)
        assertFailsWith<BackupError.Version> { backup.decode(backup.encode(doc.copy(version = 2))) }
        assertFailsWith<BackupError.NotAndarz> { backup.decode(backup.encode(doc.copy(format = "other"))) }
        assertFailsWith<InvalidBackup> { backup.validate(doc.copy(tables = doc.tables.copy(ledger_entry = emptyList()))) }
        val bad = doc.tables.trade.single().let { it.copy(legs = listOf(it.legs.single().copy(fraction = 0.5))) }
        assertFailsWith<InvalidBackup> { backup.validate(doc.copy(tables = doc.tables.copy(trade = listOf(bad)))) }
        assertEquals("andarz-backup-20261014-0900.andz", BackupService.fileName(OffsetDateTime.parse("2026-10-14T09:00:00+03:30").toInstant(), zone))
    }

    @Test
    fun csvFormatRules() {
        assertEquals("\"a,b\"", CsvFormat.quote("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvFormat.quote("say \"hi\""))
        assertEquals("'=SUM(A1)", CsvFormat.protect("=SUM(A1)"))
        assertEquals("=SUM(A1)", CsvFormat.unprotect("'=SUM(A1)"))
        assertEquals("'plain", CsvFormat.unprotect("'plain"))
        val text = CsvFormat.write(listOf(listOf("x", "a,b"), listOf("1", "line\nbreak")))
        assertTrue(text.startsWith(CsvFormat.BOM) && text.contains("\r\n"))
        assertEquals(listOf(listOf("x", "a,b"), listOf("1", "line\nbreak")), CsvFormat.parse(text))
        assertEquals("100", CsvFormat.num(100.0)); assertEquals("0.1", CsvFormat.num(0.1)); assertEquals("39.4", CsvFormat.num(39.4))
    }

    @Test
    fun excelExportImportRoundTrip() = runBlocking {
        workedExample()
        val text = csv.exportExcel(vocab, zone)
        val rows = CsvFormat.parse(text)
        assertEquals(27, rows[0].size)
        assertEquals(2, rows.size)
        // importing the same file again: one duplicate, nothing new
        val again = csv.preview(text, vocab, zone)
        assertEquals(CsvProfile.EXCEL, again.profile)
        assertEquals(1, again.duplicates); assertTrue(again.trades.isEmpty())

        // a fresh database takes the row as an imported CLOSED trade with the same result
        backup.wipe()
        assertNull(ledger.onboard(2000.0, 0.01, zone))
        val p = csv.preview(text, vocab, zone)
        assertEquals(1, p.trades.size); assertTrue(p.errors.isEmpty())
        val t = p.trades.single()
        assertEquals("CLOSED", t.status); assertTrue(t.imported)
        assertEquals(39.4, t.net!!, 1e-9); assertEquals("TAKE_PROFIT", t.exitReason); assertEquals(8, t.chk)
        assertEquals(1, p.newSetups.size)
        // the imported day precedes the new baseline (today, 00:00) only if it is earlier: same day here
        csv.commit(p)
        assertEquals(1, db.trades().all().size)
        assertTrue(db.audit().all().any { it.type == "CSV_IMPORT" })
    }

    @Test
    fun fullExportImportRoundTripAndErrors() = runBlocking {
        val id = workedExample()
        val storedOk = db.trades().get(id)!!.ok
        val text = csv.exportFull(vocab, zone)
        assertEquals(50, CsvFormat.parse(text)[0].size)
        backup.wipe()
        assertNull(ledger.onboard(2000.0, 0.01, zone))
        val p = csv.preview(text, vocab, zone)
        assertEquals(CsvProfile.FULL, p.profile)
        val t = p.trades.single()
        assertEquals(id, t.id); assertEquals(39.4, t.net!!, 1e-9); assertEquals(storedOk, t.ok, "persisted columns win (D-24)")

        assertFailsWith<CsvHeaderException> { csv.preview("a,b\r\n1,2\r\n", vocab, zone) }
        // a row with entry == sl is reported and skipped
        val rows = CsvFormat.parse(text)
        val i = rows[0].indexOf("slInitial")
        val broken = rows[1].toMutableList().also { it[i] = it[rows[0].indexOf("entry")]; it[0] = "other" }
        val q = csv.preview(CsvFormat.write(listOf(rows[0], broken)), vocab, zone)
        assertEquals(1, q.errors.size); assertEquals(2, q.errors.single().row)
    }

    @Test
    fun jalaliDatesParse() {
        val codec = ir.andarz.data.csv.CsvCodec(vocab, zone)
        assertEquals(java.time.LocalDate.of(2026, 10, 23), codec.parseDate("1405/08/01"))
        assertEquals(java.time.LocalDate.of(2026, 10, 23), codec.parseDate("2026-10-23"))
        assertNull(codec.parseDate("not a date"))
    }

    @Test
    fun pinPolicy() {
        assertFalse(PinLock.valid("12")); assertFalse(PinLock.valid("12a4")); assertTrue(PinLock.valid("1234"))
        val h = PinLock.hash("2580", iterations = 1_000)
        assertTrue(PinLock.verify("2580", h)); assertFalse(PinLock.verify("2581", h))
        assertEquals(0, PinLock.waitAfter(4)); assertEquals(30_000, PinLock.waitAfter(5)); assertEquals(60_000, PinLock.waitAfter(6))
        assertEquals(30 * 60_000L, PinLock.waitAfter(30))
        assertEquals(PinLock.ITERATIONS, PinLock.hash("1234").iterations)
        assertNotNull(h.salt)
    }

    @Test
    fun tradeAlertsAreOneShotAndVanishOnClose() = runBlocking {
        assertNull(ledger.onboard(2000.0, 0.01, zone))
        val s = setups.create("retest").id
        val t = journal.savePlan(PlanDraft("REAL", "btcusdt", Side.LONG, "INTRADAY", s, "15m", "UP", null, "CALM", 0xFF, 2000.0, 100.0, 96.0, 108.0, 5.0, 0.01, "LIMIT", true, true, true))
        clock.plus(Duration.ofMinutes(1))
        journal.confirmEntry(t.id, EntryConfirm(100.0, 500.0, clock.now()))
        db.tradeAlerts().put(ir.andarz.data.db.TradeAlertEntity(t.id, "TP", 108.0, 1))
        db.tradeAlerts().put(ir.andarz.data.db.TradeAlertEntity(t.id, "STOP", 96.0, 1))
        assertEquals(1, db.tradeAlerts().markFired(t.id, "TP", 5))
        assertEquals(0, db.tradeAlerts().markFired(t.id, "TP", 6), "a fired level never fires again")
        clock.plus(Duration.ofMinutes(10))
        journal.close(t.id, listOf(ExitLeg(108.0, 1.0)), 0.6, "TAKE_PROFIT", clock.now())
        assertTrue(db.tradeAlerts().all().isEmpty())
    }

    @Test
    fun migrationOneToTwoAddsTheTradeAlertTable() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migrate-test.db"
        ctx.deleteDatabase(name)
        // Version 1 schema straight from the exported JSON, then the 1 -> 2 migration through Room.
        val schema = File("schemas/ir.andarz.data.db.AndarzDatabase/1.json").takeIf { it.isFile }
            ?: File("data/schemas/ir.andarz.data.db.AndarzDatabase/1.json")
        val v1 = Json.parseToJsonElement(schema.readText()).jsonObject["database"]!!.jsonObject
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    for (e in (v1["entities"] as kotlinx.serialization.json.JsonArray)) {
                        val o = e.jsonObject
                        val tn = (o["tableName"] as JsonPrimitive).content
                        db.execSQL((o["createSql"] as JsonPrimitive).content.replace("\${TABLE_NAME}", tn))
                        (o["indices"] as? kotlinx.serialization.json.JsonArray)?.forEach { i ->
                            db.execSQL((i.jsonObject["createSql"] as JsonPrimitive).content.replace("\${TABLE_NAME}", tn))
                        }
                    }
                    for (q in (v1["setupQueries"] as kotlinx.serialization.json.JsonArray)) db.execSQL((q as JsonPrimitive).content)
                    db.execSQL("INSERT INTO ledger_entry (id, time, type, amount, note, createdAt) VALUES ('b', 0, 'BASELINE', 2000.0, '', 0)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build(),
        )
        helper.writableDatabase.close()
        val migrated = Room.databaseBuilder(ctx, AndarzDatabase::class.java, name).addMigrations(*AndarzDatabase.MIGRATIONS).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1, migrated.ledger().baselines().size, "data survives the migration")
            assertTrue(migrated.tradeAlerts().all().isEmpty())
        }
        migrated.close()
        ctx.deleteDatabase(name)
    }
}
