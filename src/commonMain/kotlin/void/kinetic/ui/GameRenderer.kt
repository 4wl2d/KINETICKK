package void.kinetic.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import void.kinetic.model.ChoiceOption
import void.kinetic.model.ChoiceType
import void.kinetic.model.CoreShape
import void.kinetic.model.DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
import void.kinetic.model.DAMAGE_NUMBER_POWERFUL_MULTIPLIER
import void.kinetic.model.DamageNumberTier
import void.kinetic.model.Enemy
import void.kinetic.model.EnemyType
import void.kinetic.model.GameEngine
import void.kinetic.model.GamePhase
import void.kinetic.model.ItemCatalog
import void.kinetic.model.ItemDefinition
import void.kinetic.model.ItemRarity
import void.kinetic.model.MetaUpgradeCatalog
import void.kinetic.model.MetaUpgradeDefinition
import void.kinetic.model.ParticleDensity
import void.kinetic.model.PickupType
import void.kinetic.model.RebirthProfile
import void.kinetic.model.RelicCatalog
import void.kinetic.model.RelicChoiceAction
import void.kinetic.model.RelicId
import void.kinetic.model.SettingsRow
import void.kinetic.model.TAU
import void.kinetic.model.TotemAction
import void.kinetic.model.UiScreen
import void.kinetic.model.WeaponCatalog
import void.kinetic.model.WeaponDefinition
import void.kinetic.model.WeaponId
import void.kinetic.model.WeaponMastery
import void.kinetic.model.WeaponNodeType
import void.kinetic.model.abbreviateNumber
import void.kinetic.model.clamp
import void.kinetic.model.damageNumberTier
import void.kinetic.model.formatRunTime
import void.kinetic.model.settingsRowsPerPage

private val VoidBlack = Color(0xFF050610)
private val VoidPanel = Color(0xE60B0D1D)
private val GridBlue = Color(0xFF151B38)
private val Cyan = Color(0xFF42F5E9)
private val CyanSoft = Color(0x5542F5E9)
private val Violet = Color(0xFFA96CFF)
private val VioletSoft = Color(0x55A96CFF)
private val Magenta = Color(0xFFFF4DC4)
private val Acid = Color(0xFFB6FF5B)
private val Orange = Color(0xFFFFA14B)
private val Blue = Color(0xFF73A6FF)
private val Gold = Color(0xFFFFD45B)
private val Red = Color(0xFFFF426D)
private val DamagePale = Color(0xFFFFF2C2)
private val White = Color(0xFFF4F6FF)
private val Muted = Color(0xFF8F98B5)
private val DarkLine = Color(0xFF252C4F)
private val dashEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
private val mono = FontFamily.Monospace
private val ParticleColors = listOf(Cyan, Violet, Magenta, Acid, Red)
private val RarityColors = listOf(Muted, Cyan, Violet, Magenta, Acid)
private val WeaponColors = listOf(Cyan, Violet, Magenta, Acid, Orange, Cyan, Magenta, Violet, Orange, Red, White, Color(0xFF73A6FF))
private val DamageNumberColors = listOf(DamagePale, Gold, Orange, Red)
private val DamageNumberTierScales = floatArrayOf(0.95f, 1.03f, 1.12f, 1.25f)
private val VelocityNames = listOf("DRIFT", "SURGE", "HYPER", "VOIDBREAK", "TRANSCENDENT")
private val SettingsLabels = listOf(
    "SFX",
    "MUSIC",
    "MASTER VOLUME",
    "SIMULATION SPEED",
    "TEXT SIZE",
    "SCREEN SHAKE",
    "PARTICLES",
    "DAMAGE NUMBERS",
    "DAMAGE NUMBER SIZE",
    "DAMAGE NUMBER FORMAT",
    "DAMAGE COLOR TIERS",
)
private val MenuNavLabels = listOf("LAB [L]", "ARMORY [A]", "REBIRTH [B]", "CODEX [C]", "SETTINGS [S]")
private val WeaponMasteryProgressionLabel = WeaponMastery.entries.drop(1).joinToString("  ") {
    "L${it.minimumLevel} ${it.displayLabel.uppercase()}"
}

private fun textStyle(size: Float, color: Color = White, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = mono, fontSize = size.sp, color = color, fontWeight = weight)

internal class GameTextMeasurer(
    val delegate: ComposeTextMeasurer,
    private val engine: GameEngine,
) {
    val scale: Float
        get() = engine.settings.textScale
}

private typealias TextMeasurer = GameTextMeasurer

internal fun DrawScope.drawKineticVoid(engine: GameEngine, textMeasurer: TextMeasurer, renderTime: Float) {
    drawRect(VoidBlack)
    val shake = if (engine.settings.screenShake) engine.screenShake else 0f
    val shakeX = if (shake > 0f) sin(engine.elapsed * 91f) * shake else 0f
    val shakeY = if (shake > 0f) cos(engine.elapsed * 77f) * shake else 0f
    drawBackdrop(engine, shakeX, shakeY, renderTime)

    if (engine.phase != GamePhase.MENU) {
        drawWorld(engine, shakeX, shakeY, textMeasurer)
        drawScreenFx(engine, renderTime)
        drawHud(engine, textMeasurer)
    }

    if (engine.screen == UiScreen.GAME) {
        when (engine.phase) {
            GamePhase.MENU -> drawMenu(engine, textMeasurer, renderTime)
            GamePhase.PAUSED -> drawPause(textMeasurer)
            GamePhase.CHOICE -> drawChoice(engine, textMeasurer, renderTime)
            GamePhase.GAME_OVER -> drawEnd(engine, textMeasurer, victory = false)
            GamePhase.VICTORY -> drawEnd(engine, textMeasurer, victory = true)
            GamePhase.RUNNING -> Unit
        }
    } else {
        if (engine.phase == GamePhase.MENU) drawMenu(engine, textMeasurer, renderTime)
        when (engine.screen) {
            UiScreen.SETTINGS -> drawSettings(engine, textMeasurer)
            UiScreen.LAB -> drawLab(engine, textMeasurer)
            UiScreen.ARMORY -> drawArmory(engine, textMeasurer, renderTime)
            UiScreen.REBIRTH -> drawRebirth(engine, textMeasurer)
            UiScreen.CODEX -> drawCodex(engine, textMeasurer)
            UiScreen.GAME -> Unit
        }
    }
}

private fun DrawScope.drawBackdrop(engine: GameEngine, shakeX: Float, shakeY: Float, renderTime: Float) {
    val menuDrift = if (engine.phase == GamePhase.MENU) renderTime * 13f else 0f
    val backdropCameraX = engine.cameraX + menuDrift
    val backdropCameraY = engine.cameraY + menuDrift * 0.38f

    drawGridLayer(backdropCameraX, backdropCameraY, 172f, 0.42f, GridBlue.copy(alpha = 0.26f), 1.35f, shakeX, shakeY)
    drawGridLayer(backdropCameraX, backdropCameraY, 86f, 1f, GridBlue.copy(alpha = 0.18f), 0.8f, shakeX, shakeY)

    val startCellX = floor((engine.cameraX - size.width * 0.5f) / 180f).toInt() - 1
    val endCellX = ceil((engine.cameraX + size.width * 0.5f) / 180f).toInt() + 1
    val startCellY = floor((engine.cameraY - size.height * 0.5f) / 180f).toInt() - 1
    val endCellY = ceil((engine.cameraY + size.height * 0.5f) / 180f).toInt() + 1
    for (cellX in startCellX..endCellX) {
        for (cellY in startCellY..endCellY) {
            val hash = abs(cellX * 7_919 + cellY * 104_729)
            if (hash % 4 == 0) {
                val worldX = cellX * 180f + (hash % 91)
                val worldY = cellY * 180f + ((hash / 97) % 113)
                val point = world(engine, worldX, worldY, shakeX, shakeY)
                val twinkle = 0.25f + (sin(renderTime * (0.8f + hash % 5 * 0.17f) + hash) + 1f) * 0.13f
                drawCircle(if (hash % 3 == 0) Violet else Cyan, 0.9f + hash % 3 * 0.28f, point, alpha = twinkle)
            }
        }
    }
    drawSpeedField(engine, shakeX, shakeY)
    drawRect(Color.Black.copy(alpha = 0.15f), style = Stroke(28f))
    drawRect(Color.Black.copy(alpha = 0.08f), style = Stroke(76f))
}

private fun DrawScope.drawGridLayer(
    cameraX: Float,
    cameraY: Float,
    spacing: Float,
    parallax: Float,
    color: Color,
    lineWidth: Float,
    shakeX: Float,
    shakeY: Float,
) {
    val offsetX = positiveModulo(-cameraX * parallax + size.width * 0.5f, spacing)
    val offsetY = positiveModulo(-cameraY * parallax + size.height * 0.5f, spacing)
    var x = offsetX - spacing
    while (x < size.width + spacing) {
        drawLine(color, Offset(x + shakeX, 0f), Offset(x + shakeX, size.height), lineWidth)
        x += spacing
    }
    var y = offsetY - spacing
    while (y < size.height + spacing) {
        drawLine(color, Offset(0f, y + shakeY), Offset(size.width, y + shakeY), lineWidth)
        y += spacing
    }
}

private fun DrawScope.drawSpeedField(engine: GameEngine, shakeX: Float, shakeY: Float) {
    val rawSpeed = engine.speed
    val speedRatio = speedVisualRatio(rawSpeed)
    val dashBoost = clamp(engine.dashPhaseTime / 0.24f, 0f, 1f)
    val intensity = clamp(sqrt(speedRatio) * 0.78f + dashBoost * 0.32f, 0f, 1f)
    if (rawSpeed < 18f && dashBoost <= 0f) return

    val speed = max(1f, rawSpeed)
    val directionX = engine.velocityX / speed
    val directionY = engine.velocityY / speed
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    val count = when (engine.settings.particleDensity) {
        ParticleDensity.LOW -> 12
        ParticleDensity.NORMAL -> 22
        ParticleDensity.HIGH -> 32
    }
    val margin = 150f
    val fieldWidth = size.width + margin * 2f
    val fieldHeight = size.height + margin * 2f
    val clearRadiusSquared = 105f * 105f
    val fullStrengthRadiusSquared = 340f * 340f

    repeat(count) { index ->
        val seedX = ((index * 73 + 19) % 101) / 101f
        val seedY = ((index * 47 + 31) % 97) / 97f
        val depth = ((index * 37 + 11) % 100) / 100f
        val parallax = 0.14f + depth * 0.34f
        val x = positiveModulo(seedX * fieldWidth - engine.cameraX * parallax, fieldWidth) - margin
        val y = positiveModulo(seedY * fieldHeight - engine.cameraY * parallax, fieldHeight) - margin
        val end = Offset(x + shakeX, y + shakeY)
        val distanceX = end.x - core.x
        val distanceY = end.y - core.y
        val distanceSquared = distanceX * distanceX + distanceY * distanceY
        val centerFade = 0.12f + 0.88f * clamp(
            (distanceSquared - clearRadiusSquared) / (fullStrengthRadiusSquared - clearRadiusSquared),
            0f,
            1f,
        )
        val length = 5f + intensity * (28f + depth * 54f)
        val start = Offset(end.x - directionX * length, end.y - directionY * length)
        val variation = 0.82f + ((index * 29) % 19) / 100f
        val alpha = (0.025f + intensity * (0.075f + depth * 0.045f)) * centerFade * variation
        val width = 0.55f + depth * 0.55f + intensity * 0.35f
        val color = if (index % 7 == 0) White else Cyan

        drawLine(Cyan.copy(alpha = alpha * 0.24f), start, end, width * 3f, StrokeCap.Round)
        drawLine(color.copy(alpha = alpha), start, end, width, StrokeCap.Round)
    }
}

private fun DrawScope.drawWorld(engine: GameEngine, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    val pointer = Offset(engine.pointerX + shakeX * 0.18f, engine.pointerY + shakeY * 0.18f)

    drawTotem(engine, shakeX, shakeY, textMeasurer)
    drawShockwaves(engine, shakeX, shakeY)
    drawMotionEchoes(engine, shakeX, shakeY)
    drawTrail(engine, shakeX, shakeY)
    drawWeaponNodes(engine, shakeX, shakeY)
    drawPickups(engine, shakeX, shakeY)
    drawProjectiles(engine, shakeX, shakeY)
    engine.enemies.forEach { drawEnemy(engine, it, shakeX, shakeY) }
    drawWeapon(engine, core, shakeX, shakeY)
    drawWeaponArcs(engine, shakeX, shakeY)
    drawParticles(engine, shakeX, shakeY)

    val danger = engine.tetherDistance < 75f
    val tetherColor = when {
        danger -> Red
        engine.polarityStability < 0.22f -> Red
        engine.polarityStability < 0.58f -> Orange
        engine.tetherDistance < 145f -> Orange
        else -> Cyan
    }
    val tetherPulse = (sin(engine.elapsed * 10f) + 1f) * 0.5f
    val strained = engine.polarityStability < 0.58f
    drawLine(tetherColor.copy(alpha = 0.08f + if (danger || strained) tetherPulse * 0.08f else 0f), core, pointer, if (danger || strained) 13f else 9f, StrokeCap.Round)
    drawLine(tetherColor.copy(alpha = if (danger || strained) 0.78f else 0.55f), core, pointer, if (danger || strained) 2f else 1.4f, StrokeCap.Round, dashEffect)
    drawCore(engine, core)
    drawSingularity(pointer, engine.elapsed, danger)

    val settings = engine.settings
    if (settings.damageNumbers) {
        engine.damageNumbers.forEach { number ->
            val location = world(engine, number.x, number.y, shakeX, shakeY)
            val tier = damageNumberTier(number.amount, settings.damageNumberTierThreshold, number.critical)
            drawLabel(
                textMeasurer = textMeasurer,
                text = number.formattedAmount(settings.damageNumberFormat),
                x = location.x,
                y = location.y,
                fontSize = 12f * settings.damageNumberSize.scale * DamageNumberTierScales[tier.ordinal],
                color = DamageNumberColors[tier.ordinal],
                centered = true,
                weight = if (number.critical || tier >= DamageNumberTier.POWERFUL) FontWeight.Bold else FontWeight.Normal,
                alpha = clamp(number.life / 0.3f, 0f, 1f),
            )
        }
    }
}

private fun DrawScope.drawMotionEchoes(engine: GameEngine, shakeX: Float, shakeY: Float) {
    engine.motionEchoes.forEach { echo ->
        val center = world(engine, echo.x, echo.y, shakeX, shakeY)
        if (!isOnScreen(center, 60f)) return@forEach
        val life = clamp(echo.life / echo.maxLife, 0f, 1f)
        val alpha = life * life * echo.intensity
        val radius = GameEngine.CORE_RADIUS + (1f - life) * 9f
        drawCircle(Cyan.copy(alpha = alpha * 0.07f), radius + 12f, center)
        drawCircle(White.copy(alpha = alpha * 0.32f), radius, center, style = Stroke(1.2f + echo.intensity))
    }
}

private fun DrawScope.drawShockwaves(engine: GameEngine, shakeX: Float, shakeY: Float) {
    engine.shockwaves.forEach { wave ->
        val center = world(engine, wave.x, wave.y, shakeX, shakeY)
        if (!isOnScreen(center, wave.maxRadius)) return@forEach
        val life = clamp(wave.life / wave.maxLife, 0f, 1f)
        val progress = 1f - life
        val eased = 1f - (1f - progress) * (1f - progress)
        val radius = max(2f, wave.maxRadius * eased)
        val color = ParticleColors[wave.colorIndex.coerceIn(ParticleColors.indices)]
        drawCircle(color.copy(alpha = life * 0.08f), radius * 0.72f, center)
        drawCircle(color.copy(alpha = life * 0.68f), radius, center, style = Stroke(1f + life * 2.2f))
    }
}

private fun DrawScope.drawTrail(engine: GameEngine, shakeX: Float, shakeY: Float) {
    var previousLocation: Offset? = null
    var previousLife = 0f
    engine.trail.forEach { point ->
        val location = world(engine, point.x, point.y, shakeX, shakeY)
        val life = clamp(1f - point.age / 2.25f, 0f, 1f)
        previousLocation?.let { previous ->
            val segmentLife = min(previousLife, life)
            if (segmentLife > 0f && (isOnScreen(previous, 50f) || isOnScreen(location, 50f))) {
                drawLine(Violet.copy(alpha = segmentLife * 0.1f), previous, location, 17f + segmentLife * 13f, StrokeCap.Round)
                drawLine(Magenta.copy(alpha = segmentLife * 0.28f), previous, location, 7f + segmentLife * 7f, StrokeCap.Round)
                drawLine(Cyan.copy(alpha = segmentLife * 0.42f), previous, location, 1.4f + segmentLife * 2f, StrokeCap.Round)
            }
        }
        previousLocation = location
        previousLife = life
    }
}

private fun DrawScope.drawScreenFx(engine: GameEngine, renderTime: Float) {
    if (engine.damageFlash > 0f) {
        drawRect(Red.copy(alpha = engine.damageFlash * 0.08f))
        drawRect(Red.copy(alpha = engine.damageFlash * 0.72f), style = Stroke(5f + engine.damageFlash * 10f))
    }
    if (engine.overheated) {
        val pulse = (sin(renderTime * 11f) + 1f) * 0.5f
        drawRect(Orange.copy(alpha = 0.18f + pulse * 0.16f), style = Stroke(4f + pulse * 4f))
    }
    if (engine.overdriveTime > 0f) {
        val pulse = (sin(renderTime * 7f) + 1f) * 0.5f
        drawRect(Acid.copy(alpha = 0.08f + pulse * 0.05f), style = Stroke(3f))
    }
}

private fun DrawScope.drawWeapon(engine: GameEngine, core: Offset, shakeX: Float, shakeY: Float) {
    val motionAngle = if (engine.speed > 1f) atan2(engine.velocityY, engine.velocityX) else 0f
    when (engine.weapon) {
        WeaponId.FLUX_WAKE -> Unit
        WeaponId.MORNINGSTAR -> {
            val ball = world(engine, engine.morningstarX, engine.morningstarY, shakeX, shakeY)
            drawLine(Violet.copy(alpha = 0.24f), core, ball, 7f)
            drawLine(Cyan.copy(alpha = 0.8f), core, ball, 1.5f, pathEffect = dashEffect)
            drawCircle(Magenta.copy(alpha = 0.18f), 34f, ball)
            drawCircle(Violet.copy(alpha = 0.4f), 26f, ball)
            drawPolygon(ball, 20f, 8, engine.morningstarAngle, White, Fill)
            drawPolygon(ball, 25f, 8, engine.morningstarAngle, Magenta, Stroke(2f))
            val agonyRank = engine.relicRank(RelicId.AGONY_SCEPTER)
            if (agonyRank > 0) {
                val agonyBall = world(
                    engine,
                    engine.coreX * 2f - engine.morningstarX,
                    engine.coreY * 2f - engine.morningstarY,
                    shakeX,
                    shakeY,
                )
                val agonyRadius = 17f + agonyRank
                drawLine(Gold.copy(alpha = 0.2f), core, agonyBall, 7f)
                drawLine(Red.copy(alpha = 0.88f), core, agonyBall, 1.5f, pathEffect = dashEffect)
                drawCircle(Red.copy(alpha = 0.13f), agonyRadius + 13f, agonyBall)
                drawCircle(Gold.copy(alpha = 0.3f), agonyRadius + 5f, agonyBall)
                drawPolygon(agonyBall, agonyRadius, 8, -engine.morningstarAngle, White, Fill)
                drawPolygon(agonyBall, agonyRadius + 5f, 8, -engine.morningstarAngle, Gold, Stroke(2f))
            }
        }
        WeaponId.PHASE_LATTICE -> {
            val pulse = (sin(engine.elapsed * 4f) + 1f) * 0.5f
            drawCircle(Violet.copy(alpha = 0.08f), 132f, core)
            drawCircle(Cyan.copy(alpha = 0.42f), 105f + pulse * 18f, core, style = Stroke(1.5f))
            drawCircle(Magenta.copy(alpha = 0.25f), 132f - pulse * 18f, core, style = Stroke(1f, pathEffect = dashEffect))
        }
        WeaponId.NULL_LANCE -> {
            val tip = polar(core, 48f, motionAngle)
            drawLine(Cyan.copy(alpha = 0.22f), core, polar(core, 115f, motionAngle), 7f, StrokeCap.Round)
            drawLine(White, core, tip, 2f, StrokeCap.Round)
            drawPolygon(tip, 7f, 3, motionAngle, Cyan, Fill)
        }
        WeaponId.GRAVITY_MINES -> {
            drawCircle(Violet.copy(alpha = 0.22f), 29f, core, style = Stroke(1.5f, pathEffect = dashEffect))
        }
        WeaponId.ION_SWARM -> engine.weaponOrbitals.forEach { orbital ->
            val point = world(engine, orbital.x, orbital.y, shakeX, shakeY)
            drawCircle(Cyan.copy(alpha = 0.14f), 14f, point)
            drawPolygon(point, 7f, 4, engine.elapsed * 2f + orbital.index, Cyan, Fill)
        }
        WeaponId.RIFT_BLADES -> engine.weaponOrbitals.forEach { orbital ->
            val point = world(engine, orbital.x, orbital.y, shakeX, shakeY)
            drawPolygon(point, 16f, 4, engine.elapsed * 4.2f + orbital.index, Magenta, Fill)
            drawLine(Violet.copy(alpha = 0.25f), core, point, 1f, pathEffect = dashEffect)
        }
        WeaponId.ARC_COIL -> {
            val pulse = (sin(engine.elapsed * 9f) + 1f) * 0.5f
            drawCircle(Violet.copy(alpha = 0.3f), 31f + pulse * 7f, core, style = Stroke(2f))
            drawCircle(Cyan.copy(alpha = 0.45f), 22f - pulse * 4f, core, style = Stroke(1f, pathEffect = dashEffect))
        }
        WeaponId.QUASAR_CANNON -> {
            val rear = polar(core, 19f, motionAngle + PI.toFloat())
            val tip = polar(core, 55f, motionAngle)
            drawLine(Orange.copy(alpha = 0.28f), rear, tip, 14f, StrokeCap.Round)
            drawLine(White, core, tip, 3f, StrokeCap.Round)
            drawCircle(Orange, 7f, tip)
        }
        WeaponId.ENTROPY_FIELD -> {
            val radius = 170f + engine.weaponLevel * 5f
            drawCircle(Red.copy(alpha = 0.045f), radius, core)
            drawCircle(Magenta.copy(alpha = 0.28f), radius, core, style = Stroke(1.2f, pathEffect = dashEffect))
        }
        WeaponId.SINGULARITY_SPEAR -> {
            drawCircle(White.copy(alpha = 0.25f), 36f, core, style = Stroke(2f))
            if (engine.weaponBeamTime > 0f) {
                val start = world(engine, engine.weaponBeamStartX, engine.weaponBeamStartY, shakeX, shakeY)
                val end = world(engine, engine.weaponBeamEndX, engine.weaponBeamEndY, shakeX, shakeY)
                val alpha = clamp(engine.weaponBeamTime / 0.18f, 0f, 1f)
                drawLine(Violet.copy(alpha = alpha * 0.22f), start, end, 19f, StrokeCap.Round)
                drawLine(White.copy(alpha = alpha), start, end, 4f, StrokeCap.Round)
                drawLine(Cyan.copy(alpha = alpha), start, end, 1.3f, StrokeCap.Round)
            }
        }
        WeaponId.PRISM_RELAY -> {
            val pulse = (sin(engine.elapsed * 7f) + 1f) * 0.5f
            drawPolygon(core, 27f + pulse * 4f, 4, engine.elapsed * 0.8f, Color(0xFF73A6FF), Stroke(2f))
            drawPolygon(core, 15f, 4, -engine.elapsed * 1.2f, White, Fill)
        }
    }
}

private fun DrawScope.drawWeaponNodes(engine: GameEngine, shakeX: Float, shakeY: Float) {
    engine.weaponNodes.forEach { node ->
        if (node.type == WeaponNodeType.GRAVITY_MINE) {
            val point = world(engine, node.x, node.y, shakeX, shakeY)
            val ratio = clamp(node.life / node.maxLife, 0f, 1f)
            drawCircle(Violet.copy(alpha = 0.08f + (1f - ratio) * 0.16f), node.radius, point)
            drawCircle(Magenta.copy(alpha = 0.65f), node.radius * ratio, point, style = Stroke(1.5f, pathEffect = dashEffect))
            drawPolygon(point, 11f, 6, engine.elapsed * 2.5f, White, Stroke(1.5f))
        }
    }
}

private fun DrawScope.drawWeaponArcs(engine: GameEngine, shakeX: Float, shakeY: Float) {
    engine.weaponArcs.forEachIndexed { index, arc ->
        val start = world(engine, arc.fromX, arc.fromY, shakeX, shakeY)
        val end = world(engine, arc.toX, arc.toY, shakeX, shakeY)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = max(1f, kotlin.math.sqrt(dx * dx + dy * dy))
        val nx = -dy / length
        val ny = dx / length
        var previous = start
        repeat(6) { segment ->
            val t = (segment + 1f) / 6f
            val jitter = if (segment == 5) 0f else sin(index * 7f + segment * 4.1f + engine.elapsed * 40f) * 8f
            val next = Offset(start.x + dx * t + nx * jitter, start.y + dy * t + ny * jitter)
            drawLine(Cyan.copy(alpha = clamp(arc.life / 0.14f, 0f, 1f)), previous, next, 2f, StrokeCap.Round)
            previous = next
        }
    }
}

private fun DrawScope.drawCore(engine: GameEngine, center: Offset) {
    val speedRatio = speedVisualRatio(engine.speed)
    val phase = engine.dashPhaseTime > 0f
    val main = if (phase) White else Cyan
    drawCircle(Cyan.copy(alpha = 0.06f + speedRatio * 0.08f), 50f + speedRatio * 15f, center)
    drawCircle(Violet.copy(alpha = 0.18f), 31f, center)
    if (engine.overdriveTime > 0f) {
        val pulse = (sin(engine.elapsed * 9f) + 1f) * 0.5f
        drawCircle(Acid.copy(alpha = 0.07f + pulse * 0.05f), 58f + pulse * 8f, center)
        drawArc(
            Acid.copy(alpha = 0.72f),
            engine.elapsed * 90f,
            118f,
            false,
            Offset(center.x - 43f, center.y - 43f),
            Size(86f, 86f),
            style = Stroke(2f, cap = StrokeCap.Round),
        )
        drawArc(
            Cyan.copy(alpha = 0.5f),
            -engine.elapsed * 115f + 180f,
            92f,
            false,
            Offset(center.x - 36f, center.y - 36f),
            Size(72f, 72f),
            style = Stroke(1.5f, cap = StrokeCap.Round),
        )
    }
    if (engine.braking) {
        val compression = (sin(engine.elapsed * 15f) + 1f) * 0.5f
        drawCircle(Violet.copy(alpha = 0.12f), 42f - compression * 7f, center)
        drawCircle(Violet.copy(alpha = 0.72f), 47f - compression * 11f, center, style = Stroke(2f, pathEffect = dashEffect))
    }
    if (engine.speed > 25f) {
        val length = 42f + 118f * speedRatio
        val magnitude = max(1f, engine.speed)
        val tail = Offset(center.x - engine.velocityX / magnitude * length, center.y - engine.velocityY / magnitude * length)
        drawLine(Cyan.copy(alpha = 0.24f + if (phase) 0.18f else 0f), tail, center, if (phase) 22f else 13f, StrokeCap.Round)
        drawLine(White.copy(alpha = 0.7f), tail, center, 2f, StrokeCap.Round)
    }
    if (engine.maxShield > 0f && engine.shield > 0f) {
        val shieldRatio = clamp(engine.shield / engine.maxShield, 0f, 1f)
        drawArc(
            color = Violet.copy(alpha = 0.35f + shieldRatio * 0.45f),
            startAngle = -90f,
            sweepAngle = 360f * shieldRatio,
            useCenter = false,
            topLeft = Offset(center.x - 29f, center.y - 29f),
            size = Size(58f, 58f),
            style = Stroke(2.5f, cap = StrokeCap.Round),
        )
    }
    when (engine.coreShape) {
        CoreShape.ORB -> {
            drawCircle(main, GameEngine.CORE_RADIUS, center)
            drawCircle(VoidBlack, 7f, center)
            drawCircle(Violet, 4f, center)
        }
        CoreShape.PRISM -> {
            drawPolygon(center, 21f, 4, engine.elapsed * 1.3f + (PI / 4).toFloat(), main, Fill)
            drawPolygon(center, 13f, 4, -engine.elapsed * 1.8f, VoidBlack, Fill)
            drawCircle(Violet, 4f, center)
        }
        CoreShape.SHARD -> {
            drawPolygon(center, 23f, 3, engine.elapsed * 1.2f - (PI / 2).toFloat(), main, Fill)
            drawPolygon(center, 14f, 3, -engine.elapsed * 1.7f, VoidBlack, Fill)
            drawCircle(Magenta, 4f, center)
        }
    }
    if (engine.dashPhaseTime > 0f) drawCircle(White.copy(alpha = 0.8f), 28f + engine.dashPhaseTime * 50f, center, style = Stroke(2f))
}

private fun DrawScope.drawSingularity(center: Offset, time: Float, danger: Boolean) {
    val color = if (danger) Red else Magenta
    val pulse = (sin(time * 7f) + 1f) * 0.5f
    drawCircle(color.copy(alpha = 0.08f), 34f + pulse * 8f, center)
    drawCircle(color.copy(alpha = 0.24f), 22f + pulse * 3f, center)
    drawCircle(VoidBlack, 12f, center)
    drawCircle(color, 15f, center, style = Stroke(2f))
    repeat(4) { index ->
        val angle = time * 1.9f + index * TAU / 4f
        val start = Offset(center.x + cos(angle) * 20f, center.y + sin(angle) * 20f)
        val end = Offset(center.x + cos(angle + 0.55f) * 28f, center.y + sin(angle + 0.55f) * 28f)
        drawLine(color.copy(alpha = 0.68f), start, end, 1.5f)
    }
}

private fun DrawScope.drawEnemy(engine: GameEngine, enemy: Enemy, shakeX: Float, shakeY: Float) {
    val center = world(engine, enemy.x, enemy.y, shakeX, shakeY)
    val renderMargin = if (enemy.type == EnemyType.WARDEN) 460f else 120f
    if (center.x < -renderMargin || center.y < -renderMargin || center.x > size.width + renderMargin || center.y > size.height + renderMargin) return
    val health = clamp(enemy.hp / enemy.maxHp, 0f, 1f)
    val baseColor = when (enemy.type) {
        EnemyType.DRIFTER -> Violet
        EnemyType.SHOOTER -> Magenta
        EnemyType.CHARGER -> Orange
        EnemyType.INTERCEPTOR -> Cyan
        EnemyType.WEAVER -> Blue
        EnemyType.WARDEN -> Violet
        EnemyType.SPLITTER -> Gold
        EnemyType.ELITE -> Acid
        EnemyType.ARCHITECT -> Red
    }
    val flashColor = if (enemy.flash > 0.5f) White else baseColor
    val rotation = engine.elapsed * when (enemy.type) {
        EnemyType.DRIFTER -> 0.8f
        EnemyType.SHOOTER -> -1.1f
        EnemyType.CHARGER -> 1.6f
        EnemyType.INTERCEPTOR -> -2.4f
        EnemyType.WEAVER -> 1.95f
        EnemyType.WARDEN -> -0.32f
        EnemyType.SPLITTER -> 0.62f
        EnemyType.ELITE -> -0.55f
        EnemyType.ARCHITECT -> 0.22f
    } + enemy.id
    val sides = when (enemy.type) {
        EnemyType.DRIFTER -> 6
        EnemyType.SHOOTER -> 3
        EnemyType.CHARGER -> 4
        EnemyType.INTERCEPTOR -> 5
        EnemyType.WEAVER -> 7
        EnemyType.WARDEN -> 8
        EnemyType.SPLITTER -> 10
        EnemyType.ELITE -> 6
        EnemyType.ARCHITECT -> 8
    }
    val core = world(engine, engine.coreX, engine.coreY, shakeX, shakeY)
    when (enemy.type) {
        EnemyType.SHOOTER -> if (enemy.actionTimer in 0f..0.42f) {
            val charge = 1f - enemy.actionTimer / 0.42f
            drawLine(Red.copy(alpha = 0.12f + charge * 0.42f), center, core, 1f + charge * 1.2f, pathEffect = dashEffect)
            drawCircle(Magenta.copy(alpha = 0.5f), enemy.radius + 15f * (1f - charge), center, style = Stroke(1.5f))
        }
        EnemyType.CHARGER -> if (enemy.actionTimer < 0f) {
            val charge = clamp(-enemy.actionTimer / 0.45f, 0f, 1f)
            drawLine(Orange.copy(alpha = 0.2f + charge * 0.62f), center, core, 2f + charge * 2f, StrokeCap.Round)
            drawCircle(Orange.copy(alpha = 0.72f), enemy.radius + 28f * (1f - charge), center, style = Stroke(2f, pathEffect = dashEffect))
        }
        EnemyType.INTERCEPTOR -> {
            val predictedCore = world(
                engine,
                engine.coreX + engine.velocityX * 0.45f,
                engine.coreY + engine.velocityY * 0.45f,
                shakeX,
                shakeY,
            )
            drawLine(Cyan.copy(alpha = 0.32f), center, predictedCore, 1.4f, pathEffect = dashEffect)
            drawCircle(Cyan.copy(alpha = 0.12f), 13f, predictedCore)
            drawPolygon(predictedCore, 5f, 3, atan2(predictedCore.y - center.y, predictedCore.x - center.x), Cyan, Fill)
        }
        EnemyType.WEAVER -> {
            repeat(2) { index ->
                val orbitAngle = engine.elapsed * 2.7f + enemy.id + index * PI.toFloat()
                val mote = polar(center, enemy.radius + 10f, orbitAngle)
                drawLine(Blue.copy(alpha = 0.42f), center, mote, 1.2f)
                drawCircle(Blue, 3f, mote)
            }
            if (enemy.actionTimer in 0f..0.34f) {
                drawLine(Blue.copy(alpha = 0.48f), polar(center, enemy.radius + 19f, rotation), polar(center, enemy.radius + 19f, rotation + PI.toFloat()), 2f)
            }
        }
        EnemyType.WARDEN -> {
            val gravityPulse = (sin(engine.elapsed * 2.2f + enemy.id) + 1f) * 0.5f
            drawCircle(Violet.copy(alpha = 0.035f + gravityPulse * 0.025f), 440f, center)
            drawCircle(Violet.copy(alpha = 0.24f), 440f, center, style = Stroke(1.2f, pathEffect = dashEffect))
            repeat(6) { index ->
                val angle = index * TAU / 6f - engine.elapsed * 0.22f
                val outer = polar(center, enemy.radius + 24f, angle)
                val inner = polar(center, enemy.radius + 12f, angle)
                drawLine(Violet.copy(alpha = 0.68f), outer, inner, 2f, StrokeCap.Round)
            }
            if (enemy.actionTimer in 0f..0.38f) {
                drawCircle(Red.copy(alpha = 0.52f), enemy.radius + 18f + enemy.actionTimer * 50f, center, style = Stroke(2f))
            }
        }
        EnemyType.SPLITTER -> {
            repeat(2) { index ->
                val fragmentAngle = rotation * 1.7f + index * PI.toFloat()
                val fragment = polar(center, enemy.radius * 0.38f, fragmentAngle)
                drawPolygon(fragment, enemy.radius * 0.3f, 6, -rotation, Gold.copy(alpha = 0.72f), Stroke(1.4f))
            }
            drawLine(
                Gold.copy(alpha = 0.7f),
                Offset(center.x - enemy.radius * 0.7f, center.y + enemy.radius * 0.55f),
                Offset(center.x + enemy.radius * 0.62f, center.y - enemy.radius * 0.58f),
                1.5f,
                pathEffect = dashEffect,
            )
        }
        EnemyType.ELITE -> {
            repeat(6) { index ->
                val angle = -engine.elapsed * 0.8f + index * TAU / 6f
                val inner = polar(center, enemy.radius + 9f, angle)
                val outer = polar(center, enemy.radius + 16f, angle)
                drawLine(Acid.copy(alpha = 0.65f), inner, outer, 2f, StrokeCap.Round)
            }
            if (enemy.actionTimer in 0f..0.28f) {
                drawCircle(Acid.copy(alpha = 0.48f), enemy.radius + 20f + enemy.actionTimer * 38f, center, style = Stroke(1.5f))
            }
        }
        EnemyType.ARCHITECT -> {
            val pulse = (sin(engine.elapsed * 3.4f) + 1f) * 0.5f
            drawCircle(Red.copy(alpha = 0.15f), enemy.radius + 30f + pulse * 12f, center, style = Stroke(2f, pathEffect = dashEffect))
            drawCircle(Violet.copy(alpha = 0.11f), enemy.radius + 58f - pulse * 10f, center, style = Stroke(1.3f))
        }
        EnemyType.DRIFTER -> Unit
    }
    drawCircle(flashColor.copy(alpha = 0.07f), enemy.radius * 2f, center)
    drawPolygon(center, enemy.radius, sides, rotation, flashColor.copy(alpha = 0.26f), Fill)
    drawPolygon(center, enemy.radius, sides, rotation, flashColor, Stroke(if (enemy.type == EnemyType.ARCHITECT) 3f else 1.8f))
    drawPolygon(center, enemy.radius * 0.52f, sides, -rotation * 1.4f, flashColor.copy(alpha = 0.7f), Stroke(1f))
    if (
        enemy.type == EnemyType.WARDEN ||
        enemy.type == EnemyType.SPLITTER ||
        enemy.type == EnemyType.ELITE ||
        enemy.type == EnemyType.ARCHITECT
    ) {
        val barWidth = when (enemy.type) {
            EnemyType.ARCHITECT -> 160f
            EnemyType.WARDEN -> 84f
            else -> 72f
        }
        drawBar(center.x - barWidth * 0.5f, center.y - enemy.radius - 18f, barWidth, 5f, health, flashColor, DarkLine)
    }
}

private fun DrawScope.drawProjectiles(engine: GameEngine, shakeX: Float, shakeY: Float) {
    engine.projectiles.forEach { projectile ->
        val center = world(engine, projectile.x, projectile.y, shakeX, shakeY)
        if (!isOnScreen(center, projectile.radius + 28f)) return@forEach
        val previous = world(engine, projectile.previousX, projectile.previousY, shakeX, shakeY)
        if (projectile.hostile) {
            drawLine(Red.copy(alpha = 0.24f), previous, center, projectile.radius * 1.25f, StrokeCap.Round)
            drawCircle(Red.copy(alpha = 0.13f), projectile.radius + 8f, center)
            drawCircle(Magenta, projectile.radius, center)
            drawCircle(White, projectile.radius * 0.36f, center)
        } else {
            val color = projectile.sourceWeapon?.let(::weaponColor) ?: ParticleColors[projectile.colorIndex.coerceIn(ParticleColors.indices)]
            drawLine(color.copy(alpha = 0.25f), previous, center, projectile.radius * 1.8f, StrokeCap.Round)
            drawCircle(color.copy(alpha = 0.16f), projectile.radius + 9f, center)
            drawCircle(color, projectile.radius, center)
            drawCircle(White, max(1.5f, projectile.radius * 0.28f), center)
        }
    }
}

private fun DrawScope.drawPickups(engine: GameEngine, shakeX: Float, shakeY: Float) {
    engine.pickups.forEach { pickup ->
        val center = world(engine, pickup.x, pickup.y, shakeX, shakeY)
        if (!isOnScreen(center, 28f)) return@forEach
        val previous = world(engine, pickup.previousX, pickup.previousY, shakeX, shakeY)
        when (pickup.type) {
            PickupType.DATA -> {
                drawLine(Cyan.copy(alpha = 0.28f), previous, center, 2f, StrokeCap.Round)
                drawCircle(Cyan.copy(alpha = 0.14f), 11f, center)
                drawPolygon(center, 6f, 4, engine.elapsed * 1.7f, Cyan, Fill)
            }
            PickupType.KEY -> {
                drawLine(Acid.copy(alpha = 0.25f), previous, center, 2.5f, StrokeCap.Round)
                drawCircle(Acid.copy(alpha = 0.14f), 18f, center)
                drawPolygon(center, 11f, 6, engine.elapsed, Acid, Stroke(2f))
                drawCircle(Acid, 3f, center)
            }
            PickupType.REPAIR -> {
                drawLine(Orange.copy(alpha = 0.25f), previous, center, 2f, StrokeCap.Round)
                drawCircle(Orange.copy(alpha = 0.16f), 15f, center)
                drawLine(Orange, Offset(center.x - 6f, center.y), Offset(center.x + 6f, center.y), 3f)
                drawLine(Orange, Offset(center.x, center.y - 6f), Offset(center.x, center.y + 6f), 3f)
            }
            PickupType.RELIC -> {
                drawLine(Gold.copy(alpha = 0.34f), previous, center, 2.8f, StrokeCap.Round)
                drawCircle(Gold.copy(alpha = 0.09f), 24f, center)
                drawUnresolvedRelicIcon(center, 13f, engine.elapsed)
            }
        }
    }
}

private fun DrawScope.drawParticles(engine: GameEngine, shakeX: Float, shakeY: Float) {
    engine.particles.forEach { particle ->
        val center = world(engine, particle.x, particle.y, shakeX, shakeY)
        if (!isOnScreen(center, 24f)) return@forEach
        val alpha = clamp(particle.life / particle.maxLife, 0f, 1f)
        val color = ParticleColors[particle.colorIndex.coerceIn(ParticleColors.indices)]
        val particleSpeed = kotlin.math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy)
        if (particleSpeed > 70f) {
            val stretch = min(0.045f, 12f / particleSpeed)
            val tail = world(engine, particle.x - particle.vx * stretch, particle.y - particle.vy * stretch, shakeX, shakeY)
            drawLine(color.copy(alpha = alpha * 0.42f), tail, center, max(1f, particle.size * alpha * 0.65f), StrokeCap.Round)
        }
        drawCircle(color.copy(alpha = alpha), max(0.8f, particle.size * alpha), center)
    }
}

private fun DrawScope.drawTotem(engine: GameEngine, shakeX: Float, shakeY: Float, textMeasurer: TextMeasurer) {
    val totem = engine.totem ?: return
    val location = world(engine, totem.x, totem.y, shakeX, shakeY)
    val onScreen = location.x in 45f..size.width - 45f && location.y in 45f..size.height - 45f
    if (onScreen) {
        val pulse = (sin(totem.pulse) + 1f) * 0.5f
        drawCircle(Acid.copy(alpha = 0.08f), 47f + pulse * 8f, location)
        drawPolygon(location, 30f, 6, totem.pulse * 0.25f, Acid.copy(alpha = 0.18f), Fill)
        drawPolygon(location, 30f, 6, totem.pulse * 0.25f, Acid, Stroke(2f))
        drawPolygon(location, 15f, 3, -totem.pulse, White, Stroke(1.5f))
        drawLabel(textMeasurer, "WEAPON TOTEM", location.x, location.y + 43f, 10f, Acid, centered = true)
    } else {
        val margin = 34f
        val edge = Offset(clamp(location.x, margin, size.width - margin), clamp(location.y, margin, size.height - margin))
        drawCircle(Acid.copy(alpha = 0.16f), 18f, edge)
        drawPolygon(edge, 10f, 3, kotlin.math.atan2(location.y - center.y, location.x - center.x), Acid, Fill)
    }
}

private fun DrawScope.drawHud(engine: GameEngine, textMeasurer: TextMeasurer) {
    val narrow = size.width / density < 700f
    val panelX = d(if (narrow) 10f else 18f)
    val panelY = d(if (narrow) 10f else 18f)
    val panelWidth = d(if (narrow) 185f else 250f)
    val panelHeight = d(if (narrow) 119f else 137f)
    val contentX = panelX + d(if (narrow) 10f else 13f)
    val barWidth = d(if (narrow) 165f else 220f)
    drawRect(Color(0xAA080A17), topLeft = Offset(panelX, panelY), size = Size(panelWidth, panelHeight))
    drawRect(DarkLine, topLeft = Offset(panelX, panelY), size = Size(panelWidth, panelHeight), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "CORE // ${engine.coreShape.name}", contentX, panelY + d(10f), if (narrow) 9f else 11f, Cyan, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "LV ${engine.level}   DATA ${engine.data}/${engine.nextLevelData}", contentX, panelY + d(29f), if (narrow) 8f else 10f, Muted)
    drawBar(contentX, panelY + d(52f), barWidth, d(7f), engine.hp / engine.maxHp, Cyan, DarkLine)
    drawLabel(textMeasurer, "INTEGRITY ${engine.hp.toInt()}", contentX, panelY + d(63f), if (narrow) 7f else 9f, White)
    if (engine.maxShield > 0f) {
        drawBar(contentX, panelY + d(82f), barWidth, d(5f), engine.shield / engine.maxShield, Violet, DarkLine)
        drawLabel(textMeasurer, "SHIELD ${engine.shield.toInt()}/${engine.maxShield.toInt()}", contentX, panelY + d(89f), 7f, Violet)
    }
    drawBar(contentX, panelY + d(111f), barWidth, d(6f), engine.heat / GameEngine.MAX_HEAT, if (engine.overheated) Red else Orange, DarkLine)
    drawLabel(textMeasurer, if (engine.overheated) "HEAT // OFFLINE" else "HEAT", contentX + barWidth, panelY + d(91f), if (narrow) 7f else 9f, if (engine.overheated) Red else Muted, alignRight = true)

    val timeY = d(if (narrow) 145f else 25f)
    val phaseY = d(if (narrow) 174f else 61f)
    val progressY = d(if (narrow) 193f else 83f)
    drawLabel(textMeasurer, formatRunTime(engine.elapsed), size.width * 0.5f, timeY, if (narrow) 20f else 25f, White, centered = true, weight = FontWeight.Bold)
    val phaseLabel = if (narrow) {
        "R${engine.rebirthLevel} // DRIFT > ARCHITECT"
    } else {
        "REBIRTH ${engine.rebirthLevel} // DRIFT  >  SURGE  >  ARCHITECT"
    }
    drawLabel(textMeasurer, phaseLabel, size.width * 0.5f, phaseY, if (narrow) 7f else 9f, Muted, centered = true)
    val halfProgress = min(d(240f), size.width * 0.22f)
    drawBar(size.width * 0.5f - halfProgress, progressY, halfProgress * 2f, d(4f), engine.runProgress, Violet, DarkLine)
    val overdriveWidth = min(d(180f), size.width * 0.17f)
    drawBar(size.width * 0.5f - overdriveWidth, progressY + d(14f), overdriveWidth * 2f, d(5f), engine.overdriveCharge / 100f, if (engine.overdriveTime > 0f) Acid else Magenta, DarkLine)
    drawLabel(textMeasurer, if (engine.overdriveTime > 0f) "OVERDRIVE ACTIVE" else "OVERDRIVE ${engine.overdriveCharge.toInt()}%", size.width * 0.5f, progressY + d(22f), 7f, if (engine.overdriveTime > 0f) Acid else Muted, centered = true)
    val polarityColor = when {
        engine.polarityStability < 0.22f -> Red
        engine.polarityStability < 0.58f -> Orange
        else -> Cyan
    }
    val polarityWidth = min(d(130f), size.width * 0.13f)
    drawBar(size.width * 0.5f - polarityWidth, progressY + d(38f), polarityWidth * 2f, d(4f), engine.polarityStability, polarityColor, DarkLine)
    val polarityLabel = if (engine.polarityStability < 0.58f) {
        "POLARITY ${(engine.polarityStability * 100f).toInt()}% // TURN OR RECENTER"
    } else {
        "POLARITY ${(engine.polarityStability * 100f).toInt()}%"
    }
    drawLabel(textMeasurer, polarityLabel, size.width * 0.5f, progressY + d(45f), 7f, polarityColor, centered = true)

    val right = size.width - d(if (narrow) 10f else 22f)
    val weaponColor = weaponColor(engine.weapon)
    drawLabel(textMeasurer, engine.currentWeaponDefinition.name.uppercase(), right, d(if (narrow) 14f else 22f), if (narrow) 8f else 11f, weaponColor, alignRight = true, weight = FontWeight.Bold)
    val nextMastery = engine.nextWeaponMastery
    val masteryLabel = if (nextMastery == null) {
        "LV ${engine.weaponLevel} // ${engine.currentWeaponMastery.displayLabel.uppercase()}"
    } else {
        "LV ${engine.weaponLevel} // ${engine.currentWeaponMastery.displayLabel.uppercase()} > ${nextMastery.displayLabel.uppercase()} ${nextMastery.minimumLevel}"
    }
    drawLabel(textMeasurer, masteryLabel, right, d(if (narrow) 34f else 44f), if (narrow) 6f else 8f, White, alignRight = true)
    val masteryWidth = d(if (narrow) 120f else 170f)
    drawBar(right - masteryWidth, d(if (narrow) 53f else 63f), masteryWidth, d(4f), engine.weaponMasteryProgress, weaponColor, DarkLine)
    drawLabel(textMeasurer, "${VelocityNames[engine.velocityTier.coerceIn(VelocityNames.indices)]} // ${formatCompact(engine.speed.toLong())} u/s", right, d(if (narrow) 64f else 77f), if (narrow) 8f else 10f, White, alignRight = true)
    drawLabel(textMeasurer, "MASS ${formatOneDecimal(engine.mass)}  COMBO x${max(1, engine.combo)}", right, d(if (narrow) 83f else 98f), if (narrow) 7f else 9f, Muted, alignRight = true)
    drawLabel(textMeasurer, "ITEMS ${engine.acquiredItemCount}  MATTER ${formatCompact(engine.runMatter)}", right, d(if (narrow) 101f else 119f), if (narrow) 7f else 9f, Acid, alignRight = true)
    engine.recentItem?.let { item ->
        val recentY = d(if (narrow) 119f else 140f)
        val accent = rarityColor(item.rarity)
        drawLabel(textMeasurer, "+ ${item.name.uppercase()}", right - d(20f), recentY, 7f, accent, alignRight = true)
        drawItemIcon(
            item = item,
            center = Offset(right - d(8f), recentY + d(7f)),
            radius = d(7f),
            accent = accent,
            stack = engine.itemStack(item.id),
        )
    }
    drawRelicMatrix(engine, textMeasurer, narrow, right)

    if (engine.combo >= 2 && engine.comboTime > 0f) {
        val comboProgress = clamp(engine.comboTime / max(0.001f, engine.comboWindow), 0f, 1f)
        val comboPulse = (sin(engine.elapsed * 12f) + 1f) * 0.5f
        val comboY = size.height - d(if (narrow) 112f else 88f)
        val comboWidth = d(118f)
        drawLabel(
            textMeasurer,
            "CHAIN x${engine.combo}",
            size.width * 0.5f,
            comboY,
            13f + min(5f, engine.combo * 0.12f) + comboPulse,
            if (engine.velocityTier >= 2) Acid else Cyan,
            centered = true,
            weight = FontWeight.Bold,
        )
        drawBar(size.width * 0.5f - comboWidth * 0.5f, comboY + d(25f), comboWidth, d(3f), comboProgress, Magenta, DarkLine)
    }

    val danger = engine.tetherDistance < 75f
    if (danger && engine.runGrace <= 0f) {
        drawLabel(textMeasurer, "SINGULARITY PROXIMITY", size.width * 0.5f, size.height - d(52f), 12f, Red, centered = true, weight = FontWeight.Bold)
    }
    if (engine.messageTime > 0f) {
        val alpha = clamp(engine.messageTime / 0.45f, 0f, 1f)
        val messageY = size.height * if (narrow) 0.34f else 0.23f
        drawLabel(textMeasurer, engine.message, size.width * 0.5f, messageY, 19f, if (engine.message == "OVERHEAT") Red else White, centered = true, weight = FontWeight.Bold, alpha = alpha)
    }
    if (engine.lastImpactTime > 0f && engine.phase == GamePhase.RUNNING) {
        val alpha = clamp(engine.lastImpactTime / 0.22f, 0f, 1f)
        drawLabel(textMeasurer, "IMPACT // ${engine.lastImpact.toInt()}", d(28f), size.height - d(45f), 12f, Acid, weight = FontWeight.Bold, alpha = alpha)
        drawRect(Acid.copy(alpha = alpha * 0.65f), Offset(d(28f), size.height - d(21f)), Size(d(105f) * alpha, d(2f)))
    }
    drawControls(engine, textMeasurer)
}

private fun DrawScope.drawRelicMatrix(engine: GameEngine, textMeasurer: TextMeasurer, narrow: Boolean, right: Float) {
    val slotSize = d(if (narrow) 31f else 36f)
    val gap = d(6f)
    val totalWidth = slotSize * RelicCatalog.MAX_SLOTS + gap * (RelicCatalog.MAX_SLOTS - 1)
    val startX = right - totalWidth
    val top = d(if (narrow) 156f else 174f)
    drawLabel(
        textMeasurer,
        "RELIC MATRIX ${engine.equippedRelics.size}/${RelicCatalog.MAX_SLOTS}",
        right,
        top - d(15f),
        7f,
        if (engine.equippedRelics.isEmpty()) Muted else Gold,
        alignRight = true,
        weight = FontWeight.Bold,
    )
    repeat(RelicCatalog.MAX_SLOTS) { index ->
        val left = startX + index * (slotSize + gap)
        val center = Offset(left + slotSize * 0.5f, top + slotSize * 0.5f)
        val equipped = engine.equippedRelics.getOrNull(index)
        val accent = equipped?.let { relicAspectColor(RelicCatalog.byId(it.id).aspect) } ?: DarkLine
        drawRect(Color(0xB00B0D1D), Offset(left, top), Size(slotSize, slotSize))
        drawRect(accent.copy(alpha = if (equipped == null) 0.7f else 0.95f), Offset(left, top), Size(slotSize, slotSize), style = Stroke(d(1f)))
        drawLabel(textMeasurer, "${index + 1}", left + d(3f), top + d(2f), 5f, if (equipped == null) Muted else accent, weight = FontWeight.Bold)
        if (equipped == null) {
            drawCircle(DarkLine.copy(alpha = 0.46f), slotSize * 0.18f, center, style = Stroke(d(1f)))
            drawLine(DarkLine, Offset(center.x - slotSize * 0.09f, center.y), Offset(center.x + slotSize * 0.09f, center.y), d(1f))
        } else {
            drawRelicIcon(
                id = equipped.id,
                center = center,
                radius = slotSize * 0.31f,
                rank = equipped.rank,
                time = engine.elapsed,
            )
        }
    }
}

private fun DrawScope.drawControls(engine: GameEngine, textMeasurer: TextMeasurer) {
    val dash = Offset(size.width - d(82f), size.height - d(88f))
    val brake = Offset(size.width - d(190f), size.height - d(67f))
    val dashColor = if (engine.overheated) Red else Cyan
    val dashPulse = (sin(engine.elapsed * 8f) + 1f) * 0.5f
    if (engine.dashReady) drawCircle(Cyan.copy(alpha = 0.045f + dashPulse * 0.05f), d(58f + dashPulse * 4f), dash)
    drawCircle(dashColor.copy(alpha = if (engine.overheated) 0.06f else 0.13f + dashPulse * 0.03f), d(49f), dash)
    drawCircle(dashColor.copy(alpha = 0.75f), d(48f), dash, style = Stroke(d(1.5f)))
    drawArc(
        dashColor.copy(alpha = 0.9f),
        -90f,
        360f * clamp(1f - engine.heat / GameEngine.MAX_HEAT, 0f, 1f),
        false,
        Offset(dash.x - d(54f), dash.y - d(54f)),
        Size(d(108f), d(108f)),
        style = Stroke(d(2f), cap = StrokeCap.Round),
    )
    drawLabel(textMeasurer, "DASH", dash.x, dash.y - d(12f), 12f, dashColor, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (engine.overheated) "OFFLINE" else if (engine.dashReady) "READY" else "COOLING", dash.x, dash.y + d(11f), 7f, if (engine.dashReady) White else Muted, centered = true)
    drawCircle(Violet.copy(alpha = if (engine.braking) 0.22f else 0.08f), d(39f), brake)
    drawCircle(Violet.copy(alpha = 0.65f), d(38f), brake, style = Stroke(d(1.2f)))
    drawLabel(textMeasurer, "BRAKE", brake.x, brake.y - d(10f), 9f, Violet, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "SHIFT", brake.x, brake.y + d(9f), 7f, Muted, centered = true)
}

private fun DrawScope.drawMenu(engine: GameEngine, textMeasurer: TextMeasurer, renderTime: Float) {
    val narrow = size.width / density < 700f
    val titleSize = if (narrow) 37f else 62f
    val titleCenter = Offset(size.width * 0.5f, size.height * 0.245f)
    val orbitRadius = min(d(if (narrow) 118f else 175f), size.width * 0.29f)
    val orbitAngle = renderTime * 0.42f
    val orbitPoint = polar(titleCenter, orbitRadius, orbitAngle)
    val counterPoint = polar(titleCenter, orbitRadius * 0.72f, orbitAngle + PI.toFloat())
    drawCircle(Violet.copy(alpha = 0.055f), orbitRadius, titleCenter)
    drawCircle(Cyan.copy(alpha = 0.24f), orbitRadius, titleCenter, style = Stroke(1f, pathEffect = dashEffect))
    drawLine(Violet.copy(alpha = 0.16f), orbitPoint, counterPoint, 1f, pathEffect = dashEffect)
    drawCircle(Magenta.copy(alpha = 0.13f), d(24f), orbitPoint)
    drawCircle(VoidBlack, d(8f), orbitPoint)
    drawCircle(Magenta, d(11f), orbitPoint, style = Stroke(d(1.5f)))
    drawCircle(Cyan.copy(alpha = 0.12f), d(22f), counterPoint)
    drawCircle(White, d(8f), counterPoint)
    drawLabel(textMeasurer, "KINETIC", size.width * 0.5f, size.height * 0.16f, titleSize, White, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "VOID", size.width * 0.5f, size.height * 0.16f + d(titleSize * 1.12f), titleSize, Cyan, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "YOUR MOVEMENT IS THE WEAPON. YOUR CURSOR IS THE THREAT.", size.width * 0.5f, size.height * 0.37f, if (narrow) 9f else 12f, Muted, centered = true)
    drawLine(Violet.copy(alpha = 0.35f), Offset(size.width * 0.25f, size.height * 0.41f), Offset(size.width * 0.75f, size.height * 0.41f), 1f, pathEffect = dashEffect)
    drawLabel(textMeasurer, "LEAD THE CORE  //  BUILD MOMENTUM  //  NEVER TOUCH THE SINGULARITY", size.width * 0.5f, size.height * 0.45f, if (narrow) 8f else 10f, White, centered = true)
    drawLabel(textMeasurer, "SELECT CORE", size.width * 0.5f, size.height * 0.51f, 10f, Muted, centered = true)

    val centers = listOf(size.width * 0.5f - d(130f), size.width * 0.5f, size.width * 0.5f + d(130f))
    val shapes = CoreShape.entries
    shapes.forEachIndexed { index, shape ->
        val cardCenter = Offset(centers[index], size.height * 0.62f)
        val selected = engine.coreShape == shape
        val price = when (shape) { CoreShape.ORB -> 0L; CoreShape.PRISM -> 25L; CoreShape.SHARD -> 90L }
        val unlocked = engine.isCoreShapeUnlocked(shape)
        drawRect(if (selected) CyanSoft else Color(0x88101225), Offset(cardCenter.x - d(60f), cardCenter.y - d(55f)), Size(d(120f), d(110f)))
        drawRect(if (selected) Cyan else DarkLine, Offset(cardCenter.x - d(60f), cardCenter.y - d(55f)), Size(d(120f), d(110f)), style = Stroke(d(if (selected) 2f else 1f)))
        when (shape) {
            CoreShape.ORB -> drawCircle(if (unlocked) Cyan else Muted, d(13f), Offset(cardCenter.x, cardCenter.y - d(12f)))
            CoreShape.PRISM -> drawPolygon(Offset(cardCenter.x, cardCenter.y - d(12f)), d(17f), 4, (PI / 4).toFloat(), if (unlocked) Violet else Muted, Fill)
            CoreShape.SHARD -> drawPolygon(Offset(cardCenter.x, cardCenter.y - d(12f)), d(18f), 3, -(PI / 2).toFloat(), if (unlocked) Magenta else Muted, Fill)
        }
        drawLabel(textMeasurer, shape.name, cardCenter.x, cardCenter.y + d(17f), 9f, if (selected) White else Muted, centered = true, weight = FontWeight.Bold)
        if (!unlocked) drawLabel(textMeasurer, "${formatCompact(price)} LIFETIME", cardCenter.x, cardCenter.y + d(34f), 7f, Orange, centered = true)
    }

    val buttonY = size.height * 0.78f
    drawRect(Cyan.copy(alpha = 0.12f), Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)))
    drawRect(Cyan, Offset(size.width * 0.5f - d(150f), buttonY - d(31f)), Size(d(300f), d(62f)), style = Stroke(d(2f)))
    drawLabel(textMeasurer, "ENTER THE VOID", size.width * 0.5f, buttonY - d(12f), 15f, White, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "CLICK / TAP / ENTER", size.width * 0.5f, buttonY + d(14f), 8f, Cyan, centered = true)
    val navY = size.height * 0.9f
    val spacing = min(d(132f), size.width * 0.19f)
    val navStart = size.width * 0.5f - spacing * (MenuNavLabels.lastIndex * 0.5f)
    MenuNavLabels.forEachIndexed { index, label ->
        val centerX = navStart + spacing * index
        val accent = when (index) {
            0 -> Violet
            2 -> if (engine.canRebirth) Acid else Orange
            else -> DarkLine
        }
        val labelColor = when (index) {
            0 -> Violet
            2 -> if (engine.canRebirth) Acid else Orange
            else -> Muted
        }
        drawRect(Color(0x99101225), Offset(centerX - spacing * 0.44f, navY - d(20f)), Size(spacing * 0.88f, d(40f)))
        drawRect(accent, Offset(centerX - spacing * 0.44f, navY - d(20f)), Size(spacing * 0.88f, d(40f)), style = Stroke(d(1f)))
        drawLabel(textMeasurer, label, centerX, navY - d(5f), if (narrow) 6f else 8f, labelColor, centered = true, weight = FontWeight.Bold)
    }
    drawLabel(textMeasurer, "VOID MATTER ${formatCompact(engine.totalMatter)} // REBIRTH ${engine.rebirthLevel}", d(20f), d(20f), 9f, Acid)
    drawLabel(textMeasurer, "DISCOVERED ${engine.discoveredItemCount}/400  //  WEAPONS ${engine.unlockedWeapons.size}/${WeaponCatalog.all.size}", d(20f), d(39f), 7f, Muted)
    drawLabel(textMeasurer, "DIRECTIVE ${engine.rebirthProfile.directive.displayName.uppercase()}", d(20f), d(56f), 7f, Orange)
}

private fun DrawScope.drawChoice(engine: GameEngine, textMeasurer: TextMeasurer, renderTime: Float) {
    drawRect(Color(0xF2050610))
    val bindAction = engine.choices.firstOrNull()?.relicAction
    val title = when (engine.choiceType) {
        ChoiceType.ITEM -> "CHOOSE AN ARTIFACT"
        ChoiceType.TOTEM -> "TOTEM RESONANCE"
        ChoiceType.WEAPON -> "WEAPON SYNCHRONIZATION"
        ChoiceType.RELIC -> "VOID RELIC INTERCEPT"
        ChoiceType.RELIC_BIND -> if (bindAction == RelicChoiceAction.MELD_TARGET) "RELIC MELD" else "RELIC REBIND"
    }
    val subtitle = when (engine.choiceType) {
        ChoiceType.ITEM -> "TIME IS SUSPENDED"
        ChoiceType.TOTEM -> "AMPLIFY THE CURRENT SYSTEM OR RECALIBRATE"
        ChoiceType.WEAPON -> "SELECT THE NEXT RUN WEAPON"
        ChoiceType.RELIC -> if (engine.equippedRelics.size >= RelicCatalog.MAX_SLOTS) {
            "MATRIX FULL // CLAIM A SIGNAL OR MELD IT INTO THE MATRIX"
        } else {
            "ELITE SIGNAL CAPTURED // RELICS SYNCHRONIZE WITH EVERY WEAPON"
        }
        ChoiceType.RELIC_BIND -> if (bindAction == RelicChoiceAction.MELD_TARGET) {
            "SELECT A TARGET // THE CHOSEN RELIC GAINS ONE RANK"
        } else {
            "SELECT A SLOT // ITS CURRENT RELIC WILL BE REPLACED"
        }
    }
    val titleAccent = if (engine.choiceType == ChoiceType.RELIC || engine.choiceType == ChoiceType.RELIC_BIND) Gold else White
    val subtitleAccent = if (engine.choiceType == ChoiceType.RELIC || engine.choiceType == ChoiceType.RELIC_BIND) Magenta else Violet
    drawLabel(textMeasurer, title, size.width * 0.5f, size.height * 0.14f, 24f, titleAccent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, subtitle, size.width * 0.5f, size.height * 0.17f + d(36f), 9f, subtitleAccent, centered = true, maxWidth = size.width - d(30f), maxLines = 2)
    val choiceCount = engine.choices.size.coerceAtLeast(1)
    val gap = d(if (choiceCount >= 4) 10f else 18f)
    val maxCardWidth = d(when {
        choiceCount >= 4 -> 190f
        choiceCount == 3 -> 250f
        else -> 300f
    })
    val availableCardWidth = (size.width - d(30f) - gap * (choiceCount - 1)) / choiceCount
    val cardWidth = min(maxCardWidth, availableCardWidth).coerceAtLeast(d(92f))
    val total = cardWidth * choiceCount + gap * (choiceCount - 1)
    val startX = (size.width - total) * 0.5f
    val top = size.height * if (choiceCount >= 4) 0.29f else 0.31f
    val bottomReserve = d(if (engine.choicesCanReroll) 105f else 35f)
    val cardHeight = min(d(270f), size.height - bottomReserve - top).coerceAtLeast(d(170f))
    engine.choices.forEachIndexed { index, choice ->
        drawChoiceCard(engine, textMeasurer, choice, index, startX + index * (cardWidth + gap), top, cardWidth, cardHeight, renderTime)
    }
    if (engine.choicesCanReroll) {
        val rerollY = size.height - d(72f)
        val accent = if (engine.choiceType == ChoiceType.RELIC) Gold else Violet
        drawRect(accent.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(90f), rerollY - d(22f)), Size(d(180f), d(44f)))
        drawRect(accent, Offset(size.width * 0.5f - d(90f), rerollY - d(22f)), Size(d(180f), d(44f)), style = Stroke(d(1.3f)))
        drawLabel(textMeasurer, "REROLL [Q] // ${engine.rerollsRemaining}", size.width * 0.5f, rerollY - d(6f), 9f, accent, centered = true, weight = FontWeight.Bold)
    }
}

private fun DrawScope.drawChoiceCard(engine: GameEngine, textMeasurer: TextMeasurer, choice: ChoiceOption, index: Int, x: Float, y: Float, width: Float, height: Float, renderTime: Float) {
    if (choice.type == ChoiceType.RELIC || choice.type == ChoiceType.RELIC_BIND) {
        drawRelicChoiceCard(engine, textMeasurer, choice, index, x, y, width, height, renderTime)
        return
    }
    val item = choice.itemId?.let(ItemCatalog::byId)
    val weapon = choice.weaponId?.let(WeaponCatalog::byId)
    val accent = item?.let { rarityColor(it.rarity) } ?: weapon?.let { weaponColor(it.id) } ?: ParticleColors[index.coerceIn(0, 2)]
    drawRect(VoidPanel, Offset(x, y), Size(width, height))
    val pulse = (sin(renderTime * 2.4f + index * 1.6f) + 1f) * 0.5f
    drawRect(accent.copy(alpha = 0.72f + pulse * 0.2f), Offset(x, y), Size(width, height), style = Stroke(d(1.5f + pulse * 0.5f)))
    drawRect(accent.copy(alpha = 0.16f), Offset(x, y), Size(width, d(43f)))
    val tag = if (choice.type == ChoiceType.TOTEM) choice.tag else weapon?.tags?.joinToString(" / ") ?: choice.tag
    drawLabel(textMeasurer, "0${index + 1} // $tag", x + d(16f), y + d(15f), 8f, accent, weight = FontWeight.Bold)
    val glyphCenter = Offset(x + width * 0.5f, y + d(91f))
    when {
        weapon != null -> drawWeaponGlyph(weapon.id, glyphCenter, d(25f), renderTime, accent)
        item != null -> drawItemIcon(
            item = item,
            center = glyphCenter,
            radius = d(26f),
            accent = accent,
            stack = engine.itemStack(item.id) + 1,
        )
        choice.type == ChoiceType.TOTEM -> {
            drawCircle(accent.copy(alpha = 0.16f), d(28f), glyphCenter)
            drawCircle(accent, d(24f), glyphCenter, style = Stroke(d(2f), pathEffect = dashEffect))
            drawPolygon(glyphCenter, d(13f), 6, renderTime * 0.8f, White, Stroke(d(1.6f)))
            drawLine(accent, Offset(glyphCenter.x - d(19f), glyphCenter.y), Offset(glyphCenter.x + d(19f), glyphCenter.y), d(2f), StrokeCap.Round)
        }
        else -> drawPolygon(glyphCenter, d(25f), index + 4, renderTime * 0.32f + index * 0.7f, accent, Stroke(d(2f)))
    }
    drawLabel(textMeasurer, choice.title.uppercase(), x + width * 0.5f, y + d(132f), if (width / density < 200f) 10f else 12f, White, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, choice.description, x + width * 0.5f, y + d(159f), if (width / density < 200f) 7f else 8f, Muted, centered = true, maxWidth = width - d(28f), maxLines = 3)
    val footer = when {
        item != null -> "STACK ${engine.itemStack(item.id) + 1}/${item.maxStacks}"
        choice.type == ChoiceType.TOTEM && choice.totemAction == TotemAction.AMPLIFY_CURRENT ->
            "CURRENT WEAPON // LV ${engine.weaponLevel} > ${engine.weaponLevel + 1}"
        choice.type == ChoiceType.TOTEM -> "OPEN WEAPON PICKER"
        weapon != null -> "RUN WEAPON // ${weapon.tags.first()}"
        else -> choice.tag
    }
    drawLabel(textMeasurer, footer, x + width * 0.5f, y + d(191f), 8f, accent, centered = true)
    drawLabel(textMeasurer, "SELECT [${index + 1}]", x + width * 0.5f, y + height - d(30f), 9f, accent, centered = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawRelicChoiceCard(
    engine: GameEngine,
    textMeasurer: TextMeasurer,
    choice: ChoiceOption,
    index: Int,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    renderTime: Float,
) {
    val slotRelic = choice.relicSlot?.let(engine.equippedRelics::getOrNull)
    val optionRelicId = choice.relicId ?: slotRelic?.id
    val displayRelicId = if (choice.relicAction == RelicChoiceAction.REPLACE) slotRelic?.id ?: optionRelicId else optionRelicId
    val relic = displayRelicId?.let(RelicCatalog::byId)
    val replacementRelic = if (choice.relicAction == RelicChoiceAction.REPLACE) choice.relicId?.let(RelicCatalog::byId) else null
    val accent = relic?.let { relicAspectColor(it.aspect) } ?: Gold
    val pulse = (sin(renderTime * 2.1f + index * 1.45f) + 1f) * 0.5f
    drawRect(VoidPanel, Offset(x, y), Size(width, height))
    drawRect(accent.copy(alpha = 0.72f + pulse * 0.2f), Offset(x, y), Size(width, height), style = Stroke(d(1.4f + pulse * 0.45f)))
    drawRect(accent.copy(alpha = 0.13f), Offset(x, y), Size(width, d(39f)))

    val slotLabel = choice.relicSlot?.let { "SLOT ${it + 1}" }
    val tag = when {
        choice.tag.isNotBlank() && (slotLabel == null || choice.tag.contains(slotLabel)) -> choice.tag
        else -> listOfNotNull(slotLabel, choice.tag.takeIf { it.isNotBlank() }).joinToString(" // ")
    }
    drawLabel(
        textMeasurer,
        "0${index + 1} // $tag",
        x + d(10f),
        y + d(13f),
        if (width / density < 175f) 6f else 7f,
        accent,
        weight = FontWeight.Bold,
        maxWidth = width - d(20f),
    )

    val glyphCenter = Offset(x + width * 0.5f, y + d(78f))
    val previewRank = when (choice.relicAction) {
        RelicChoiceAction.ACQUIRE -> optionRelicId?.let { (engine.relicRank(it) + 1).coerceIn(1, RelicCatalog.MAX_RANK) }
        RelicChoiceAction.REPLACE -> slotRelic?.rank
        RelicChoiceAction.MELD_TARGET -> slotRelic?.rank?.plus(1)?.coerceAtMost(RelicCatalog.MAX_RANK)
        RelicChoiceAction.MELD, null -> null
    }
    if (displayRelicId != null) {
        drawRelicIcon(displayRelicId, glyphCenter, d(if (width / density < 175f) 21f else 24f), previewRank, renderTime)
        replacementRelic?.let { incoming ->
            val incomingCenter = Offset(glyphCenter.x + d(23f), glyphCenter.y + d(13f))
            drawCircle(VoidBlack.copy(alpha = 0.92f), d(12f), incomingCenter)
            drawRelicIcon(incoming.id, incomingCenter, d(9f), rank = 1, time = renderTime)
            drawLabel(textMeasurer, "→", glyphCenter.x + d(13f), glyphCenter.y - d(2f), 7f, Gold, centered = true, weight = FontWeight.Bold)
        }
    } else {
        drawUnresolvedRelicIcon(glyphCenter, d(23f), renderTime)
        drawLabel(textMeasurer, "+", glyphCenter.x + d(23f), glyphCenter.y - d(9f), 12f, White, centered = true, weight = FontWeight.Bold)
    }

    drawLabel(
        textMeasurer,
        choice.title.uppercase(),
        x + width * 0.5f,
        y + d(112f),
        if (width / density < 175f) 8f else 10f,
        White,
        centered = true,
        weight = FontWeight.Bold,
        maxWidth = width - d(18f),
        maxLines = 2,
    )
    drawLabel(
        textMeasurer,
        choice.description,
        x + width * 0.5f,
        y + d(143f),
        if (width / density < 175f) 6f else 7f,
        Muted,
        centered = true,
        maxWidth = width - d(20f),
        maxLines = 3,
    )
    relic?.let {
        drawLabel(
            textMeasurer,
            it.rankEffect,
            x + width * 0.5f,
            y + height - d(76f),
            if (width / density < 175f) 5.5f else 6.5f,
            accent,
            centered = true,
            maxWidth = width - d(20f),
            maxLines = 2,
        )
    }
    val actionLabel = when (choice.relicAction) {
        RelicChoiceAction.ACQUIRE -> when {
            optionRelicId != null && engine.relicRank(optionRelicId) >= RelicCatalog.MAX_RANK -> "SALVAGE RESONANCE"
            optionRelicId != null && engine.relicRank(optionRelicId) > 0 ->
                "MELD // R${engine.relicRank(optionRelicId)} > R${previewRank ?: engine.relicRank(optionRelicId)}"
            else -> "BIND TO MATRIX"
        }
        RelicChoiceAction.MELD -> "MELD SIGNAL INTO A SLOT"
        RelicChoiceAction.REPLACE -> "REPLACE SLOT ${(choice.relicSlot ?: index) + 1}"
        RelicChoiceAction.MELD_TARGET -> if ((slotRelic?.rank ?: 1) >= RelicCatalog.MAX_RANK) {
            "SALVAGE EXCESS"
        } else {
            "MELD // R${slotRelic?.rank ?: 1} > R${previewRank ?: slotRelic?.rank ?: 1}"
        }
        null -> choice.tag
    }
    drawLabel(textMeasurer, actionLabel, x + width * 0.5f, y + height - d(45f), if (width / density < 175f) 6f else 7f, accent, centered = true, weight = FontWeight.Bold, maxWidth = width - d(14f))
    drawLabel(textMeasurer, "SELECT [${index + 1}]", x + width * 0.5f, y + height - d(24f), 8f, accent, centered = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawPause(textMeasurer: TextMeasurer) {
    drawRect(Color(0xC9050610))
    drawLabel(textMeasurer, "SYSTEM PAUSED", size.width * 0.5f, size.height * 0.30f, 28f, White, centered = true, weight = FontWeight.Bold)
    drawPauseButton(textMeasurer, "RESUME [P / ESC]", size.height * 0.5f, Cyan)
    drawPauseButton(textMeasurer, "SETTINGS [S]", size.height * 0.62f, Violet)
    drawPauseButton(textMeasurer, "RETURN TO MENU", size.height * 0.74f, Red)
}

private fun DrawScope.drawPauseButton(textMeasurer: TextMeasurer, label: String, top: Float, accent: Color) {
    val left = size.width * 0.5f - d(150f)
    drawRect(accent.copy(alpha = 0.1f), Offset(left, top), Size(d(300f), d(52f)))
    drawRect(accent, Offset(left, top), Size(d(300f), d(52f)), style = Stroke(d(1.5f)))
    drawLabel(textMeasurer, label, size.width * 0.5f, top + d(17f), 11f, accent, centered = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawEnd(engine: GameEngine, textMeasurer: TextMeasurer, victory: Boolean) {
    drawRect(Color(0xDE050610))
    val color = if (victory) Acid else Red
    drawLabel(textMeasurer, if (victory) "VOID CONQUERED" else engine.message, size.width * 0.5f, size.height * 0.25f, if (size.width / density < 700f) 28f else 42f, color, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (victory) "THE ARCHITECT HAS FALLEN" else "THE SINGULARITY REMEMBERS", size.width * 0.5f, size.height * 0.36f, 10f, Muted, centered = true)
    val statY = size.height * 0.47f
    drawLabel(textMeasurer, "TIME ${formatRunTime(engine.elapsed)}", size.width * 0.5f - d(165f), statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "KILLS ${engine.kills}", size.width * 0.5f, statY, 13f, White, centered = true)
    drawLabel(textMeasurer, "MATTER ${formatCompact(engine.runMatter)}", size.width * 0.5f + d(165f), statY, 13f, Acid, centered = true)
    drawLabel(textMeasurer, "WEAPON ${engine.currentWeaponDefinition.name.uppercase()} // LV ${engine.weaponLevel}", size.width * 0.5f, statY + d(38f), 10f, weaponColor(engine.weapon), centered = true)
    drawLabel(textMeasurer, "ITEMS ${engine.acquiredItemCount}   DISCOVERIES ${engine.discoveredItemCount}/400   PEAK ${VelocityNames[engine.velocityTier.coerceIn(VelocityNames.indices)]}", size.width * 0.5f, statY + d(64f), 9f, Muted, centered = true)
    val buttonY = size.height * 0.72f
    drawRect(color.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(155f), buttonY - d(38f)), Size(d(310f), d(76f)))
    drawRect(color, Offset(size.width * 0.5f - d(155f), buttonY - d(38f)), Size(d(310f), d(76f)), style = Stroke(d(2f)))
    drawLabel(textMeasurer, "RE-ENTER [R]", size.width * 0.5f, buttonY - d(10f), 15f, White, centered = true, weight = FontWeight.Bold)
    if (victory) {
        val rebirthTop = buttonY + d(50f)
        val rebirthAccent = if (engine.canRebirth) Acid else Muted
        val rebirthLabel = if (engine.nextRebirthProfile.tier > engine.rebirthLevel) {
            "REBIRTH [B] // TIER ${engine.nextRebirthProfile.tier}"
        } else {
            "MAXIMUM REBIRTH TIER"
        }
        drawRect(rebirthAccent.copy(alpha = 0.1f), Offset(size.width * 0.5f - d(120f), rebirthTop), Size(d(240f), d(40f)))
        drawRect(rebirthAccent, Offset(size.width * 0.5f - d(120f), rebirthTop), Size(d(240f), d(40f)), style = Stroke(d(1.4f)))
        drawLabel(textMeasurer, rebirthLabel, size.width * 0.5f, rebirthTop + d(12f), 9f, rebirthAccent, centered = true, weight = FontWeight.Bold)
    }
    val menuHintY = buttonY + d(if (victory) 104f else 65f)
    drawLabel(textMeasurer, "TAP BELOW FOR CORE SELECT // BANK ${formatCompact(engine.totalMatter)}", size.width * 0.5f, menuHintY, 8f, Muted, centered = true)
}

private fun DrawScope.drawSettings(engine: GameEngine, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds(640f, 620f)
    drawOverlayFrame(bounds, Violet)
    drawLabel(textMeasurer, "SYSTEM SETTINGS", bounds.left + d(24f), bounds.top + d(24f), 19f, White, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "− / + ADJUST // DAMAGE HEAT: YELLOW > RED", bounds.right - d(24f), bounds.top + d(29f), 7f, Muted, alignRight = true)

    val startY = bounds.top + d(72f)
    val settingsBottom = bounds.bottom - d(64f)
    val availableHeight = settingsBottom - startY
    val rowsPerPage = settingsRowsPerPage(availableHeight, density)
    val maxPage = SettingsRow.entries.lastIndex / rowsPerPage
    val page = engine.settingsPage.coerceIn(0, maxPage)
    val pageStart = page * rowsPerPage
    val visibleRows = SettingsRow.entries.subList(
        pageStart,
        min(pageStart + rowsPerPage, SettingsRow.entries.size),
    )
    val spacing = min(d(48f), availableHeight / visibleRows.size)
    visibleRows.forEachIndexed { index, row ->
        val top = startY + spacing * index
        val rowHeight = spacing - d(4f)
        val controlLeft = bounds.right - d(190f)
        val controlRight = bounds.right - d(20f)
        val controlTop = top + d(4f)
        val controlHeight = rowHeight - d(8f)
        val labelY = top + max(0f, (rowHeight - d(9f * textMeasurer.scale)) * 0.5f)
        val valueY = top + max(0f, (rowHeight - d(8f * textMeasurer.scale)) * 0.5f)
        val buttonY = top + max(0f, (rowHeight - d(14f * textMeasurer.scale)) * 0.5f)
        val value = settingValue(engine, row)
        drawRect(Color(0x66101225), Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight))
        drawRect(DarkLine, Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), rowHeight), style = Stroke(d(1f)))
        drawLabel(textMeasurer, SettingsLabels[row.ordinal], bounds.left + d(35f), labelY, 9f, White, weight = FontWeight.Bold)
        if (row == SettingsRow.DAMAGE_COLOR_THRESHOLDS) {
            DamageNumberColors.forEachIndexed { colorIndex, color ->
                drawCircle(
                    color = color,
                    radius = d(3.5f),
                    center = Offset(controlLeft - d(65f) + d(15f) * colorIndex, top + rowHeight * 0.5f),
                )
            }
        }
        drawRect(Violet.copy(alpha = 0.08f), Offset(controlLeft, controlTop), Size(controlRight - controlLeft, controlHeight))
        drawLine(DarkLine, Offset(controlLeft + d(42f), controlTop), Offset(controlLeft + d(42f), controlTop + controlHeight), d(1f))
        drawLine(DarkLine, Offset(controlRight - d(42f), controlTop), Offset(controlRight - d(42f), controlTop + controlHeight), d(1f))
        drawLabel(textMeasurer, "−", controlLeft + d(21f), buttonY, 14f, Violet, centered = true, weight = FontWeight.Bold)
        drawLabel(textMeasurer, "+", controlRight - d(21f), buttonY, 14f, Violet, centered = true, weight = FontWeight.Bold)
        val valueColor = when {
            value == "OFF" -> Red
            row == SettingsRow.DAMAGE_COLOR_THRESHOLDS -> Orange
            else -> Cyan
        }
        drawLabel(textMeasurer, value, (controlLeft + controlRight) * 0.5f, valueY, 8f, valueColor, centered = true, weight = FontWeight.Bold)
    }
    if (maxPage > 0) {
        drawPagedFooter(textMeasurer, bounds, page, maxPage, Violet)
    } else {
        drawFooterBack(textMeasurer, bounds, Violet)
    }
}

private fun DrawScope.settingValue(engine: GameEngine, row: SettingsRow): String = when (row) {
    SettingsRow.SFX -> if (engine.settings.soundEnabled) "ON" else "OFF"
    SettingsRow.MUSIC -> if (engine.settings.musicEnabled) "ON" else "OFF"
    SettingsRow.MASTER_VOLUME -> "${(engine.settings.masterVolume * 100f).roundToInt()}%"
    SettingsRow.SIMULATION_SPEED -> formatMultiplier(engine.settings.simulationSpeed)
    SettingsRow.TEXT_SIZE -> "${(engine.settings.textScale * 100f).roundToInt()}%"
    SettingsRow.SCREEN_SHAKE -> if (engine.settings.screenShake) "ON" else "OFF"
    SettingsRow.PARTICLES -> engine.settings.particleDensity.name
    SettingsRow.DAMAGE_NUMBERS -> if (engine.settings.damageNumbers) "ON" else "OFF"
    SettingsRow.DAMAGE_NUMBER_SIZE -> engine.settings.damageNumberSize.name
    SettingsRow.DAMAGE_NUMBER_FORMAT -> engine.settings.damageNumberFormat.name
    SettingsRow.DAMAGE_COLOR_THRESHOLDS -> {
        val first = engine.settings.damageNumberTierThreshold.toLong()
        val second = first * DAMAGE_NUMBER_POWERFUL_MULTIPLIER
        val third = first * DAMAGE_NUMBER_DEVASTATING_MULTIPLIER
        "${abbreviateNumber(first)}/${abbreviateNumber(second)}/${abbreviateNumber(third)}"
    }
}

private fun DrawScope.drawLab(engine: GameEngine, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Acid)
    drawLabel(textMeasurer, "VOID LAB", bounds.left + d(25f), bounds.top + d(24f), 20f, Acid, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "PERMANENT RESEARCH // MATTER ${formatCompact(engine.totalMatter)}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val contentTop = bounds.top + d(88f)
    val contentWidth = bounds.width - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = d(105f)
    MetaUpgradeCatalog.all.forEachIndexed { index, definition ->
        val column = index % 2
        val row = index / 2
        val left = bounds.left + d(25f) + columnWidth * column
        val top = contentTop + rowHeight * row
        drawMetaCard(engine, textMeasurer, definition, left, top, columnWidth, rowHeight)
    }
    drawLabFooter(textMeasurer, bounds, Acid)
}

private fun DrawScope.drawMetaCard(engine: GameEngine, textMeasurer: TextMeasurer, definition: MetaUpgradeDefinition, x: Float, y: Float, width: Float, height: Float) {
    val level = engine.metaLevel(definition.id)
    val maxed = level >= definition.maxRanks
    val cost = if (maxed) 0L else definition.cost(level).toLong()
    val affordable = !maxed && engine.totalMatter >= cost
    val accent = if (maxed) Acid else if (affordable) Cyan else Muted
    drawRect(Color(0x99101225), Offset(x, y), Size(width, height))
    drawRect(accent.copy(alpha = 0.7f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, definition.name.uppercase(), x + d(14f), y + d(12f), 9f, accent, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "RANK $level/${definition.maxRanks}", x + width - d(14f), y + d(12f), 8f, White, alignRight = true)
    drawLabel(textMeasurer, definition.description, x + d(14f), y + d(36f), 7f, Muted, maxWidth = width - d(28f), maxLines = 2)
    drawLabel(textMeasurer, if (maxed) "MAXIMUM SYNCHRONY" else "BUY ${formatCompact(cost)} MATTER", x + width - d(14f), y + height - d(24f), 8f, accent, alignRight = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawRebirth(engine: GameEngine, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    val current = engine.rebirthProfile
    val next = engine.nextRebirthProfile
    val maximumTier = next.tier <= current.tier
    drawOverlayFrame(bounds, Orange)
    drawLabel(textMeasurer, "REBIRTH PROTOCOL", bounds.left + d(24f), bounds.top + d(24f), 20f, Orange, weight = FontWeight.Bold)
    drawLabel(
        textMeasurer,
        "PERMANENT THREAT // TIER ${current.tier} > ${next.tier}",
        bounds.right - d(24f),
        bounds.top + d(30f),
        8f,
        White,
        alignRight = true,
    )

    val contentLeft = bounds.left + d(24f)
    val cardTop = bounds.top + d(78f)
    val cardGap = d(14f)
    val cardWidth = (bounds.width - d(62f)) * 0.5f
    val cardHeight = d(92f)
    drawRebirthTierCard(textMeasurer, current, "CURRENT CYCLE", contentLeft, cardTop, cardWidth, cardHeight, Cyan)
    drawRebirthTierCard(textMeasurer, next, "NEXT CYCLE", contentLeft + cardWidth + cardGap, cardTop, cardWidth, cardHeight, Orange)

    val statsTop = cardTop + cardHeight + d(14f)
    val statsHeight = min(d(220f), max(d(176f), bounds.bottom - d(210f) - statsTop))
    drawRebirthHostileStats(textMeasurer, current, next, contentLeft, statsTop, cardWidth, statsHeight)
    drawRebirthRewardStats(textMeasurer, current, next, contentLeft + cardWidth + cardGap, statsTop, cardWidth, statsHeight)

    drawLabel(textMeasurer, "RESET: RUN BUILD", bounds.center.x, bounds.bottom - d(194f), 9f, Orange, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "KEPT: MATTER // LAB // ARMORY // CODEX", bounds.center.x, bounds.bottom - d(172f), 9f, Acid, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "LIFETIME UNLOCKS + SETTINGS ALSO REMAIN", bounds.center.x, bounds.bottom - d(151f), 7f, Muted, centered = true)

    val actionLeft = bounds.left + d(24f)
    val actionTop = bounds.bottom - d(118f)
    val actionWidth = bounds.width - d(48f)
    val actionAccent = when {
        engine.rebirthConfirmationArmed -> Red
        engine.canRebirth && !maximumTier -> Acid
        else -> Muted
    }
    val actionLabel = when {
        maximumTier -> "MAXIMUM REBIRTH TIER"
        !engine.canRebirth -> "REBIRTH LOCKED"
        engine.rebirthConfirmationArmed -> "CONFIRM REBIRTH // ENTER TIER ${next.tier}"
        else -> "ARM REBIRTH // TIER ${current.tier} > ${next.tier}"
    }
    val actionDetail = when {
        maximumTier -> "ALL THREAT DIRECTIVES CLEARED"
        !engine.canRebirth -> "DEFEAT THE ARCHITECT ON TIER ${current.tier}"
        engine.rebirthConfirmationArmed -> "SECOND PRESS RESETS THE RUN BUILD"
        else -> "FIRST PRESS ARMS THIS TRANSITION"
    }
    drawRect(actionAccent.copy(alpha = 0.11f), Offset(actionLeft, actionTop), Size(actionWidth, d(50f)))
    drawRect(actionAccent, Offset(actionLeft, actionTop), Size(actionWidth, d(50f)), style = Stroke(d(if (engine.rebirthConfirmationArmed) 2f else 1.4f)))
    drawLabel(textMeasurer, actionLabel, bounds.center.x, actionTop + d(7f), 11f, actionAccent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, actionDetail, bounds.center.x, actionTop + d(28f), 7f, if (engine.canRebirth) White else Muted, centered = true)
    drawFooterBack(textMeasurer, bounds, Orange)
}

private fun DrawScope.drawRebirthTierCard(
    textMeasurer: TextMeasurer,
    profile: RebirthProfile,
    eyebrow: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    accent: Color,
) {
    drawRect(Color(0x99101225), Offset(x, y), Size(width, height))
    drawRect(accent.copy(alpha = 0.75f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, eyebrow, x + d(13f), y + d(10f), 7f, Muted, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "TIER ${profile.tier} // ${profile.directive.displayName.uppercase()}", x + d(13f), y + d(30f), 11f, accent, weight = FontWeight.Bold)
    drawLabel(textMeasurer, profile.directive.description, x + d(13f), y + d(55f), 7f, White, maxWidth = width - d(26f), maxLines = 2)
}

private fun DrawScope.drawRebirthHostileStats(
    textMeasurer: TextMeasurer,
    current: RebirthProfile,
    next: RebirthProfile,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    drawRect(Color(0x80101225), Offset(x, y), Size(width, height))
    drawRect(Red.copy(alpha = 0.45f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "HOSTILE ESCALATION", x + d(13f), y + d(11f), 8f, Red, weight = FontWeight.Bold)
    val rowY = y + d(38f)
    val rowStep = (height - d(62f)) / 5f
    drawRebirthStatRow(textMeasurer, "OPENING HOSTILES", current.openingEnemyCount.toString(), next.openingEnemyCount.toString(), x, rowY, width, Red)
    drawRebirthStatRow(textMeasurer, "ACTIVE ENEMY CAP", formatMultiplier(current.enemyCapMultiplier), formatMultiplier(next.enemyCapMultiplier), x, rowY + rowStep, width, Red)
    drawRebirthStatRow(textMeasurer, "SPAWN RATE", formatMultiplier(current.spawnRateMultiplier), formatMultiplier(next.spawnRateMultiplier), x, rowY + rowStep * 2f, width, Red)
    drawRebirthStatRow(textMeasurer, "ENEMY INTEGRITY", formatMultiplier(current.enemyHealthMultiplier), formatMultiplier(next.enemyHealthMultiplier), x, rowY + rowStep * 3f, width, Red)
    drawRebirthStatRow(textMeasurer, "ENEMY SPEED", formatMultiplier(current.enemySpeedMultiplier), formatMultiplier(next.enemySpeedMultiplier), x, rowY + rowStep * 4f, width, Red)
    drawRebirthStatRow(textMeasurer, "INCOMING DAMAGE", formatMultiplier(current.incomingDamageMultiplier), formatMultiplier(next.incomingDamageMultiplier), x, rowY + rowStep * 5f, width, Red)
}

private fun DrawScope.drawRebirthRewardStats(
    textMeasurer: TextMeasurer,
    current: RebirthProfile,
    next: RebirthProfile,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    drawRect(Color(0x80101225), Offset(x, y), Size(width, height))
    drawRect(Acid.copy(alpha = 0.45f), Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "CYCLE COMPENSATION", x + d(13f), y + d(11f), 8f, Acid, weight = FontWeight.Bold)
    val rowY = y + d(38f)
    val rowStep = (height - d(67f)) / 4f
    drawRebirthStatRow(textMeasurer, "THREAT ADVANCE", "+${current.threatTimeOffsetSeconds.toInt()}s", "+${next.threatTimeOffsetSeconds.toInt()}s", x, rowY, width, Orange)
    drawRebirthStatRow(textMeasurer, "PLAYER POWER", formatMultiplier(current.playerPowerMultiplier), formatMultiplier(next.playerPowerMultiplier), x, rowY + rowStep, width, Acid)
    drawRebirthStatRow(textMeasurer, "CORE INTEGRITY", "+${current.playerIntegrityBonus.toInt()}", "+${next.playerIntegrityBonus.toInt()}", x, rowY + rowStep * 2f, width, Acid)
    drawRebirthStatRow(textMeasurer, "VOID MATTER", formatMultiplier(current.matterGainMultiplier), formatMultiplier(next.matterGainMultiplier), x, rowY + rowStep * 3f, width, Acid)
    drawRebirthStatRow(textMeasurer, "BONUS REROLLS", "+${current.bonusRerolls}", "+${next.bonusRerolls}", x, rowY + rowStep * 4f, width, Acid)
}

private fun DrawScope.drawRebirthStatRow(
    textMeasurer: TextMeasurer,
    label: String,
    current: String,
    next: String,
    x: Float,
    y: Float,
    width: Float,
    accent: Color,
) {
    drawLabel(textMeasurer, label, x + d(13f), y, 7f, Muted, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "$current  >  $next", x + width - d(13f), y, 7f, accent, alignRight = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawArmory(engine: GameEngine, textMeasurer: TextMeasurer, renderTime: Float) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Cyan)
    drawLabel(textMeasurer, "WEAPON ARMORY", bounds.left + d(25f), bounds.top + d(24f), 20f, Cyan, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "${WeaponCatalog.all.size} SYSTEMS // ${engine.unlockedWeapons.size} UNLOCKED // MATTER ${formatCompact(engine.totalMatter)}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val cardWidth = min(d(245f), (bounds.width - d(80f)) / 3f)
    val gap = d(16f)
    val total = cardWidth * 3f + gap * 2f
    val startX = (size.width - total) * 0.5f
    val cardTop = bounds.top + d(118f)
    val cardBottom = bounds.bottom - d(85f)
    engine.armoryPageWeapons.forEachIndexed { index, definition ->
        drawWeaponCard(engine, textMeasurer, definition, startX + index * (cardWidth + gap), cardTop, cardWidth, cardBottom - cardTop, renderTime)
    }
    drawPagedFooter(textMeasurer, bounds, engine.armoryPage, engine.maxArmoryPage, Cyan)
}

private fun DrawScope.drawWeaponCard(engine: GameEngine, textMeasurer: TextMeasurer, definition: WeaponDefinition, x: Float, y: Float, width: Float, height: Float, renderTime: Float) {
    val unlocked = engine.isWeaponUnlocked(definition.id)
    val equipped = engine.startingWeapon == definition.id
    val active = engine.weapon == definition.id && engine.phase != GamePhase.MENU
    val accent = if (unlocked) weaponColor(definition.id) else Muted
    drawRect(Color(0xB00B0D1D), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(if (equipped) 2.2f else 1f)))
    drawRect(accent.copy(alpha = 0.12f), Offset(x, y), Size(width, d(50f)))
    drawWeaponGlyph(definition.id, Offset(x + width * 0.5f, y + d(95f)), d(28f), renderTime, accent)
    drawLabel(textMeasurer, definition.name.uppercase(), x + width * 0.5f, y + d(139f), 11f, accent, centered = true, weight = FontWeight.Bold)
    drawLabel(textMeasurer, definition.tags.joinToString(" / "), x + width * 0.5f, y + d(164f), 7f, Muted, centered = true)
    drawLabel(textMeasurer, definition.description, x + d(14f), y + d(193f), 7f, White, maxWidth = width - d(28f), maxLines = 3)
    drawLabel(textMeasurer, WeaponMasteryProgressionLabel, x + width * 0.5f, y + d(274f), 6f, accent, centered = true, maxWidth = width - d(20f), maxLines = 2)
    drawLabel(textMeasurer, "MILESTONES BOOST DAMAGE + ACTIVATION", x + width * 0.5f, y + d(295f), 6f, Muted, centered = true)
    val state = when {
        equipped -> "EQUIPPED LOADOUT"
        active -> "ACTIVE THIS RUN"
        unlocked -> "EQUIP"
        else -> "UNLOCK ${formatCompact(definition.permanentUnlockCost.toLong())}"
    }
    drawLabel(textMeasurer, state, x + width * 0.5f, y + height - d(34f), 9f, if (equipped) Acid else accent, centered = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawCodex(engine: GameEngine, textMeasurer: TextMeasurer) {
    drawRect(Color(0xD9050610))
    val bounds = overlayBounds()
    drawOverlayFrame(bounds, Magenta)
    drawLabel(textMeasurer, "ARTIFACT CODEX", bounds.left + d(25f), bounds.top + d(24f), 20f, Magenta, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "${engine.discoveredItemCount}/400 DISCOVERED // PAGE ${engine.codexPage + 1}/${engine.maxCodexPage + 1}", bounds.right - d(25f), bounds.top + d(30f), 8f, White, alignRight = true)
    val contentTop = bounds.top + d(76f)
    val contentWidth = bounds.width - d(50f)
    val columnWidth = contentWidth * 0.5f
    val rowHeight = (bounds.height - d(146f)) / 5f
    engine.codexPageItems.forEachIndexed { index, item ->
        val column = index % 2
        val row = index / 2
        val x = bounds.left + d(25f) + column * columnWidth
        val y = contentTop + row * rowHeight
        drawCodexItem(engine, textMeasurer, item, x, y, columnWidth - d(10f), rowHeight - d(8f))
    }
    drawPagedFooter(textMeasurer, bounds, engine.codexPage, engine.maxCodexPage, Magenta)
}

private fun DrawScope.drawCodexItem(engine: GameEngine, textMeasurer: TextMeasurer, item: ItemDefinition, x: Float, y: Float, width: Float, height: Float) {
    val discovered = engine.isItemDiscovered(item.id)
    val stack = engine.itemStack(item.id)
    val accent = if (discovered) rarityColor(item.rarity) else DarkLine
    drawRect(Color(0x8A0B0D1D), Offset(x, y), Size(width, height))
    drawRect(accent, Offset(x, y), Size(width, height), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "#${item.id.toString().padStart(3, '0')}", x + d(10f), y + d(9f), 7f, Muted)
    drawLabel(textMeasurer, if (discovered) item.name.uppercase() else "UNKNOWN SIGNAL", x + d(47f), y + d(9f), 8f, if (discovered) accent else Muted, weight = FontWeight.Bold)
    drawLabel(textMeasurer, if (discovered) "${item.rarity.displayLabel.uppercase()} // STACK $stack/${item.maxStacks}" else "LOCKED // LEVEL ${item.unlockLevel}", x + width - d(10f), y + d(9f), 7f, if (discovered) White else Muted, alignRight = true)
    drawItemIcon(
        item = item,
        center = Offset(x + d(25f), y + d(43f)),
        radius = d(14f),
        accent = accent,
        stack = if (discovered) stack else null,
        obscured = !discovered,
    )
    drawLabel(
        textMeasurer,
        if (discovered) item.description else "Acquire during a run to decode this artifact.",
        x + d(49f),
        y + d(33f),
        7f,
        Muted,
        maxWidth = width - d(59f),
        maxLines = 2,
    )
}

private fun DrawScope.drawOverlayFrame(bounds: Rect, accent: Color) {
    drawRect(VoidPanel, bounds.topLeft, bounds.size)
    drawRect(accent.copy(alpha = 0.85f), bounds.topLeft, bounds.size, style = Stroke(d(1.5f)))
    drawRect(accent.copy(alpha = 0.09f), bounds.topLeft, Size(bounds.width, d(61f)))
}

private fun DrawScope.drawFooterBack(textMeasurer: TextMeasurer, bounds: Rect, accent: Color) {
    val top = bounds.bottom - d(55f)
    drawRect(accent.copy(alpha = 0.08f), Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), d(41f)))
    drawRect(accent, Offset(bounds.left + d(20f), top), Size(bounds.width - d(40f), d(41f)), style = Stroke(d(1f)))
    drawLabel(textMeasurer, "BACK [ESC / ENTER]", bounds.center.x, top + d(13f), 9f, accent, centered = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawLabFooter(textMeasurer: TextMeasurer, bounds: Rect, accent: Color) {
    val top = bounds.bottom - d(55f)
    drawRect(accent.copy(alpha = 0.08f), Offset(bounds.left, top), Size(bounds.width, d(55f)))
    drawLine(accent.copy(alpha = 0.65f), Offset(bounds.left, top), Offset(bounds.right, top), d(1f))
    drawLabel(textMeasurer, "BACK [ESC / ENTER]", bounds.center.x, top + d(18f), 9f, accent, centered = true, weight = FontWeight.Bold)
}

private fun DrawScope.drawPagedFooter(textMeasurer: TextMeasurer, bounds: Rect, page: Int, maxPage: Int, accent: Color) {
    val top = bounds.bottom - d(55f)
    val closeRight = bounds.left + bounds.width * 0.45f
    val nextLeft = bounds.right - d(85f)
    drawRect(accent.copy(alpha = 0.07f), Offset(bounds.left, top), Size(bounds.width, d(55f)))
    drawLine(DarkLine, Offset(closeRight, top), Offset(closeRight, bounds.bottom), d(1f))
    drawLine(DarkLine, Offset(nextLeft, top), Offset(nextLeft, bounds.bottom), d(1f))
    drawLabel(textMeasurer, "BACK [ESC]", bounds.left + d(25f), top + d(18f), 9f, accent, weight = FontWeight.Bold)
    drawLabel(textMeasurer, "‹  PAGE ${page + 1}/${maxPage + 1}", (closeRight + nextLeft) * 0.5f, top + d(18f), 9f, if (page > 0) White else Muted, centered = true)
    drawLabel(textMeasurer, "NEXT ›", bounds.right - d(42f), top + d(18f), 8f, if (page < maxPage) White else Muted, centered = true)
}

private fun DrawScope.overlayBounds(maxWidth: Float = 900f, maxHeight: Float = 650f): Rect {
    val width = min(d(maxWidth), size.width - d(30f))
    val height = min(d(maxHeight), size.height - d(30f))
    val left = (size.width - width) * 0.5f
    val top = (size.height - height) * 0.5f
    return Rect(left, top, left + width, top + height)
}

private fun DrawScope.drawWeaponGlyph(id: WeaponId, center: Offset, radius: Float, time: Float, color: Color) {
    when (id) {
        WeaponId.FLUX_WAKE -> {
            drawLine(color.copy(alpha = 0.35f), Offset(center.x - radius, center.y + radius * 0.55f), Offset(center.x + radius, center.y - radius * 0.55f), radius * 0.35f, StrokeCap.Round)
            drawLine(White, Offset(center.x - radius * 0.7f, center.y + radius * 0.4f), Offset(center.x + radius * 0.75f, center.y - radius * 0.4f), radius * 0.08f, StrokeCap.Round)
        }
        WeaponId.MORNINGSTAR -> {
            drawCircle(color.copy(alpha = 0.3f), radius, center, style = Stroke(radius * 0.08f, pathEffect = dashEffect))
            val ball = polar(center, radius, time * 2f)
            drawLine(color, center, ball, radius * 0.08f)
            drawPolygon(ball, radius * 0.32f, 8, time, White, Fill)
        }
        WeaponId.PHASE_LATTICE -> {
            drawCircle(color.copy(alpha = 0.22f), radius, center)
            drawCircle(color, radius * 0.78f, center, style = Stroke(radius * 0.09f))
            drawCircle(White, radius * 0.35f, center, style = Stroke(radius * 0.05f, pathEffect = dashEffect))
        }
        WeaponId.NULL_LANCE -> {
            drawLine(color.copy(alpha = 0.3f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.28f, StrokeCap.Round)
            drawPolygon(Offset(center.x + radius * 0.72f, center.y), radius * 0.34f, 3, 0f, White, Fill)
        }
        WeaponId.GRAVITY_MINES -> {
            drawCircle(color.copy(alpha = 0.2f), radius, center)
            drawCircle(color, radius * 0.75f, center, style = Stroke(radius * 0.07f, pathEffect = dashEffect))
            drawPolygon(center, radius * 0.42f, 6, time, White, Stroke(radius * 0.07f))
        }
        WeaponId.ION_SWARM -> repeat(3) { index ->
            val point = polar(center, radius * 0.78f, time + index * TAU / 3f)
            drawPolygon(point, radius * 0.2f, 4, time + index, color, Fill)
        }
        WeaponId.RIFT_BLADES -> {
            drawPolygon(Offset(center.x - radius * 0.38f, center.y), radius * 0.55f, 4, time, color, Fill)
            drawPolygon(Offset(center.x + radius * 0.38f, center.y), radius * 0.55f, 4, -time, White, Fill)
        }
        WeaponId.ARC_COIL -> {
            drawCircle(color, radius, center, style = Stroke(radius * 0.1f))
            var previous = Offset(center.x - radius, center.y)
            repeat(5) { index ->
                val next = Offset(center.x - radius + radius * 0.4f * (index + 1), center.y + if (index % 2 == 0) -radius * 0.42f else radius * 0.42f)
                drawLine(White, previous, next, radius * 0.07f)
                previous = next
            }
        }
        WeaponId.QUASAR_CANNON -> {
            drawLine(color.copy(alpha = 0.35f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.42f, StrokeCap.Round)
            drawCircle(White, radius * 0.23f, Offset(center.x + radius * 0.72f, center.y))
            drawCircle(color, radius * 0.82f, center, style = Stroke(radius * 0.07f))
        }
        WeaponId.ENTROPY_FIELD -> {
            drawCircle(color.copy(alpha = 0.12f), radius, center)
            drawCircle(color, radius, center, style = Stroke(radius * 0.07f, pathEffect = dashEffect))
            drawPolygon(center, radius * 0.48f, 7, time * 0.3f, White, Stroke(radius * 0.06f))
        }
        WeaponId.SINGULARITY_SPEAR -> {
            drawLine(color.copy(alpha = 0.28f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.35f, StrokeCap.Round)
            drawLine(White, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), radius * 0.09f, StrokeCap.Round)
            drawPolygon(Offset(center.x + radius * 0.8f, center.y), radius * 0.37f, 3, 0f, color, Fill)
        }
        WeaponId.PRISM_RELAY -> {
            val first = polar(center, radius * 0.78f, time * 1.8f)
            val second = polar(center, radius * 0.78f, time * 1.8f + TAU / 3f)
            val third = polar(center, radius * 0.78f, time * 1.8f + TAU * 2f / 3f)
            drawLine(color.copy(alpha = 0.65f), first, second, radius * 0.08f)
            drawLine(color.copy(alpha = 0.65f), second, third, radius * 0.08f)
            drawLine(color.copy(alpha = 0.65f), third, first, radius * 0.08f)
            drawPolygon(center, radius * 0.42f, 4, time, White, Fill)
        }
    }
}

private fun weaponColor(id: WeaponId): Color = WeaponColors[id.ordinal.coerceIn(WeaponColors.indices)]

private fun rarityColor(rarity: ItemRarity): Color = RarityColors[(rarity.rank - 1).coerceIn(RarityColors.indices)]

private fun speedVisualRatio(speed: Float): Float {
    val safeSpeed = max(0f, speed)
    return clamp(ln(1f + safeSpeed / 300f) / ln(1f + 5_000f / 300f), 0f, 1f)
}

private fun polar(center: Offset, radius: Float, angle: Float): Offset =
    Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)

private fun formatCompact(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    val divisor = when {
        safe >= 1_000_000_000_000L -> 1_000_000_000_000L
        safe >= 1_000_000_000L -> 1_000_000_000L
        safe >= 1_000_000L -> 1_000_000L
        safe >= 1_000L -> 1_000L
        else -> return safe.toString()
    }
    val suffix = when (divisor) {
        1_000L -> "K"
        1_000_000L -> "M"
        1_000_000_000L -> "B"
        else -> "T"
    }
    val tenths = safe / (divisor / 10L)
    return if (tenths % 10L == 0L) "${tenths / 10L}$suffix" else "${tenths / 10L}.${tenths % 10L}$suffix"
}

private fun DrawScope.drawBar(x: Float, y: Float, width: Float, height: Float, progress: Float, foreground: Color, background: Color) {
    drawRect(background, Offset(x, y), Size(width, height))
    drawRect(foreground, Offset(x, y), Size(width * clamp(progress, 0f, 1f), height))
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    fontSize: Float,
    color: Color,
    centered: Boolean = false,
    alignRight: Boolean = false,
    weight: FontWeight = FontWeight.Normal,
    alpha: Float = 1f,
    maxWidth: Float? = null,
    maxLines: Int = 1,
) {
    val style = textStyle(fontSize * textMeasurer.scale, color.copy(alpha = color.alpha * alpha), weight)
    val result = if (maxWidth != null) {
        textMeasurer.delegate.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = max(1, maxWidth.toInt())),
        )
    } else {
        textMeasurer.delegate.measure(text, style)
    }
    val actualX = when {
        centered -> x - result.size.width * 0.5f
        alignRight -> x - result.size.width
        else -> x
    }
    drawText(result, topLeft = Offset(actualX, y))
}

private fun DrawScope.drawPolygon(center: Offset, radius: Float, sides: Int, rotation: Float, color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    val path = Path()
    repeat(sides) { index ->
        val angle = rotation + index * TAU / sides
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = style)
}

private fun DrawScope.world(engine: GameEngine, x: Float, y: Float, shakeX: Float, shakeY: Float): Offset =
    Offset(size.width * 0.5f + x - engine.cameraX + shakeX, size.height * 0.5f + y - engine.cameraY + shakeY)

private fun DrawScope.isOnScreen(point: Offset, margin: Float = 0f): Boolean =
    point.x >= -margin && point.y >= -margin && point.x <= size.width + margin && point.y <= size.height + margin

private fun DrawScope.d(value: Float): Float = value * density

private fun positiveModulo(value: Float, modulus: Float): Float = ((value % modulus) + modulus) % modulus

private fun formatOneDecimal(value: Float): String {
    val scaled = (value * 10f).toInt()
    return "${scaled / 10}.${abs(scaled % 10)}"
}

private fun formatMultiplier(value: Float): String {
    val hundredths = (value * 100f + 0.5f).toInt()
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${hundredths / 100}x" else "${hundredths / 100}.$fraction" + "x"
}
