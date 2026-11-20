package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCritical
import com.example.ui.theme.EmeraldOptimal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MoltenGold
import com.example.ui.theme.MoltenGoldGlow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumElevated
import com.example.ui.theme.TitaniumSurface
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class LiquidNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val badgeCount: Int = 0
)

/**
 * Water-smooth spring press physics modifier for responsive tactile micro-interactions.
 */
fun Modifier.liquidSpringClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquid_press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(color = MoltenGold.copy(alpha = 0.25f)),
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}

/**
 * Floating dark glassmorphic liquid navigation dock with morphing molten-gold/cyan metaball capsule
 * that stretches dynamically as it glides between tabs like mercury/water.
 */
@Composable
fun LiquidFloatingNavBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    pagerOffsetFraction: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val continuousPosition = (selectedIndex + pagerOffsetFraction).coerceIn(0f, (items.size - 1).toFloat())
    val animatedPosition by animateFloatAsState(
        targetValue = continuousPosition,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 340f
        ),
        label = "liquid_nav_pos"
    )

    val stretchFactor = (abs(continuousPosition - animatedPosition) * 1.4f).coerceIn(0f, 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            color = ObsidianSurface.copy(alpha = 0.94f),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        GlassBorder.copy(alpha = 0.7f),
                        MoltenGold.copy(alpha = 0.45f),
                        TelemetryCyan.copy(alpha = 0.45f),
                        GlassBorder.copy(alpha = 0.7f)
                    )
                )
            ),
            shadowElevation = 18.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // Liquid morphing indicator canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = items.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val basePillWidth = slotWidth * (0.90f + stretchFactor * 0.35f)
                    val centerX = (animatedPosition + 0.5f) * slotWidth
                    val left = (centerX - basePillWidth / 2f).coerceIn(0f, size.width - basePillWidth)

                    // Outer ambient glow
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MoltenGold.copy(alpha = 0.32f),
                                TelemetryCyan.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, size.height / 2f),
                            radius = basePillWidth * 0.75f
                        ),
                        topLeft = Offset(left - 8f, 0f),
                        size = Size(basePillWidth + 16f, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )

                    // Morphing liquid glass pill
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MoltenGold.copy(alpha = 0.24f),
                                MoltenGoldGlow.copy(alpha = 0.30f),
                                TelemetryCyan.copy(alpha = 0.18f)
                            ),
                            startX = left,
                            endX = left + basePillWidth
                        ),
                        topLeft = Offset(left, 2f),
                        size = Size(basePillWidth, size.height - 4f),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )

                    // Luminous liquid top crest highlight
                    drawRoundRect(
                        color = MoltenGoldGlow.copy(alpha = 0.55f),
                        topLeft = Offset(centerX - basePillWidth * 0.22f, 4f),
                        size = Size(basePillWidth * 0.44f, 3.dp.toPx()),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }

                // Nav items row
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 0.95f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "nav_icon_scale_$index"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(28.dp))
                                .testTag(item.testTag)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = MoltenGold.copy(alpha = 0.2f)),
                                    onClick = { onSelect(index) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        tint = if (isSelected) MoltenGoldGlow else TextSecondary,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                    if (item.badgeCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(CrimsonCritical)
                                                .border(1.dp, ObsidianSurface, CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp
                                    ),
                                    color = if (isSelected) TextPrimary else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated dual-wave liquid wheat/atta silo fill tank drawn in Compose Canvas.
 */
@Composable
fun LiquidSiloTankGauge(
    fillFraction: Float,
    belowThreshold: Boolean,
    label: String,
    quantityLabel: String,
    modifier: Modifier = Modifier,
    height: Dp = 128.dp
) {
    val clampedFill = fillFraction.coerceIn(0.06f, 0.98f)
    val animatedFill by animateFloatAsState(
        targetValue = clampedFill,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 120f),
        label = "silo_fill"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "liquid_wave_loop")
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_1"
    )
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = (2 * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_2"
    )

    val primaryFluid = when {
        belowThreshold -> CrimsonCritical
        animatedFill < 0.35f -> AmberWarning
        else -> MoltenGold
    }
    val secondaryFluid = when {
        belowThreshold -> AmberWarning.copy(alpha = 0.55f)
        else -> TelemetryCyan.copy(alpha = 0.42f)
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianVoid)
            .border(
                width = 1.dp,
                color = if (belowThreshold) CrimsonCritical.copy(alpha = 0.65f) else GlassBorder,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val surfaceY = h * (1f - animatedFill)
            val amp1 = 7f
            val amp2 = 5f

            // Back counter-flowing translucent liquid wave
            val backPath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, surfaceY)
                var x = 0f
                while (x <= w + 6f) {
                    val y = surfaceY + sin((x / w * 2.2f * PI).toFloat() + wavePhase2) * amp2
                    lineTo(x, y)
                    x += 6f
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path = backPath,
                brush = Brush.verticalGradient(
                    colors = listOf(secondaryFluid, primaryFluid.copy(alpha = 0.18f)),
                    startY = surfaceY - 12f,
                    endY = h
                )
            )

            // Front molten gold/status liquid wave
            val frontPath = Path().apply {
                moveTo(0f, h)
                lineTo(0f, surfaceY)
                var x = 0f
                while (x <= w + 6f) {
                    val y = surfaceY + sin((x / w * 2.5f * PI).toFloat() + wavePhase1) * amp1
                    lineTo(x, y)
                    x += 6f
                }
                lineTo(w, h)
                close()
            }
            drawPath(
                path = frontPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryFluid.copy(alpha = 0.85f),
                        primaryFluid.copy(alpha = 0.45f),
                        ObsidianSurface.copy(alpha = 0.85f)
                    ),
                    startY = surfaceY,
                    endY = h
                )
            )

            // Subtle vertical glass sheen
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
        }

        // Overlay telemetry readout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(animatedFill * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                if (belowThreshold) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CrimsonCritical.copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LOW",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonCritical
                        )
                    }
                }
            }

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = quantityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Glowing Cubic Bezier AI Sales Demand Forecast chart with animated liquid draw-in.
 */
@Composable
fun LiquidForecastChart(
    labels: List<String>,
    values: List<Double>,
    modifier: Modifier = Modifier
) {
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
        )
    }

    val safeValues = if (values.size >= 2) values else listOf(38.0, 41.0, 44.5, 43.0, 48.2, 52.0, 55.4)
    val safeLabels = if (labels.size == safeValues.size) labels else safeValues.indices.map { "D${it + 1}" }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        ) {
            val w = size.width
            val h = size.height
            val padTop = 18.dp.toPx()
            val padBottom = 16.dp.toPx()
            val padHoriz = 12.dp.toPx()
            val usableW = (w - padHoriz * 2).coerceAtLeast(1f)
            val usableH = (h - padTop - padBottom).coerceAtLeast(1f)

            val minVal = (safeValues.minOrNull() ?: 0.0) * 0.88
            val maxVal = ((safeValues.maxOrNull() ?: 100.0) * 1.08).coerceAtLeast(minVal + 1.0)

            // Subtle horizontal grid lines
            for (i in 0..3) {
                val y = padTop + usableH * (i / 3f)
                drawLine(
                    color = GlassBorder.copy(alpha = 0.35f),
                    start = Offset(padHoriz, y),
                    end = Offset(w - padHoriz, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val points = safeValues.mapIndexed { index, v ->
                val x = padHoriz + (index.toFloat() / (safeValues.size - 1)) * usableW
                val normY = ((v - minVal) / (maxVal - minVal)).toFloat().coerceIn(0f, 1f)
                val y = padTop + usableH * (1f - normY)
                Offset(x, y)
            }

            val curvePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.lastIndex) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(curvePath)
                lineTo(points.last().x, h - padBottom)
                lineTo(points.first().x, h - padBottom)
                close()
            }

            val clipRight = padHoriz + usableW * drawProgress.value
            // Gradient area fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MoltenGold.copy(alpha = 0.36f * drawProgress.value),
                        TelemetryCyan.copy(alpha = 0.12f * drawProgress.value),
                        Color.Transparent
                    ),
                    startY = padTop,
                    endY = h - padBottom
                )
            )

            // Glowing outer spline aura
            drawPath(
                path = curvePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(TelemetryCyan.copy(alpha = 0.35f), MoltenGold.copy(alpha = 0.45f))
                ),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )

            // Sharp luminous primary curve
            drawPath(
                path = curvePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(TelemetryCyan, MoltenGold, MoltenGoldGlow)
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Glowing node markers
            points.forEach { pt ->
                if (pt.x <= clipRight + 6f) {
                    drawCircle(
                        color = MoltenGold.copy(alpha = 0.35f),
                        radius = 7.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = ObsidianVoid,
                        radius = 4.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = MoltenGoldGlow,
                        radius = 2.5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            safeLabels.forEachIndexed { i, lbl ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = lbl,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f", safeValues[i]),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Animated radial equipment health ring for Predictive Mill Maintenance cards.
 */
@Composable
fun AnimatedHealthRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.coerceIn(0, 100).toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 140f),
        label = "health_ring_score"
    )
    val ringColor = when {
        score >= 82 -> EmeraldOptimal
        score >= 65 -> AmberWarning
        else -> CrimsonCritical
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 5.5.dp.toPx()
            drawArc(
                color = TitaniumElevated,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * (animatedScore / 100f),
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${animatedScore.toInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = ringColor
        )
    }
}

@Composable
fun GlassTelemetryCard(
    modifier: Modifier = Modifier,
    accentColor: Color = GlassBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TitaniumSurface.copy(alpha = 0.92f),
                        ObsidianSurface.copy(alpha = 0.96f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.55f),
                        GlassBorder.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        content()
    }
}

@Composable
fun LiveSyncPulseChip(
    label: String,
    isLive: Boolean,
    accentColor: Color = if (isLive) EmeraldOptimal else TelemetryCyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "pulse_sync")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .liquidSpringClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = pulseAlpha))
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor,
            maxLines = 1
        )
    }
}

@Composable
fun AnimatedMetricText(
    targetValue: Double,
    suffix: String,
    decimals: Int = 1,
    color: Color = TextPrimary,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 180f),
        label = "metric_counter"
    )
    val pattern = "%.${decimals}f"
    Text(
        text = "${String.format(Locale.US, pattern, animated)}$suffix",
        style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold
        ),
        color = color,
        modifier = modifier
    )
}
