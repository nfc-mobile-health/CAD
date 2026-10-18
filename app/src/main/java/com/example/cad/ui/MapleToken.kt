package com.example.cad.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cad.ui.theme.MapleColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class TokenState { Ready, Working, Done, Problem }

@Composable
fun MapleToken(state: TokenState, size: Dp, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reducedMotion = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f
    val transition = rememberInfiniteTransition(label = "token-rings")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "token-pulse",
    )
    val fill by animateColorAsState(state.fill(), tween(300), label = "token-fill")
    Canvas(
        modifier = modifier.size(size).semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        val lobeScale = 0.61f
        if (state == TokenState.Ready || state == TokenState.Working) {
            val ringColor = if (state == TokenState.Ready) MapleColors.Mist2 else MapleColors.Amber
            val progress = if (reducedMotion) 0f else pulse
            drawPath(
                maplePath(lobeScale * (1.2f + progress * 0.08f)),
                ringColor.copy(alpha = 1f - progress * 0.3f),
                style = Stroke(3.dp.toPx()),
            )
            drawPath(
                maplePath(lobeScale * (1.4f + progress * 0.08f)),
                ringColor.copy(alpha = 0.5f - progress * 0.2f),
                style = Stroke(3.dp.toPx()),
            )
        }
        drawPath(maplePath(lobeScale), fill)
        drawSymbol(state, center, min(this.size.width, this.size.height) * .115f)
    }
}

private fun TokenState.fill(): Color = when (this) {
    TokenState.Ready, TokenState.Done -> MapleColors.Pine
    TokenState.Working -> MapleColors.Amber
    TokenState.Problem -> MapleColors.Red
}

private fun DrawScope.maplePath(scale: Float): Path {
    val c = center
    val amp = .085f
    val base = min(size.width, size.height) * scale / 2f / (1f + amp)
    return Path().apply {
        for (index in 0..170) {
            val theta = 2.0 * PI * index / 170
            val r = base * (1f + amp * cos(5 * (theta + PI / 2)).toFloat())
            val point = Offset(c.x + r * cos(theta).toFloat(), c.y + r * sin(theta).toFloat())
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
}

private fun DrawScope.drawSymbol(state: TokenState, c: Offset, unit: Float) {
    val color = if (state == TokenState.Working) MapleColors.Ink else MapleColors.White
    when (state) {
        TokenState.Done -> {
            drawLine(color, Offset(c.x - unit, c.y), Offset(c.x - unit / 4, c.y + unit), unit / 3, StrokeCap.Round)
            drawLine(color, Offset(c.x - unit / 4, c.y + unit), Offset(c.x + unit * 1.2f, c.y - unit), unit / 3, StrokeCap.Round)
        }
        TokenState.Problem -> {
            drawLine(color, Offset(c.x, c.y - unit), Offset(c.x, c.y + unit / 3), unit / 3, StrokeCap.Round)
            drawCircle(color, unit / 6, Offset(c.x, c.y + unit))
        }
        TokenState.Ready, TokenState.Working -> {
            // A card and bilateral waves stay legible at the 92 dp inline size.
            drawRect(
                color = color,
                topLeft = Offset(c.x - unit * .35f, c.y - unit * .78f),
                size = androidx.compose.ui.geometry.Size(unit * .7f, unit * 1.56f),
            )
            for (factor in listOf(1.05f, 1.75f)) {
                val left = Path().apply {
                    moveTo(c.x - unit * factor, c.y - unit * factor)
                    quadraticTo(c.x - unit * .55f, c.y, c.x - unit * factor, c.y + unit * factor)
                }
                val right = Path().apply {
                    moveTo(c.x + unit * factor, c.y - unit * factor)
                    quadraticTo(c.x + unit * .55f, c.y, c.x + unit * factor, c.y + unit * factor)
                }
                drawPath(left, color, style = Stroke(unit / 5, cap = StrokeCap.Round))
                drawPath(right, color, style = Stroke(unit / 5, cap = StrokeCap.Round))
            }
        }
    }
}
