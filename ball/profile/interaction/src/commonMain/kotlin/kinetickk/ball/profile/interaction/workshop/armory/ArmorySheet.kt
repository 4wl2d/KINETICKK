// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.armory

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.interaction.PROFILE_DESCRIPTION_MAX_LINES
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.fitKkText
import kinetickk.ball.profile.interaction.workshop.hangar.hangarFormIndex
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*
import kotlin.math.max

/** A weapon's element hue under the player's colour-vision preset; Kinetic weapons take the preset's volt. */
internal fun armoryElementColor(measurer: CanvasTextMeasurer, definition: WeaponDefinition): Color =
    definition.element?.let { measurer.roles.element(it.ordinal) } ?: measurer.roles.kinetic

/** A text of the weapon sheet at its offset (px) inside its section, with its color and probe role. */
internal class ArmorySheetText(
    val layout: TextLayoutResult,
    val x: Float,
    val y: Float,
    val color: Color,
    val role: String,
    val owner: Any? = null,
    val align: KkAlign = KkAlign.START,
    /** The text as written (before the style's casing), for the accessible description. */
    val spoken: String = layout.layoutInput.text.text,
)

/** The signature character's form glyph inside the synergy section: [fill] lit in `you` when it is the next run's character. */
internal class ArmorySheetGlyph(val formIndex: Int, val center: Offset, val radius: Float, val fill: Color, val ring: Color?)

/** One section of the weapon sheet: its texts in reading order, its height (px) and an optional form glyph. */
internal class ArmorySheetSection(
    val texts: List<ArmorySheetText>,
    val height: Float,
    val glyph: ArmorySheetGlyph? = null,
)

/**
 * The weapon sheet under the mastery ladder, measured for one panel width: the three mastery forms
 * by level, the live synergy (signature character and twist, heirloom harmony, tag resonance) and
 * the typed facts (family, element, Relic key, scaling input, cadence).
 */
internal class ArmorySheet(
    val forms: ArmorySheetSection,
    val synergy: ArmorySheetSection,
    val facts: ArmorySheetSection,
) {
    /** Every sheet text in reading order, for the detail panel's accessible description. */
    val spoken: String
        get() = armorySentences(listOf(forms, synergy, facts).flatMap { section -> section.texts.map { it.spoken } })

    /** The texts of the sections [layout] shows (a hidden segment's sections are 0 px tall). */
    fun spoken(layout: ArmoryLayout): String = armorySentences(
        listOf(forms to layout.forms, synergy to layout.synergy, facts to layout.facts)
            .filter { (_, rect) -> rect.height > 0f }
            .flatMap { (section, _) -> section.texts.map { it.spoken } },
    )
}

/** [parts] read as one description: each ends with a full stop unless it already ends a sentence. */
internal fun armorySentences(parts: List<String>): String = parts.filter { it.isNotBlank() }.joinToString(" ") { part ->
    val trimmed = part.trim()
    if (trimmed.last() in ".!?") trimmed else "$trimmed."
}

/** Display label of a weapon cadence. */
internal fun WeaponCadence.armoryLabel(language: AppLanguage): String = language.text(when (this) {
    WeaponCadence.CONTINUOUS -> ProfileScreensRedesignText.CadenceContinuous
    WeaponCadence.CONTACT -> ProfileScreensRedesignText.CadenceContact
    WeaponCadence.DISCRETE -> ProfileScreensRedesignText.CadenceDiscrete
})

/** The element of [definition] as the sheet names it: its element, or Kinetic. */
internal fun armoryElementLabel(definition: WeaponDefinition, language: AppLanguage): String =
    (definition.element?.displayLabel ?: KINETIC_LABEL).localizedContent(language)

private const val KINETIC_LABEL = "Kinetic"

/** Measures the weapon sheet of [weapon] for a panel [width] px wide (see [ArmorySheet]). */
internal fun armorySheet(
    measurer: CanvasTextMeasurer,
    frame: ProfileFrame,
    type: ArmoryType,
    weapon: WeaponDefinition,
    synergy: ArmorySynergy,
    formLevels: List<Int>,
    language: AppLanguage,
    width: Float,
): ArmorySheet {
    val k = frame.k
    val roles = measurer.roles
    val room = width.coerceAtLeast(frame.d(60f))
    val bodySize = type.body * ARMORY_SHEET_BODY * k
    fun title(text: String) = fitKkText(measurer, text, type.label * k, room, minFactor = 0.5f) { measurer.typography.labelStyle(it) }
    fun body(text: String, maxWidth: Float) = fitKkText(measurer, text, bodySize, maxWidth.coerceAtLeast(frame.d(40f)),
        maxLines = PROFILE_DESCRIPTION_MAX_LINES, uppercase = false, minFactor = 0.5f) {
        measurer.typography.bodyStyle(it, lineHeightEm = 1.32f)
    }
    fun mono(text: String, maxWidth: Float, lines: Int = 1, uppercase: Boolean = true) = fitKkText(measurer, text, type.ladderMono * k,
        maxWidth.coerceAtLeast(frame.d(40f)), maxLines = lines, uppercase = uppercase, minFactor = 0.5f) {
        measurer.typography.monoStyle(it, lineHeightEm = 1.4f)
    }

    // Mastery forms: "Lvl 3" and the form, one per row, coloured like the ladder's milestones.
    val forms = ArrayList<ArmorySheetText>()
    var y = 0f
    val formsTitle = title(language.text(ProfileScreensRedesignText.ArmoryForms))
    forms += ArmorySheetText(formsTitle, 0f, y, Kk.Mute, "armory.forms.title", spoken = language.text(ProfileScreensRedesignText.ArmoryForms))
    y += formsTitle.kkBoxHeight + frame.d(8f)
    val levelTexts = formLevels.map { language.text(ProfileScreensRedesignText.MasteryLevel, kkIntString(it)) }
    val levels = levelTexts.map { mono(it, room * 0.4f) }
    val levelColumn = (levels.maxOfOrNull { it.size.width.toFloat() } ?: 0f) + frame.d(12f)
    weapon.masteryForms.forEachIndexed { index, form ->
        val color = when (index) {
            weapon.masteryForms.lastIndex -> Kk.RLegend
            0 -> roles.you
            else -> Kk.Bone
        }
        val level = levels.getOrNull(index)
        val text = body(form.localizedContent(language), room - levelColumn)
        if (level != null) forms += ArmorySheetText(level, 0f, y, color, "armory.forms.level", index, spoken = levelTexts[index])
        forms += ArmorySheetText(text, levelColumn, y, Kk.Bone, "armory.forms.text", index)
        y += max(level?.kkBoxHeight ?: 0f, text.kkBoxHeight) + frame.d(6f)
    }
    val formsSection = ArmorySheetSection(forms, y - frame.d(6f))

    // Synergy: the signature character (lit when it is the next run's), its twist, harmony and resonance.
    val synergyTexts = ArrayList<ArmorySheetText>()
    y = 0f
    val synergyTitle = title(language.text(ProfileScreensRedesignText.ArmorySynergy))
    synergyTexts += ArmorySheetText(synergyTitle, 0f, y, Kk.Mute, "armory.synergy.title", spoken = language.text(ProfileScreensRedesignText.ArmorySynergy))
    y += synergyTitle.kkBoxHeight + frame.d(10f)
    val character = synergy.signature?.displayName?.localizedContent(language) ?: weapon.signature.name
    val glyphRadius = frame.d(type.label) * 0.62f * measurer.scale.coerceIn(1f, 2f)
    val indent = glyphRadius * 2f + frame.d(10f)
    val signatureText = language.text(ProfileScreensRedesignText.ArmorySignatureOf, character)
    val signatureName = fitKkText(measurer, signatureText, type.label * k,
        room - indent, maxLines = 2, minFactor = 0.5f) { measurer.typography.labelStyle(it) }
    val rowHeight = max(glyphRadius * 2f, signatureName.kkBoxHeight)
    val glyph = ArmorySheetGlyph(weapon.signature.hangarFormIndex(), Offset(glyphRadius, y + rowHeight * 0.5f), glyphRadius * 0.78f,
        fill = if (synergy.signatureActive) roles.you else Kk.Bone2, ring = if (synergy.signatureActive) roles.you else null)
    synergyTexts += ArmorySheetText(signatureName, indent, y + (rowHeight - signatureName.kkBoxHeight) * 0.5f,
        if (synergy.signatureActive) roles.you else Kk.Bone, "armory.synergy.signature", spoken = signatureText)
    y += rowHeight + frame.d(4f)
    val signatureState = mono(
        if (synergy.signatureActive) language.text(ProfileScreensRedesignText.ArmorySignatureActive)
        else language.text(ProfileScreensRedesignText.ArmorySignatureInactive, character),
        room - indent, lines = PROFILE_DESCRIPTION_MAX_LINES, uppercase = false,
    )
    synergyTexts += ArmorySheetText(signatureState, indent, y, if (synergy.signatureActive) roles.you else Kk.Mute, "armory.synergy.state")
    y += signatureState.kkBoxHeight + frame.d(8f)
    val twistLabel = mono(language.text(ProfileScreensRedesignText.ArmoryTwist), room - indent)
    synergyTexts += ArmorySheetText(twistLabel, indent, y, Kk.Mute, "armory.synergy.twist.label", spoken = language.text(ProfileScreensRedesignText.ArmoryTwist))
    y += twistLabel.kkBoxHeight + frame.d(2f)
    val twist = body(weapon.signatureText.localizedContent(language), room - indent)
    synergyTexts += ArmorySheetText(twist, indent, y, Kk.Bone, "armory.synergy.twist")
    y += twist.kkBoxHeight + frame.d(14f)
    val key = weapon.key.displayLabel.localizedContent(language)
    val harmonyTitleText = language.text(ProfileScreensRedesignText.ArmoryHarmony, key)
    val harmonyTitle = fitKkText(measurer, harmonyTitleText, type.label * k, room,
        maxLines = 2, minFactor = 0.5f) { measurer.typography.labelStyle(it) }
    val harmonized = synergy.harmonizing.isNotEmpty()
    synergyTexts += ArmorySheetText(harmonyTitle, 0f, y, if (harmonized) roles.you else Kk.Bone, "armory.synergy.harmony", spoken = harmonyTitleText)
    y += harmonyTitle.kkBoxHeight + frame.d(4f)
    val harmonyText = body(
        if (harmonized) language.text(ProfileScreensRedesignText.ArmoryHarmonyCandidates,
            synergy.harmonizing.joinToString(", ") { it.name.localizedContent(language) })
        else language.text(ProfileScreensRedesignText.ArmoryHarmonyNone),
        room,
    )
    synergyTexts += ArmorySheetText(harmonyText, 0f, y, Kk.Bone2, "armory.synergy.harmony.text")
    y += harmonyText.kkBoxHeight + frame.d(14f)
    val resonanceTitle = title(language.text(ProfileScreensRedesignText.ArmoryResonance))
    synergyTexts += ArmorySheetText(resonanceTitle, 0f, y, Kk.Bone, "armory.synergy.resonance", spoken = language.text(ProfileScreensRedesignText.ArmoryResonance))
    y += resonanceTitle.kkBoxHeight + frame.d(4f)
    val tags = weapon.tags.map { it.displayLabel.localizedContent(language) }
    val resonance = body(language.text(ProfileScreensRedesignText.ArmoryResonanceText, tags.getOrElse(0) { "" }, tags.getOrElse(1) { "" }), room)
    synergyTexts += ArmorySheetText(resonance, 0f, y, Kk.Bone2, "armory.synergy.resonance.text")
    y += resonance.kkBoxHeight
    val synergySection = ArmorySheetSection(synergyTexts, y, glyph)

    // Facts: label at the left, value at the right edge (the element value in its hue).
    val facts = ArrayList<ArmorySheetText>()
    y = 0f
    val rows = listOf(
        ProfileScreensRedesignText.ArmoryFamily to (weapon.family.displayLabel.localizedContent(language) to Kk.Bone),
        ProfileScreensRedesignText.ArmoryElement to (armoryElementLabel(weapon, language) to armoryElementColor(measurer, weapon)),
        ProfileScreensRedesignText.ArmoryKey to (key to Kk.Bone),
        ProfileScreensRedesignText.ArmoryScaling to (weapon.scaling.displayLabel.localizedContent(language) to Kk.Bone),
        ProfileScreensRedesignText.ArmoryCadence to (weapon.cadence.armoryLabel(language) to Kk.Bone),
    )
    rows.forEachIndexed { index, (label, value) ->
        val labelText = language.text(label)
        val labelLayout = mono(labelText, room * 0.5f)
        val valueLayout = fitKkText(measurer, value.first, type.label * k, room - labelLayout.size.width - frame.d(12f), minFactor = 0.5f) {
            measurer.typography.labelStyle(it)
        }
        val height = max(labelLayout.kkBoxHeight, valueLayout.kkBoxHeight)
        facts += ArmorySheetText(labelLayout, 0f, y + (height - labelLayout.kkBoxHeight) * 0.5f, Kk.Mute, "armory.facts.label", index,
            spoken = labelText + " " + value.first)
        facts += ArmorySheetText(valueLayout, room, y + (height - valueLayout.kkBoxHeight) * 0.5f, value.second, "armory.facts.value", index,
            align = KkAlign.END, spoken = "")
        y += height + frame.d(8f)
    }
    val factsSection = ArmorySheetSection(facts, y - frame.d(8f))
    return ArmorySheet(formsSection, synergySection, factsSection)
}

/** The sheet's body size as a share of the description's. */
private const val ARMORY_SHEET_BODY = 0.86f
