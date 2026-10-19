package ir.andarz.market.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.andarz.market.MarketChannel
import ir.andarz.market.MarketModule
import ir.andarz.market.MarketTarget
import ir.andarz.market.R
import ir.andarz.market.alarm.Channels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What the reminder code needs from AlarmManager; faked in the JVM tests (boot restore through a fake scheduler). */
interface ReminderScheduler {
    fun schedule(id: String, title: String, at: Long)
    fun cancel(id: String)
}

/**
 * Release reminders (March's `Reminders`): ten minutes before, inexact while idle so no special permission
 * is needed. They are local and keep working with the Market switch off (K4). Pure logic over a
 * [ReminderScheduler], so the rules are tested on the JVM.
 */
object Reminders {
    /** Arms one reminder; false when its moment has passed. */
    fun arm(s: ReminderScheduler, e: EconEvent, now: Long): Boolean {
        val at = ReleaseTimes.reminderAt(e, now) ?: return false
        s.schedule(e.id, e.title, at)
        return true
    }

    /**
     * Re-arms every saved reminder that is still ahead (reboot, app update, app start). Ids that are not in
     * the calendar any more are ignored, so a refreshed list never re-arms an old reminder (K5). Returns
     * the armed ids.
     */
    fun restore(s: ReminderScheduler, calendar: CalendarData, saved: Set<String>, now: Long): List<String> =
        calendar.events.filter { it.id in saved && arm(s, it, now) }.map { it.id }
}

/** The AlarmManager implementation. */
internal class AlarmScheduler(private val context: Context) : ReminderScheduler {
    private fun pending(id: String, title: String?): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra("id", id)
        if (title != null) i.putExtra("title", title)
        return PendingIntent.getBroadcast(context, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun schedule(id: String, title: String, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Inexact-while-idle: no special permission needed; Android may deliver it a few minutes late (March).
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(id, title))
    }

    override fun cancel(id: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(id, null))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val texts = MarketModule.textsOrNull() ?: return
        Channels.ensure(context, texts)
        val open = PendingIntent.getActivity(
            context, 0, MarketModule.openIntent(context, MarketTarget.News),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, MarketChannel.RELEASES.id)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle(title)
            .setContentText(texts.reminderBody(ReleaseTimes.LEAD_MINUTES))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        Channels.notify(context, (intent.getStringExtra("id") ?: title).hashCode(), n)
    }
}

/**
 * Alarms are wiped on reboot and on app update (March): put the saved reminders back, and restart the
 * monitor only when it is enabled. Market state only; it never touches the journal.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!MarketModule.isInstalled) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MarketModule.restoreAfterBoot(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}
