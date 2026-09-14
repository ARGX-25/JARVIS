package com.example.jarvis.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.jarvis.ui.theme.JarvisColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

private const val TwoPi = (2 * PI).toFloat()
private const val Steps = 72

/**
 * Overlapping translucent ribbons that taper to a thin centre line at both ends.
 * [level] is the microphone level in 0..1; silence still animates gently.
 */
@Composable
fun VoiceWaveform(level: Float, modifier: Modifier = Modifier) {
    val smoothedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 140),
        label = "voiceLevel"
    )
    val phase by rememberInfiniteTransition(label = "waveform").animateFloat(
        initialValue = 0f,
        targetValue = TwoPi,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing)),
        label = "wavePhase"
    )

    Canvas(modifier = modifier) {
        val mid = size.height / 2f
        val maxAmplitude = mid * (0.16f + 0.84f * smoothedLevel)
        val brush = Brush.horizontalGradient(
            colors = listOf(JarvisColors.Periwinkle, JarvisColors.Cyan, JarvisColors.Azure, JarvisColors.Royal),
            startX = 0f,
            endX = size.width
        )
        val heights = FloatArray(Steps + 1)

        for (ribbon in Ribbons) {
            for (step in 0..Steps) {
                val t = step / Steps.toFloat()
                val distance = (t - ribbon.center) / ribbon.spread
                val envelope = exp(-distance * distance)
                val lobe = 0.3f + 0.7f * abs(sin(TwoPi * ribbon.frequency * t + phase * ribbon.speed + ribbon.offset))
                heights[step] = maxAmplitude * ribbon.weight * envelope * lobe
            }
            val path = Path().apply {
                moveTo(0f, mid - heights[0])
                for (step in 1..Steps) lineTo(size.width * step / Steps, mid - heights[step])
                for (step in Steps downTo 0) lineTo(size.width * step / Steps, mid + heights[step])
                close()
            }
            drawPath(path = path, brush = brush, alpha = ribbon.alpha)
        }

        drawLine(brush, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round, alpha = 0.18f)
        drawLine(brush, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round, alpha = 0.9f)
    }
}

// Speeds are whole numbers so each ribbon completes full cycles and the loop has no visible jump.
private data class Ribbon(
    val frequency: Float,
    val speed: Float,
    val offset: Float,
    val weight: Float,
    val center: Float,
    val spread: Float,
    val alpha: Float
)

private val Ribbons = listOf(
    Ribbon(frequency = 1.6f, speed = 1f, offset = 0f, weight = 1f, center = 0.5f, spread = 0.24f, alpha = 0.42f),
    Ribbon(frequency = 2.4f, speed = -2f, offset = 1.3f, weight = 0.8f, center = 0.46f, spread = 0.2f, alpha = 0.38f),
    Ribbon(frequency = 3.1f, speed = 1f, offset = 2.6f, weight = 0.65f, center = 0.56f, spread = 0.18f, alpha = 0.34f),
    Ribbon(frequency = 1.1f, speed = 2f, offset = 4f, weight = 0.55f, center = 0.5f, spread = 0.3f, alpha = 0.3f)
)
