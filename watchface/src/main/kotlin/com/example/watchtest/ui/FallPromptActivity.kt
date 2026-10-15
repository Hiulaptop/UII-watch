package com.example.watchtest.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import com.example.watchtest.R
import com.example.watchtest.uii.FallAlerts
import com.example.watchtest.uii.Notifications
import com.example.watchtest.uii.Payloads
import com.example.watchtest.uii.SosCard
import com.example.watchtest.uii.Uii
import kotlinx.coroutines.delay

/**
 * Shown by the fall notification as a full-screen alert: "are you fine?" with the cancel window counting down and
 * one big button. The notification and its single action remain as the fallback where the system will not show this.
 */
class FallPromptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val due = intent.getLongExtra(EXTRA_DUE, 0L)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: Payloads.FALL
        promptId = id
        promptDue = due
        promptType = type
        setContent {
            UiiWatchTheme {
                FallPrompt(id = id, dueMs = due, type = type, onClose = { finish() })
            }
        }
    }

    private var promptId = ""
    private var promptDue = 0L
    private var promptType = Payloads.FALL

    /** Leaving the prompt (swipe, screen off) while the window is still open hands over to the notification. */
    override fun onStop() {
        if (promptId.isNotEmpty() && System.currentTimeMillis() < promptDue && StatusSource.isQueued(this, promptId)) {
            Notifications.showFall(this, promptId, promptType)
        }
        super.onStop()
    }

    companion object {
        const val EXTRA_ID = "outbox_id"
        const val EXTRA_DUE = "due_ms"
        const val EXTRA_TYPE = "alert_type"
        @JvmStatic
        fun intent(c: Context, id: String, dueMs: Long, type: String): Intent =
            Intent(c, FallPromptActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_DUE, dueMs).putExtra(EXTRA_TYPE, type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun FallPrompt(id: String, dueMs: Long, type: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val windowMs = (dueMs - System.currentTimeMillis()).coerceAtLeast(1)
    var remaining by remember { mutableLongStateOf(windowMs) }
    var sent by remember { mutableStateOf(false) }
    var delivery by remember { mutableStateOf(Delivery.WAITING) }
    var card by remember { mutableStateOf<SosCard?>(null) }

    LaunchedEffect(id) {
        // Until the window ends: count down, and close if the alert was cancelled somewhere else (the notification).
        while (remaining > 0) {
            remaining = dueMs - System.currentTimeMillis()
            if (!StatusSource.isQueued(context, id)) { onClose(); return@LaunchedEffect }
            delay(100)
        }
        sent = true
        // Then follow delivery exactly like a manual alert: only "sent" once the server accepted it.
        while (StatusSource.isQueued(context, id)) delay(500)
        delivery = StatusSource.delivery(context, id)
        if (delivery == Delivery.SENT) {
            // Whoever is next to the wearer may need the medical card; it stays until dismissed. No card: just close.
            val cached = Uii.get(context).sosCard()
            if (cached != null) card = cached else { delay(2500); onClose() }
        }
    }

    if (sent) {
        val shown = card
        if (shown != null) SosCardScreen(shown, onDone = onClose) else DeliveryContent(delivery)
        return
    }
    Column(
        Modifier.fillMaxSize().padding(top = 26.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val question = if (type == Payloads.PRE_SEIZURE) R.string.warn_sit else R.string.fall_question
        Label(stringResource(question), 18.sp, WatchColors.Primary, bold = true)
        Ring(
            color = WatchColors.Accent,
            progress = (remaining.toFloat() / windowMs).coerceIn(0f, 1f),
            modifier = Modifier.size(100.dp),
        ) { Label(secondsLeft(remaining).toString(), 48.sp, WatchColors.Primary, bold = true) }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = { FallAlerts.cancel(context, id); onClose() },
            modifier = Modifier.fillMaxWidth(0.62f).height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Ok, contentColor = WatchColors.Bg),
        ) { Label(stringResource(R.string.fall_ok), 20.sp, WatchColors.Bg, bold = true, modifier = Modifier.fillMaxWidth()) }
    }
}
