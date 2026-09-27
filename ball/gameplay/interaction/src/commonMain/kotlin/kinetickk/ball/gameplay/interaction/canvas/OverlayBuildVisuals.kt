// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SynergyDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.formatCompact
import kotlin.math.abs
import kotlin.math.roundToLong

// Presentation mappings shared by the reward, pause and run-report overlays. They only map
// committed render data to visuals; no rule here decides an outcome.

private val WeaponIcons: Array<KkIcon> = Array(WeaponId.entries.size) { index ->
    KkIcon.valueOf("WEAPONS_" + WeaponId.entries[index].name)
}

private val FormIcons: Array<KkIcon> = arrayOf(
    KkIcon.FORMS_CIRCLE, // ORB
    KkIcon.FORMS_SQUARE, // PRISM
    KkIcon.FORMS_TRIANGLE, // SHARD
    KkIcon.FORMS_RING, // RING
    KkIcon.FORMS_DIAMOND, // DIAMOND
    KkIcon.FORMS_TESSERACT, // TESSERACT
)

internal fun WeaponId.overlayIcon(): KkIcon = WeaponIcons[ordinal]

internal fun CoreShape.overlayIcon(): KkIcon = FormIcons[ordinal.coerceIn(0, FormIcons.size - 1)]

internal fun RelicAspect.overlayIcon(): KkIcon = KkIcon.Aspects[ordinal]

internal fun RelicAspect.overlayColor(): Color = Kk.aspect(ordinal)

/** Relics equipped in slot order, shared by the relic matrix, pause and report rows. */
internal class OverlayRelicSlot(val id: RelicId, val aspect: RelicAspect, val rank: Int)

/** A synergy formed by the relics in [slots] (ascending slot indices). */
internal class OverlaySynergyLink(
    val definition: SynergyDefinition,
    val slots: List<Int>,
) {
    val first: Int get() = slots.first()
    val last: Int get() = slots.last()
}

/**
 * Synergies that a matrix of [relics] forms, with the slots that form each one. This mirrors the
 * synergy definitions for display (brackets and links); the Gameplay nucleus stays the authority
 * that applies them, and reward previews supply which synergies a choice adds or removes.
 */
internal fun overlaySynergyLinks(
    relics: List<RelicId?>,
    content: GameplayContentSnapshot,
): List<OverlaySynergyLink> = content.synergies.mapNotNull { definition ->
    val aspect = definition.requiredAspect
    val slots = if (aspect != null) {
        if (aspect == RelicAspect.SOVEREIGN) return@mapNotNull null
        val members = relics.indices.filter { index -> relics[index]?.let { content.relic(it).aspect } == aspect }
        if (members.mapNotNull { relics[it] }.distinct().size < 2) return@mapNotNull null
        members
    } else {
        val members = relics.indices.filter { index -> relics[index] in definition.requiredRelics }
        if (definition.requiredRelics.any { required -> relics.none { it == required } }) return@mapNotNull null
        members
    }
    OverlaySynergyLink(definition, slots)
}

/** Aspect synergies read in their aspect color; relic-pair synergies in bone. */
internal fun SynergyDefinition.overlayColor(): Color = requiredAspect?.overlayColor() ?: Kk.Bone

/** One stat row of the pause build overview. [highlight] marks a value above its neutral 1×. */
internal class OverlayStat(val label: String, val value: String, val highlight: Boolean)

/** The build stats the render model offers, labelled with the item effect names they share. */
internal fun GameplayRenderModel.overlayBuildStats(language: AppLanguage): List<OverlayStat> {
    fun label(effect: ItemEffect) = effect.displayLabel.localizedContent(language)
    return listOf(
        OverlayStat(label(ItemEffect.IMPACT_DAMAGE), overlayMultiplier(damageMultiplier, language), damageMultiplier > 1.001f),
        OverlayStat(label(ItemEffect.WEAPON_POWER), overlayMultiplier(effectiveWeaponPower, language), effectiveWeaponPower > 1.001f),
        OverlayStat(label(ItemEffect.MASS), overlayDecimal(mass, language), false),
        OverlayStat(label(ItemEffect.MAGNETISM), overlayDecimal(magnetStrength, language), false),
        OverlayStat(label(ItemEffect.COOLING), overlayDecimal(coolingRate, language), false),
        OverlayStat(label(ItemEffect.MAX_INTEGRITY), overlayGrouped(maxHp.roundToLong(), language), false),
        OverlayStat(label(ItemEffect.DASH_POWER), overlayGrouped(dashImpulse.roundToLong(), language), false),
        OverlayStat(label(ItemEffect.CRIT_CHANCE), overlayPercent(critChance, language), false),
        OverlayStat(label(ItemEffect.OVERDRIVE_GAIN), overlayMultiplier(overdriveGain, language), overdriveGain > 1.001f),
        OverlayStat(label(ItemEffect.PICKUP_RADIUS), overlayGrouped(pickupRadius.roundToLong(), language), false),
    )
}

/** "×1.64" with the language's decimal separator (two decimals, trailing zeros removed). */
internal fun overlayMultiplier(value: Float, language: AppLanguage): String = "×" + overlayDecimal(value, language)

/** Up to two decimals, trailing zeros removed, Russian decimal comma. */
internal fun overlayDecimal(value: Float, language: AppLanguage): String {
    val hundredths = (abs(value) * 100f).roundToLong()
    val whole = hundredths / 100
    // Two decimal digits from 100..199 so a leading zero survives ("1.05"), then trim.
    val fraction = (100 + hundredths % 100).toString().substring(1).trimEnd('0')
    val separator = if (language == AppLanguage.Russian) "," else "."
    return (if (value < 0f && hundredths > 0) "−" else "") + whole + if (fraction.isEmpty()) "" else separator + fraction
}

/** A 0..1 fraction as a whole percentage ("8%"). */
internal fun overlayPercent(fraction: Float, language: AppLanguage): String =
    overlayDecimal((fraction * 100f).roundToLong().toFloat(), language) + "%"

/** Thousands grouped per language: "1,412" / "1 412" (narrow no-break space). */
internal fun overlayGrouped(value: Long, language: AppLanguage): String {
    val digits = abs(value).toString()
    val separator = if (language == AppLanguage.Russian) " " else ","
    val grouped = buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(separator)
            append(digit)
        }
    }
    return if (value < 0) "−$grouped" else grouped
}

/** Large totals such as damage ("612K"); small values stay grouped. */
internal fun overlayCompact(value: Long, language: AppLanguage): String =
    if (value < 100_000L) overlayGrouped(value, language) else formatCompact(value, language)

/** The localized "Lvl" label followed by [level] ("Lvl 7" / "Ур. 7"). */
internal fun overlayLevel(level: Int, language: AppLanguage): String =
    language.text(OverlayRedesignText.LevelLabel) + " " + level
