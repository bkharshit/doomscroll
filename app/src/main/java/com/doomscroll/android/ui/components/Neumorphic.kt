package com.doomscroll.android.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.ui.theme.CoralFixed
import com.doomscroll.android.ui.theme.CoralGradientEnd
import com.doomscroll.android.ui.theme.CoralGradientStart
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.InfinityWhite
import com.doomscroll.android.ui.theme.MintSecondary
import com.doomscroll.android.ui.theme.TactileBorder
import com.doomscroll.android.ui.theme.TactileGroove
import com.doomscroll.android.ui.theme.TactileRecessed
import com.doomscroll.android.ui.theme.TactileShadowDark
import com.doomscroll.android.ui.theme.TactileShadowLight
import com.doomscroll.android.ui.theme.TactileSurface
import com.doomscroll.android.ui.theme.TactileSurfaceVariant

fun Modifier.neoExtruded(
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 5.dp,
    backgroundColor: Color = TactileSurface,
    lightShadowColor: Color = TactileShadowLight,
    darkShadowColor: Color = TactileShadowDark,
    borderColor: Color = TactileBorder,
    borderWidth: Dp = 1.dp
): Modifier = this
    .drawWithCache {
        val cornerRadiusPx = cornerRadius.toPx()
        val blurRadius = elevation.toPx() * 1.6f
        val offsetLight = -elevation.toPx()
        val offsetDark = elevation.toPx()

        val lightPaint = if (blurRadius > 0f) {
            android.graphics.Paint().apply {
                color = lightShadowColor.toArgb()
                isAntiAlias = true
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        } else null

        val darkPaint = if (blurRadius > 0f) {
            android.graphics.Paint().apply {
                color = darkShadowColor.toArgb()
                isAntiAlias = true
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        } else null

        onDrawBehind {
            if (blurRadius > 0f && lightPaint != null && darkPaint != null) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    nativeCanvas.drawRoundRect(
                        offsetLight,
                        offsetLight,
                        size.width + offsetLight,
                        size.height + offsetLight,
                        cornerRadiusPx,
                        cornerRadiusPx,
                        lightPaint
                    )
                    nativeCanvas.drawRoundRect(
                        offsetDark,
                        offsetDark,
                        size.width + offsetDark,
                        size.height + offsetDark,
                        cornerRadiusPx,
                        cornerRadiusPx,
                        darkPaint
                    )
                }
            }
        }
    }
    .background(backgroundColor, RoundedCornerShape(cornerRadius))
    .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))

fun Modifier.neoExtrudedCircle(
    elevation: Dp = 5.dp,
    backgroundColor: Color = TactileSurface,
    lightShadowColor: Color = TactileShadowLight,
    darkShadowColor: Color = TactileShadowDark,
    borderColor: Color = TactileBorder,
    borderWidth: Dp = 1.dp
): Modifier = this
    .drawWithCache {
        val radius = size.minDimension / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val blurRadius = elevation.toPx() * 1.6f
        val offsetLight = -elevation.toPx()
        val offsetDark = elevation.toPx()

        val lightPaint = if (blurRadius > 0f) {
            android.graphics.Paint().apply {
                color = lightShadowColor.toArgb()
                isAntiAlias = true
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        } else null

        val darkPaint = if (blurRadius > 0f) {
            android.graphics.Paint().apply {
                color = darkShadowColor.toArgb()
                isAntiAlias = true
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        } else null

        onDrawBehind {
            if (blurRadius > 0f && lightPaint != null && darkPaint != null) {
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    nativeCanvas.drawCircle(
                        centerX + offsetLight,
                        centerY + offsetLight,
                        radius,
                        lightPaint
                    )
                    nativeCanvas.drawCircle(
                        centerX + offsetDark,
                        centerY + offsetDark,
                        radius,
                        darkPaint
                    )
                }
            }
        }
    }
    .background(backgroundColor, CircleShape)
    .border(borderWidth, borderColor, CircleShape)

fun Modifier.neoRecessed(
    cornerRadius: Dp = 18.dp,
    backgroundColor: Color = TactileRecessed,
    innerBorderColor: Color = TactileGroove
): Modifier = this
    .background(backgroundColor, RoundedCornerShape(cornerRadius))
    .border(1.dp, innerBorderColor, RoundedCornerShape(cornerRadius))

fun Modifier.neoRecessedCircle(
    backgroundColor: Color = TactileRecessed,
    innerBorderColor: Color = TactileGroove
): Modifier = this
    .background(backgroundColor, CircleShape)
    .border(1.dp, innerBorderColor, CircleShape)

@Composable
fun NeumorphicToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = CoralPrimary
) {
    val trackWidth = 58.dp
    val trackHeight = 32.dp
    val knobSize = 24.dp
    val padding = 4.dp
    val maxOffset = trackWidth - knobSize - (padding * 2)

    val animatedOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "toggle_knob_offset"
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .neoRecessed(cornerRadius = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(padding),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .size(knobSize)
                .neoExtrudedCircle(
                    elevation = 2.dp,
                    backgroundColor = if (checked) activeColor else TactileSurfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(InfinityWhite, CircleShape)
                )
            }
        }
    }
}

@Composable
fun TactileProgressSlider(
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    fillBrush: Brush = Brush.horizontalGradient(listOf(CoralPrimary, CoralGradientEnd)),
    knobDotColor: Color = CoralPrimary
) {
    // pointerInput(Unit) keeps its first lambdas forever; read callbacks through updated state so
    // callers whose state is keyed (e.g. remember(limit) drafts) aren't stuck with stale closures.
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .neoRecessed(cornerRadius = 17.dp)
            .padding(4.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = if (size.width > 0) (offset.x / size.width).coerceIn(0f, 1f) else 0f
                    currentOnValueChange(ratio)
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val ratio = if (size.width > 0) (offset.x / size.width).coerceIn(0f, 1f) else 0f
                        currentOnValueChange(ratio)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val ratio = if (size.width > 0) (change.position.x / size.width).coerceIn(0f, 1f) else 0f
                        currentOnValueChange(ratio)
                    },
                    onDragEnd = {
                        currentOnValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        currentOnValueChangeFinished?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val knobSize = 26.dp
        val availableSlideWidth = (totalWidth - knobSize).coerceAtLeast(0.dp)
        val clampedValue = value.coerceIn(0f, 1f)
        val fillWidth = (knobSize + availableSlideWidth * clampedValue).coerceAtMost(totalWidth)
        val knobOffset = (availableSlideWidth * clampedValue).coerceIn(0.dp, availableSlideWidth)

        // Filled Track
        Box(
            modifier = Modifier
                .width(fillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(13.dp))
                .background(fillBrush)
        )

        // Knob Thumb
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(knobSize)
                .neoExtrudedCircle(elevation = 3.dp, backgroundColor = TactileSurface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(knobDotColor, CircleShape)
            )
        }
    }
}
