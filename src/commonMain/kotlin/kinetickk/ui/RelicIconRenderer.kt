package kinetickk.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kinetickk.model.RelicAspect
import kinetickk.model.RelicCatalog
import kinetickk.model.RelicId

private val RelicInk = Color(0xFFF4F6FF)
private val RelicBackground = Color(0xFF050610)
private val RelicCyan = Color(0xFF42F5E9)
private val RelicViolet = Color(0xFFA96CFF)
private val RelicBlue = Color(0xFF73A6FF)
private val RelicMagenta = Color(0xFFFF4DC4)
private val RelicAcid = Color(0xFFB6FF5B)
private val RelicOrange = Color(0xFFFF714B)
private val RelicGold = Color(0xFFFFD45B)
private const val RELIC_TAU = 6.2831855f

internal fun relicAspectColor(aspect: RelicAspect): Color = when (aspect) {
    RelicAspect.VECTOR -> RelicCyan
    RelicAspect.GRAVITIC -> RelicViolet
    RelicAspect.ION -> RelicBlue
    RelicAspect.RIFT -> RelicMagenta
    RelicAspect.PRISM -> RelicAcid
    RelicAspect.ENTROPY -> RelicOrange
    RelicAspect.SOVEREIGN -> RelicGold
}

/**
 * A compact procedural mark shared by world pickups, the four-slot matrix, and
 * choice cards. Aspect owns the frame/color; every relic owns a different rune.
 */
internal fun DrawScope.drawRelicIcon(
    id: RelicId,
    center: Offset,
    radius: Float,
    rank: Int? = null,
    time: Float = 0f,
    alpha: Float = 1f,
) {
    if (radius <= 0f || alpha <= 0f) return
    val definition = RelicCatalog.byId(id)
    val accent = relicAspectColor(definition.aspect).copy(alpha = alpha.coerceIn(0f, 1f))
    val stroke = (radius * 0.085f).coerceAtLeast(0.65f)
    val sides = when (definition.aspect) {
        RelicAspect.VECTOR -> 3
        RelicAspect.GRAVITIC -> 6
        RelicAspect.ION -> 8
        RelicAspect.RIFT -> 4
        RelicAspect.PRISM -> 5
        RelicAspect.ENTROPY -> 7
        RelicAspect.SOVEREIGN -> 10
    }
    val rotation = -PI.toFloat() * 0.5f + if (definition.isSovereign) time * 0.08f else 0f

    drawCircle(accent.copy(alpha = accent.alpha * 0.09f), radius * 1.22f, center)
    drawRelicPolygon(center, radius, sides, rotation, RelicBackground.copy(alpha = 0.9f), Fill)
    drawRelicPolygon(center, radius, sides, rotation, accent.copy(alpha = accent.alpha * 0.82f), Stroke(stroke))
    drawCircle(accent.copy(alpha = accent.alpha * 0.18f), radius * 0.78f, center, style = Stroke(stroke * 0.72f))

    drawRelicRune(id, center, radius * 0.62f, accent, time)

    val visibleRank = rank?.coerceIn(1, RelicCatalog.MAX_RANK)
    if (visibleRank != null) {
        repeat(RelicCatalog.MAX_RANK) { index ->
            val angle = PI.toFloat() * 0.72f + index * PI.toFloat() * 0.14f
            drawCircle(
                color = if (index < visibleRank) RelicInk.copy(alpha = alpha) else accent.copy(alpha = alpha * 0.22f),
                radius = (radius * 0.052f).coerceAtLeast(0.7f),
                center = relicPolar(center, radius * 1.08f, angle),
            )
        }
    }
}

/** Generic signal used before an elite pickup resolves into a catalog relic. */
internal fun DrawScope.drawUnresolvedRelicIcon(center: Offset, radius: Float, time: Float) {
    val accent = RelicGold
    val stroke = (radius * 0.085f).coerceAtLeast(0.7f)
    drawCircle(accent.copy(alpha = 0.11f), radius * 1.35f, center)
    drawRelicPolygon(center, radius, 6, time * 0.35f, RelicBackground.copy(alpha = 0.88f), Fill)
    drawRelicPolygon(center, radius, 6, time * 0.35f, accent, Stroke(stroke))
    drawRelicPolygon(center, radius * 0.58f, 4, -time * 0.65f, RelicInk, Stroke(stroke))
    drawCircle(RelicMagenta, radius * 0.13f, center)
}

private fun DrawScope.drawRelicRune(id: RelicId, center: Offset, radius: Float, color: Color, time: Float) {
    val stroke = (radius * 0.14f).coerceAtLeast(0.7f)
    val thin = (radius * 0.085f).coerceAtLeast(0.55f)
    when (id) {
        RelicId.KINETIC_FLYWHEEL -> {
            drawCircle(color, radius * 0.72f, center, style = Stroke(stroke))
            repeat(6) { index -> drawLine(color, center, relicPolar(center, radius * 0.72f, index * RELIC_TAU / 6f), thin) }
            drawCircle(RelicInk, radius * 0.16f, center)
        }
        RelicId.GHOST_VECTOR -> {
            drawRelicChevron(Offset(center.x - radius * 0.26f, center.y), radius * 0.72f, color.copy(alpha = color.alpha * 0.48f), stroke)
            drawRelicChevron(Offset(center.x + radius * 0.24f, center.y), radius * 0.72f, RelicInk, stroke)
        }
        RelicId.OVERTAKE_PROTOCOL -> {
            drawLine(color, Offset(center.x - radius, center.y + radius * 0.33f), Offset(center.x + radius * 0.55f, center.y + radius * 0.33f), stroke, StrokeCap.Round)
            drawLine(RelicInk, Offset(center.x - radius * 0.62f, center.y - radius * 0.38f), Offset(center.x + radius * 0.88f, center.y - radius * 0.38f), stroke, StrokeCap.Round)
            drawRelicPolygon(Offset(center.x + radius * 0.72f, center.y - radius * 0.38f), radius * 0.25f, 3, 0f, RelicInk, Fill)
        }
        RelicId.SLIPSTREAM_RELAY -> {
            repeat(3) { index ->
                val y = center.y + (index - 1) * radius * 0.42f
                drawLine(if (index == 1) RelicInk else color, Offset(center.x - radius * (0.94f - index * 0.12f), y), Offset(center.x + radius * (0.72f - index * 0.08f), y), thin, StrokeCap.Round)
            }
            drawCircle(color, radius * 0.18f, Offset(center.x + radius * 0.68f, center.y))
        }
        RelicId.BRAKEPOINT_MEMORY -> {
            drawLine(color, Offset(center.x - radius * 0.62f, center.y - radius * 0.68f), Offset(center.x - radius * 0.62f, center.y + radius * 0.68f), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x - radius * 0.18f, center.y - radius * 0.68f), Offset(center.x - radius * 0.18f, center.y + radius * 0.68f), stroke, StrokeCap.Round)
            repeat(5) { index -> drawLine(RelicInk, Offset(center.x + radius * 0.05f, center.y), relicPolar(Offset(center.x + radius * 0.05f, center.y), radius * 0.78f, -1.0f + index * 0.5f), thin, StrokeCap.Round) }
        }
        RelicId.POLARITY_SLING -> {
            drawArc(color, 205f, 310f, false, Offset(center.x - radius * 0.82f, center.y - radius * 0.68f), Size(radius * 1.64f, radius * 1.36f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawCircle(RelicInk, radius * 0.18f, Offset(center.x + radius * 0.72f, center.y - radius * 0.1f))
        }

        RelicId.ORBITAL_NAIL -> {
            drawOval(color, Offset(center.x - radius, center.y - radius * 0.45f), Size(radius * 2f, radius * 0.9f), style = Stroke(thin))
            drawLine(RelicInk, Offset(center.x, center.y - radius * 0.82f), Offset(center.x, center.y + radius * 0.72f), stroke, StrokeCap.Round)
            drawRelicPolygon(Offset(center.x, center.y + radius * 0.72f), radius * 0.24f, 3, PI.toFloat() / 2f, RelicInk, Fill)
        }
        RelicId.EVENTIDE_ANCHOR -> {
            drawLine(color, Offset(center.x, center.y - radius * 0.88f), Offset(center.x, center.y + radius * 0.56f), stroke, StrokeCap.Round)
            drawCircle(RelicInk, radius * 0.2f, Offset(center.x, center.y - radius * 0.66f), style = Stroke(thin))
            drawArc(color, 10f, 160f, false, Offset(center.x - radius * 0.78f, center.y - radius * 0.26f), Size(radius * 1.56f, radius * 1.18f), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        RelicId.PERIAPSIS_HOOK -> {
            drawArc(color, -80f, 275f, false, Offset(center.x - radius * 0.72f, center.y - radius * 0.72f), Size(radius * 1.44f, radius * 1.44f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawRelicPolygon(Offset(center.x - radius * 0.47f, center.y + radius * 0.55f), radius * 0.24f, 3, 2.5f, RelicInk, Fill)
            drawCircle(RelicInk, radius * 0.14f, center)
        }
        RelicId.CRUSH_DEPTH -> {
            repeat(2) { direction ->
                val sign = if (direction == 0) -1f else 1f
                drawLine(color, Offset(center.x + sign * radius * 0.92f, center.y - radius * 0.62f), Offset(center.x + sign * radius * 0.22f, center.y), stroke, StrokeCap.Round)
                drawLine(color, Offset(center.x + sign * radius * 0.92f, center.y + radius * 0.62f), Offset(center.x + sign * radius * 0.22f, center.y), stroke, StrokeCap.Round)
            }
            drawCircle(RelicInk, radius * 0.18f, center)
        }
        RelicId.MASS_ECHO -> {
            drawCircle(color.copy(alpha = color.alpha * 0.45f), radius * 0.7f, Offset(center.x - radius * 0.22f, center.y), style = Stroke(stroke))
            drawCircle(RelicInk, radius * 0.55f, Offset(center.x + radius * 0.24f, center.y), style = Stroke(thin))
            drawCircle(color, radius * 0.16f, center)
        }
        RelicId.TIDAL_LOCK -> {
            drawOval(color, Offset(center.x - radius, center.y - radius * 0.42f), Size(radius * 2f, radius * 0.84f), style = Stroke(thin))
            drawCircle(RelicInk, radius * 0.2f, Offset(center.x - radius * 0.72f, center.y))
            drawCircle(RelicInk, radius * 0.2f, Offset(center.x + radius * 0.72f, center.y))
            drawLine(color, Offset(center.x, center.y - radius * 0.55f), Offset(center.x, center.y + radius * 0.55f), stroke)
        }

        RelicId.VOLTAIC_FILAMENT -> {
            drawBolt(center, radius, color)
            drawCircle(RelicInk, radius * 0.16f, Offset(center.x + radius * 0.72f, center.y - radius * 0.72f))
        }
        RelicId.STATIC_CHORUS -> {
            repeat(3) { index ->
                val shift = (index - 1) * radius * 0.45f
                drawLine(if (index == 1) RelicInk else color, Offset(center.x - radius * 0.72f + shift, center.y + radius * 0.7f), Offset(center.x + shift, center.y - radius * 0.7f), stroke, StrokeCap.Round)
            }
        }
        RelicId.ION_DEBT -> {
            drawRect(color.copy(alpha = color.alpha * 0.22f), Offset(center.x - radius * 0.72f, center.y - radius * 0.5f), Size(radius * 1.44f, radius))
            drawRect(color, Offset(center.x - radius * 0.72f, center.y - radius * 0.5f), Size(radius * 1.44f, radius), style = Stroke(thin))
            repeat(5) { index -> drawCircle(if (index < 4) RelicInk else color, radius * 0.1f, Offset(center.x - radius * 0.48f + index * radius * 0.24f, center.y)) }
        }
        RelicId.CIRCUIT_BREAKER -> {
            drawLine(color, Offset(center.x - radius * 0.9f, center.y), Offset(center.x - radius * 0.2f, center.y), stroke, StrokeCap.Round)
            drawLine(color, Offset(center.x + radius * 0.2f, center.y), Offset(center.x + radius * 0.9f, center.y), stroke, StrokeCap.Round)
            drawLine(RelicInk, Offset(center.x - radius * 0.2f, center.y), Offset(center.x + radius * 0.42f, center.y - radius * 0.6f), stroke, StrokeCap.Round)
            drawCircle(RelicInk, radius * 0.12f, Offset(center.x - radius * 0.2f, center.y))
            drawCircle(RelicInk, radius * 0.12f, Offset(center.x + radius * 0.2f, center.y))
        }
        RelicId.RETURN_CIRCUIT -> {
            drawArc(color, -40f, 285f, false, Offset(center.x - radius * 0.75f, center.y - radius * 0.75f), Size(radius * 1.5f, radius * 1.5f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawRelicPolygon(Offset(center.x + radius * 0.58f, center.y - radius * 0.45f), radius * 0.23f, 3, -0.5f, RelicInk, Fill)
            drawCircle(RelicInk, radius * 0.18f, center)
        }
        RelicId.STORM_INDEX -> {
            repeat(4) { index ->
                val angle = index * RELIC_TAU / 4f
                drawLine(color, relicPolar(center, radius * 0.38f, angle), relicPolar(center, radius * 0.9f, angle + 0.24f), stroke, StrokeCap.Round)
            }
            drawRelicPolygon(center, radius * 0.25f, 4, PI.toFloat() / 4f, RelicInk, Fill)
        }

        RelicId.ECHO_CHAMBER -> {
            repeat(3) { index -> drawRelicPolygon(center, radius * (0.92f - index * 0.25f), 4, PI.toFloat() / 4f, if (index == 2) RelicInk else color.copy(alpha = color.alpha * (1f - index * 0.2f)), Stroke(if (index == 2) stroke else thin)) }
        }
        RelicId.PALIMPSEST_ROUND -> {
            repeat(3) { index ->
                val shift = (index - 1) * radius * 0.22f
                drawLine(if (index == 2) RelicInk else color.copy(alpha = color.alpha * (0.45f + index * 0.22f)), Offset(center.x - radius * 0.7f + shift, center.y - radius * 0.72f), Offset(center.x + radius * 0.52f + shift, center.y + radius * 0.72f), stroke, StrokeCap.Round)
            }
        }
        RelicId.SECOND_HAND -> {
            drawCircle(color, radius * 0.78f, center, style = Stroke(stroke))
            drawLine(RelicInk, center, Offset(center.x, center.y - radius * 0.62f), stroke, StrokeCap.Round)
            drawLine(color, center, Offset(center.x + radius * 0.56f, center.y + radius * 0.26f), stroke, StrokeCap.Round)
            drawCircle(RelicInk, radius * 0.13f, center)
        }
        RelicId.FRACTURE_GATE -> {
            drawLine(color, Offset(center.x - radius * 0.52f, center.y - radius * 0.82f), Offset(center.x - radius * 0.52f, center.y + radius * 0.82f), stroke)
            drawLine(color, Offset(center.x + radius * 0.52f, center.y - radius * 0.82f), Offset(center.x + radius * 0.52f, center.y + radius * 0.82f), stroke)
            drawBolt(center, radius * 0.72f, RelicInk)
        }
        RelicId.SPLIT_HORIZON -> {
            drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), thin)
            drawArc(RelicInk, 190f, 160f, false, Offset(center.x - radius * 0.65f, center.y - radius * 0.38f), Size(radius * 1.3f, radius * 1.3f), style = Stroke(stroke))
            drawArc(color, 10f, 160f, false, Offset(center.x - radius * 0.65f, center.y - radius * 0.92f), Size(radius * 1.3f, radius * 1.3f), style = Stroke(stroke))
        }
        RelicId.BORROWED_MOMENT -> {
            drawRelicPolygon(center, radius * 0.88f, 4, PI.toFloat() / 4f, color, Stroke(stroke))
            drawLine(RelicInk, Offset(center.x - radius * 0.52f, center.y - radius * 0.52f), Offset(center.x + radius * 0.52f, center.y + radius * 0.52f), thin)
            drawLine(RelicInk, Offset(center.x + radius * 0.52f, center.y - radius * 0.52f), Offset(center.x - radius * 0.52f, center.y + radius * 0.52f), thin)
            drawCircle(color, radius * 0.13f, center)
        }

        RelicId.GLASS_WITNESS -> {
            val eye = Path().apply {
                moveTo(center.x - radius, center.y)
                quadraticTo(center.x, center.y - radius * 0.82f, center.x + radius, center.y)
                quadraticTo(center.x, center.y + radius * 0.82f, center.x - radius, center.y)
                close()
            }
            drawPath(eye, color, style = Stroke(stroke))
            drawRelicPolygon(center, radius * 0.34f, 4, PI.toFloat() / 4f, RelicInk, Fill)
        }
        RelicId.FRACTURE_LENS -> {
            drawCircle(color, radius * 0.76f, center, style = Stroke(stroke))
            drawLine(RelicInk, Offset(center.x - radius * 0.15f, center.y - radius * 0.74f), Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), thin)
            drawLine(RelicInk, Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), Offset(center.x - radius * 0.38f, center.y + radius * 0.68f), thin)
            drawLine(RelicInk, Offset(center.x + radius * 0.12f, center.y - radius * 0.1f), Offset(center.x + radius * 0.68f, center.y + radius * 0.42f), thin)
        }
        RelicId.SPECTRAL_FAN -> {
            repeat(5) { index ->
                val angle = -0.95f + index * 0.48f
                drawLine(if (index == 2) RelicInk else color, Offset(center.x - radius * 0.62f, center.y + radius * 0.62f), relicPolar(center, radius * 0.95f, angle), thin, StrokeCap.Round)
            }
            drawCircle(color, radius * 0.18f, Offset(center.x - radius * 0.62f, center.y + radius * 0.62f))
        }
        RelicId.HARDLIGHT_EDGE -> {
            val blade = Path().apply {
                moveTo(center.x + radius * 0.78f, center.y - radius * 0.88f)
                lineTo(center.x + radius * 0.26f, center.y + radius * 0.58f)
                lineTo(center.x - radius * 0.78f, center.y + radius * 0.88f)
                lineTo(center.x - radius * 0.18f, center.y + radius * 0.08f)
                close()
            }
            drawPath(blade, color, style = Fill)
            drawLine(RelicInk, Offset(center.x - radius * 0.65f, center.y + radius * 0.72f), Offset(center.x + radius * 0.67f, center.y - radius * 0.72f), thin)
        }
        RelicId.CHROMA_FEEDBACK -> {
            repeat(3) { index ->
                val angle = -PI.toFloat() / 2f + index * RELIC_TAU / 3f
                val point = relicPolar(center, radius * 0.7f, angle)
                drawLine(color.copy(alpha = color.alpha * (0.55f + index * 0.2f)), center, point, stroke, StrokeCap.Round)
                drawCircle(if (index == 1) RelicInk else color, radius * 0.19f, point)
            }
            drawCircle(RelicBackground, radius * 0.2f, center)
        }
        RelicId.MIRROR_CUT -> {
            drawRelicPolygon(Offset(center.x - radius * 0.35f, center.y), radius * 0.55f, 4, PI.toFloat() / 4f, color, Stroke(thin))
            drawRelicPolygon(Offset(center.x + radius * 0.35f, center.y), radius * 0.55f, 4, PI.toFloat() / 4f, color, Stroke(thin))
            drawLine(RelicInk, Offset(center.x - radius * 0.82f, center.y + radius * 0.82f), Offset(center.x + radius * 0.82f, center.y - radius * 0.82f), stroke, StrokeCap.Round)
        }

        RelicId.HEAT_DEBT -> {
            drawArc(color, 150f, 240f, false, Offset(center.x - radius * 0.8f, center.y - radius * 0.8f), Size(radius * 1.6f, radius * 1.6f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawLine(RelicInk, center, relicPolar(center, radius * 0.68f, -0.72f), stroke, StrokeCap.Round)
            drawCircle(RelicInk, radius * 0.13f, center)
        }
        RelicId.SCAR_TISSUE -> {
            drawLine(color, Offset(center.x - radius * 0.72f, center.y - radius * 0.82f), Offset(center.x + radius * 0.72f, center.y + radius * 0.82f), stroke, StrokeCap.Round)
            repeat(4) { index ->
                val t = (index + 1f) / 5f
                val p = Offset(center.x - radius * 0.72f + radius * 1.44f * t, center.y - radius * 0.82f + radius * 1.64f * t)
                drawLine(RelicInk, Offset(p.x - radius * 0.28f, p.y + radius * 0.22f), Offset(p.x + radius * 0.28f, p.y - radius * 0.22f), thin, StrokeCap.Round)
            }
        }
        RelicId.QUIETUS_BLOOM -> {
            repeat(6) { index ->
                val angle = index * RELIC_TAU / 6f
                val petal = relicPolar(center, radius * 0.5f, angle)
                drawCircle(color.copy(alpha = color.alpha * 0.28f), radius * 0.38f, petal)
                drawCircle(color, radius * 0.38f, petal, style = Stroke(thin))
            }
            drawCircle(RelicInk, radius * 0.2f, center)
        }
        RelicId.DEVOURERS_TOLL -> {
            drawArc(color, 190f, 160f, false, Offset(center.x - radius * 0.72f, center.y - radius * 0.66f), Size(radius * 1.44f, radius * 1.55f), style = Stroke(stroke, cap = StrokeCap.Round))
            repeat(3) { index ->
                val x = center.x + (index - 1) * radius * 0.38f
                drawRelicPolygon(Offset(x, center.y + radius * 0.57f), radius * 0.18f, 3, PI.toFloat() / 2f, if (index == 1) RelicInk else color, Fill)
            }
            drawCircle(RelicInk, radius * 0.14f, Offset(center.x, center.y - radius * 0.62f))
        }
        RelicId.DOOM_CLOCK -> {
            drawCircle(color, radius * 0.78f, center, style = Stroke(stroke))
            repeat(4) { index -> drawLine(color, relicPolar(center, radius * 0.62f, index * RELIC_TAU / 4f), relicPolar(center, radius * 0.8f, index * RELIC_TAU / 4f), thin) }
            drawLine(RelicInk, center, Offset(center.x + radius * 0.46f, center.y - radius * 0.48f), stroke, StrokeCap.Round)
            drawCircle(RelicInk, radius * 0.13f, center)
        }
        RelicId.LAST_LIGHT -> {
            val flame = Path().apply {
                moveTo(center.x, center.y - radius)
                cubicTo(center.x + radius * 0.75f, center.y - radius * 0.2f, center.x + radius * 0.65f, center.y + radius * 0.72f, center.x, center.y + radius * 0.9f)
                cubicTo(center.x - radius * 0.72f, center.y + radius * 0.42f, center.x - radius * 0.35f, center.y - radius * 0.18f, center.x, center.y - radius)
                close()
            }
            drawPath(flame, color, style = Fill)
            drawRelicPolygon(Offset(center.x, center.y + radius * 0.22f), radius * 0.28f, 4, PI.toFloat() / 4f, RelicInk, Fill)
        }

        RelicId.AGONY_SCEPTER -> {
            drawLine(color, Offset(center.x - radius * 0.48f, center.y + radius * 0.82f), Offset(center.x + radius * 0.28f, center.y - radius * 0.58f), stroke * 1.25f, StrokeCap.Round)
            drawCircle(RelicInk, radius * 0.3f, Offset(center.x + radius * 0.42f, center.y - radius * 0.68f))
            repeat(3) { index ->
                val angle = -2.65f + index * 0.62f
                drawLine(color, Offset(center.x + radius * 0.42f, center.y - radius * 0.68f), relicPolar(Offset(center.x + radius * 0.42f, center.y - radius * 0.68f), radius * 0.58f, angle), thin, StrokeCap.Round)
            }
        }
        RelicId.CROWN_OF_FOUR_WINDS -> {
            val crown = Path().apply {
                moveTo(center.x - radius * 0.9f, center.y + radius * 0.55f)
                lineTo(center.x - radius * 0.72f, center.y - radius * 0.62f)
                lineTo(center.x - radius * 0.25f, center.y - radius * 0.08f)
                lineTo(center.x, center.y - radius * 0.9f)
                lineTo(center.x + radius * 0.25f, center.y - radius * 0.08f)
                lineTo(center.x + radius * 0.72f, center.y - radius * 0.62f)
                lineTo(center.x + radius * 0.9f, center.y + radius * 0.55f)
                close()
            }
            drawPath(crown, color.copy(alpha = color.alpha * 0.28f), style = Fill)
            drawPath(crown, color, style = Stroke(stroke))
            repeat(4) { index -> drawCircle(if (index == 1) RelicInk else color, radius * 0.11f, Offset(center.x - radius * 0.48f + index * radius * 0.32f, center.y + radius * 0.35f)) }
        }
        RelicId.MIRROR_OF_THE_HUNT -> {
            drawRelicPolygon(center, radius * 0.88f, 4, PI.toFloat() / 4f, color, Stroke(stroke))
            drawCircle(color.copy(alpha = color.alpha * 0.22f), radius * 0.5f, center)
            drawCircle(RelicInk, radius * 0.2f, center)
            drawLine(RelicInk, Offset(center.x - radius * 0.85f, center.y + radius * 0.7f), Offset(center.x + radius * 0.85f, center.y - radius * 0.7f), thin)
        }
        RelicId.ENGINE_OF_PARADOX -> {
            drawArc(color, -65f + time * 8f, 235f, false, Offset(center.x - radius * 0.72f, center.y - radius * 0.72f), Size(radius * 1.44f, radius * 1.44f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(RelicInk, 115f - time * 8f, 235f, false, Offset(center.x - radius * 0.48f, center.y - radius * 0.48f), Size(radius * 0.96f, radius * 0.96f), style = Stroke(thin, cap = StrokeCap.Round))
            drawRelicPolygon(center, radius * 0.2f, 6, time, color, Fill)
        }
    }
}

private fun DrawScope.drawRelicChevron(center: Offset, radius: Float, color: Color, stroke: Float) {
    drawLine(color, Offset(center.x - radius * 0.48f, center.y - radius * 0.7f), Offset(center.x + radius * 0.28f, center.y), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x + radius * 0.28f, center.y), Offset(center.x - radius * 0.48f, center.y + radius * 0.7f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawBolt(center: Offset, radius: Float, color: Color) {
    val bolt = Path().apply {
        moveTo(center.x + radius * 0.18f, center.y - radius)
        lineTo(center.x - radius * 0.55f, center.y + radius * 0.05f)
        lineTo(center.x - radius * 0.08f, center.y)
        lineTo(center.x - radius * 0.35f, center.y + radius)
        lineTo(center.x + radius * 0.62f, center.y - radius * 0.18f)
        lineTo(center.x + radius * 0.1f, center.y - radius * 0.08f)
        close()
    }
    drawPath(bolt, color, style = Fill)
}

private fun DrawScope.drawRelicPolygon(
    center: Offset,
    radius: Float,
    sides: Int,
    rotation: Float,
    color: Color,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle,
) {
    val path = Path()
    repeat(sides) { index ->
        val point = relicPolar(center, radius, rotation + index * RELIC_TAU / sides)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color, style = style)
}

private fun relicPolar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
