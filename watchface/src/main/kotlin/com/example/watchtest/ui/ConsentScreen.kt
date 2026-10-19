package com.example.watchtest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import com.example.watchtest.R

/**
 * What data the app reads, why, who sees it, and that it is not a medical device. Shown before the app asks for any
 * sensor permission or starts monitoring, as Google Play's policy for health and sensor data requires.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Label(stringResource(R.string.consent_title), 18.sp, WatchColors.Primary, bold = true) }
            item { Paragraph(stringResource(R.string.consent_collect)) }
            item { Paragraph(stringResource(R.string.consent_share)) }
            item { Paragraph(stringResource(R.string.consent_disclaimer)) }
            item {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Ok, contentColor = WatchColors.Bg),
                ) { Label(stringResource(R.string.consent_accept), 16.sp, WatchColors.Bg, bold = true, modifier = Modifier.fillMaxWidth()) }
            }
            item {
                Button(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Surface, contentColor = WatchColors.Primary),
                ) { Label(stringResource(R.string.consent_decline), 16.sp, WatchColors.Primary, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Label(text, 14.sp, WatchColors.Secondary, modifier = Modifier.fillMaxWidth(0.9f))
}
