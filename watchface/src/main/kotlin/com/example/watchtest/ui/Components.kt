package com.example.watchtest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text

/** A circle with a progress arc (full by default): the status ring of the home screen and the countdown. */
@Composable
fun Ring(
    color: Color,
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    strokeWidth: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        }
        content()
    }
}

/** The ring made tappable, with one spoken sentence describing the state. */
@Composable
fun StatusRing(
    color: Color,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    content: @Composable () -> Unit,
) {
    Ring(
        color = color,
        progress = progress,
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        content = content,
    )
}

@Composable
fun GlyphIcon(res: Int, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    // Decorative: the surrounding element carries the spoken description.
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** A filled disc with a glyph in it (SOS options, the unpaired screen). */
@Composable
fun GlyphDisc(res: Int, discColor: Color, glyphColor: Color, discSize: Dp, glyphSize: Dp) {
    Box(Modifier.size(discSize).clip(CircleShape).background(discColor), contentAlignment = Alignment.Center) {
        GlyphIcon(res, glyphColor, glyphSize)
    }
}

@Composable
fun Label(text: String, size: TextUnit, color: Color, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center, modifier = modifier)
}

/** A rounded row on the surface colour with a leading glyph and a one-word label. */
@Composable
fun StatusRow(glyph: Int, tint: Color, text: String, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(CircleShape)
            .background(WatchColors.Surface)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(10.dp))
        GlyphIcon(glyph, tint, 14.dp)
        Spacer(Modifier.width(8.dp))
        Label(text, 14.sp, WatchColors.Primary, bold = true)
    }
}

@Composable
fun CenterColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) { content() }
}
