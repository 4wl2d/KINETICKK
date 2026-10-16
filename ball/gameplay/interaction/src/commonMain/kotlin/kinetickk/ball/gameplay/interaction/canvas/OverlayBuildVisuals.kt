// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.RelicPairingKind
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicLinkKind
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
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

/** The aspect hue in the Default palette; screens that know the player's preset use the overload below. */
internal fun RelicAspect.overlayColor(): Color = Kk.aspect(ordinal)

/**
 * The aspect hue under the player's color-vision preset: elemental aspects take the element ramp,
 * Kinetic the preset's volt ([KkRolePalette.kinetic]), Sovereign the fixed gold.
 */
internal fun RelicAspect.overlayColor(roles: KkRolePalette): Color =
    element?.overlayColor(roles) ?: if (this == RelicAspect.KINETIC) roles.kinetic else Kk.aspect(ordinal)

/** An element's hue under the player's color-vision preset. */
internal fun StatusElement.overlayColor(roles: KkRolePalette): Color = roles.element(ordinal)

/** The glyph of an element; glyph and pattern carry identity where hues converge (color vision). */
internal fun StatusElement.overlayIcon(): KkIcon = KkIcon.Elements[ordinal]

/** The monochrome glyph of an archetype tag. */
internal fun BuildTag.overlayIcon(): KkIcon = KkIcon.Tags[ordinal]

/** Relics equipped in slot order, shared by the relic matrix, pause and report rows. */
internal class OverlayRelicSlot(val id: RelicId, val aspect: RelicAspect, val rank: Int)

/**
 * A link between two relics of a matrix: a named pairing (concord or discord) or the generic key
 * rule (harmony for a shared key, dissonance for opposed keys). [slots] holds the two slot indices
 * in ascending order; [discovered] is false only for a named pairing the player has not met yet.
 */
internal class OverlayRelicLink(
    val relics: List<RelicId>,
    val kind: RelicLinkKind,
    val pairing: RelicPairingId?,
    val discovered: Boolean,
    val slots: List<Int>,
) {
    val first: Int get() = slots.first()
    val last: Int get() = slots.last()

    /** The same two relics, whatever slots they sit in. */
    fun joinsSame(other: OverlayRelicLink): Boolean =
        relics.size == other.relics.size && relics.all { it in other.relics }

    /** Interference (named discord or opposed keys) reads as a threat to the build. */
    val interferes: Boolean get() = kind == RelicLinkKind.DISCORD || kind == RelicLinkKind.DISSONANCE
}

/**
 * The links a matrix of [relics] forms, with the slots that form each one. The nucleus is the
 * authority that applies links ([GameplayRenderModel.build]); this reading mirrors its content rule
 * (a named pairing first, then opposed keys, then a shared key) so the HUD, pause and candidate
 * matrices of a relic preview read alike. [isDiscovered] tells whether a named pairing was met.
 */
internal fun overlayRelicLinks(
    relics: List<RelicId?>,
    content: GameplayContentSnapshot,
    isDiscovered: (RelicPairingId) -> Boolean,
): List<OverlayRelicLink> {
    val links = ArrayList<OverlayRelicLink>()
    for (a in relics.indices) {
        val first = relics[a] ?: continue
        for (b in a + 1 until relics.size) {
            val second = relics[b] ?: continue
            if (second == first) continue
            val named = content.pairing(first, second)
            val kind = when {
                named != null -> if (named.kind == RelicPairingKind.CONCORD) RelicLinkKind.CONCORD else RelicLinkKind.DISCORD
                content.relic(first).dissonates(content.relic(second)) -> RelicLinkKind.DISSONANCE
                content.relic(first).harmonizes(content.relic(second)) -> RelicLinkKind.HARMONY
                else -> continue
            }
            links += OverlayRelicLink(listOf(first, second), kind, named?.id, named == null || isDiscovered(named.id), listOf(a, b))
        }
    }
    return links
}

/** Hollow Crown weakens every other bound relic's upside; mirrors the nucleus build compiler. */
private const val HollowCrownUpsideScale = 0.75f

/**
 * The upside multiplier of the relic in slot [index] of the matrix [relics], read the way the
 * nucleus compiles it: every partner with a shared key multiplies it by the harmony scale, every
 * partner with an opposed key by the dissonance scale, a named pairing replaces both for its pair
 * and a Hollow Crown weakens every other relic while it is bound or, through severance, while its
 * liability lingers ([crownSevered]). 1 for an empty slot.
 */
internal fun overlayUpsideMultiplier(
    relics: List<RelicId?>,
    index: Int,
    content: GameplayContentSnapshot,
    crownSevered: Boolean = false,
): Float {
    val id = relics.getOrNull(index) ?: return 1f
    val relic = content.relic(id)
    val policy = content.build.policy
    var multiplier = 1f
    relics.forEachIndexed { other, partnerId ->
        if (other == index || partnerId == null || partnerId == id) return@forEachIndexed
        val partner = content.relic(partnerId)
        when {
            content.pairing(id, partnerId) != null -> Unit
            relic.dissonates(partner) -> multiplier *= policy.dissonanceScale
            relic.harmonizes(partner) -> multiplier *= policy.harmonyScale
        }
    }
    if (id != RelicId.HOLLOW_CROWN && (crownSevered || relics.any { it == RelicId.HOLLOW_CROWN })) multiplier *= HollowCrownUpsideScale
    return multiplier
}

/** The links of [relics] with this frame's pairing discoveries. */
internal fun GameplayRenderModel.overlayRelicLinks(relics: List<RelicId?>): List<OverlayRelicLink> =
    overlayRelicLinks(relics, content, ::isPairingDiscovered)

/** Named concord in bone, generic harmony muted, interference in the threat hues. */
internal fun OverlayRelicLink.overlayColor(): Color = when (kind) {
    RelicLinkKind.CONCORD -> Kk.Bone
    RelicLinkKind.HARMONY -> Kk.Mute
    RelicLinkKind.DISCORD -> Kk.Hazard
    RelicLinkKind.DISSONANCE -> Kk.Hazard2
}

/** An undiscovered named pairing reads as a question mark in every language. */
internal const val UNKNOWN_PAIRING_NAME = "?"

/** A link's name: the pairing once discovered, "?" before, or the generic key rule. */
internal fun OverlayRelicLink.overlayName(content: GameplayContentSnapshot, language: AppLanguage): String {
    val named = pairing?.let(content.build::pairing)
    return when {
        named != null && discovered -> named.name.localizedContent(language)
        named != null -> UNKNOWN_PAIRING_NAME
        kind == RelicLinkKind.DISSONANCE -> language.text(OverlayRedesignText.Dissonance)
        else -> language.text(OverlayRedesignText.Harmony)
    }
}

/** What a link does; undiscovered pairings only say whether they amplify or interfere. */
internal fun OverlayRelicLink.overlayDescription(content: GameplayContentSnapshot, language: AppLanguage): String {
    val named = pairing?.let(content.build::pairing)
    val policy = content.build.policy
    return when {
        named != null && discovered -> named.description.localizedContent(language)
        named != null && kind == RelicLinkKind.DISCORD -> language.text(OverlayRedesignText.UnknownDiscordEffect)
        named != null -> language.text(OverlayRedesignText.UnknownConcordEffect)
        kind == RelicLinkKind.DISSONANCE -> language.text(OverlayRedesignText.DissonanceEffect,
            overlayDecimal(((1f - policy.dissonanceScale) * 100f).roundToLong().toFloat(), language))
        else -> language.text(OverlayRedesignText.HarmonyEffect,
            overlayDecimal(((policy.harmonyScale - 1f) * 100f).roundToLong().toFloat(), language))
    }
}

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

/** "×1.10", "×1.00": always two decimals, so compounding link multipliers read alike side by side. */
internal fun overlayLinkMultiplier(value: Float, language: AppLanguage): String {
    val hundredths = (abs(value) * 100f).roundToLong()
    val separator = if (language == AppLanguage.Russian) "," else "."
    return "×" + hundredths / 100 + separator + (100 + hundredths % 100).toString().substring(1)
}

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
