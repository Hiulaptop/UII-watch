package com.example.watchtest.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import com.example.watchtest.R
import com.example.watchtest.uii.FallAlerts
import com.example.watchtest.uii.Payloads
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The three things the wearer can report by hand. */
enum class SosKind(val payloadType: String, val labelRes: Int, val iconRes: Int) {
    PRE(Payloads.PRE_SEIZURE, R.string.sos_pre, R.drawable.ic_bell),
    NOW(Payloads.SEIZURE, R.string.sos_now, R.drawable.ic_bolt),
    FALL(Payloads.FALL, R.string.sos_fall, R.drawable.ic_arrow_down),
}

const val COUNTDOWN_MS = 5_000L

/** Seconds left to show for a remaining time in ms: a full second until it truly reaches zero. */
fun secondsLeft(remainingMs: Long): Int = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)

@Composable
fun SosMenuScreen(onPick: (SosKind) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(stringResource(R.string.sos_header), 13.sp, WatchColors.Secondary)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            for (kind in SosKind.entries) {
                Button(
                    onClick = { onPick(kind) },
                    modifier = Modifier.fillMaxWidth(0.88f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Surface, contentColor = WatchColors.Primary),
                ) {
                    GlyphDisc(kind.iconRes, WatchColors.Accent, WatchColors.Primary, 28.dp, 15.dp)
                    Spacer(Modifier.width(8.dp))
                    Label(stringResource(kind.labelRes), 16.sp, WatchColors.Primary, bold = true)
                }
            }
        }
    }
}

@Composable
fun CountdownScreen(kind: SosKind, onCancel: () -> Unit, onFinished: (itemId: String) -> Unit) {
    val context = LocalContext.current
    val progress = remember { Animatable(1f) }
    var seconds by remember { mutableIntStateOf(secondsLeft(COUNTDOWN_MS)) }

    LaunchedEffect(kind) {
        // The ring and the number come from the same clock, so they never disagree.
        val start = System.currentTimeMillis()
        val ticker = launch {
            while (true) {
                seconds = secondsLeft(COUNTDOWN_MS - (System.currentTimeMillis() - start))
                delay(100)
            }
        }
        progress.animateTo(0f, tween(COUNTDOWN_MS.toInt(), easing = LinearEasing))
        ticker.cancel()
        onFinished(FallAlerts.sendManual(context, kind.payloadType))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(stringResource(kind.labelRes), 14.sp, WatchColors.Secondary)
            Spacer(Modifier.height(2.dp))
            Ring(
                color = WatchColors.Accent,
                progress = progress.value,
                modifier = Modifier.size(112.dp),
            ) {
                val spoken = stringResource(R.string.cd_countdown, seconds)
                Box(Modifier.semantics { contentDescription = spoken }) { Label(seconds.toString(), 60.sp, WatchColors.Primary, bold = true) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            val spoken = stringResource(R.string.cd_cancel)
            Button(
                onClick = onCancel,
                modifier = Modifier.width(96.dp).height(34.dp).semantics { contentDescription = spoken },
                colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Surface, contentColor = WatchColors.Primary),
            ) {
                GlyphIcon(R.drawable.ic_close, WatchColors.Primary, 14.dp)
                Spacer(Modifier.width(4.dp))
                Label(stringResource(R.string.cancel), 15.sp, WatchColors.Primary, bold = true)
            }
        }
    }
}

/** Sent or waiting, decided only by whether the server has accepted the item (it leaves the outbox then). */
@Composable
fun DeliveryScreen(itemId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(StatusSource.delivery(context, itemId)) }
    LaunchedEffect(itemId) {
        while (state == Delivery.WAITING) {
            delay(500)
            state = StatusSource.delivery(context, itemId)
        }
        // A refused alert stays on screen: the wearer must know to call someone.
        if (state == Delivery.SENT) { delay(2500); onDone() }
    }
    DeliveryContent(state)
}

@Composable
fun DeliveryContent(state: Delivery) {
    val colors = LocalStatusColors.current
    val color = when (state) { Delivery.WAITING -> colors.waiting; Delivery.SENT -> colors.ok; Delivery.FAILED -> colors.warn }
    val description = stringResource(when (state) { Delivery.WAITING -> R.string.cd_waiting_sent; Delivery.SENT -> R.string.cd_sent; Delivery.FAILED -> R.string.cd_delivery_failed })
    val icon = when (state) { Delivery.WAITING -> R.drawable.ic_cloud; Delivery.SENT -> R.drawable.ic_check; Delivery.FAILED -> R.drawable.ic_alert }
    val label = when (state) { Delivery.WAITING -> R.string.waiting_network; Delivery.SENT -> R.string.sent; Delivery.FAILED -> R.string.delivery_failed }
    Column(Modifier.fillMaxSize().padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        StatusRing(color = color, description = description, onClick = {}, modifier = Modifier.size(125.dp)) {
            GlyphIcon(icon, color, 60.dp)
        }
        Spacer(Modifier.height(10.dp))
        Label(stringResource(label), 24.sp, WatchColors.Primary, bold = true)
    }
}
