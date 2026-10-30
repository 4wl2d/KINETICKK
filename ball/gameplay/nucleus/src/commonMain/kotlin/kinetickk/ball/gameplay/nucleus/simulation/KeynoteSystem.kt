// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.Enemy
import kinetickk.ball.gameplay.nucleus.model.counterElement
import kinetickk.ball.gameplay.nucleus.model.profile
import kotlin.math.ceil
import kotlin.random.Random

/*
 * The run's Keynote: one featured BuildTag and one featured element. Both are drawn from the draft deck's stream after
 * every dealing draw, so the dealt order and every other stream stay put. The deck deals a share of the featured tag's
 * and element's passives, Elites lean toward the aspect weak to the element, and a banner names both at run start.
 */

/** Draws the Keynote from the deck stream [random]: the tag, then the element. */
internal fun MutableGameState.rollKeynote(random: Random) {
    keynoteTag = BuildTag.entries[random.nextInt(BuildTag.entries.size)]
    keynoteElement = StatusElement.entries[random.nextInt(StatusElement.entries.size)]
}

/** A passive item at most Rare carrying [tag]: what the Keynote tag floor counts and deals. */
internal fun ItemDefinition.isKeynotePassive(tag: BuildTag): Boolean = isFloorPassive() && tag in tags

/** A passive item at most Rare carrying [element]: what the Keynote element floor counts and deals. */
internal fun ItemDefinition.isKeynotePassive(element: StatusElement): Boolean = isFloorPassive() && element in elements

private fun ItemDefinition.isFloorPassive(): Boolean = !isActive && rarity.rank <= ItemRarity.RARE.rank

/** How many of the [qualifying] passives a Keynote floor of [share] asks the deck to hold: ⌈share × qualifying⌉. */
internal fun keynoteFloor(share: Float, qualifying: Int): Int = ceil(share * qualifying).toInt()

/**
 * Deals reserve passives (at most Rare) of the Keynote tag, then of its element, in deal priority until [share] of the
 * rank-eligible ones are dealt. No draw; a deck that deals everything, or a run without a Keynote, is left as it is.
 */
internal fun MutableGameState.promoteKeynoteFloors(share: Float) {
    val deck = itemDeck
    if (deck.dealsEverything || keynoteTag == null && keynoteElement == null) return
    val reserve = deck.reserve()
    val newlyDealt = ArrayList<Int>()
    fun promote(qualifies: (ItemDefinition) -> Boolean) {
        val eligible = content.items.filter { it.minimumRebirth <= rebirthRank && qualifies(it) }
        var missing = keynoteFloor(share, eligible.size) - eligible.count { it.id in deck || it.id in newlyDealt }
        for (id in reserve) {
            if (missing <= 0) return
            if (id in newlyDealt || !qualifies(content.items[id])) continue
            newlyDealt += id
            missing--
        }
    }
    keynoteTag?.let { tag -> promote { it.isKeynotePassive(tag) } }
    keynoteElement?.let { element -> promote { it.isKeynotePassive(element) } }
    if (newlyDealt.isNotEmpty()) itemDeck = deck.withDealt(newlyDealt)
}

/** The aspect whose weakness is [element] (the inverse of [counterElement]): an Elite of it is weak to the Keynote. */
internal fun weakAspectFor(element: StatusElement): Int {
    for (aspect in StatusElement.entries) if (counterElement(aspect.ordinal) == element.ordinal) return aspect.ordinal
    error("No aspect is weak to $element")
}

/**
 * An Elite whose stateless roll (keyed by its id, so no stream draws) falls under the Keynote's bias takes the featured
 * aspect in place of the one it drew from the spawn stream. The Elemental Storm raises the bias. No featured aspect, no
 * bias.
 */
internal fun MutableGameState.biasEliteAspect(enemy: Enemy) {
    if (featuredAspect < 0 || !enemy.isElite) return
    val policy = content.variety.keynote
    val bias =
        if (directorPersonality == DirectorPersonalityId.ELEMENTAL_STORM) policy.stormEliteAspectBias else policy.eliteAspectBias
    if (hashUnit(runHash(runSeed, ASPECT_BIAS_SALT, enemy.id.toLong())) < bias) {
        assignAspect(enemy, featuredAspect, immune = enemy.type.profile.aspectImmune)
    }
}

/**
 * The second run-start announcement: once the run reaches the Keynote time, the banner names the Keynote's tag and
 * element (a run without a Keynote passes the stage silently).
 */
internal fun MutableGameState.announceKeynote() {
    if (varietyAnnouncement >= KEYNOTE_ANNOUNCEMENT || elapsed < content.variety.director.keynoteAnnounceAtSeconds) return
    varietyAnnouncement = KEYNOTE_ANNOUNCEMENT
    val tag = keynoteTag ?: return
    val element = keynoteElement ?: return
    message = keynoteBanner(tag, element)
    messageTime = KEYNOTE_BANNER_SECONDS
}

/** The Keynote banner, an English content key the feed localizes. */
internal fun keynoteBanner(tag: BuildTag, element: StatusElement): String =
    "KEYNOTE // ${tag.displayLabel.uppercase()} // ${element.displayLabel.uppercase()}"

/** [MutableGameState.varietyAnnouncement] once the Keynote banner's time has passed. */
internal const val KEYNOTE_ANNOUNCEMENT: Int = 2

/** How long the Keynote banner shows, as long as the hostile pattern's before it. */
internal const val KEYNOTE_BANNER_SECONDS: Float = 2.4f
