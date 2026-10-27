// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import kinetickk.ball.content.api.BuffStat
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectClause
import kinetickk.ball.content.api.EffectCondition
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemRule
import kinetickk.ball.content.api.RelicAspect
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.SkillId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.gameplay.interaction.canvas.elementFill
import kinetickk.ball.gameplay.interaction.canvas.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.skillGlyph
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkCardMaterial
import kinetickk.foundation.design.KkCardOrnament
import kinetickk.foundation.design.KkCardStyle
import kinetickk.foundation.design.KkFillKind
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.kkLuminance
import kinetickk.foundation.design.kkMix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The look of one reward card under one palette, in two channels that never mix: the rarity
 * channel is the [style]'s material alone (the frame: border, band, echo, glow, effects), the type
 * channel is the face (the [hue] and pattern by element, the preset's volt for Kinetic; the ornament
 * by primary tag, the watermark and seed, the bespoke [art] of an epic or legendary item).
 */
internal class RewardCardLook(
    val style: KkCardStyle,
    val art: RewardCardArt?,
    /** The type hue: the element's (the preset's volt for Kinetic, `you` for weapons) under the colour-vision preset. */
    val hue: Color,
    /** The second element hue, or [hue] when the card has one. */
    val secondHue: Color,
) {
    /** Whether anything is drawn behind the text, so the text rows need the soft face plates. */
    val needsPlates: Boolean get() = art != null || style.drawsBehindText

    /**
     * The icon plate's tint when the type hue tells little apart by colour ([neutralHue]: a pale or
     * greyish hue, as Protan and Deutan show Ion and Cryo, near the steel and chrome greys), else
     * unspecified. The type then carries a cue that is not hue: the plate tinted in the hue (a dark
     * patch, [plateCueTint]), its pattern stronger ([plateCuePattern]) and the body pattern at its
     * strongest.
     */
    val plateTint: Color = if (neutralHue(hue)) plateCueTint(hue) else Color.Unspecified

    /** Whether the card carries the plate cue ([plateTint]). */
    val plateCue: Boolean get() = plateTint.isSpecified
}

/** The pattern's strength inside the icon plate: faint, or stronger on a [RewardCardLook.plateCue] plate. */
internal fun plateCuePattern(cue: Boolean): Float = if (cue) PLATE_CUE_PATTERN else PLATE_PATTERN

private const val PLATE_PATTERN = 0.28f
private const val PLATE_CUE_PATTERN = 0.6f

/**
 * [hue] over the plate's ink, as strong as it goes (up to [PLATE_TINT_MAX]) while the plate stays a
 * dark patch (luminance up to [PLATE_TINT_LUMINANCE]): a slate Ion tints it by about 30 %, a pale Cryo
 * by less, and the plate always stands apart from the mid and light greys of the metal frames.
 */
internal fun plateCueTint(hue: Color): Color {
    var tint = Kk.Ink3
    var step = 1
    while (step * PLATE_TINT_STEP <= PLATE_TINT_MAX) {
        val next = kkMix(Kk.Ink3, hue, step * PLATE_TINT_STEP)
        if (kkLuminance(next) > PLATE_TINT_LUMINANCE) break
        tint = next
        step++
    }
    return tint
}

private const val PLATE_TINT_STEP = 0.02f
private const val PLATE_TINT_MAX = 0.3f
private const val PLATE_TINT_LUMINANCE = 0.05f

/**
 * What makes a card visibly its own, for the uniqueness check over the shipped catalog: material,
 * primary and secondary hue index (element ordinal, −1 for Kinetic), ornament, watermark glyph and
 * art. The seed is left out on purpose: a watermark turned or moved a little never tells two cards
 * apart on its own.
 */
internal data class RewardCardSignature(
    val material: KkCardMaterial,
    val hue: Int,
    val secondHue: Int,
    val ornament: KkCardOrnament,
    val watermark: KkIcon?,
    val art: RewardCardArt?,
)

internal fun RewardCardPresentation.signature(): RewardCardSignature {
    val look = rewardCardLook(this, KkRolePalette.Default)
    return RewardCardSignature(
        look.style.material,
        elements.getOrNull(0)?.ordinal ?: -1,
        elements.getOrNull(1)?.ordinal ?: -1,
        look.style.ornament,
        look.style.watermark,
        look.art,
    )
}

/**
 * The look of [presentation] under [roles], memoized by identity: the card canvas reads it every
 * frame without allocating.
 */
internal fun rewardCardLook(presentation: RewardCardPresentation, roles: KkRolePalette): RewardCardLook =
    RewardCardLooks.get(presentation, roles)

/** Seeds of the card kinds: a kind offset plus the stable id, so the watermark step cycles by id. */
private const val ItemSeed = 0x1000
private const val RelicSeed = 0x2000
private const val WeaponSeed = 0x3000
private const val OtherSeed = 0x4000

/** Builds the look (not memoized; see [rewardCardLook]). */
internal fun buildRewardCardLook(presentation: RewardCardPresentation, roles: KkRolePalette): RewardCardLook {
    val item = presentation.item
    val relic = presentation.relic
    val weapon = presentation.weapon
    val choice = presentation.choice
    return when {
        presentation.decline -> {
            val style = KkCardStyle(KkCardMaterial.CALM, Kk.Mute2, gold = roles.gold, spectrum = roles.spectrum)
            RewardCardLook(style, null, Kk.Mute2, Kk.Mute2)
        }
        item != null -> {
            val primary = item.elements.getOrNull(0)
            val second = item.elements.getOrNull(1)
            // The rarity takes the material of its rank; the type (Kinetic is the preset's volt) takes the face.
            val hue = primary?.overlayColor(roles) ?: roles.kinetic
            val secondHue = second?.overlayColor(roles) ?: Color.Unspecified
            val art = rewardCardArt(item)
            val material = KkCardMaterial.ofRank(item.rarity.rank)
            val style = KkCardStyle(
                material = material,
                hue = hue,
                pattern = primary?.let { elementFill(it.ordinal) } ?: KineticFill,
                ornament = item.tags.firstOrNull()?.cardOrnament() ?: KkCardOrnament.PATTERN_CORNER,
                // Bespoke art is the card's identity: no generic glyph competes with it.
                watermark = if (art != null) null else item.watermarkIcon(),
                seed = ItemSeed + item.id,
                bodyEffects = art == null,
                raysAlpha = if (art != null) 0.4f else 1f,
                patternBoost = patternBoost(hue, roles),
                // Art fills the face's lower right: a corner ornament moves to the lower left.
                ornamentLeft = art != null,
                gold = roles.gold,
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, art, hue, if (secondHue.isSpecified) secondHue else hue)
        }
        relic != null -> {
            val element = relic.aspect.element
            // The aspect's hue: an element's, the preset's volt for Kinetic, gold for Sovereign. It paints
            // the face only: every relic wears the one relic material, so no aspect ever picks a frame.
            val hue = presentation.accentColor(roles)
            val kinetic = relic.aspect == RelicAspect.KINETIC
            val style = KkCardStyle(
                material = RelicMaterial,
                hue = hue,
                pattern = element?.let { elementFill(it.ordinal) } ?: if (kinetic) KineticFill else KkFillKind.HALFTONE,
                ornament = relic.attunedTags.firstOrNull()?.cardOrnament() ?: KkCardOrnament.PATTERN_CORNER,
                watermark = relic.id.watermarkIcon(),
                seed = RelicSeed + relic.id.ordinal,
                patternBoost = if (element != null || kinetic) patternBoost(hue, roles) else 1f,
                gold = roles.gold,
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, null, hue, hue)
        }
        choice.rewardFocus != null -> {
            // A direction: its aspect's hue and pattern, the `you` hue for weapon directions, Kinetic otherwise.
            val focus = requireNotNull(choice.rewardFocus)
            val aspect = RelicAspect.entries.firstOrNull { it.name == focus.name }
            val element = aspect?.element
            val weapons = choice.type == ChoiceType.TOTEM || choice.type == ChoiceType.WEAPON
            val hue = when {
                element != null -> element.overlayColor(roles)
                weapons -> roles.you
                else -> roles.kinetic
            }
            val style = KkCardStyle(
                material = KkCardMaterial.STEEL,
                hue = hue,
                pattern = element?.let { elementFill(it.ordinal) } ?: if (weapons) KkFillKind.HALFTONE else KineticFill,
                ornament = KkCardOrnament.RETICLE,
                watermark = presentation.icon,
                seed = OtherSeed + focus.ordinal,
                patternBoost = patternBoost(hue, roles),
                gold = roles.gold,
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, null, hue, hue)
        }
        weapon != null || choice.type == ChoiceType.TOTEM || choice.type == ChoiceType.WEAPON -> {
            // The player's own system: the `you` hue in a steel frame, its pattern and ornament from
            // what the weapon is (an orbital, an aura, a lance, mines ...).
            val hue = roles.you
            val (pattern, ornament) = weapon?.id?.cardMarks()
                ?: (KkFillKind.HALFTONE to if (choice.totemAction == TotemAction.CHANGE_WEAPON) KkCardOrnament.VENTS else KkCardOrnament.RACK)
            val style = KkCardStyle(
                material = KkCardMaterial.STEEL,
                hue = hue,
                pattern = pattern,
                ornament = ornament,
                watermark = presentation.icon,
                seed = WeaponSeed + (weapon?.id?.ordinal ?: (32 + (choice.totemAction?.ordinal ?: 0))),
                gold = roles.gold,
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, null, hue, hue)
        }
        choice.relicAction == RelicChoiceAction.MELD -> {
            // A meld joins two forms and has no element: Kinetic in a steel frame, a perforated link edge,
            // two forms joined as its watermark.
            val hue = roles.kinetic
            val style = KkCardStyle(
                material = KkCardMaterial.STEEL,
                hue = hue,
                pattern = KineticFill,
                ornament = KkCardOrnament.PERFORATION,
                watermark = KkIcon.FORMS_TESSERACT,
                seed = OtherSeed + 64,
                patternBoost = patternBoost(hue, roles),
                gold = roles.gold,
                spectrum = roles.spectrum,
            )
            RewardCardLook(style, null, hue, hue)
        }
        else -> {
            // Any other card: the material of its rank in its own hue, nothing behind the text.
            val hue = presentation.accentColor(roles)
            val style = KkCardStyle(KkCardMaterial.ofRank(presentation.rank), hue, seed = OtherSeed + 96, gold = roles.gold, spectrum = roles.spectrum)
            RewardCardLook(style, null, hue, hue)
        }
    }
}

/**
 * The one material of every relic card (a relic has no rarity): chrome, whatever its aspect, so a
 * Sovereign relic's gold stays on its face and never takes the legendary frame.
 */
internal val RelicMaterial = KkCardMaterial.CHROME

/** Ram bumper, Drive slashes, Anchor weight, Arsenal rack, Precision reticle, Bulwark rim, Overclock vents, Chain perforation, Fortune ticket. */
internal fun BuildTag.cardOrnament(): KkCardOrnament = when (this) {
    BuildTag.RAM -> KkCardOrnament.BUMPER
    BuildTag.DRIVE -> KkCardOrnament.SLASHES
    BuildTag.ANCHOR -> KkCardOrnament.WEIGHT
    BuildTag.ARSENAL -> KkCardOrnament.RACK
    BuildTag.PRECISION -> KkCardOrnament.RETICLE
    BuildTag.BULWARK -> KkCardOrnament.DOUBLE_RIM
    BuildTag.OVERCLOCK -> KkCardOrnament.VENTS
    BuildTag.CHAIN -> KkCardOrnament.PERFORATION
    BuildTag.FORTUNE -> KkCardOrnament.TICKET
}

/**
 * The Kinetic body pattern: the slash cut, which no element uses, so a Kinetic card is told from an
 * element's card by its pattern as well as by its volt (Mono's white Kinetic beside its white Cryo).
 */
internal val KineticFill = KkFillKind.SLASH

/**
 * How much stronger the element pattern is drawn for [hue] under [roles]: a pale or greyish hue (Cryo,
 * a slate Ion under Protan, every Mono hue) tells little apart by colour, so its pattern carries the
 * identity; so, a little less, does a hue the preset paints close to another damage tone (the Protan
 * and Deutan volt beside their amber Thermal).
 */
internal fun patternBoost(hue: Color, roles: KkRolePalette): Float {
    if (neutralHue(hue)) return PATTERN_BOOST
    for (index in 0..StatusElement.entries.size) {
        val tone = roles.tone(index)
        if (tone != hue && rgbDistance(tone, hue) < NEAR_TONE) return NEAR_TONE_BOOST
    }
    return 1f
}

/** Whether [hue] is pale or greyish: little chroma, or close to bone or mute (it tells little apart by colour). */
internal fun neutralHue(hue: Color): Boolean {
    val chroma = max(hue.red, max(hue.green, hue.blue)) - min(hue.red, min(hue.green, hue.blue))
    return chroma < LOW_CHROMA || rgbDistance(hue, Kk.Bone) < GREYISH || rgbDistance(hue, Kk.Mute) < GREYISH
}

private fun rgbDistance(a: Color, b: Color): Float {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return sqrt(dr * dr + dg * dg + db * db)
}

private const val GREYISH = 0.25f
private const val LOW_CHROMA = 0.28f
/** A neutral hue's pattern at twice the usual boosted strength (the style caps it at KK_PATTERN_MAX_ALPHA). */
private const val PATTERN_BOOST = 4f
private const val NEAR_TONE = 0.4f
private const val NEAR_TONE_BOOST = 1.6f

/**
 * The item's own watermark glyph: an active item's comes from its effect ([activeWatermark]), since its one small
 * passive is never its identity; any other item's is the mark of its headline (its first stat line, else its first
 * clause, by what the clause does on its trigger, else its first rule), so cards that share a rarity, a tag and an
 * element, or a trigger, still carry different glyphs. Never a letter-like or arrow-like mark.
 */
internal fun ItemDefinition.watermarkIcon(): KkIcon {
    active?.let { return activeWatermark(it) }
    stats.firstOrNull()?.let { return it.effect.watermarkIcon() }
    clauses.firstOrNull()?.let { return it.watermarkIcon() }
    for (rule in rules) rule.watermarkIcon()?.let { return it }
    return elements.firstOrNull()?.overlayIcon() ?: KkIcon.FORMS_TESSERACT
}

/**
 * The watermark of the active item carrying [effect]: the effect's own glyph ([skillGlyph], the mark its Z/X/C slot,
 * icon plate and Codex entry show), except where that glyph's faint outline would repeat a card of the same rarity,
 * element and ornament: Gravity Well's mines read as the ring of Gravity Loom's, so its watermark is the well's inward
 * spiral.
 */
internal fun activeWatermark(effect: SkillId): KkIcon = when (effect) {
    SkillId.GRAVITY_WELL -> KkIcon.FORMS_SPIRAL
    else -> skillGlyph(effect)
}

/** A clause's mark: what its first action does, read with its trigger (the hue already names an applied element). */
private fun EffectClause.watermarkIcon(): KkIcon = when (val action = actions.first()) {
    is EffectAction.Chain -> KkIcon.WEAPONS_ARC_COIL
    is EffectAction.Push -> KkIcon.WEAPONS_GRAVITY_MINES
    // A pull on a turn swings the field like a wake; any other pull is the magnet.
    is EffectAction.Pull -> if (trigger == EffectTrigger.TURN) KkIcon.WEAPONS_FLUX_WAKE else KkIcon.SYSTEM_POLARITY
    is EffectAction.Vent -> KkIcon.SYSTEM_HEAT
    is EffectAction.Overdrive -> KkIcon.SYSTEM_OVERDRIVE
    is EffectAction.GainData -> KkIcon.SYSTEM_DATA
    is EffectAction.RefreshWeapon -> KkIcon.WEAPONS_QUASAR_CANNON
    is EffectAction.Shield -> KkIcon.SYSTEM_SHIELD
    is EffectAction.Heal -> KkIcon.SYSTEM_INTEGRITY
    is EffectAction.ExtendCombo -> KkIcon.SYSTEM_CYCLE_CLOCK
    is EffectAction.ApplyStatus -> when {
        action.pick.element == null -> KkIcon.WEAPONS_PRISM_RELAY
        // A weapon infusion that splashes around the target reads as a field, not a single hit.
        trigger == EffectTrigger.WEAPON_HIT && action.radius > 0f -> KkIcon.WEAPONS_ENTROPY_FIELD
        else -> trigger.watermarkIcon()
    }
    is EffectAction.Burst -> when (trigger) {
        EffectTrigger.DASH -> KkIcon.WEAPONS_NULL_LANCE
        EffectTrigger.OVERDRIVE -> KkIcon.WEAPONS_SINGULARITY_SPEAR
        else -> trigger.watermarkIcon()
    }
    // Shards are a prism's splinters; a slam's are the wreckage it breaks off.
    is EffectAction.Shards -> if (trigger == EffectTrigger.SLAM) trigger.watermarkIcon() else KkIcon.WEAPONS_PRISM_RELAY
    is EffectAction.Strike -> when (trigger) {
        EffectTrigger.WEAPON_HIT -> KkIcon.WEAPONS_MORNINGSTAR
        // A follow-up on a critical hit is a lance into the opened seam.
        EffectTrigger.CRIT -> KkIcon.WEAPONS_NULL_LANCE
        else -> trigger.watermarkIcon()
    }
    is EffectAction.Buff -> when {
        trigger == EffectTrigger.KILL -> KkIcon.SYSTEM_ELITE
        action.stat == BuffStat.CRIT_CHANCE -> KkIcon.FORMS_DIAMOND
        action.stat == BuffStat.DAMAGE_REDUCTION -> KkIcon.SYSTEM_SHIELD
        action.stat == BuffStat.COOLING -> KkIcon.SYSTEM_HEAT
        action.stat == BuffStat.ACTIVATION -> KkIcon.SYSTEM_CYCLE_CLOCK
        action.stat == BuffStat.WEAPON -> KkIcon.WEAPONS_QUASAR_CANNON
        // Damage while overheated: the redline's warning triangle.
        action.stat == BuffStat.DAMAGE && trigger == EffectTrigger.OVERHEAT -> KkIcon.FORMS_TRIANGLE
        else -> trigger.watermarkIcon()
    }
    // A beat coming round again.
    is EffectAction.RefundSkills -> KkIcon.WEAPONS_RESONANCE_DRUM
    // The Apex badge.
    is EffectAction.ChargeApex -> KkIcon.FORMS_STAR
    // A burst of reactions.
    is EffectAction.ForceReaction -> KkIcon.WEAPONS_SCATTER_BLOOM
    // A lens placed in the world.
    is EffectAction.Zone -> KkIcon.WEAPONS_EVENT_LENS
    is EffectAction.EraseShots -> KkIcon.WEAPONS_AEGIS_SHARDS
    // An arc of motion.
    is EffectAction.CoreImpulse -> KkIcon.FORMS_CRESCENT
    // A scar left on the target.
    is EffectAction.ApplyMark -> KkIcon.WEAPONS_HOLLOW_RIFT
    // A shove.
    is EffectAction.Throw -> KkIcon.WEAPONS_CONCUSSION_ENGINE
    // A coiled spring.
    EffectAction.PrimeDash -> KkIcon.FORMS_SPIRAL
    else -> trigger.watermarkIcon()
}

private fun EffectTrigger.watermarkIcon(): KkIcon = when (this) {
    EffectTrigger.IMPACT -> KkIcon.FORMS_RING
    EffectTrigger.WEAPON_HIT -> KkIcon.WEAPONS_PHASE_LATTICE
    EffectTrigger.CRIT -> KkIcon.FORMS_DIAMOND
    EffectTrigger.KILL -> KkIcon.WEAPONS_ENTROPY_FIELD
    EffectTrigger.DASH -> KkIcon.SYSTEM_DASH
    EffectTrigger.DASH_HIT -> KkIcon.WEAPONS_RIFT_BLADES
    EffectTrigger.BRAKE_RELEASE -> KkIcon.SYSTEM_BRAKE
    EffectTrigger.TURN -> KkIcon.WEAPONS_FLUX_WAKE
    EffectTrigger.HURT -> KkIcon.SYSTEM_INTEGRITY
    EffectTrigger.SHIELD_BREAK -> KkIcon.SYSTEM_SHIELD
    EffectTrigger.OVERDRIVE -> KkIcon.SYSTEM_OVERDRIVE
    EffectTrigger.OVERHEAT -> KkIcon.SYSTEM_HEAT
    EffectTrigger.REACTION -> KkIcon.WEAPONS_PRISM_RELAY
    EffectTrigger.BREAK -> KkIcon.SYSTEM_DISMANTLED
    EffectTrigger.LEVEL_UP -> KkIcon.FORMS_TRIANGLE
    EffectTrigger.PICKUP -> KkIcon.SYSTEM_DATA
    EffectTrigger.RICOCHET -> KkIcon.SYSTEM_ANOMALY
    EffectTrigger.SLAM -> KkIcon.SYSTEM_DISMANTLED
    EffectTrigger.SKILL_CAST -> KkIcon.WEAPONS_SOLAR_SWEEP
    EffectTrigger.SKILL_HIT -> KkIcon.WEAPONS_HUNTER_SALVO
    EffectTrigger.APEX -> KkIcon.SYSTEM_ELITE
    EffectTrigger.FIELD_ENTER -> KkIcon.WEAPONS_GRAVITY_MINES
    EffectTrigger.SHOT_ERASED -> KkIcon.WEAPONS_AEGIS_SHARDS
    EffectTrigger.MARK_CONSUMED -> KkIcon.WEAPONS_NULL_LANCE
}

private fun ItemEffect.watermarkIcon(): KkIcon = when (this) {
    ItemEffect.MASS -> KkIcon.FORMS_RING
    // Impact damage is the mace's head; mass stays the ring of the Core itself.
    ItemEffect.IMPACT_DAMAGE -> KkIcon.WEAPONS_MORNINGSTAR
    ItemEffect.WEAPON_POWER -> KkIcon.WEAPONS_QUASAR_CANNON
    // Attack speed is a stream of small shots, never the cannon's single heavy one.
    ItemEffect.ATTACK_SPEED -> KkIcon.WEAPONS_ION_SWARM
    ItemEffect.MAGNETISM, ItemEffect.PICKUP_RADIUS -> KkIcon.SYSTEM_POLARITY
    ItemEffect.COOLING -> KkIcon.SYSTEM_HEAT
    ItemEffect.MAX_INTEGRITY -> KkIcon.SYSTEM_INTEGRITY
    // Regeneration is a repair swarm at work, never a second hexagon beside the shield's.
    ItemEffect.REGEN -> KkIcon.WEAPONS_ION_SWARM
    ItemEffect.DASH_POWER, ItemEffect.DASH_EFFICIENCY -> KkIcon.SYSTEM_DASH
    ItemEffect.CRIT_CHANCE -> KkIcon.FORMS_DIAMOND
    // Critical damage is the cut a critical hit leaves.
    ItemEffect.CRIT_DAMAGE -> KkIcon.WEAPONS_RIFT_BLADES
    ItemEffect.LUCK -> KkIcon.FORMS_CIRCLE
    ItemEffect.DATA_GAIN -> KkIcon.SYSTEM_DATA
    ItemEffect.MATTER_GAIN -> KkIcon.SYSTEM_MATTER
    ItemEffect.SHIELD_CAPACITY, ItemEffect.DAMAGE_REDUCTION -> KkIcon.SYSTEM_SHIELD
    ItemEffect.COMBO_WINDOW -> KkIcon.SYSTEM_CYCLE_CLOCK
    ItemEffect.OVERDRIVE_GAIN -> KkIcon.SYSTEM_OVERDRIVE
}

private fun ItemRule.watermarkIcon(): KkIcon? = when (this) {
    is ItemRule.WhileModifier -> when (val condition = condition) {
        is EffectCondition.SpeedAtLeast, is EffectCondition.SpeedBelow -> KkIcon.WEAPONS_FLUX_WAKE
        EffectCondition.Braking -> KkIcon.SYSTEM_BRAKE
        EffectCondition.ShieldUp -> KkIcon.SYSTEM_SHIELD
        is EffectCondition.IntegrityBelow -> KkIcon.SYSTEM_INTEGRITY
        EffectCondition.CoreInField -> KkIcon.WEAPONS_GRAVITY_MINES
        EffectCondition.ApexActive -> KkIcon.SYSTEM_ELITE
        is EffectCondition.SkillsCharging -> KkIcon.SYSTEM_CYCLE_CLOCK
        is EffectCondition.WeaponFamilyIs -> condition.family.watermarkIcon()
        else -> KkIcon.SYSTEM_ANOMALY
    }
    is ItemRule.ElementPotency -> KkIcon.FORMS_TESSERACT
    is ItemRule.StatusDuration -> KkIcon.SYSTEM_CYCLE_CLOCK
    // Damage that grows with the combo: a cluster of hits, never the combo window's clock.
    is ItemRule.ComboDamage -> KkIcon.WEAPONS_ION_SWARM
    is ItemRule.BreakThreshold, is ItemRule.BreakPower -> KkIcon.SYSTEM_DISMANTLED
    is ItemRule.BreakSpread -> KkIcon.WEAPONS_ENTROPY_FIELD
    is ItemRule.ReactionPower, is ItemRule.ReactionResidue, is ItemRule.ReactionRecoil -> KkIcon.WEAPONS_PRISM_RELAY
    is ItemRule.AlwaysCrit, is ItemRule.NonCriticalDamage, is ItemRule.GuaranteedCritAfter -> KkIcon.FORMS_DIAMOND
    is ItemRule.CritDamageVersus, is ItemRule.DamageVersus -> KkIcon.SYSTEM_ANOMALY
    is ItemRule.BonusRerolls, is ItemRule.OfferCards -> KkIcon.SYSTEM_REROLL
    is ItemRule.FreeDash -> KkIcon.SYSTEM_DASH
    is ItemRule.Convert -> KkIcon.SYSTEM_MATTER
    is ItemRule.ReactionKeystone -> KkIcon.WEAPONS_TETHER_ARC
    is ItemRule.ReactionTuning -> KkIcon.WEAPONS_PRISM_RELAY
    is ItemRule.FieldTuning -> KkIcon.WEAPONS_GRAVITY_MINES
    is ItemRule.SkillCooldown, ItemRule.SkillCharges -> KkIcon.SYSTEM_CYCLE_CLOCK
    is ItemRule.SkillPower, is ItemRule.SkillAttunement -> KkIcon.WEAPONS_SOLAR_SWEEP
    is ItemRule.CatalysisRate -> KkIcon.WEAPONS_ARC_COIL
    is ItemRule.ApexCooldown -> KkIcon.SYSTEM_ELITE
    is ItemRule.CoreLinkRadius -> KkIcon.SYSTEM_POLARITY
}

/** A weapon family's mark (a family-gated "while" rule names the family by its emblem weapon or form). */
private fun WeaponFamily.watermarkIcon(): KkIcon = when (this) {
    WeaponFamily.MOMENTUM -> KkIcon.WEAPONS_GYRE_DISC
    WeaponFamily.ORBITAL -> KkIcon.FORMS_DIPOLE
    WeaponFamily.AURA -> KkIcon.WEAPONS_GLACIER_BELL
    WeaponFamily.PROJECTILE -> KkIcon.WEAPONS_SCATTER_BLOOM
    WeaponFamily.TRAP -> KkIcon.WEAPONS_SENTRY_GRID
    WeaponFamily.CHAIN -> KkIcon.FORMS_PENTAGON
    WeaponFamily.BEAM -> KkIcon.WEAPONS_SINGULARITY_SPEAR
    WeaponFamily.PULSE -> KkIcon.WEAPONS_CONCUSSION_ENGINE
}

/** Each relic's own watermark glyph (its rule in one mark), so relics of one aspect differ. */
internal fun RelicId.watermarkIcon(): KkIcon = when (this) {
    RelicId.PERPETUAL_VECTOR -> KkIcon.SYSTEM_DASH
    RelicId.BLINK_DRIVE -> KkIcon.WEAPONS_RIFT_BLADES
    RelicId.GYRE_ENGINE -> KkIcon.FORMS_RING
    RelicId.GLASS_COMET -> KkIcon.SYSTEM_DISMANTLED
    RelicId.EXECUTIONERS_PRISM -> KkIcon.WEAPONS_PRISM_RELAY
    RelicId.ECHO_CHAMBER -> KkIcon.WEAPONS_FLUX_WAKE
    RelicId.FURNACE_COVENANT -> KkIcon.SYSTEM_HEAT
    RelicId.CINDER_CROWN -> KkIcon.WEAPONS_ENTROPY_FIELD
    RelicId.OVERDRIVE_CASCADE -> KkIcon.SYSTEM_OVERDRIVE
    RelicId.ABSOLUTE_ZERO -> KkIcon.FORMS_DIAMOND
    RelicId.STASIS_ANCHOR -> KkIcon.TAG_ANCHOR
    RelicId.STORMHEART_CAPACITOR -> KkIcon.WEAPONS_ARC_COIL
    RelicId.AEGIS_INVERSION -> KkIcon.SYSTEM_SHIELD
    RelicId.NEUTRON_HEART -> KkIcon.SYSTEM_MATTER
    RelicId.EVENT_HORIZON -> KkIcon.WEAPONS_GRAVITY_MINES
    RelicId.VERDANT_ROT -> KkIcon.SYSTEM_INTEGRITY
    RelicId.PANDEMIC_CODEX -> KkIcon.WEAPONS_ION_SWARM
    RelicId.AGONY_SCEPTER -> KkIcon.WEAPONS_MORNINGSTAR
    RelicId.HOLLOW_CROWN -> KkIcon.ASPECT_SOVEREIGN
    RelicId.OUROBOROS_SEAL -> KkIcon.SYSTEM_CYCLE_CLOCK
    RelicId.THUNDER_CHOIR -> KkIcon.ASPECT_SONIC
    RelicId.NULL_COVENANT -> KkIcon.ASPECT_VOID
}

/** A weapon's body pattern and frame ornament, from what it is (orbital, aura, lance, mines, drones ...). */
internal fun WeaponId.cardMarks(): Pair<KkFillKind, KkCardOrnament> = when (this) {
    WeaponId.FLUX_WAKE -> KkFillKind.SHIMMER to KkCardOrnament.SLASHES
    WeaponId.MORNINGSTAR -> KkFillKind.HALFTONE_LARGE to KkCardOrnament.BUMPER
    WeaponId.PHASE_LATTICE -> KkFillKind.LATTICE to KkCardOrnament.DOUBLE_RIM
    WeaponId.NULL_LANCE -> KkFillKind.SLASH to KkCardOrnament.RETICLE
    WeaponId.GRAVITY_MINES -> KkFillKind.RINGS to KkCardOrnament.WEIGHT
    WeaponId.ION_SWARM -> KkFillKind.HALFTONE to KkCardOrnament.RACK
    WeaponId.RIFT_BLADES -> KkFillKind.SCANLINE to KkCardOrnament.SLASHES
    WeaponId.ARC_COIL -> KkFillKind.ZIGZAG to KkCardOrnament.PERFORATION
    WeaponId.QUASAR_CANNON -> KkFillKind.HAIRLINE to KkCardOrnament.VENTS
    WeaponId.ENTROPY_FIELD -> KkFillKind.PITTING to KkCardOrnament.PATTERN_CORNER
    WeaponId.SINGULARITY_SPEAR -> KkFillKind.SLASH to KkCardOrnament.VENTS
    WeaponId.PRISM_RELAY -> KkFillKind.LATTICE to KkCardOrnament.RETICLE
    WeaponId.GYRE_DISC -> KkFillKind.RINGS to KkCardOrnament.SLASHES
    WeaponId.SCATTER_BLOOM -> KkFillKind.HALFTONE to KkCardOrnament.RETICLE
    WeaponId.TETHER_ARC -> KkFillKind.ZIGZAG to KkCardOrnament.DOUBLE_RIM
    WeaponId.SENTRY_GRID -> KkFillKind.LATTICE to KkCardOrnament.RACK
    WeaponId.HUNTER_SALVO -> KkFillKind.HAIRLINE to KkCardOrnament.RACK
    WeaponId.CONCUSSION_ENGINE -> KkFillKind.HALFTONE_LARGE to KkCardOrnament.VENTS
    WeaponId.GLACIER_BELL -> KkFillKind.RINGS to KkCardOrnament.DOUBLE_RIM
    WeaponId.EVENT_LENS -> KkFillKind.PITTING to KkCardOrnament.WEIGHT
    WeaponId.SOLAR_SWEEP -> KkFillKind.SCANLINE to KkCardOrnament.PERFORATION
    WeaponId.AEGIS_SHARDS -> KkFillKind.SHIMMER to KkCardOrnament.BUMPER
    WeaponId.HOLLOW_RIFT -> KkFillKind.SLASH to KkCardOrnament.PATTERN_CORNER
    WeaponId.RESONANCE_DRUM -> KkFillKind.RINGS to KkCardOrnament.BUMPER
}

/** Identity-keyed memo of card looks (draw thread; a dealt hand plus the gallery's sheets). */
private object RewardCardLooks {
    private const val CAPACITY = 16
    private val presentations = arrayOfNulls<RewardCardPresentation>(CAPACITY)
    private val palettes = arrayOfNulls<KkRolePalette>(CAPACITY)
    private val looks = arrayOfNulls<RewardCardLook>(CAPACITY)
    private var next = 0

    fun get(presentation: RewardCardPresentation, roles: KkRolePalette): RewardCardLook {
        for (index in 0 until CAPACITY) {
            if (presentations[index] === presentation && palettes[index] === roles) return looks[index]!!
        }
        val look = buildRewardCardLook(presentation, roles)
        presentations[next] = presentation
        palettes[next] = roles
        looks[next] = look
        next = (next + 1) % CAPACITY
        return look
    }
}
