package com.example.watchtest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Colours of the watch app. They mirror the Figma variables `watch/...` (file "UII Web", row "Watch app v2"):
 * change them there and here together.
 */
object WatchColors {
    val Bg = Color(0xFF101216)         // watch/bg
    val Primary = Color(0xFFF2F5F9)    // watch/primary
    val Secondary = Color(0xFFADB5C0)  // watch/secondary
    val Surface = Color(0xFF30343C)    // watch/surface
    val Accent = Color(0xFFFF682C)     // watch/accent (= ember-orange on the web)
    val Ok = Color(0xFF34D399)         // watch/ok
    val Warn = Color(0xFFFFB020)       // watch/warn
}

/** Material 3 has no slots for "ok" and "warning", so the status colours travel separately. */
data class StatusColors(val ok: Color, val warn: Color, val waiting: Color)

val LocalStatusColors = staticCompositionLocalOf { StatusColors(WatchColors.Ok, WatchColors.Warn, WatchColors.Secondary) }

private val scheme = ColorScheme().copy(
    primary = WatchColors.Accent,
    onPrimary = WatchColors.Primary,
    background = WatchColors.Bg,
    onBackground = WatchColors.Primary,
    onSurface = WatchColors.Primary,
    onSurfaceVariant = WatchColors.Secondary,
    surfaceContainerLow = WatchColors.Surface,
    surfaceContainer = WatchColors.Surface,
    surfaceContainerHigh = WatchColors.Surface,
    secondaryContainer = WatchColors.Surface,
    onSecondaryContainer = WatchColors.Primary,
)

@Composable
fun UiiWatchTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides StatusColors(WatchColors.Ok, WatchColors.Warn, WatchColors.Secondary)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
