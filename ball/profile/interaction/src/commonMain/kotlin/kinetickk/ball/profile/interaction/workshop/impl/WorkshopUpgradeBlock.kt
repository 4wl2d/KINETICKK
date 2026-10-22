// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileRejection
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.ProfileSlabButton
import kinetickk.ball.profile.interaction.dp
import kinetickk.ball.profile.interaction.drawProfileText
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileButtonNaturalWidth
import kinetickk.ball.profile.interaction.profileLayoutGrow
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** What a Refit (character) or Honing (weapon) level is bought for. */
internal sealed interface UpgradeTarget {
    data class Character(val shape: CoreShape) : UpgradeTarget
    data class Weapon(val id: WeaponId) : UpgradeTarget
}

/** The typed Profile purchase of the next level of [this] track. */
internal fun UpgradeTarget.pulse(): ProfilePulse.Business = when (this) {
    is UpgradeTarget.Character -> ProfilePulse.PurchaseCharacterUpgrade(shape)
    is UpgradeTarget.Weapon -> ProfilePulse.PurchaseWeaponUpgrade(id)
}

/** The effects a track lists: Refit adds integrity and character damage, Honing weapon damage. */
internal enum class UpgradeStat {
    INTEGRITY,
    CHARACTER_DAMAGE,
    WEAPON_DAMAGE,
}

/** One effect line: [now] at the current level, [next] after the next one (null at the maximum). */
internal data class UpgradeEffectLine(val stat: UpgradeStat, val now: Float, val next: Float?)

/**
 * One Refit or Honing track as the upgrade block shows it. [nextPrice] is the content price rule
 * ([UpgradePolicy.nextCost], the function the Profile Nucleus charges), null at the maximum;
 * [shortfall] is the Matter still missing for it. The Profile stays the authority: an affordable
 * track only enables the buy action.
 */
internal data class UpgradeTrackRenderModel(
    val level: Int,
    val maxLevel: Int,
    val nextPrice: Long?,
    val affordable: Boolean,
    /** An unlocked character or an owned weapon: only those tracks are sold. */
    val owned: Boolean,
    val shortfall: Long,
    val effects: List<UpgradeEffectLine>,
) {
    val maxed: Boolean get() = level >= maxLevel

    /** The buy action may ask the Profile for the next level. */
    val canBuy: Boolean get() = owned && !maxed && affordable
}

/**
 * The track of a definition priced from [base] at [level] (clamped to the policy's range) for a
 * balance of [matter]: each of [stats] adds its per-level amount.
 */
internal fun upgradeTrack(
    policy: UpgradePolicy,
    base: Int,
    level: Int,
    matter: Long,
    owned: Boolean,
    stats: List<Pair<UpgradeStat, Float>>,
): UpgradeTrackRenderModel {
    val current = level.coerceIn(0, policy.maxLevel)
    val maxed = current >= policy.maxLevel
    val price = if (maxed) null else policy.nextCost(base, current)
    val affordable = price != null && owned && matter >= price
    return UpgradeTrackRenderModel(
        level = current,
        maxLevel = policy.maxLevel,
        nextPrice = price,
        affordable = affordable,
        owned = owned,
        shortfall = if (price == null || affordable) 0L else (price - matter).coerceAtLeast(0L),
        effects = stats.map { (stat, perLevel) -> UpgradeEffectLine(stat, perLevel * current, if (maxed) null else perLevel * (current + 1)) },
    )
}

/** A character's Refit: maximum integrity and character-mechanic damage per level. */
internal fun characterUpgradeTrack(
    policy: UpgradePolicy,
    definition: CoreShapeDefinition,
    level: Int,
    matter: Long,
    unlocked: Boolean,
): UpgradeTrackRenderModel = upgradeTrack(policy, definition.upgradeBaseCost, level, matter, unlocked, listOf(
    UpgradeStat.INTEGRITY to policy.characterIntegrityPerLevel,
    UpgradeStat.CHARACTER_DAMAGE to policy.characterPowerPerLevel * 100f,
))

/** A weapon's Honing: the weapon's own hits per level. */
internal fun weaponUpgradeTrack(
    policy: UpgradePolicy,
    definition: WeaponDefinition,
    level: Int,
    matter: Long,
    owned: Boolean,
): UpgradeTrackRenderModel = upgradeTrack(policy, definition.upgradeBaseCost, level, matter, owned, listOf(
    UpgradeStat.WEAPON_DAMAGE to policy.weaponPowerPerLevel * 100f,
))

/** The effect line's name: [mechanic] names the character's damage. */
internal fun UpgradeStat.label(language: AppLanguage, mechanic: String?): String = when (this) {
    UpgradeStat.INTEGRITY -> language.text(ProfileScreensRedesignText.HangarIntegrity)
    UpgradeStat.CHARACTER_DAMAGE -> language.text(ProfileScreensRedesignText.UpgradeCharacterDamage, mechanic.orEmpty())
    UpgradeStat.WEAPON_DAMAGE -> language.text(ProfileScreensRedesignText.UpgradeWeaponDamage)
}

/** "+20" or "+20%"; a level-0 value is the dash (nothing yet). */
internal fun UpgradeStat.value(value: Float, language: AppLanguage): String {
    if (value <= 0f) return language.text(ProfileScreensRedesignText.NoValue)
    val tenths = (value * 10f).roundToInt()
    val number = if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10).toString() + "." + (tenths % 10)
    return language.text(if (this == UpgradeStat.INTEGRITY) ProfileScreensRedesignText.UpgradePlus else ProfileScreensRedesignText.UpgradePlusPercent,
        number)
}

/** "Lvl N" on an owned tile once a level is bought; null for a tile without one. */
internal fun workshopTileLevel(level: Int, owned: Boolean, maxLevel: Int, language: AppLanguage): String? =
    if (owned && level >= 1) language.text(ProfileScreensRedesignText.MasteryLevel, level.coerceAtMost(maxLevel)) else null

/** A tile's level mark: gold at the maximum, ink on the inspected (bone) face, muted otherwise. */
internal fun workshopTileLevelColor(maxed: Boolean, inspected: Boolean): Color = when {
    inspected -> Kk.Ink
    maxed -> Kk.RLegend
    else -> Kk.Bone2
}

/** "Lvl 3 of 10": the pips' and the buy action's state. */
internal fun upgradeLevelText(track: UpgradeTrackRenderModel, language: AppLanguage): String =
    language.text(ProfileScreensRedesignText.UpgradeLevelOf, track.level, track.maxLevel)

/** The buy action's label: Upgrade, Maximum at the top level, Locked for a track that is not sold. */
internal fun upgradeBuyLabel(track: UpgradeTrackRenderModel, language: AppLanguage): String = language.text(when {
    !track.owned -> ProfileScreensRedesignText.Locked
    track.maxed -> ProfileScreensRedesignText.UpgradeMaximum
    else -> ProfileScreensRedesignText.UpgradeBuy
})

/** "Need 120 more matter" beside a buy action the balance does not cover; null otherwise. */
internal fun upgradeNeedText(track: UpgradeTrackRenderModel, language: AppLanguage): String? =
    if (track.owned && !track.maxed && !track.affordable) {
        language.text(ProfileScreensRedesignText.NeedMore, formatMatter(track.shortfall, language))
    } else null

/** What a refused purchase tells the player (the assertive region reads it). */
internal fun upgradeRefusalText(rejection: ProfileRejection, track: UpgradeTrackRenderModel, language: AppLanguage): String = when (rejection) {
    ProfileRejection.InsufficientMatter -> language.text(ProfileScreensRedesignText.ArmoryRefusedMatter, formatMatter(track.shortfall, language))
    ProfileRejection.UpgradeMaxLevel -> language.text(ProfileScreensRedesignText.UpgradeRefusedMax, track.maxLevel)
    ProfileRejection.WeaponNotOwned -> language.text(ProfileScreensRedesignText.UpgradeRefusedWeapon)
    ProfileRejection.CoreShapeLocked -> language.text(ProfileScreensRedesignText.UpgradeRefusedCharacter)
    else -> language.text(ProfileScreensRedesignText.ArmoryRefused)
}

/** Board sizes of the upgrade block per frame (design px before the frame scale). */
internal class WorkshopUpgradeMetrics(
    val header: Float,
    val pipsGap: Float,
    val pipSize: Float,
    val pipWidth: Float,
    val pipGap: Float,
    val captionGap: Float,
    val caption: Float,
    val line: Float,
    val buyGap: Float,
    val buy: Float,
    val buyMin: Float,
    val column: Float,
    val labelSize: Float,
    val levelSize: Float,
    val captionSize: Float,
    val nameSize: Float,
    val valueSize: Float,
    val needSize: Float,
    val buySize: KkButtonSize,
)

internal fun workshopUpgradeMetrics(frame: ProfileFrame): WorkshopUpgradeMetrics = if (frame.regular) {
    WorkshopUpgradeMetrics(22f, 10f, 14f, 26f, 4f, 8f, 12f, 22f, 6f, 52f, 220f, 104f, 15f, 13f, 11f, 16f, 14f, 14f, KkButtonSize.MD)
} else {
    WorkshopUpgradeMetrics(18f, 6f, 10f, 18f, 3f, 4f, 10f, 18f, 4f, 44f, 170f, 84f, 12f, 11f, 9f, 13f, 12f, 12f, KkButtonSize.SM)
}

/**
 * The upgrade block's geometry (the parent's coordinates): the header row ("Upgrade" and the level),
 * the ten pips, the Now / Next captions over the value columns, one row per effect line, the buy
 * action and, when the balance is short, the need line beside it (under it when that leaves it less
 * than d(96)). Text rows grow with the text size; the pips and the action keep their size.
 */
internal class WorkshopUpgradeLayout(
    val header: Rect,
    val pips: Rect,
    val captions: Rect,
    val effects: List<Rect>,
    /** Left edges of the Now and Next value columns. */
    val nowLeft: Float,
    val nextLeft: Float,
    val buy: Rect,
    val need: Rect?,
    val metrics: WorkshopUpgradeMetrics,
) {
    val height: Float get() = max(buy.bottom, need?.bottom ?: 0f) - header.top
}

/**
 * Lays the block out from ([left], [top]) across [width] px for [effectCount] lines, a buy action
 * [buyWidth] px wide at its natural size and an optional need line [needWidth] px wide ([needHeight]
 * tall when it stacks under the action).
 */
internal fun workshopUpgradeLayout(
    frame: ProfileFrame,
    left: Float,
    top: Float,
    width: Float,
    textScale: Float,
    effectCount: Int,
    buyWidth: Float,
    needWidth: Float?,
    needHeight: Float = 0f,
): WorkshopUpgradeLayout {
    fun d(value: Float) = frame.d(value)
    val metrics = workshopUpgradeMetrics(frame)
    val grow = profileLayoutGrow(textScale, 0.8f)
    val right = left + width
    val header = Rect(left, top, right, top + d(metrics.header) * grow)
    val pipsTop = header.bottom + d(metrics.pipsGap)
    val pipsWidth = frame.density * frame.k * (metrics.pipWidth * 10f + metrics.pipGap * 9f)
    val pips = Rect(left, pipsTop, left + min(width, pipsWidth), pipsTop + frame.density * frame.k * metrics.pipSize)
    val captionsTop = pips.bottom + d(metrics.captionGap)
    val captions = Rect(left, captionsTop, right, captionsTop + d(metrics.caption) * grow)
    val column = frame.density * frame.k * metrics.column
    val nextLeft = right - column
    val nowLeft = nextLeft - column
    var y = captions.bottom
    val effects = List(effectCount) {
        Rect(left, y, right, y + d(metrics.line) * grow).also { y = it.bottom }
    }
    val buyTop = y + d(metrics.buyGap)
    val buy = Rect(left, buyTop, left + min(width, max(buyWidth, d(metrics.buyMin))), buyTop + d(metrics.buy))
    val need = needWidth?.let {
        val besideLeft = buy.right + d(16f)
        if (right - besideLeft >= d(96f)) {
            Rect(besideLeft, buy.top, right, buy.bottom)
        } else {
            Rect(left, buy.bottom + d(4f), right, buy.bottom + d(4f) + needHeight)
        }
    }
    return WorkshopUpgradeLayout(header, pips, captions, effects, nowLeft, nextLeft, buy, need, metrics)
}

/** The buy action's label and its gem price (none at the maximum or for a track not sold). */
internal class WorkshopUpgradeTexts(val label: String, val cost: String?, val need: String?)

internal fun workshopUpgradeTexts(track: UpgradeTrackRenderModel, language: AppLanguage): WorkshopUpgradeTexts = WorkshopUpgradeTexts(
    label = upgradeBuyLabel(track, language),
    cost = track.nextPrice?.takeIf { track.owned }?.let { formatMatter(it, language) },
    need = upgradeNeedText(track, language),
)

/** The block's layout measured for [track]'s texts with [measurer] (the panel's text size). */
internal fun workshopUpgradeLayoutFor(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    track: UpgradeTrackRenderModel,
    language: AppLanguage,
    left: Float,
    top: Float,
    width: Float,
): WorkshopUpgradeLayout {
    val metrics = workshopUpgradeMetrics(frame)
    val texts = workshopUpgradeTexts(track, language)
    val buyWidth = profileButtonNaturalWidth(measurer, frame, texts.label, metrics.buySize, cost = texts.cost, icon = !track.owned)
    val needLayout = texts.need?.let { workshopNeedLayout(measurer, frame, it, width) }
    return workshopUpgradeLayout(frame, left, top, width, measurer.scale, track.effects.size, buyWidth,
        needLayout?.size?.width?.toFloat(), needLayout?.kkBoxHeight ?: 0f)
}

private fun workshopNeedLayout(measurer: CanvasTextMeasurer, frame: ProfileFrame, text: String, width: Float): TextLayoutResult =
    fitKkText(measurer, text, workshopUpgradeMetrics(frame).needSize * frame.k, width, maxLines = 2, uppercase = false, minFactor = 0.6f) {
        measurer.typography.bodyStyle(it, lineHeightEm = 1.2f)
    }

/** Test tags of the block's nodes. */
internal const val WORKSHOP_UPGRADE_BUY_TAG = "kinetickk.workshop.upgrade.buy"
internal const val WORKSHOP_UPGRADE_PIPS_TAG = "kinetickk.workshop.upgrade.pips"

/**
 * The upgrade block for [track] at [layout] (the parent's coordinates): the drawn header, pips,
 * effect lines and need line, the pips' node and the buy action. A level bought while the block is
 * shown slams its pip in; [refusalSerial] flashes the action's face in the threat colour for one
 * frame each time it changes. [name] names the character or weapon in the action's description;
 * [mechanic] names the character's damage line.
 */
@Composable
internal fun WorkshopUpgradeBlock(
    frame: ProfileFrame,
    layout: WorkshopUpgradeLayout,
    track: UpgradeTrackRenderModel,
    target: UpgradeTarget,
    name: String,
    mechanic: String?,
    textScale: Float,
    refusalSerial: Int,
    onBuy: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val measurer = rememberKkCanvasMeasurer(textScale)
    val metrics = layout.metrics
    val texts = workshopUpgradeTexts(track, language)
    // A level bought for the same target while shown slams in; switching targets shows levels at rest.
    val slam = remember(target) { Animatable(1f) }
    var shownLevelValue by remember(target) { mutableIntStateOf(track.level) }
    LaunchedEffect(target, track.level) {
        val raised = track.level > shownLevelValue
        shownLevelValue = track.level
        if (raised) {
            slam.snapTo(0f)
            slam.animateTo(1f, tween(KkSlam.DURATION_MS, easing = LinearEasing))
        }
    }
    var flashValue by remember(target) { mutableStateOf(false) }
    val flashPath = remember { Path() }
    val seenRefusal = remember(target) { RefusalMemory(refusalSerial) }
    LaunchedEffect(refusalSerial) {
        if (seenRefusal.serial == refusalSerial) return@LaunchedEffect
        seenRefusal.serial = refusalSerial
        flashValue = true
        delay(KkTime.Impact.toLong())
        flashValue = false
    }
    val bounds = Rect(layout.header.left, layout.header.top, layout.header.right, layout.header.top + layout.height)
    Box(
        Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(frame.dp(bounds.width), frame.dp(bounds.height))
            .drawBehind {
                translate(-bounds.left, -bounds.top) {
                    drawWorkshopUpgrade(measurer, frame, layout, track, texts.need, mechanic, language, slam.value)
                }
            },
    )
    val pips = layout.pips
    val levelText = upgradeLevelText(track, language)
    Box(
        Modifier.offset { IntOffset(pips.left.roundToInt(), pips.top.roundToInt()) }
            .size(frame.dp(pips.width), frame.dp(pips.height))
            .testTag(WORKSHOP_UPGRADE_PIPS_TAG)
            .semantics { contentDescription = levelText },
    )
    val buy = layout.buy
    val description = if (track.canBuy || (track.owned && !track.maxed)) {
        language.text(ProfileScreensRedesignText.UpgradeBuyDescription, name, track.level + 1, texts.cost.orEmpty())
    } else texts.label
    ProfileSlabButton(
        label = texts.label,
        onClick = onBuy,
        frame = frame,
        tag = WORKSHOP_UPGRADE_BUY_TAG,
        size = metrics.buySize,
        enabled = track.canBuy,
        locked = !track.owned,
        cost = texts.cost,
        textScale = textScale,
        contentDescription = description,
        stateDescription = listOfNotNull(levelText, texts.need).joinToString(", "),
        modifier = Modifier.offset { IntOffset(buy.left.roundToInt(), buy.top.roundToInt()) }
            .size(frame.dp(buy.width), frame.dp(buy.height)),
    )
    if (flashValue) {
        // A refusal flashes the action's face in the threat colour for one frame.
        Box(
            Modifier.offset { IntOffset(buy.left.roundToInt(), buy.top.roundToInt()) }
                .size(frame.dp(buy.width), frame.dp(buy.height))
                .drawBehind { drawPath(flashPath.kkSlab(Rect(0f, 0f, size.width, size.height), frame.d(metrics.buySize.cutDp)), measurer.roles.threat) },
        )
    }
}

/** The last refusal serial a block has flashed for. */
private class RefusalMemory(var serial: Int)

/** Draws the header row, the pips, the captions and effect lines and the need line of the block. */
internal fun DrawScope.drawWorkshopUpgrade(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    layout: WorkshopUpgradeLayout,
    track: UpgradeTrackRenderModel,
    need: String?,
    mechanic: String?,
    language: AppLanguage,
    slam: Float = 1f,
) {
    val k = frame.k
    val metrics = layout.metrics
    val roles = measurer.roles
    val header = layout.header
    val title = measureKkText(measurer, language.text(ProfileScreensRedesignText.SegmentUpgrade),
        measurer.typography.labelStyle(metrics.labelSize * k), uppercase = true)
    drawProfileText(title, header.left, header.center.y, Kk.Mute, "workshop.upgrade.title", valign = KkVAlign.CENTER)
    val levelLabel = if (track.maxed) language.text(ProfileScreensRedesignText.UpgradeMaximum)
        else language.text(ProfileScreensRedesignText.MasteryLevel, track.level)
    val level = fitKkText(measurer, levelLabel, metrics.levelSize * k, header.width - title.size.width - frame.d(12f), minFactor = 0.6f) {
        measurer.typography.monoStyle(it)
    }
    drawProfileText(level, header.right, header.center.y, if (track.maxed) Kk.RLegend else Kk.Bone, "workshop.upgrade.level",
        align = KkAlign.END, valign = KkVAlign.CENTER)

    // Ten sheared pips: the bought levels in `you` (gold at the maximum), the next one outlined when affordable;
    // a track that is not sold shows empty hatched pips.
    val pips = layout.pips
    val pitch = (metrics.pipWidth + metrics.pipGap) * k
    val filledColor = if (track.maxed) Kk.RLegend else roles.you
    val settled = if (slam < 1f && track.level > 0) track.level - 1 else track.level
    drawKkPips(pips.topLeft, track.maxLevel, settled, filledColor, sizeDp = metrics.pipSize * k, widthDp = metrics.pipWidth * k,
        gapDp = metrics.pipGap * k, nextOutlined = track.affordable && slam >= 1f)
    if (settled < track.level) {
        val left = pips.left + density * pitch * settled
        val pip = Rect(left, pips.top, left + density * metrics.pipWidth * k, pips.bottom)
        withKkSlam(slam, pip.center, rotationDeg = 0f) {
            drawKkPips(pip.topLeft, 1, 1, filledColor.copy(alpha = KkSlam.alpha(slam)), sizeDp = metrics.pipSize * k,
                widthDp = metrics.pipWidth * k, gapDp = 0f)
        }
    }
    if (!track.owned) drawKkHatch(pips)

    // Now / Next over their value columns, then one line per effect.
    if (layout.effects.isNotEmpty()) {
        val captionStyle = measurer.typography.labelStyle(metrics.captionSize * k)
        val captions = layout.captions
        drawProfileText(measureKkText(measurer, language.text(ProfileScreensRedesignText.UpgradeNow), captionStyle, uppercase = true),
            layout.nowLeft, captions.center.y, Kk.Mute, "workshop.upgrade.now", valign = KkVAlign.CENTER)
        if (!track.maxed) {
            drawProfileText(measureKkText(measurer, language.text(ProfileScreensRedesignText.UpgradeNext), captionStyle, uppercase = true),
                layout.nextLeft, captions.center.y, Kk.Mute, "workshop.upgrade.next", valign = KkVAlign.CENTER)
        }
    }
    val columnWidth = layout.nextLeft - layout.nowLeft - frame.d(6f)
    track.effects.forEachIndexed { index, line ->
        val row = layout.effects.getOrNull(index) ?: return@forEachIndexed
        val name = fitKkText(measurer, line.stat.label(language, mechanic), metrics.nameSize * k, layout.nowLeft - row.left - frame.d(10f),
            maxLines = 2, uppercase = false, minFactor = 0.6f) { measurer.typography.bodyStyle(it, lineHeightEm = 1.1f) }
        drawProfileText(name, row.left, row.center.y, Kk.Bone, "workshop.upgrade.effect", line.stat, valign = KkVAlign.CENTER)
        val now = fitKkText(measurer, line.stat.value(line.now, language), metrics.valueSize * k, columnWidth, minFactor = 0.6f) {
            measurer.typography.monoStyle(it)
        }
        drawProfileText(now, layout.nowLeft, row.center.y, Kk.Bone, "workshop.upgrade.effect.now", line.stat, valign = KkVAlign.CENTER)
        line.next?.let { next ->
            val value = fitKkText(measurer, line.stat.value(next, language), metrics.valueSize * k, columnWidth, minFactor = 0.6f) {
                measurer.typography.monoStyle(it)
            }
            drawProfileText(value, layout.nextLeft, row.center.y, roles.you, "workshop.upgrade.effect.next", line.stat, valign = KkVAlign.CENTER)
        }
    }
    val needRect = layout.need
    if (need != null && needRect != null) {
        val text = workshopNeedLayout(measurer, frame, need, needRect.width)
        drawProfileText(text, needRect.left, needRect.center.y, roles.threat, "workshop.upgrade.need", valign = KkVAlign.CENTER)
    }
}
