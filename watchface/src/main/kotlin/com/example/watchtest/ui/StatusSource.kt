package com.example.watchtest.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.watchtest.BuildConfig
import com.example.watchtest.uii.Permissions
import com.example.watchtest.uii.Uii
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Reads the plain values the screens need from the existing core. */
object StatusSource {
    private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** The permissions the app asks for on this Android level, in the order the home screen names them. */
    fun neededPermissions(): List<String> = Permissions.needed(Build.VERSION.SDK_INT)

    private fun hasInternet(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun read(c: Context): StatusInput {
        val uii = Uii.get(c)
        val s = uii.state
        val now = System.currentTimeMillis()
        val hr = uii.freshHeartRate(now)?.let { freshHeartRate(it, 0) }
        return StatusInput(
            paired = uii.secrets.isPaired,
            refused = s.pairingRevoked(),
            permissions = mapOf(
                Perm.HEART to granted(c, Permissions.heartRate(Build.VERSION.SDK_INT)),
                Perm.ACTIVITY to granted(c, Manifest.permission.ACTIVITY_RECOGNITION),
                Perm.NOTIFICATIONS to granted(c, Manifest.permission.POST_NOTIFICATIONS),
            ),
            permissionLost = s.permissionLost(),
            queued = uii.outbox.size(),
            uploadBlocked = s.lastUploadState().startsWith("RETRY_LATER"),
            networkAvailable = hasInternet(c),
            heartRate = hr,
            locationGranted = granted(c, Manifest.permission.ACCESS_FINE_LOCATION) && granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            fallSupported = when (s.fallSupport()) { 1 -> true; 0 -> false; else -> null },
            versionName = BuildConfig.VERSION_NAME,
            serverHost = Uri.parse(BuildConfig.API_BASE_URL).host ?: "",
            lastUploadTime = if (s.lastUploadMs() > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.lastUploadMs())) else "",
            lastUploadState = s.lastUploadState(),
        )
    }

    /** True while the outbox still holds the item: it has not been accepted by the server. */
    fun isQueued(c: Context, itemId: String): Boolean = Uii.get(c).outbox.list().any { it.id == itemId }

    /** Where an alert is: still waiting, accepted, or refused by the server for good. */
    fun delivery(c: Context, itemId: String): Delivery = when {
        isQueued(c, itemId) -> Delivery.WAITING
        Uii.get(c).state.isRejected(itemId) -> Delivery.FAILED
        else -> Delivery.SENT
    }
}

/** The status, refreshed every two seconds while the screen is in front. */
@Composable
fun rememberStatus(): State<StatusInput> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = StatusSource.read(context), context, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = StatusSource.read(context)
                delay(2000)
            }
        }
    }
}
