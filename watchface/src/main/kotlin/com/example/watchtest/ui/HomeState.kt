package com.example.watchtest.ui

/** The three permissions the wearer can be asked to fix, in the order they are shown. */
enum class Perm { HEART, ACTIVITY, NOTIFICATIONS }

/** What the home screen shows. Exactly one of these at any time. */
sealed interface HomeState {
    data class Ok(val heartRate: Int?) : HomeState
    /** Something the wearer can fix; the first missing item is named on screen. */
    data class Attention(val missing: List<Perm>) : HomeState
    /** Items are queued and the server cannot be reached. */
    data class Waiting(val queued: Int) : HomeState
    data object Unpaired : HomeState
    /** The server dropped this watch's credentials; it must be paired again. */
    data object Refused : HomeState
}

/** Plain values the state is computed from, so the rules can be tested without Android. */
data class StatusInput(
    val paired: Boolean = false,
    val refused: Boolean = false,
    val permissions: Map<Perm, Boolean> = emptyMap(),
    /** Health Services told us the permission it needs was taken away. */
    val permissionLost: Boolean = false,
    val queued: Int = 0,
    /** The last upload attempt failed for lack of a connection or a server error. */
    val uploadBlocked: Boolean = false,
    /** The system reports a working internet connection right now. */
    val networkAvailable: Boolean = true,
    val heartRate: Int? = null,
    /** Null when Health Services has not reported yet. */
    val fallSupported: Boolean? = null,
    /** Position can be read around an SOS (optional: it never changes the Home state). */
    val locationGranted: Boolean = false,
    val versionName: String = "",
    val serverHost: String = "",
    val lastUploadTime: String = "",
    val lastUploadState: String = "",
)

/** No connection, or the last attempt to reach the server failed. */
fun cannotReachServer(i: StatusInput) = !i.networkAvailable || i.uploadBlocked

fun missingPermissions(i: StatusInput): List<Perm> =
    Perm.entries.filter { i.permissions[it] == false }

fun homeState(i: StatusInput): HomeState {
    if (!i.paired) return if (i.refused) HomeState.Refused else HomeState.Unpaired
    val missing = missingPermissions(i)
    if (missing.isNotEmpty()) return HomeState.Attention(missing)
    // A revoked health permission can look granted from the app's side; name heart rate, which is what is revoked.
    if (i.permissionLost) return HomeState.Attention(listOf(Perm.HEART))
    if (i.queued > 0 && cannotReachServer(i)) return HomeState.Waiting(i.queued)
    return HomeState.Ok(i.heartRate)
}

/** What happened to an alert the wearer raised. */
enum class Delivery { WAITING, SENT, FAILED }

/** A heart rate older than this is not shown as if it described "now". */
const val HEART_RATE_FRESH_MS = 2 * 60_000L

fun freshHeartRate(bpm: Double?, ageMs: Long): Int? =
    if (bpm != null && !bpm.isNaN() && ageMs in 0..HEART_RATE_FRESH_MS) Math.round(bpm).toInt() else null

/** One row of the Details screen: which check and whether it passes. */
data class DetailRow(val key: DetailKey, val ok: Boolean)
enum class DetailKey { HEART, ACTIVITY, NOTIFICATIONS, FALL, NETWORK }

fun detailRows(i: StatusInput): List<DetailRow> = listOf(
    DetailRow(DetailKey.HEART, i.permissions[Perm.HEART] == true && !i.permissionLost),
    DetailRow(DetailKey.ACTIVITY, i.permissions[Perm.ACTIVITY] == true),
    DetailRow(DetailKey.NOTIFICATIONS, i.permissions[Perm.NOTIFICATIONS] == true),
    DetailRow(DetailKey.FALL, i.fallSupported == true),
    DetailRow(DetailKey.NETWORK, i.networkAvailable && !(i.queued > 0 && i.uploadBlocked)),
)
