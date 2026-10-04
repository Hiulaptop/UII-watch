package com.example.watchtest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.watchtest.R

@Composable
private fun permLabel(p: Perm) = stringResource(
    when (p) { Perm.HEART -> R.string.perm_heart; Perm.ACTIVITY -> R.string.perm_activity; Perm.NOTIFICATIONS -> R.string.perm_notifications },
)

@Composable
fun SosEdgeButton(onClick: () -> Unit) {
    val spoken = stringResource(R.string.cd_sos)
    EdgeButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = spoken },
        buttonSize = EdgeButtonSize.Small,
        colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Accent, contentColor = WatchColors.Primary),
    ) {
        Text(stringResource(R.string.sos), fontSize = 18.sp)
    }
}

@Composable
fun HomeScreen(
    status: StatusInput,
    onSos: () -> Unit,
    onDetails: () -> Unit,
    onFixPermission: () -> Unit,
    onPair: () -> Unit,
) {
    when (val state = homeState(status)) {
        HomeState.Unpaired -> UnpairedScreen(onPair)
        HomeState.Refused -> RefusedScreen(onPair)
        else -> Box(Modifier.fillMaxSize()) {
            val colors = LocalStatusColors.current
            Box(Modifier.fillMaxSize().padding(bottom = 50.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    is HomeState.Ok -> StatusRing(
                        color = colors.ok,
                        description = stringResource(R.string.cd_status_ok),
                        onClick = onDetails,
                        modifier = Modifier.size(125.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlyphIcon(R.drawable.ic_heart, colors.ok, 20.dp)
                            Label(state.heartRate?.toString() ?: "--", 44.sp, WatchColors.Primary, bold = true)
                            Label(stringResource(R.string.bpm), 12.sp, WatchColors.Secondary)
                        }
                    }
                    is HomeState.Attention -> StatusRing(
                        color = colors.warn,
                        description = stringResource(R.string.cd_status_attention, permLabel(state.missing.first())),
                        onClick = onFixPermission,
                        modifier = Modifier.size(125.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            GlyphIcon(R.drawable.ic_alert, colors.warn, 32.dp)
                            Label(stringResource(R.string.fix_permission), 16.sp, WatchColors.Primary, bold = true)
                        }
                    }
                    is HomeState.Waiting -> StatusRing(
                        color = colors.waiting,
                        description = stringResource(R.string.cd_status_waiting, state.queued),
                        onClick = onDetails,
                        modifier = Modifier.size(125.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlyphIcon(R.drawable.ic_cloud, colors.waiting, 28.dp)
                            Label(state.queued.toString(), 36.sp, WatchColors.Primary, bold = true)
                            Label(stringResource(R.string.home_waiting_unit), 12.sp, WatchColors.Secondary)
                        }
                    }
                    else -> Unit
                }
            }
            // Not inside a scroll-driven scaffold: with nothing to scroll the scaffold would keep the button hidden.
            Box(Modifier.align(Alignment.BottomCenter)) { SosEdgeButton(onSos) }
        }
    }
}

@Composable
private fun UnpairedScreen(onPair: () -> Unit) {
    CenterColumn(Modifier.padding(horizontal = 24.dp)) {
        val unpairedDescription = stringResource(R.string.cd_status_unpaired)
        Box(Modifier.semantics(mergeDescendants = true) { contentDescription = unpairedDescription }) {
            GlyphDisc(R.drawable.ic_link, WatchColors.Surface, WatchColors.Primary, 70.dp, 34.dp)
        }
        Spacer(Modifier.height(10.dp))
        Label(stringResource(R.string.home_unpaired_title), 22.sp, WatchColors.Primary, bold = true)
        Label(stringResource(R.string.home_unpaired_caption), 13.sp, WatchColors.Secondary)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPair,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Accent, contentColor = WatchColors.Primary),
        ) { Label(stringResource(R.string.home_unpaired_start), 16.sp, WatchColors.Primary, bold = true, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun RefusedScreen(onPair: () -> Unit) {
    CenterColumn(Modifier.padding(horizontal = 24.dp)) {
        val warn = LocalStatusColors.current.warn
        val refusedDescription = stringResource(R.string.cd_status_refused)
        Box(Modifier.semantics(mergeDescendants = true) { contentDescription = refusedDescription }) {
            GlyphDisc(R.drawable.ic_alert, warn.copy(alpha = 0.18f), warn, 70.dp, 34.dp)
        }
        Spacer(Modifier.height(10.dp))
        Label(stringResource(R.string.repair_title), 20.sp, WatchColors.Primary, bold = true)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onPair,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Accent, contentColor = WatchColors.Primary),
        ) { Label(stringResource(R.string.repair_button), 16.sp, WatchColors.Primary, bold = true, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
fun DetailsScreen(status: StatusInput) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (row in detailRows(status)) {
                item {
                    val colors = LocalStatusColors.current
                    val name = stringResource(
                        when (row.key) {
                            DetailKey.HEART -> R.string.row_heart
                            DetailKey.ACTIVITY -> R.string.row_activity
                            DetailKey.NOTIFICATIONS -> R.string.row_notifications
                            DetailKey.FALL -> R.string.row_fall
                            DetailKey.NETWORK -> R.string.row_network
                        },
                    )
                    StatusRow(
                        glyph = if (row.ok) R.drawable.ic_check else R.drawable.ic_alert,
                        tint = if (row.ok) colors.ok else colors.warn,
                        text = name,
                        description = stringResource(if (row.ok) R.string.cd_row_ok else R.string.cd_row_warn, name),
                        modifier = Modifier.fillMaxWidth(0.82f),
                    )
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp)) {
                    Label(stringResource(R.string.details_footer, status.versionName, status.serverHost), 11.sp, WatchColors.Secondary)
                    if (status.queued > 0) Label(stringResource(R.string.details_queue, status.queued), 11.sp, WatchColors.Secondary)
                    if (status.lastUploadTime.isNotEmpty()) Label(status.lastUploadTime + " · " + status.lastUploadState, 10.sp, WatchColors.Secondary)
                }
            }
        }
    }
}
