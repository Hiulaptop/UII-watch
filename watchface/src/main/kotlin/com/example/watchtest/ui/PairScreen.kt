package com.example.watchtest.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchtest.R
import com.example.watchtest.uii.Monitoring
import com.example.watchtest.uii.Pairing
import com.example.watchtest.uii.Uii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val CODE_LENGTH = 8

/** Pure so the message mapping is tested: which short text each outcome gets. */
fun pairMessage(r: Pairing.Result): Int? = when (r) {
    Pairing.Result.PAIRED -> null
    Pairing.Result.INVALID_CODE -> R.string.pair_err_wrong
    Pairing.Result.RATE_LIMITED -> R.string.pair_err_rate
    Pairing.Result.TOO_MANY_DEVICES -> R.string.pair_err_devices
    Pairing.Result.NETWORK -> R.string.pair_err_network
    Pairing.Result.ERROR -> R.string.pair_err_other
}

@Composable
fun PairScreen(onPaired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun press(key: String) {
        if (busy) return
        message = null
        when (key) {
            "del" -> digits = digits.dropLast(1)
            "ok" -> {
                if (digits.length != CODE_LENGTH) { message = R.string.pair_need_eight; return }
                busy = true
                val code = digits
                scope.launch {
                    val uii = Uii.get(context)
                    val result = withContext(Dispatchers.IO) { Pairing.pair(uii.api, uii.secrets, code, Build.MODEL, Build.MODEL) }
                    busy = false
                    if (result == Pairing.Result.PAIRED) {
                        uii.state.setPairingRevoked(false)
                        Monitoring.start(context)
                        onPaired()
                    } else {
                        message = pairMessage(result)
                        if (result == Pairing.Result.INVALID_CODE) digits = ""
                    }
                }
            }
            else -> if (digits.length < CODE_LENGTH) digits += key
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val progressDescription = stringResource(R.string.cd_code_progress, digits.length)
        Box(Modifier.height(24.dp).semantics(mergeDescendants = true) { contentDescription = progressDescription }, contentAlignment = Alignment.Center) {
            val text = when {
                busy -> stringResource(R.string.pairing_busy)
                message != null -> stringResource(message!!)
                else -> null
            }
            if (text != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message != null) { GlyphIcon(R.drawable.ic_alert, WatchColors.Accent, 14.dp); Spacer(Modifier.size(4.dp)) }
                    Label(text, 13.sp, if (message != null) WatchColors.Accent else WatchColors.Secondary)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until CODE_LENGTH) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < digits.length) WatchColors.Primary else WatchColors.Surface))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("del", "0", "ok"))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (row in keys) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (k in row) Key(k, enabled = !busy, onClick = { press(k) })
                }
            }
        }
    }
}

@Composable
private fun Key(k: String, enabled: Boolean, onClick: () -> Unit) {
    val description = when (k) {
        "del" -> stringResource(R.string.cd_key_delete)
        "ok" -> stringResource(R.string.cd_key_ok)
        else -> k
    }
    Box(
        Modifier
            .size(37.dp)
            .clip(CircleShape)
            .background(if (k == "ok") WatchColors.Accent else WatchColors.Surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (k) {
            "del" -> GlyphIcon(R.drawable.ic_backspace, WatchColors.Primary, 17.dp)
            "ok" -> GlyphIcon(R.drawable.ic_check, WatchColors.Primary, 18.dp)
            else -> Label(k, 18.sp, WatchColors.Primary, bold = true)
        }
    }
}
