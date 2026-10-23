package ir.andarz.market.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.andarz.market.MarketModule
import ir.andarz.market.MarketTarget
import kotlinx.coroutines.delay

/**
 * Full-screen alarm shown over the lock screen while a price alert is ringing (March's `AlarmActivity` and
 * `AlarmScreen`). It lives outside the app lock because it shows market data only (K6.11), and it does not
 * use :app's theme (K5): a minimal dark screen of its own. "Open coin" goes through
 * [ir.andarz.market.MarketHost.openIntent], so the app lock is shown before the coin.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        wakeScreen()
        val texts = MarketModule.textsOrNull()
        setContent {
            val info by AlarmRinger.state.collectAsStateWithLifecycle()
            LaunchedEffect(info) { if (info == null) finish() }
            BackHandler { stopAndClose() }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val a = info
                if (a != null && texts != null) {
                    AlarmScreen(
                        info = a,
                        clock = texts::clock,
                        day = texts::day,
                        openLabel = texts.alarmOpenCoin(),
                        snoozeLabel = texts.alarmSnooze(),
                        stopLabel = texts.alarmStop(),
                        onStop = { stopAndClose() },
                        onSnooze = { snoozeAndClose() },
                        onOpen = { openCoin(a.coinId) },
                    )
                }
            }
        }
    }

    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stopAndClose() {
        AlarmRinger.stop(this)
        finish()
    }

    private fun snoozeAndClose() {
        AlarmRinger.snooze(this)
        finish()
    }

    private fun openCoin(id: String?) {
        AlarmRinger.stop(this)
        val target = if (id != null) MarketTarget.Coin(id) else MarketTarget.Home
        MarketModule.openIntentOrNull(this, target)?.let { i ->
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
            startActivity(i)
        }
        finish()
    }
}

private val Deep = Color(0xFF0D0E10)
private val Muted = Color(0xFFA3A79C)
private val Accent = Color(0xFFC9342A)

@Composable
private fun AlarmScreen(
    info: AlarmRinger.Info,
    clock: (Long) -> String,
    day: (Long) -> String,
    openLabel: String,
    snoozeLabel: String,
    stopLabel: String,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onOpen: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    Column(
        Modifier.fillMaxSize().background(Deep).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(clock(now), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Text(day(now), color = Muted, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(110.dp).background(Accent, CircleShape))
        Spacer(Modifier.height(24.dp))
        Text(info.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(info.body, color = Muted, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        if (info.coinId != null) {
            AlarmButton(openLabel, Color.White.copy(alpha = 0.14f), Modifier.fillMaxWidth(), onOpen)
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlarmButton(snoozeLabel, Color.White.copy(alpha = 0.14f), Modifier.weight(1f), onSnooze)
            AlarmButton(stopLabel, Accent, Modifier.weight(1f), onStop)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AlarmButton(label: String, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(vertical = 16.dp),
    )
}
