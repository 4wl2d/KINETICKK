// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.liabilityText
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.content.api.upsideText
import kinetickk.ball.gameplay.interaction.canvas.look.OverlayRelicLink
import kinetickk.ball.gameplay.interaction.canvas.look.overlayColor
import kinetickk.ball.gameplay.interaction.canvas.look.overlayDecimal
import kinetickk.ball.gameplay.interaction.canvas.look.overlayDescription
import kinetickk.ball.gameplay.interaction.canvas.look.overlayIcon
import kinetickk.ball.gameplay.interaction.canvas.look.overlayLinkMultiplier
import kinetickk.ball.gameplay.interaction.canvas.look.overlayName
import kinetickk.ball.gameplay.interaction.canvas.look.overlayRelicLinks
import kinetickk.ball.gameplay.interaction.canvas.look.overlayUpsideMultiplier
import kinetickk.ball.gameplay.interaction.localization.OverlayRedesignText
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.KkIcon
import kinetickk.foundation.design.label
import kinetickk.ball.gameplay.interaction.canvas.look.*
import kinetickk.ball.gameplay.interaction.overlay.meldTotal

/**
 * The game's outcome of a relic action, named for its button or tag: a new Relic binds into one of [openSlots] (or
 * replaces a bound one without), a duplicate ranks up (or salvages at the top rank), and the Meld seals the empty slot.
 */
internal fun rewardRelicAction(
    action: RelicChoiceAction,
    ownedRank: Int,
    openSlots: Int,
    policy: RelicPolicy,
    language: AppLanguage,
): RewardAction = when (action) {
    RelicChoiceAction.ACQUIRE -> when {
        ownedRank >= policy.maxRank -> RewardAction(language.text(OverlayRedesignText.Salvage), RewardTone.MUTE)
        ownedRank > 0 -> RewardAction(language.text(OverlayRedesignText.RankUp), RewardTone.YOU)
        openSlots > 0 -> RewardAction(language.text(OverlayRedesignText.Bind), RewardTone.YOU)
        else -> RewardAction(language.text(OverlayRedesignText.Replace), RewardTone.THREAT)
    }
    RelicChoiceAction.MELD -> RewardAction(language.text(OverlayRedesignText.Meld), RewardTone.YOU)
    RelicChoiceAction.REPLACE -> RewardAction(language.text(OverlayRedesignText.Replace), RewardTone.THREAT)
    RelicChoiceAction.DECLINE -> RewardAction(language.text(OverlayRedesignText.Decline), RewardTone.MUTE)
}

/** Relic slots of the render model a Relic may still bind into: neither bound nor sealed by a Meld. */
internal fun GameplayRenderModel.openRelicSlots(): Int =
    (content.relicPolicy.maxSlots - equippedRelics.size - build.meldedSlots).coerceAtLeast(0)

internal fun GameplayRenderModel.rewardRelicPreview(
    choice: ChoiceOption,
    preview: kinetickk.ball.gameplay.nucleus.render.RewardPreview?,
    language: AppLanguage,
): RewardRelicPreview {
    val policy = content.relicPolicy
    val current: List<RelicId?> = List(policy.maxSlots) { equippedRelics.getOrNull(it)?.id }
    val action = requireNotNull(choice.relicAction)
    val relicId = choice.relicId
    val slot = choice.relicSlot
    val ownedSlot = relicId?.let { id -> equippedRelics.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    val openSlots = openRelicSlots()
    val (target, candidate) = when (action) {
        RelicChoiceAction.ACQUIRE -> when {
            relicId == null -> null to current
            ownedSlot != null -> ownedSlot to current
            openSlots > 0 -> equippedRelics.size to current.toMutableList().also { it[equippedRelics.size] = relicId }
            else -> null to current
        }
        RelicChoiceAction.REPLACE -> if (slot != null && relicId != null && slot in current.indices) {
            slot to current.toMutableList().also { it[slot] = relicId }
        } else slot to current
        // A Meld seals the last open slot: sealed slots fill the matrix from its end.
        RelicChoiceAction.MELD -> (equippedRelics.size + openSlots - 1).takeIf { openSlots > 0 } to current
        RelicChoiceAction.DECLINE -> null to current
    }
    val before = overlayRelicLinks(current)
    val after = overlayRelicLinks(candidate)
    val added = after.filter { link -> before.none(link::joinsSame) }
    val removed = before.filter { link -> after.none(link::joinsSame) }
    val kept = before.filter { link -> removed.none(link::joinsSame) }
    val slotRelic = slot?.let(equippedRelics::getOrNull)
    val relicAction = rewardRelicAction(action, relicId?.let(::relicRank) ?: 0, openSlots, policy, language)
    // The relic the preview is about: the one entering the matrix (or ranking up).
    val subject = when (action) {
        RelicChoiceAction.ACQUIRE, RelicChoiceAction.REPLACE -> relicId?.takeIf { target != null }
        else -> null
    }
    // The Hollow Crown's weakening is its liability: it lingers through severance, and replacing
    // the Crown severs it. The nucleus reports the current multipliers when it has compiled them.
    val crownSevered = build.severed.any { it.id == RelicId.HOLLOW_CROWN }
    val crownSeveredAfter = crownSevered || (action == RelicChoiceAction.REPLACE && slot?.let(current::getOrNull) == RelicId.HOLLOW_CROWN)
    fun upsideNow(index: Int): Float {
        val id = current.getOrNull(index) ?: return 1f
        return if (build.relicUpsides.isNotEmpty()) build.relicUpside(id) else overlayUpsideMultiplier(current, index, content, crownSevered)
    }
    fun upsideAfter(index: Int): Float = overlayUpsideMultiplier(candidate, index, content, crownSeveredAfter)
    fun name(id: RelicId) = content.relic(id).name.localizedContent(language)
    /** A link row about the partner relic in slot [partner]: its name, the link and its multiplier now and after. */
    fun linkRow(link: OverlayRelicLink, sign: String, tone: RewardTone, partner: Int?): RewardPreviewRow {
        val generic = link.pairing == null
        val partnerId = partner?.let { candidate.getOrNull(it) ?: current.getOrNull(it) }
        return RewardPreviewRow(
            sign = sign,
            title = link.overlayName(content, language),
            detail = link.overlayDescription(content, language),
            tone = tone,
            color = link.overlayColor(),
            kicker = partnerId?.let(::name),
            before = if (generic && partner != null) overlayLinkMultiplier(upsideNow(partner), language) else null,
            after = if (generic && partner != null) overlayLinkMultiplier(upsideAfter(partner), language) else null,
            unknown = !link.discovered,
        )
    }
    fun partnerOf(link: OverlayRelicLink, self: Int?): Int? = link.slots.firstOrNull { it != self } ?: link.slots.firstOrNull()
    val rows = buildList {
        if (action == RelicChoiceAction.REPLACE && slotRelic != null) {
            val lost = content.relic(slotRelic.id)
            add(RewardPreviewRow("−", lost.name.localizedContent(language), lost.upsideText(language, slotRelic.rank, numberScale), RewardTone.THREAT))
            // Severance: the replaced relic's liability lingers after it leaves.
            add(RewardPreviewRow("=", language.text(OverlayRedesignText.CostStays, overlayDecimal(content.build.policy.severanceSeconds, language)),
                lost.liabilityText(language), RewardTone.THREAT))
        }
        // A duplicate's rank-up: the panel shows the whole upside at the next rank, the row only the values that grow.
        val owned = ownedSlot?.let(equippedRelics::getOrNull)
        if (action == RelicChoiceAction.ACQUIRE && owned != null && owned.rank < policy.maxRank) {
            val relic = content.relic(owned.id)
            val (before, after) = relic.rankValueChange(language, owned.rank, owned.rank + 1, numberScale)
            add(RewardPreviewRow("+", language.text(OverlayRedesignText.Rank, owned.rank + 1),
                if (after == null) relic.upsideText(language, owned.rank + 1, numberScale) else null, RewardTone.YOU, before = before, after = after))
        }
        // The Meld's lasting bonus once this slot is sealed (it compounds with earlier Melds).
        if (action == RelicChoiceAction.MELD && target != null) {
            add(RewardPreviewRow("+", meldTotal(build.meldedSlots + 1, language), null, RewardTone.YOU))
        }
        // The subject's own upside multiplier, now and after the choice, when its links change it.
        if (subject != null && target != null) {
            val now = current.indexOf(subject).takeIf { it >= 0 }?.let(::upsideNow) ?: 1f
            val next = upsideAfter(target)
            if (now != next || next != 1f) {
                val (sign, tone) = when {
                    next > now -> "+" to RewardTone.YOU
                    next < now -> "−" to RewardTone.THREAT
                    else -> "=" to RewardTone.FIXED
                }
                add(RewardPreviewRow(sign, language.text(OverlayRedesignText.Upside), null, tone,
                    before = overlayLinkMultiplier(now, language), after = overlayLinkMultiplier(next, language)))
            }
        }
        // Losing interference is a gain; forming it is a loss.
        removed.forEach { link ->
            add(if (link.interferes) linkRow(link, "+", RewardTone.YOU, partnerOf(link, slot)) else linkRow(link, "−", RewardTone.THREAT, partnerOf(link, slot)))
        }
        added.forEach { link ->
            add(if (link.interferes) linkRow(link, "−", RewardTone.THREAT, partnerOf(link, target)) else linkRow(link, "+", RewardTone.FIXED, partnerOf(link, target)))
        }
        // Kept links of the subject (a rank-up keeps every link of its relic).
        kept.filter { link -> subject == null || subject in link.relics }.forEach { link ->
            add(linkRow(link, "=", RewardTone.FIXED, partnerOf(link, target)).copy(before = null, after = null))
        }
    }
    val panelRelic = relicId?.let(content::relic)
    val panelRank = when (action) {
        RelicChoiceAction.REPLACE -> 1
        else -> relicId?.let { (relicRank(it) + 1).coerceAtMost(policy.maxRank) }
    }
    return RewardRelicPreview(
        targetSlot = target,
        replace = action == RelicChoiceAction.REPLACE,
        meld = action == RelicChoiceAction.MELD,
        incomingAspect = when (action) {
            RelicChoiceAction.ACQUIRE, RelicChoiceAction.REPLACE -> relicId?.let { content.relic(it).aspect }
            else -> null
        },
        addedLinks = added,
        addedNames = added.map { it.overlayName(content, language) },
        removedLinks = removed,
        rows = rows,
        panel = panelRelic?.let { relic ->
            rewardRelicPanel(relic, panelRank ?: 1, candidate.filterNotNull(), language, choice.heirloom)
        },
        kicker = choice.title.localizedContent(language),
        primaryLabel = relicAction.label,
        incoming = when (action) {
            RelicChoiceAction.ACQUIRE, RelicChoiceAction.REPLACE -> relicId?.let(content::relic)
            else -> null
        },
    )
}

/** A weapon offer's family and element ("Trap / Gravitic"; a weapon without an element is Kinetic). */
internal fun rewardWeaponFamily(weapon: WeaponDefinition, language: AppLanguage): String =
    weapon.family.displayLabel.localizedContent(language) + " / " + (weapon.element?.displayLabel ?: KineticLabel).localizedContent(language)

private const val KineticLabel = "Kinetic"

/**
 * A weapon offer's identity chips: Signature when the run's character is its signature character
 * (the first chip, so it always shows), its element, then its two tags (lit when the build has points in them).
 */
internal fun GameplayRenderModel.rewardWeaponChips(weapon: WeaponDefinition, language: AppLanguage): List<RewardChip> = buildList {
    if (weapon.signature == coreShape) add(RewardChip(RewardChipKind.SIGNATURE, language.text(OverlayRedesignText.Signature), core = coreShape))
    add(RewardChip(RewardChipKind.ELEMENT, (weapon.element?.displayLabel ?: KineticLabel).localizedContent(language),
        elements = weapon.element?.let(::listOf).orEmpty()))
    weapon.tags.forEach { tag ->
        add(RewardChip(RewardChipKind.TAG, tag.displayLabel.localizedContent(language), tag = tag, active = build.tagPoints(tag) > 0))
    }
}

/** A weapon row's meta line on the totem: family, element and tags. */
private fun rewardWeaponMeta(weapon: WeaponDefinition, language: AppLanguage): String =
    rewardWeaponFamily(weapon, language) + " / " + weapon.tags.joinToString(", ") { it.displayLabel.localizedContent(language) }

/** A totem offering row; [description] is the card's effect line (never the game's step text). */
internal fun GameplayRenderModel.rewardTotemRow(
    choice: ChoiceOption,
    weapon: WeaponDefinition?,
    description: String,
    language: AppLanguage,
): RewardTotemRow {
    val mastery = { level: Int -> content.weaponMasteryForLevel(level).displayLabel.localizedContent(language) }
    val focus = choice.rewardFocus
    return when {
        focus != null -> RewardTotemRow(
            icon = focus.overlayIcon(), kind = choice.tag.localizedContent(language), kindTone = RewardTone.MUTE,
            meta = null, name = choice.title.localizedContent(language), description = description,
            level = null, fromLevel = 0, toLevel = 0, mastery = null,
        )
        choice.totemAction == TotemAction.AMPLIFY_CURRENT -> RewardTotemRow(
            icon = this.weapon.overlayIcon(), kind = language.text(OverlayRedesignText.UpgradeKind), kindTone = RewardTone.MUTE,
            meta = language.text(OverlayRedesignText.Equipped), name = currentWeaponDefinition.name.localizedContent(language),
            description = description,
            level = weaponLevel + 1, fromLevel = weaponLevel, toLevel = weaponLevel + 1, mastery = mastery(weaponLevel + 1),
            badge = language.text(OverlayRedesignText.Signature).takeIf { currentWeaponDefinition.signature == coreShape },
        )
        choice.totemAction == TotemAction.CHANGE_WEAPON -> RewardTotemRow(
            icon = KkIcon.SYSTEM_REROLL, kind = language.text(OverlayRedesignText.ChangeKind), kindTone = RewardTone.YOU,
            meta = null, name = choice.title.localizedContent(language), description = description,
            level = weaponLevel, fromLevel = weaponLevel, toLevel = weaponLevel, mastery = mastery(weaponLevel),
        )
        weapon != null -> RewardTotemRow(
            icon = weapon.id.overlayIcon(), kind = language.text(OverlayRedesignText.NewKind), kindTone = RewardTone.YOU,
            meta = rewardWeaponMeta(weapon, language), name = weapon.name.localizedContent(language), description = description,
            level = weaponLevel, fromLevel = weaponLevel, toLevel = weaponLevel, mastery = mastery(weaponLevel),
            badge = language.text(OverlayRedesignText.Signature).takeIf { weapon.signature == coreShape },
        )
        else -> RewardTotemRow(
            icon = KkIcon.SYSTEM_DATA, kind = choice.tag.localizedContent(language), kindTone = RewardTone.MUTE, meta = null,
            name = choice.title.localizedContent(language), description = description,
            level = null, fromLevel = 0, toLevel = 0, mastery = null,
        )
    }
}
