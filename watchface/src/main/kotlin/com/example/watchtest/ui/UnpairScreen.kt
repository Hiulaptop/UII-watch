package com.example.watchtest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import com.example.watchtest.R
import com.example.watchtest.uii.Uii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asks before disconnecting: alerts stop until the watch is paired again, and unsent items are lost. */
@Composable
fun UnpairScreen(onCancel: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val queued = remember { Uii.get(context).outbox.size() }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Label(stringResource(R.string.unpair_title), 18.sp, WatchColors.Primary, bold = true) }
            item { Label(stringResource(R.string.unpair_body), 14.sp, WatchColors.Secondary, modifier = Modifier.fillMaxWidth(0.9f)) }
            if (queued > 0) item {
                Label(stringResource(R.string.unpair_queue, queued), 14.sp, WatchColors.Accent, bold = true, modifier = Modifier.fillMaxWidth(0.9f))
            }
            item {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        scope.launch {
                            withContext(Dispatchers.IO) { Uii.get(context).unpair() }
                            onDone()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Accent, contentColor = WatchColors.Primary),
                ) {
                    Label(
                        stringResource(if (busy) R.string.unpair_busy else R.string.unpair_confirm),
                        16.sp, WatchColors.Primary, bold = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Button(
                    onClick = onCancel,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Surface, contentColor = WatchColors.Primary),
                ) { Label(stringResource(R.string.cancel), 16.sp, WatchColors.Primary, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
