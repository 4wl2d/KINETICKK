// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.canvas.hud

import androidx.compose.ui.graphics.Color
import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.interaction.fx.BuildNoticeKind
import kinetickk.ball.gameplay.interaction.fx.BuildNoticeTitles
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.localization.HudRedesignText
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.*

/** Game messages the HUD shows as state (stamps, stripes, glows, pips) rather than as text. */
private val StateMessages = hashSetOf("OVERHEAT", "POLARITY FIELD STRAIN", "DASH ONLINE", "KINETIC OVERDRIVE")

/** Whether the current run message appears in the feed (see [drawHudFeed]). */
internal fun GameplayRenderModel.showsMessage(): Boolean {
    if (messageTime <= 0f || message.isBlank() || message in StateMessages) return false
    val definitions = content.sites.definitions
    for (index in definitions.indices) if (definitions[index].instruction == message) return false
    return true
}

/**
 * Message tails that only tell the player what to do; the feed shows the event, not the
 * instruction (explanations live behind (!) buttons).
 */
private val InstructionTails = hashSetOf("CHOOSE A COURSE")

/** A weapon amplify that reaches no mastery milestone (`amplifyCurrentWeapon`): " // LEVEL n". */
private val LevelTail = Regex("LEVEL ([0-9]+)")

/** A relic replace (`replaceRelic`): " // SLOT n BOUND"; the slot's position is not shown. */
private val SlotBoundTail = Regex("SLOT [0-9]+ BOUND")
private const val BOUND_TAIL = "BOUND"

/**
 * A run message as feed text: the localized title (before the first " // ") and its display-cased
 * detail, or no detail when the tail is an instruction. A weapon level reads in the short level
 * form ("Lvl 7" / "Ур. 7"); a replaced relic reads as bound, without its slot number. Allocates;
 * called only when the message changes.
 */
internal fun feedMessageParts(message: String, language: AppLanguage): Pair<String, String?> {
    val localized = message.localizedContent(language)
    val split = localized.indexOf(" // ")
    if (split < 0) return localized to null
    val title = localized.substring(0, split)
    val sourceSplit = message.indexOf(" // ")
    val tail = if (sourceSplit >= 0) message.substring(sourceSplit + 4).trim() else ""
    if (tail in InstructionTails) return title to null
    LevelTail.matchEntire(tail)?.let { level ->
        return title to language.text(HudRedesignText.WeaponLevel, level.groupValues[1].toInt())
    }
    if (SlotBoundTail.matches(tail)) return title to BOUND_TAIL.localizedContent(language).uppercase()
    return title to localized.substring(split + 4).replace(" // ", "  ").uppercase()
}

/** The current message's feed text, rebuilt only when the message or language changes. */
internal class SplitMessage {
    private var source: String? = null
    private var language: AppLanguage? = null
    var title = ""
        private set
    var detail: String? = null
        private set

    fun update(message: String, language: AppLanguage) {
        if (message == source && language === this.language) return
        source = message
        this.language = language
        val parts = feedMessageParts(message, language)
        title = parts.first
        detail = parts.second
    }
}

/**
 * A string localized once per (source, language). A synergy line keeps its sign on the name's line
 * (a no-break space follows "+" / "−") when it wraps. The nucleus's discovery titles read through
 * [HudRedesignText]; a resonance or affinity tier line ("+ Ram II") loses its numeral, which the
 * feed draws as lit pips ([tier]); a tier or Break line knows its element ([element]).
 */
internal class LocalizedText {
    private var source: String? = null
    private var language: AppLanguage? = null
    private var text = ""

    /** Tier 1..3 of a resonance or affinity line, 0 otherwise. */
    var tier = 0
        private set

    /** The element a tier or Break line names, -1 otherwise. */
    var element = -1
        private set

    fun of(value: String, language: AppLanguage): String {
        if (value != source || language !== this.language) {
            source = value
            this.language = language
            tier = 0
            element = -1
            val title = discoveryTitle(value)
            val tierLine = if (title == null) TierLine.matchEntire(value) else null
            val label = tierLine?.groupValues?.get(2)
            val tag = label?.let { name -> BuildTag.entries.firstOrNull { it.displayLabel == name } }
            val tierElement = label?.let { name -> StatusElement.entries.firstOrNull { it.displayLabel == name } }
            text = when {
                title != null -> language.text(title)
                tierLine != null && (tag != null || tierElement != null) -> {
                    tier = tierLine.groupValues[3].length
                    element = tierElement?.ordinal ?: -1
                    tierLine.groupValues[1] + "\u00A0" + label.localizedContent(language)
                }
                else -> {
                    element = StatusElement.entries.firstOrNull { value == "+ " + it.breakLabel }?.ordinal ?: -1
                    val localized = value.localizedContent(language)
                    if (isSynergyLine(localized)) localized[0] + "\u00A0" + localized.substring(2) else localized
                }
            }
        }
        return text
    }
}

/** A resonance or affinity tier reached ("+ Ram II", `emitBuildChange`). */
private val TierLine = Regex("([+\u2212]) (.+) (I|II|III)")

/** The feed's own wording for the nucleus's discovery titles and its Break notes. */
private fun discoveryTitle(title: String): HudRedesignText? = when (title) {
    BuildNoticeTitles.REACTION_DISCOVERED -> HudRedesignText.ReactionDiscovered
    BuildNoticeTitles.PAIRING_DISCOVERED -> HudRedesignText.PairingDiscovered
    BuildNoticeTitles.INCOMPATIBILITY_DISCOVERED -> HudRedesignText.IncompatibilityDiscovered
    BuildNoticeTitles.BREAK -> HudRedesignText.BreakNotice
    else -> null
}

/**
 * How one toast is marked, resolved once per toast: its gem's colors (a reaction's two elements,
 * a pairing in bone for concord or threat for discord, as its brackets read in the HUD and pause,
 * a warning in threat, a Break in its element, a Legendary in gold, acquisitions in the you color),
 * whether its lines read bright (discoveries name things worth reading) and whether its title reads
 * in gold (a Legendary).
 */
internal class FeedNoticeStyle {
    // Keyed by value: a toast is copied every frame as its life runs down, keeping its details list.
    private var title: String? = null
    private var details: List<String>? = null
    private var kind: BuildNoticeKind? = null
    private var content: GameplayContentSnapshot? = null
    private var firstCode = YOU
    private var secondCode = YOU
    var bright = false
        private set
    var gold = false
        private set

    fun of(notice: BuildNotificationProjection, content: GameplayContentSnapshot): FeedNoticeStyle {
        if (notice.details === details && notice.kind == kind && notice.title == title && content === this.content) return this
        title = notice.title
        details = notice.details
        kind = notice.kind
        this.content = content
        val line = notice.details.firstOrNull().orEmpty()
        bright = notice.kind != BuildNoticeKind.CHANGE
        gold = notice.kind == BuildNoticeKind.LEGENDARY
        firstCode = YOU
        secondCode = YOU
        when (notice.kind) {
            BuildNoticeKind.CHANGE -> Unit
            BuildNoticeKind.LEGENDARY -> {
                firstCode = GOLD
                secondCode = GOLD
            }
            BuildNoticeKind.WARNING -> {
                firstCode = THREAT
                secondCode = THREAT
            }
            BuildNoticeKind.BREAK -> {
                val element = StatusElement.entries.firstOrNull { line == "+ " + it.breakLabel }
                firstCode = element?.ordinal ?: BONE
                secondCode = firstCode
            }
            BuildNoticeKind.DISCOVERY -> if (notice.title == BuildNoticeTitles.PAIRING_DISCOVERED) {
                firstCode = if (line.startsWith("\u2212 ")) THREAT else BONE
                secondCode = firstCode
            } else {
                val reaction = content.build.reactions.firstOrNull { line == "+ " + it.name }
                firstCode = reaction?.id?.first?.ordinal ?: BONE
                secondCode = reaction?.id?.second?.ordinal ?: BONE
            }
        }
        return this
    }

    fun first(roles: KkRolePalette): Color = color(firstCode, roles)

    fun second(roles: KkRolePalette): Color = color(secondCode, roles)

    /** The title's color: a Legendary's in the preset's light gold, every other in bone. */
    fun title(roles: KkRolePalette): Color = if (gold) roles.gold.light else Kk.Bone

    private fun color(code: Int, roles: KkRolePalette): Color = when (code) {
        YOU -> roles.you
        THREAT -> roles.threat
        BONE -> Kk.Bone
        GOLD -> roles.gold.base
        else -> roles.element(code)
    }

    private companion object {
        const val YOU = -1
        const val THREAT = -2
        const val BONE = -3
        const val GOLD = -4
    }
}
