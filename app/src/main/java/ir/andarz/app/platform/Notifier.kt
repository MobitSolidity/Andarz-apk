package ir.andarz.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ir.andarz.app.MainActivity
import ir.andarz.app.R
import ir.andarz.app.content.ContentRepository
import ir.andarz.data.EngineFacade
import ir.andarz.data.db.AndarzDatabase
import ir.andarz.data.settings.SettingsRepository
import ir.andarz.engine.Fmt
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discipline notifications (7). Three channels in the "discipline" group, created at start. Texts are
 * private on the lock screen: the public version shows only the app name, never equity, P&L or a symbol.
 */
object DisciplineChannels {
    const val GROUP = "discipline"
    val IDS = listOf("discipline", "reviews", "system")

    fun ensure(context: Context, content: ContentRepository) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup(GROUP, content.ui("notif.channel.discipline")))
        for (id in IDS) {
            val imp = if (id == "discipline") NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW
            nm.createNotificationChannel(
                NotificationChannel(id, content.ui("notif.channel.$id"), imp).apply {
                    group = GROUP
                    enableVibration(id == "discipline")
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                },
            )
        }
    }

    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** The one place that posts; checks the runtime permission first (D-M53 pattern). */
    @SuppressLint("MissingPermission")
    fun post(context: Context, r: Reminder, title: String, body: String) {
        if (!permitted(context)) return
        val open = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, r.route)
        val pi = PendingIntent.getActivity(context, r.name.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(context, r.kind.channel).setSmallIcon(R.drawable.ic_stat_andarz)
            .setContentTitle(context.getString(R.string.app_name)).build()
        val n = NotificationCompat.Builder(context, r.kind.channel)
            .setSmallIcon(R.drawable.ic_stat_andarz).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(TAG, r.name.hashCode(), n)
    }

    /** Discipline ids use their own tag, so they can never collide with :market's ("market"). */
    const val TAG = "discipline"
}

/** Wording of each reminder from content, resolved at fire time (7). */
object ReminderTexts {
    fun of(content: ContentRepository, r: Reminder, symbol: String?, n: Int?, lockTitle: String?, advice: String?, fa: Boolean): Pair<String, String> {
        val num = { x: Int -> Fmt.number(x.toDouble(), 0, fa) }
        return when (r.kind) {
            ReminderKind.COOLDOWN_END -> content.ui("notif.cooldownEnd.title") to content.ui("notif.cooldownEnd.body")
            ReminderKind.LOCK_END -> content.ui("notif.lockEnd.title") to content.ui("notif.lockEnd.body", "title" to (lockTitle ?: ""))
            ReminderKind.TIME_STOP -> content.ui("notif.timeStop.title", "symbol" to Fmt.isolate(symbol ?: "")) to content.ui("notif.timeStop.body", "n" to num(n ?: 0))
            ReminderKind.OPEN -> content.ui("notif.open.title") to content.ui("notif.open.body")
            ReminderKind.REPORT_DUE -> content.ui("notif.reportDue.title") to content.ui("notif.reportDue.body")
            ReminderKind.DAILY_CLOSE -> content.ui("notif.dailyClose.title") to content.ui("notif.dailyClose.body")
            ReminderKind.WEEKLY -> content.ui("notif.weekly.title") to content.ui("notif.weekly.body")
            ReminderKind.MONTHLY -> content.ui("notif.monthly.title") to content.ui("notif.monthly.body")
            ReminderKind.BACKUP -> content.ui("notif.backup.title") to content.ui("notif.backup.body")
            ReminderKind.SECURITY -> content.ui("notif.security.title") to content.ui("notif.security.body")
            ReminderKind.ADVICE -> content.ui("notif.advice.title") to (advice ?: "")
        }
    }
}

/**
 * The ScheduleReconciler of 7: computes the desired set and diffs it against the unique WorkManager jobs
 * (tag [TAG]). Workers do no network work; they recompute and post only when still due.
 */
@Singleton
class ScheduleReconciler @Inject constructor(
    private val db: AndarzDatabase,
    private val facade: EngineFacade,
    private val settings: SettingsRepository,
) {
    suspend fun desired(now: Instant): List<Reminder> {
        val s = settings.current()
        return ReminderPlanner.plan(facade.snapshot(now), db.trades().all(), db.reviews().all(), s, now)
    }

    suspend fun reconcile(context: Context) {
        val wm = WorkManager.getInstance(context)
        val now = Instant.now()
        val want = desired(now).associateBy { it.name }
        val have = runCatching { wm.getWorkInfosByTag(TAG).get() }.getOrDefault(emptyList())
            .filter { it.state == WorkInfo.State.ENQUEUED }
            .flatMap { info -> info.tags.filter { it.startsWith(NAME_PREFIX) }.map { it.removePrefix(NAME_PREFIX) to info } }
            .toMap()
        for ((name, _) in have) if (name !in want) wm.cancelUniqueWork(name)
        for ((name, r) in want) {
            val delay = Duration.between(now, r.at).toMillis().coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_NAME to name, KEY_AT to r.at.toEpochMilli()))
                .addTag(TAG).addTag(NAME_PREFIX + name).addTag(AT_PREFIX + r.at.toEpochMilli())
                .build()
            val same = have[name]?.tags?.contains(AT_PREFIX + r.at.toEpochMilli()) == true
            if (!same) wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
        }
        wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReconcileWorker>(1, TimeUnit.DAYS).build())
    }

    companion object {
        const val TAG = "andarz-reminder"
        const val NAME_PREFIX = "name:"
        const val AT_PREFIX = "at:"
        const val KEY_NAME = "name"
        const val KEY_AT = "at"
        const val DAILY = "reconcile-daily"
    }
}

/** Fires one reminder: recomputes the desired set just before its instant and posts only if still due. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: ScheduleReconciler,
    private val content: ContentRepository,
    private val db: AndarzDatabase,
    private val settings: SettingsRepository,
    private val facade: EngineFacade,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val name = inputData.getString(ScheduleReconciler.KEY_NAME) ?: return Result.success()
        val at = Instant.ofEpochMilli(inputData.getLong(ScheduleReconciler.KEY_AT, System.currentTimeMillis()))
        val probe = minOf(at, Instant.now()).minusSeconds(1)
        val r = reconciler.desired(probe).firstOrNull { it.name == name }
        if (r != null) {
            val s = settings.settings.first()
            val t = r.ref?.let { db.trades().get(it) }
            val n = if (r.kind == ReminderKind.TIME_STOP && t != null) facade.paramsAt(Instant.now()).timeStopCandles.toInt() else null
            val lockTitle = if (r.kind == ReminderKind.LOCK_END) content.uiOrNull("lock.${r.ref}.title") else null
            val advice = if (r.kind == ReminderKind.ADVICE) adviceOfDay(content, Instant.now().atZone(s.zone).toLocalDate().toEpochDay()) else null
            val (title, body) = ReminderTexts.of(content, r, t?.symbol, n, lockTitle, advice, s.digits == ir.andarz.data.settings.Digits.FA)
            DisciplineChannels.post(applicationContext, r, title, body)
        }
        reconciler.reconcile(applicationContext)
        return Result.success()
    }
}

/** Once a day, and after boot or a clock change through WorkManager's own rescheduling. */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconciler: ScheduleReconciler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { reconciler.reconcile(applicationContext); return Result.success() }
}

/** The advice of the day by day number (D-32), the same pick as Today. */
fun adviceOfDay(content: ContentRepository, epochDay: Long): String? {
    val pool = content.bundle.arr("adviceOfDay").orEmpty()
    if (pool.isEmpty()) return null
    val o = pool[Math.floorMod(epochDay, pool.size.toLong()).toInt()] as? kotlinx.serialization.json.JsonObject ?: return null
    return (o["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
}
