package com.example.watchtest.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.watchtest.uii.Monitoring

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun openAppSettings(c: Context) {
    try {
        c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: RuntimeException) {
        Toast.makeText(c, "Cài đặt > Ứng dụng > UII > Quyền", Toast.LENGTH_LONG).show()
    }
}

/** Two ways to ask: by itself when the app starts, or because the wearer tapped the amber ring. */
class PermissionActions(val auto: () -> Unit, val fix: () -> Unit)

/**
 * After the wearer has refused, Android may stop showing its dialog and answer "denied" at once; the only way back
 * is the app's page in Settings. That page is opened only when the wearer asked for the fix (tapped the ring) and
 * the answer came back instantly, i.e. without a dialog; it is never forced on someone who has just said no.
 */
@Composable
fun rememberPermissionActions(): PermissionActions {
    val context = LocalContext.current
    var askedByWearer by remember { mutableStateOf(false) }
    var launchedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        // Permissions change what Health Services and the motion service may do, so start them again.
        Monitoring.start(context)
        val noDialogShown = SystemClock.elapsedRealtime() - launchedAt < 400
        val stillDenied = results.values.any { !it }
        if (askedByWearer && stillDenied && noDialogShown) openAppSettings(context)
        askedByWearer = false
    }
    return remember(context, launcher) {
        fun ask(byWearer: Boolean) {
            val missing = StatusSource.neededPermissions().filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) return
            askedByWearer = byWearer
            launchedAt = SystemClock.elapsedRealtime()
            launcher.launch(missing.toTypedArray())
        }
        PermissionActions(auto = { ask(false) }, fix = { ask(true) })
    }
}
