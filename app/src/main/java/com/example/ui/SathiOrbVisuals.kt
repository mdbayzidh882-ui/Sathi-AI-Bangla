package com.example.ui

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RadialGradient as AndroidRadialGradient
import android.graphics.RectF as AndroidRectF
import android.graphics.Shader as AndroidShader
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
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.core.SathiAnimationStyle
import com.example.core.SathiOrbStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class OrbPreviewState(val labelEn: String, val labelBn: String) {
    IDLE("Idle", "শান্ত"),
    LISTENING("Listening", "শুনছে"),
    THINKING("Thinking", "ভাবছে"),
    SPEAKING("Speaking", "বলছে")
}

@Composable
fun rememberSathiInteractiveScale(
    pressed: Boolean,
    animationStyle: SathiAnimationStyle,
    reduceMotion: Boolean
): Float {
    val target = when {
        reduceMotion || animationStyle == SathiAnimationStyle.OFF -> 1f
        pressed && animationStyle == SathiAnimationStyle.ZOOM -> 1.045f
        pressed -> 0.96f
        else -> 1f
    }
    val spec = when {
        reduceMotion || animationStyle == SathiAnimationStyle.OFF -> tween<Float>(0)
        animationStyle == SathiAnimationStyle.BOUNCY -> spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
        animationStyle == SathiAnimationStyle.ZOOM -> tween(180, easing = FastOutSlowInEasing)
        else -> tween(220, easing = FastOutSlowInEasing)
    }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = spec,
        label = "sathi_interactive_scale"
    )
    return scale
}

@Composable
fun SathiStyledOrbCanvas(
    style: SathiOrbStyle,
    orbState: OrbPreviewState,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    phaseOffset: Float = 0f
) {
    val speedMs = when {
        reduceMotion -> 7200
        orbState == OrbPreviewState.SPEAKING -> 1800
        orbState == OrbPreviewState.THINKING -> 2200
        orbState == OrbPreviewState.LISTENING -> 2600
        else -> 4400
    }
    val infiniteTransition = rememberInfiniteTransition(label = "sathi_orb_${style.name}")
    val rawPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speedMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )
    val breath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reduceMotion) 3200 else if (orbState == OrbPreviewState.IDLE) 1900 else 760,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breath"
    )

    val phase = rawPhase + phaseOffset
    val energy = when (orbState) {
        OrbPreviewState.IDLE -> if (reduceMotion) 0.25f else 0.45f
        OrbPreviewState.LISTENING -> 0.85f
        OrbPreviewState.THINKING -> 0.78f
        OrbPreviewState.SPEAKING -> 1.0f
    }

    Canvas(modifier = modifier) {
        drawComposeStyledOrb(
            style = style,
            orbState = orbState,
            phase = phase,
            breath = if (reduceMotion) 0.5f else breath,
            energy = energy
        )
    }
}

private fun DrawScope.drawComposeStyledOrb(
    style: SathiOrbStyle,
    orbState: OrbPreviewState,
    phase: Float,
    breath: Float,
    energy: Float
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val center = Offset(cx, cy)
    val maxR = min(size.width, size.height) / 2f
    val primary = Color(style.primaryColorHex)
    val accent = Color(style.accentColorHex)
    val stateAccent = when (orbState) {
        OrbPreviewState.LISTENING -> Color(0xFFFF3D7F)
        OrbPreviewState.SPEAKING -> Color(0xFF00E5FF)
        OrbPreviewState.THINKING -> Color(0xFFFFD54F)
        OrbPreviewState.IDLE -> primary
    }

    // Soft ambient outer halo
    val haloScale = 0.88f + breath * 0.11f * energy
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primary.copy(alpha = 0.34f * energy),
                stateAccent.copy(alpha = 0.14f * energy),
                Color.Transparent
            ),
            center = center,
            radius = maxR * haloScale
        ),
        radius = maxR * haloScale,
        center = center
    )

    val coreR = maxR * (0.62f + breath * 0.04f * energy)

    when (style) {
        SathiOrbStyle.ORBIT_BLUE -> {
            drawSphereCore(center, coreR, Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF071A36))
            for (i in 0 until 6) {
                val a = phase + i * (PI.toFloat() * 2f / 6f)
                val rx = coreR * 1.24f
                val ry = coreR * 0.44f
                val dx = cos(a) * rx
                val dy = sin(a) * ry
                val rot = 0.45f
                val px = cx + dx * cos(rot) - dy * sin(rot)
                val py = cy + dx * sin(rot) + dy * cos(rot)
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFF7DD3FC) else Color(0xFF38BDF8),
                    radius = maxR * (0.045f + 0.015f * sin(a)),
                    center = Offset(px, py)
                )
            }
        }
        SathiOrbStyle.NEBULA -> {
            drawSphereCore(center, coreR, Color(0xFF4C1D95), Color(0xFF2E1065), Color(0xFF0F0724))
            for (i in 0 until 26) {
                val angle = phase * (if (i % 2 == 0) 1f else -0.7f) + i * 0.63f
                val dist = coreR * (0.18f + ((i * 37) % 100) / 135f)
                val p = Offset(cx + cos(angle) * dist, cy + sin(angle * 1.1f) * dist)
                drawCircle(
                    color = if (i % 3 == 0) Color(0xFFE879F9) else Color(0xFFA78BFA),
                    radius = maxR * (0.024f + (i % 3) * 0.01f),
                    center = p
                )
            }
        }
        SathiOrbStyle.PLASMA_RING -> {
            drawSphereCore(center, coreR * 0.88f, Color(0xFFEC4899), Color(0xFF7E22CE), Color(0xFF140826))
            for (ring in 0 until 3) {
                rotate(degrees = (phase * 57.3f) + ring * 60f, pivot = center) {
                    drawOval(
                        color = if (ring % 2 == 0) Color(0xFF22D3EE) else Color(0xFFF472B6),
                        topLeft = Offset(cx - coreR * 1.18f, cy - coreR * 0.46f),
                        size = Size(coreR * 2.36f, coreR * 0.92f),
                        style = Stroke(width = maxR * 0.045f)
                    )
                }
            }
        }
        SathiOrbStyle.PARTICLE_GLOBE -> {
            drawCircle(color = Color(0xFF08203E), radius = coreR, center = center)
            for (lat in -3..3) {
                val yNorm = lat / 3.6f
                val ringR = coreR * kotlin.math.sqrt((1f - yNorm * yNorm).coerceAtLeast(0f))
                val py = cy + yNorm * coreR * 0.88f
                val count = 8 - abs(lat)
                for (j in 0 until count) {
                    val lon = phase + j * (2f * PI.toFloat() / count) + lat * 0.4f
                    val px = cx + cos(lon) * ringR
                    val depthAlpha = (0.35f + 0.65f * ((sin(lon) + 1f) / 2f)).coerceIn(0.25f, 1f)
                    drawCircle(
                        color = Color(0xFFBAE6FD).copy(alpha = depthAlpha),
                        radius = maxR * (0.025f + 0.015f * depthAlpha),
                        center = Offset(px, py)
                    )
                }
            }
        }
        SathiOrbStyle.ECLIPSE -> {
            val flareR = coreR * (1.16f + breath * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color.Transparent),
                    center = center,
                    radius = flareR
                ),
                radius = flareR,
                center = center
            )
            drawCircle(color = Color(0xFF05060A), radius = coreR * 0.84f, center = center)
            drawCircle(
                color = Color(0xFFFCD34D).copy(alpha = 0.85f),
                radius = coreR * 0.86f,
                center = center,
                style = Stroke(width = maxR * 0.032f)
            )
        }
        SathiOrbStyle.RINGED_PLANET -> {
            drawSphereCore(center, coreR * 0.86f, Color(0xFFFDE68A), Color(0xFFD97706), Color(0xFF451A03))
            rotate(degrees = -22f + sin(phase) * 5f, pivot = center) {
                drawOval(
                    color = Color(0xFF7DD3FC).copy(alpha = 0.85f),
                    topLeft = Offset(cx - coreR * 1.32f, cy - coreR * 0.30f),
                    size = Size(coreR * 2.64f, coreR * 0.60f),
                    style = Stroke(width = maxR * 0.055f)
                )
            }
        }
        SathiOrbStyle.WIREFRAME -> {
            drawCircle(color = Color(0xFF041C2C), radius = coreR, center = center)
            val stroke = Stroke(width = maxR * 0.025f)
            drawCircle(color = Color(0xFF22D3EE), radius = coreR, center = center, style = stroke)
            for (k in 1..3) {
                val frac = k / 4f
                drawOval(
                    color = Color(0xFF22D3EE).copy(alpha = 0.65f),
                    topLeft = Offset(cx - coreR * abs(cos(phase + frac * 1.5f)).coerceAtLeast(0.18f), cy - coreR),
                    size = Size(coreR * 2f * abs(cos(phase + frac * 1.5f)).coerceAtLeast(0.18f), coreR * 2f),
                    style = stroke
                )
                drawOval(
                    color = Color(0xFF67E8F9).copy(alpha = 0.55f),
                    topLeft = Offset(cx - coreR, cy - coreR * frac),
                    size = Size(coreR * 2f, coreR * 2f * frac),
                    style = stroke
                )
            }
        }
        SathiOrbStyle.NORTHERN_GLOW -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF10B981).copy(alpha = 0.9f),
                        Color(0xFF7C3AED).copy(alpha = 0.78f),
                        Color(0xFF081426)
                    ),
                    center = Offset(cx + cos(phase) * coreR * 0.25f, cy + sin(phase) * coreR * 0.25f),
                    radius = coreR * 1.15f
                ),
                radius = coreR,
                center = center
            )
            drawGlassRim(center, coreR, Color(0xFF34D399), maxR * 0.03f)
        }
        SathiOrbStyle.MAGMA -> {
            drawSphereCore(center, coreR, Color(0xFFF97316), Color(0xFFB91C1C), Color(0xFF1F0905))
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = center, radius = coreR)) }
            clipPath(clip) {
                for (i in 0 until 4) {
                    val p = Path()
                    val yOff = cy + (i - 1.5f) * coreR * 0.42f
                    p.moveTo(cx - coreR, yOff)
                    p.cubicTo(
                        cx - coreR * 0.3f, yOff + sin(phase + i) * coreR * 0.28f,
                        cx + coreR * 0.3f, yOff - cos(phase + i) * coreR * 0.28f,
                        cx + coreR, yOff
                    )
                    drawPath(p, color = Color(0xFFFDE047), style = Stroke(width = maxR * 0.032f, cap = StrokeCap.Round))
                }
            }
        }
        SathiOrbStyle.HALO -> {
            drawCircle(color = Color(0xFF061021), radius = coreR * 0.92f, center = center)
            drawCircle(
                brush = Brush.sweepGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF22D3EE), Color(0xFF38BDF8))),
                radius = coreR * (0.98f + breath * 0.09f),
                center = center,
                style = Stroke(width = maxR * 0.075f)
            )
        }
        SathiOrbStyle.STORM_CORE -> {
            drawSphereCore(center, coreR, Color(0xFF1E3A8A), Color(0xFF0F172A), Color(0xFF020617))
            val bolt = Path().apply {
                moveTo(cx - coreR * 0.15f, cy - coreR * 0.65f)
                lineTo(cx + coreR * 0.16f + sin(phase * 3f) * coreR * 0.08f, cy - coreR * 0.08f)
                lineTo(cx - coreR * 0.08f, cy + coreR * 0.05f)
                lineTo(cx + coreR * 0.22f, cy + coreR * 0.62f)
            }
            drawPath(bolt, color = Color(0xFFE0F2FE), style = Stroke(width = maxR * 0.042f, cap = StrokeCap.Round))
            drawGlassRim(center, coreR, Color(0xFF60A5FA), maxR * 0.03f)
        }
        SathiOrbStyle.VOICE_RING -> {
            drawCircle(color = Color(0xFF071529), radius = coreR * 0.72f, center = center)
            val bars = 24
            for (i in 0 until bars) {
                val angle = i * (2f * PI.toFloat() / bars)
                val amp = 0.12f + 0.22f * energy * abs(sin(phase * 2.5f + i * 0.7f))
                val rIn = coreR * 0.76f
                val rOut = coreR * (0.82f + amp)
                drawLine(
                    color = if (i % 2 == 0) Color(0xFF2DD4BF) else Color(0xFF38BDF8),
                    start = Offset(cx + cos(angle) * rIn, cy + sin(angle) * rIn),
                    end = Offset(cx + cos(angle) * rOut, cy + sin(angle) * rOut),
                    strokeWidth = maxR * 0.034f,
                    cap = StrokeCap.Round
                )
            }
        }
        SathiOrbStyle.GLOW_FLIES -> {
            drawSphereCore(center, coreR, Color(0xFF0F2922), Color(0xFF071A19), Color(0xFF040D0E))
            for (i in 0 until 12) {
                val a = phase * 0.8f + i * 1.1f
                val rad = coreR * (0.2f + ((i * 29) % 70) / 100f)
                val alpha = (0.3f + 0.7f * abs(sin(phase * 1.5f + i))).coerceIn(0.2f, 1f)
                drawCircle(
                    color = if (i % 2 == 0) Color(0xFFBEF264).copy(alpha = alpha) else Color(0xFF22D3EE).copy(alpha = alpha),
                    radius = maxR * (0.03f + 0.015f * alpha),
                    center = Offset(cx + cos(a) * rad, cy + sin(a * 1.3f) * rad)
                )
            }
        }
        SathiOrbStyle.SPIRAL -> {
            drawCircle(color = Color(0xFF071429), radius = coreR, center = center)
            for (arm in 0 until 4) {
                val armPath = Path()
                val baseA = phase + arm * (PI.toFloat() / 2f)
                for (step in 0..14) {
                    val t = step / 14f
                    val a = baseA + t * 2.3f
                    val r = t * coreR * 0.88f
                    val pt = Offset(cx + cos(a) * r, cy + sin(a) * r)
                    if (step == 0) armPath.moveTo(pt.x, pt.y) else armPath.lineTo(pt.x, pt.y)
                }
                drawPath(
                    armPath,
                    color = if (arm % 2 == 0) Color(0xFF2DD4BF) else Color(0xFF818CF8),
                    style = Stroke(width = maxR * 0.04f, cap = StrokeCap.Round)
                )
            }
        }
        SathiOrbStyle.MERCURY -> {
            val wobbleR = coreR * (0.94f + 0.06f * sin(phase * 2f))
            drawSphereCore(
                Offset(cx - wobbleR * 0.22f, cy - wobbleR * 0.22f),
                wobbleR,
                Color(0xFFFFFFFF),
                Color(0xFF94A3B8),
                Color(0xFF334155),
                orbCenter = center
            )
        }
        SathiOrbStyle.SUGAR_SWIRL -> {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF9A8D4), Color(0xFF99F6E4), Color(0xFFC4B5FD)),
                    start = Offset(cx - coreR * cos(phase), cy - coreR * sin(phase)),
                    end = Offset(cx + coreR * cos(phase), cy + coreR * sin(phase))
                ),
                radius = coreR,
                center = center
            )
            drawGlassRim(center, coreR, Color.White.copy(alpha = 0.65f), maxR * 0.03f)
        }
        SathiOrbStyle.CODE_RAIN -> {
            drawCircle(color = Color(0xFF04140B), radius = coreR, center = center)
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = center, radius = coreR)) }
            clipPath(clip) {
                for (col in -3..3) {
                    val x = cx + col * coreR * 0.25f
                    val dropOffset = ((phase / (2f * PI.toFloat()) + (col + 3) * 0.17f) % 1f)
                    val yStart = cy - coreR + dropOffset * coreR * 2f
                    drawLine(
                        color = Color(0xFF22C55E),
                        start = Offset(x, yStart - coreR * 0.35f),
                        end = Offset(x, yStart),
                        strokeWidth = maxR * 0.032f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = Color(0xFFBBF7D0), radius = maxR * 0.022f, center = Offset(x, yStart))
                }
            }
            drawGlassRim(center, coreR, Color(0xFF22C55E), maxR * 0.03f)
        }
        SathiOrbStyle.WATCHER -> {
            drawSphereCore(center, coreR, Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
            val gazeX = cx + cos(phase) * coreR * 0.14f * energy
            val gazeY = cy + sin(phase * 0.5f) * coreR * 0.08f
            drawCircle(color = Color(0xFF38BDF8), radius = coreR * 0.44f, center = Offset(gazeX, gazeY))
            drawCircle(color = Color(0xFF020617), radius = coreR * 0.22f, center = Offset(gazeX, gazeY))
            drawCircle(color = Color.White, radius = coreR * 0.08f, center = Offset(gazeX - coreR * 0.1f, gazeY - coreR * 0.1f))
        }
        SathiOrbStyle.LUNAR -> {
            drawSphereCore(center, coreR, Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF334155))
            drawCircle(color = Color(0xFF64748B).copy(alpha = 0.45f), radius = coreR * 0.16f, center = Offset(cx - coreR * 0.28f, cy - coreR * 0.18f))
            drawCircle(color = Color(0xFF64748B).copy(alpha = 0.35f), radius = coreR * 0.11f, center = Offset(cx + coreR * 0.25f, cy + coreR * 0.22f))
            drawCircle(color = Color(0xFF64748B).copy(alpha = 0.30f), radius = coreR * 0.09f, center = Offset(cx + coreR * 0.10f, cy - coreR * 0.32f))
        }
        SathiOrbStyle.SOAP_BUBBLE -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x4467E8F9), Color(0x99F472B6)),
                    center = center,
                    radius = coreR
                ),
                radius = coreR * (0.96f + 0.04f * sin(phase * 2f)),
                center = center
            )
            drawCircle(
                brush = Brush.sweepGradient(listOf(Color(0xFF67E8F9), Color(0xFFF472B6), Color(0xFFFDE047), Color(0xFF67E8F9))),
                radius = coreR,
                center = center,
                style = Stroke(width = maxR * 0.042f)
            )
        }
        SathiOrbStyle.CANDLELIGHT -> {
            drawCircle(color = Color(0xFF1A1108), radius = coreR, center = center)
            val flameR = coreR * (0.55f + breath * 0.15f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFBBF24), Color(0xFFF97316), Color.Transparent),
                    center = Offset(cx, cy - coreR * 0.06f),
                    radius = flameR
                ),
                radius = flameR,
                center = center
            )
        }
        SathiOrbStyle.AMBER_PULSE -> {
            drawSphereCore(center, coreR * 0.78f, Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFF78350F))
            for (r in 0..1) {
                val prog = ((phase / (2f * PI.toFloat()) + r * 0.5f) % 1f)
                drawCircle(
                    color = Color(0xFFFBBF24).copy(alpha = (1f - prog) * 0.75f),
                    radius = coreR * (0.78f + prog * 0.45f),
                    center = center,
                    style = Stroke(width = maxR * 0.032f)
                )
            }
        }
        SathiOrbStyle.FROST -> {
            drawSphereCore(center, coreR, Color(0xFFF0F9FF), Color(0xFF7DD3FC), Color(0xFF0369A1))
            for (i in 0 until 6) {
                val a = phase * 0.4f + i * (PI.toFloat() / 3f)
                drawLine(
                    color = Color.White.copy(alpha = 0.65f),
                    start = Offset(cx - cos(a) * coreR * 0.55f, cy - sin(a) * coreR * 0.55f),
                    end = Offset(cx + cos(a) * coreR * 0.55f, cy + sin(a) * coreR * 0.55f),
                    strokeWidth = maxR * 0.022f
                )
            }
        }
        SathiOrbStyle.TARGET_LOCK -> {
            drawCircle(color = Color(0xFF061629), radius = coreR * 0.85f, center = center)
            drawCircle(color = Color(0xFFFACC15), radius = coreR * 0.28f, center = center)
            rotate(degrees = phase * 57.3f, pivot = center) {
                drawArc(
                    color = Color(0xFF38BDF8),
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(cx - coreR * 0.92f, cy - coreR * 0.92f),
                    size = Size(coreR * 1.84f, coreR * 1.84f),
                    style = Stroke(width = maxR * 0.045f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF38BDF8),
                    startAngle = 180f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(cx - coreR * 0.92f, cy - coreR * 0.92f),
                    size = Size(coreR * 1.84f, coreR * 1.84f),
                    style = Stroke(width = maxR * 0.045f, cap = StrokeCap.Round)
                )
            }
        }
        SathiOrbStyle.DEEP_SEA -> {
            drawSphereCore(center, coreR, Color(0xFF38BDF8), Color(0xFF0369A1), Color(0xFF04142B))
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = center, radius = coreR)) }
            clipPath(clip) {
                val wave = Path().apply {
                    moveTo(cx - coreR, cy + coreR * 0.1f)
                    for (step in 0..16) {
                        val t = step / 16f
                        val wx = cx - coreR + t * coreR * 2f
                        val wy = cy + sin(phase * 2f + t * 5f) * coreR * 0.14f
                        lineTo(wx, wy)
                    }
                    lineTo(cx + coreR, cy + coreR)
                    lineTo(cx - coreR, cy + coreR)
                    close()
                }
                drawPath(wave, color = Color(0x552DD4BF))
            }
        }
        SathiOrbStyle.SUNSET_GLASS -> {
            drawSphereCore(center, coreR, Color(0xFFFDBA74), Color(0xFFEA580C), Color(0xFF7C2D12))
            drawGlassRim(center, coreR, Color(0xFFFB7185), maxR * 0.04f)
        }
        SathiOrbStyle.NEON_PINK -> {
            drawSphereCore(center, coreR, Color(0xFFF472B6), Color(0xFFDB2777), Color(0xFF581C87))
            drawCircle(
                color = Color(0xFFF472B6).copy(alpha = 0.5f + 0.4f * breath),
                radius = coreR * (1.06f + 0.06f * breath),
                center = center,
                style = Stroke(width = maxR * 0.045f)
            )
        }
    }
}

private fun DrawScope.drawSphereCore(
    highlightOffset: Offset,
    radius: Float,
    topColor: Color,
    midColor: Color,
    edgeColor: Color,
    orbCenter: Offset = highlightOffset
) {
    val lightPos = if (orbCenter == highlightOffset) {
        Offset(orbCenter.x - radius * 0.28f, orbCenter.y - radius * 0.28f)
    } else {
        highlightOffset
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(topColor, midColor, edgeColor),
            center = lightPos,
            radius = radius * 1.45f
        ),
        radius = radius,
        center = orbCenter
    )
}

private fun DrawScope.drawGlassRim(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float
) {
    drawCircle(
        color = color.copy(alpha = 0.72f),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Native Android Canvas renderer used by SathiFloatingService overlay bubble so the floating
 * orb reflects the user's chosen Orb Style across all apps.
 */
fun drawSathiOrbOnAndroidCanvas(
    canvas: AndroidCanvas,
    cx: Float,
    cy: Float,
    maxR: Float,
    phase: Float,
    style: SathiOrbStyle,
    isListening: Boolean,
    isSpeaking: Boolean,
    reduceMotion: Boolean
) {
    val effectivePhase = if (reduceMotion) phase * 0.25f else phase
    val pulse = if (reduceMotion) 0.3f else 0.5f + 0.5f * sin(effectivePhase)
    val coreR = maxR * 0.68f

    val stylePrimary = (style.primaryColorHex.toInt() or 0xFF000000.toInt())
    val styleAccent = (style.accentColorHex.toInt() or 0xFF000000.toInt())
    val ringStart = when {
        isSpeaking -> AndroidColor.parseColor("#00E5FF")
        isListening -> AndroidColor.parseColor("#FF4081")
        else -> stylePrimary
    }
    val ringEnd = when {
        isSpeaking -> AndroidColor.parseColor("#1DE9B6")
        isListening -> AndroidColor.parseColor("#FF9100")
        else -> styleAccent
    }

    val outerRingPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.style = AndroidPaint.Style.STROKE
        strokeWidth = maxR * 0.055f
        color = ringStart
        alpha = if (isListening || isSpeaking) (105 + (pulse * 125).toInt()) else (65 + (pulse * 75).toInt())
    }
    canvas.drawCircle(cx, cy, coreR + maxR * (0.14f + pulse * 0.12f), outerRingPaint)

    if (isSpeaking || isListening) {
        val sec = (effectivePhase / (2f * PI.toFloat()) + 0.5f) % 1f
        outerRingPaint.strokeWidth = maxR * 0.035f
        outerRingPaint.color = ringEnd
        outerRingPaint.alpha = ((1f - sec) * 165f).toInt().coerceIn(20, 175)
        canvas.drawCircle(cx, cy, coreR + maxR * (0.06f + sec * 0.24f), outerRingPaint)
    }

    val corePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.style = AndroidPaint.Style.FILL
        shader = AndroidRadialGradient(
            cx - coreR * 0.25f,
            cy - coreR * 0.25f,
            coreR * 1.4f,
            intArrayOf(stylePrimary, AndroidColor.parseColor("#CC0B101E"), AndroidColor.parseColor("#EE040812")),
            floatArrayOf(0f, 0.58f, 1f),
            AndroidShader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, coreR, corePaint)

    // Style-specific inner accent (orbiting dots / ring / reticle)
    val accentDotPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.style = AndroidPaint.Style.FILL
        color = styleAccent
    }
    if (style == SathiOrbStyle.ORBIT_BLUE || style == SathiOrbStyle.PARTICLE_GLOBE || style == SathiOrbStyle.NEBULA) {
        for (i in 0 until 4) {
            val a = effectivePhase + i * (PI.toFloat() / 2f)
            val px = cx + cos(a) * coreR * 0.92f
            val py = cy + sin(a) * coreR * 0.42f
            canvas.drawCircle(px, py, maxR * 0.045f, accentDotPaint)
        }
    } else if (style == SathiOrbStyle.RINGED_PLANET || style == SathiOrbStyle.PLASMA_RING || style == SathiOrbStyle.TARGET_LOCK) {
        val ringP = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.style = AndroidPaint.Style.STROKE
            strokeWidth = maxR * 0.04f
            color = styleAccent
            alpha = 200
        }
        canvas.save()
        canvas.rotate(-22f, cx, cy)
        canvas.drawOval(
            AndroidRectF(cx - coreR * 1.15f, cy - coreR * 0.34f, cx + coreR * 1.15f, cy + coreR * 0.34f),
            ringP
        )
        canvas.restore()
    }

    val borderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.style = AndroidPaint.Style.STROKE
        strokeWidth = maxR * 0.06f
        shader = AndroidLinearGradient(
            cx - coreR,
            cy - coreR,
            cx + coreR,
            cy + coreR,
            ringStart,
            ringEnd,
            AndroidShader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, coreR, borderPaint)

    // Voice wave arc above the label
    val wavePath = AndroidPath()
    val waveWidth = coreR * 1.2f
    val startX = cx - waveWidth / 2f
    val baseY = cy - coreR * 0.18f
    val amp = coreR * when {
        isSpeaking -> 0.28f
        isListening -> 0.23f
        else -> 0.12f
    }
    val steps = 22
    for (i in 0..steps) {
        val frac = i / steps.toFloat()
        val x = startX + frac * waveWidth
        val envelope = sin(frac * PI).toFloat()
        val harmonic = sin(effectivePhase * 3.0f + frac * 11f) + 0.45f * sin(effectivePhase * 5.0f - frac * 17f)
        val y = baseY + harmonic * amp * 0.72f * envelope
        if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
    }
    val wavePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeWidth = maxR * 0.06f
        shader = AndroidLinearGradient(
            startX,
            baseY,
            startX + waveWidth,
            baseY,
            ringStart,
            ringEnd,
            AndroidShader.TileMode.CLAMP
        )
    }
    canvas.drawPath(wavePath, wavePaint)

    val labelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = AndroidPaint.Align.CENTER
        isFakeBoldText = true
        textSize = coreR * 0.34f
    }
    canvas.drawText("Sathi", cx, cy + coreR * 0.38f, labelPaint)
}
