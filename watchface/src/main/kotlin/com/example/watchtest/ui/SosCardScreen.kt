package com.example.watchtest.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import com.example.watchtest.R
import com.example.watchtest.uii.SosCard

/**
 * The wearer's emergency card, shown once an alert has been accepted by the server. It is meant for whoever is
 * standing next to the wearer, so it stays on screen until it is dismissed and each contact can be dialled.
 */
@Composable
fun SosCardScreen(card: SosCard, onDone: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()
    val spoken = stringResource(R.string.cd_card)
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics { contentDescription = spoken },
        ) {
            item {
                val title = listOfNotNull(card.name, card.age?.let { stringResource(R.string.card_age, it) }).joinToString(" · ")
                Label(title.ifEmpty { stringResource(R.string.card_title) }, 18.sp, WatchColors.Primary, bold = true)
            }
            card.bloodType?.let { item { Field(stringResource(R.string.card_blood), it) } }
            card.epilepsyType?.let { item { Field(stringResource(R.string.card_epilepsy), it) } }
            card.notes?.let { item { Field(stringResource(R.string.card_notes), it) } }
            if (card.allergies.isNotEmpty()) item { Field(stringResource(R.string.card_allergies), card.allergies.joinToString(", ")) }
            if (card.conditions.isNotEmpty()) item { Field(stringResource(R.string.card_conditions), card.conditions.joinToString(", ")) }
            if (card.meds.isNotEmpty()) item { Field(stringResource(R.string.card_meds), card.meds.joinToString("\n")) }
            for (contact in card.contacts) {
                item {
                    val who = listOfNotNull(contact.name, contact.relation?.takeIf { it != contact.name }).joinToString(" · ")
                    Button(
                        onClick = { dial(context, contact.phone) },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Surface, contentColor = WatchColors.Primary),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Label(who, 14.sp, WatchColors.Primary, bold = true)
                            Label(contact.phone, 13.sp, WatchColors.Secondary)
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(0.62f).padding(top = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WatchColors.Ok, contentColor = WatchColors.Bg),
                ) { Label(stringResource(R.string.done), 16.sp, WatchColors.Bg, bold = true, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun Field(name: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.9f)) {
        Label(name, 12.sp, WatchColors.Secondary)
        Label(value, 15.sp, WatchColors.Primary)
    }
}

/** Opens the dialler with the number filled in; a watch without a dialler simply does nothing. */
private fun dial(context: Context, phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
