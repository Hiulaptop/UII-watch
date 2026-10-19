package com.example.watchtest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.watchtest.uii.Monitoring
import com.example.watchtest.uii.Uii

/** Ask for permissions once per app process; after that the wearer uses the ring on the home screen. */
private var promptedThisProcess = false

object Routes {
    const val HOME = "home"
    const val DETAILS = "details"
    const val PAIR = "pair"
    const val SOS = "sos"
    const val COUNTDOWN = "countdown/{kind}"
    const val DELIVERY = "delivery/{id}"
    const val CARD = "card"
    const val CONSENT = "consent"
    fun countdown(kind: SosKind) = "countdown/${kind.name}"
    fun delivery(id: String) = "delivery/$id"
}

@Composable
fun UiiApp() {
    UiiWatchTheme {
        AppScaffold {
            val nav = rememberSwipeDismissableNavController()
            val status by rememberStatus()
            val permissions = rememberPermissionActions()
            val context = LocalContext.current

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (Uii.get(context).secrets.isPaired) {
                    if (!Uii.get(context).state.consentAccepted()) {
                        // Ask before any permission or sensor is touched.
                        if (nav.currentDestination?.route != Routes.CONSENT) nav.navigate(Routes.CONSENT)
                    } else if (!promptedThisProcess && missingPermissions(StatusSource.read(context)).isNotEmpty()) {
                        promptedThisProcess = true
                        permissions.auto()
                    } else {
                        Monitoring.start(context)
                    }
                } else {
                    promptedThisProcess = false
                }
            }

            SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        status = status,
                        onSos = { nav.navigate(Routes.SOS) },
                        onDetails = { nav.navigate(Routes.DETAILS) },
                        onFixPermission = {
                            if (Uii.get(context).state.consentAccepted()) permissions.fix()
                            else nav.navigate(Routes.CONSENT)
                        },
                        onPair = { nav.navigate(Routes.PAIR) },
                    )
                }
                composable(Routes.DETAILS) { DetailsScreen(status) }
                composable(Routes.PAIR) {
                    PairScreen(onPaired = {
                        nav.popBackStack(Routes.HOME, inclusive = false)
                        if (!Uii.get(context).state.consentAccepted()) nav.navigate(Routes.CONSENT)
                        else if (missingPermissions(StatusSource.read(context)).isNotEmpty()) permissions.auto()
                    })
                }
                composable(Routes.CONSENT) {
                    ConsentScreen(
                        onAccept = {
                            Uii.get(context).state.setConsentAccepted()
                            nav.popBackStack(Routes.HOME, inclusive = false)
                            if (missingPermissions(StatusSource.read(context)).isNotEmpty()) permissions.auto()
                            else Monitoring.start(context)
                        },
                        onDecline = { nav.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }
                composable(Routes.SOS) {
                    SosMenuScreen(onPick = { nav.navigate(Routes.countdown(it)) })
                }
                composable(
                    Routes.COUNTDOWN,
                    arguments = listOf(navArgument("kind") { type = NavType.StringType }),
                ) { entry ->
                    val kind = SosKind.valueOf(entry.arguments?.getString("kind") ?: SosKind.NOW.name)
                    CountdownScreen(
                        kind = kind,
                        onCancel = { nav.popBackStack(Routes.HOME, inclusive = false) },
                        onFinished = { id ->
                            nav.navigate(Routes.delivery(id)) { popUpTo(Routes.HOME) }
                        },
                    )
                }
                composable(
                    Routes.DELIVERY,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    DeliveryScreen(
                        itemId = entry.arguments?.getString("id") ?: "",
                        onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                        onCard = { nav.navigate(Routes.CARD) { popUpTo(Routes.HOME) } },
                    )
                }
                composable(Routes.CARD) {
                    val card = Uii.get(context).sosCard()
                    if (card != null) {
                        SosCardScreen(card, onDone = { nav.popBackStack(Routes.HOME, inclusive = false) })
                    } else {
                        nav.popBackStack(Routes.HOME, inclusive = false)
                    }
                }
            }
        }
    }
}
