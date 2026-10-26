package com.example.watchtest.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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

/**
 * Explains, then asks for the location permissions. Optional: declining changes nothing else in the app. Android wants
 * the background permission asked on its own after the foreground one, so the two requests run one after the other.
 */
@Composable
fun LocationScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val state = Uii.get(context).state
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true || results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val backgroundGranted = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted && !backgroundGranted) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else onDone()
    }
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Label(stringResource(R.string.location_title), 18.sp, WatchColors.Primary, bold = true) }
            item { Label(stringResource(R.string.location_body), 14.sp, WatchColors.Secondary, modifier = Modifier.fillMaxWidth(0.9f)) }
            item {
                Button(
                    onClick = {
                        state.setLocationChoice(1)
                        foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Ok, contentColor = WatchColors.Bg),
                ) { Label(stringResource(R.string.location_allow), 16.sp, WatchColors.Bg, bold = true, modifier = Modifier.fillMaxWidth()) }
            }
            item {
                Button(
                    onClick = { state.setLocationChoice(2); onDone() },
                    modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Surface, contentColor = WatchColors.Primary),
                ) { Label(stringResource(R.string.location_decline), 16.sp, WatchColors.Primary, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
