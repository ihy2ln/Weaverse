package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * A deliberately chunky, low-poly d20 animation. The resolved number is the
 * same backend roll supplied to the AI DM, never a second cosmetic roll.
 */
@Composable
fun PixelDiceRollOverlay(
    roll: AdventureRoll?,
    sequence: Long,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var resolved by remember { mutableStateOf(false) }
    var face by remember { mutableIntStateOf(1) }
    LaunchedEffect(sequence, roll) {
        if (sequence == 0L || roll == null) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        resolved = false
        repeat(11) {
            face = Random.nextInt(1, 21)
            delay(64)
        }
        face = roll.total
        resolved = true
        delay(1050)
        visible = false
    }

    val transition = rememberInfiniteTransition(label = "pixel-d20")
    val rotation by transition.animateFloat(
        initialValue = -10f,
        targetValue = 350f,
        animationSpec = infiniteRepeatable(tween(720, easing = LinearEasing)),
        label = "d20 rotation",
    )
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.88f),
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xE620172B), RoundedCornerShape(14.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                FacetedD20(
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            rotationZ = if (resolved) 0f else rotation
                            scaleX = if (resolved) 1f else 0.92f
                            scaleY = if (resolved) 1f else 0.92f
                        },
                )
                Text(
                    face.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                if (resolved && roll != null) {
                    "${roll.system} · ${roll.notation} = ${roll.total}"
                } else {
                    "ROLLING ${roll?.notation.orEmpty()}"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (resolved) Color(0xFF8FE3B2) else Color.White,
            )
        }
    }
}

@Composable
private fun FacetedD20(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val top = Offset(w * .50f, h * .03f)
        val upperRight = Offset(w * .94f, h * .30f)
        val lowerRight = Offset(w * .82f, h * .82f)
        val bottom = Offset(w * .50f, h * .98f)
        val lowerLeft = Offset(w * .18f, h * .82f)
        val upperLeft = Offset(w * .06f, h * .30f)
        val center = Offset(w * .50f, h * .48f)
        val midLeft = Offset(w * .22f, h * .42f)
        val midRight = Offset(w * .78f, h * .42f)

        fun triangle(a: Offset, b: Offset, c: Offset, color: Color) {
            val path = Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(c.x, c.y)
                close()
            }
            drawPath(path, color)
        }

        triangle(top, upperRight, center, Color(0xFF8956D8))
        triangle(top, center, upperLeft, Color(0xFF3F2575))
        triangle(upperLeft, center, midLeft, Color(0xFF623BA1))
        triangle(upperRight, midRight, center, Color(0xFFB56AF1))
        triangle(midLeft, center, bottom, Color(0xFF4F2D82))
        triangle(center, midRight, bottom, Color(0xFF7646B8))
        triangle(upperLeft, midLeft, lowerLeft, Color(0xFF2D1C51))
        triangle(midLeft, bottom, lowerLeft, Color(0xFF9860CD))
        triangle(midRight, lowerRight, bottom, Color(0xFF3A2361))
        triangle(upperRight, lowerRight, midRight, Color(0xFF7040A7))

        val outline = Path().apply {
            moveTo(top.x, top.y)
            lineTo(upperRight.x, upperRight.y)
            lineTo(lowerRight.x, lowerRight.y)
            lineTo(bottom.x, bottom.y)
            lineTo(lowerLeft.x, lowerLeft.y)
            lineTo(upperLeft.x, upperLeft.y)
            close()
        }
        drawPath(outline, InkAccentBlue, style = Stroke(width = 5f))
        listOf(top, upperRight, lowerRight, bottom, lowerLeft, upperLeft).forEach { point ->
            drawLine(Color.White.copy(alpha = .38f), point, center, strokeWidth = 2f)
        }
        // Square corner sparks keep the visual intentionally pixelized.
        drawRect(Color.White, topLeft = Offset(w * .02f, h * .12f), size = androidx.compose.ui.geometry.Size(7f, 7f))
        drawRect(InkAccentBlue, topLeft = Offset(w * .91f, h * .73f), size = androidx.compose.ui.geometry.Size(9f, 9f))
        drawRect(Color(0xFFB56AF1), topLeft = Offset(w * .07f, h * .80f), size = androidx.compose.ui.geometry.Size(6f, 6f))
    }
}
