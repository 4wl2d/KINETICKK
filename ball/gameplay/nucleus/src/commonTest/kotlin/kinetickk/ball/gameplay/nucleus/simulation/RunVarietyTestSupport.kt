// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.DirectorPersonalityId
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.KeynotePolicy
import kinetickk.ball.content.api.OmenId
import kinetickk.ball.content.api.RunVarietyContent
import kinetickk.ball.gameplay.api.GameplayInteractionPulse
import kinetickk.ball.gameplay.nucleus.GameplayDecision
import kinetickk.ball.gameplay.nucleus.GameplayNucleus
import kinetickk.ball.gameplay.nucleus.GameplayNucleusPulse
import kinetickk.ball.gameplay.nucleus.GameplayState
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kotlin.test.assertIs

/** The content with every run-variety system switched off: the run plays as it did before them. */
internal fun GameplayContentSnapshot.withoutRunVariety(): GameplayContentSnapshot = copy(variety = RunVarietyContent.DISABLED)

/** The content with ambient world events switched off: a run plans none. */
internal fun GameplayContentSnapshot.withoutWorldEvents(): GameplayContentSnapshot =
    copy(variety = variety.copy(worldEvents = variety.worldEvents.copy(enabled = false)))

/**
 * The content with the director's variety switched off: no personality, no onslaught themes, no Elite affixes and no
 * champions, so the director paces and spawns as it did before them.
 */
internal fun GameplayContentSnapshot.withoutDirectorVariety(): GameplayContentSnapshot = copy(
    variety = variety.copy(
        director = variety.director.copy(personalitiesEnabled = false, surgeThemesEnabled = false),
        affixes = variety.affixes.copy(enabled = false),
        champions = variety.champions.copy(enabled = false),
    ),
)

/** The content with the Keynote switched off: no featured tag or element, no Keynote floors and no Elite bias. */
internal fun GameplayContentSnapshot.withoutKeynote(): GameplayContentSnapshot = withKeynote(KeynotePolicy(enabled = false))

/** The content with [keynote] in place of the shipped Keynote policy. */
internal fun GameplayContentSnapshot.withKeynote(keynote: KeynotePolicy): GameplayContentSnapshot =
    copy(variety = variety.copy(keynote = keynote))

/** Accepts omen [id] at once (null: No omen) and drops a pending offer, as a harness arm that plays one omen. */
internal fun MutableGameState.forceRunOmenForTesting(id: OmenId?) {
    omenPending = false
    id?.let { acceptRunOmen(it) }
}

/**
 * Makes [id] the run's personality (null: the plain director) with its featured aspect, then resets the director so the
 * opening calm takes the personality's length. Both arms of a comparison take the same extra draw.
 */
internal fun MutableGameState.forcePersonalityForTesting(id: DirectorPersonalityId?) {
    applyDirectorPersonality(id)
    resetSpawnDirector()
}

/**
 * Takes the No omen card when the run shows the omen offer (the production start opens it at the end of the first fixed
 * step); any other state is returned as it is.
 */
internal fun GameplayState.declineOmenIfOffered(): GameplayState {
    val model = engine?.model ?: return this
    if (model.phase != GamePhase.CHOICE || model.choiceType != ChoiceType.OMEN) return this
    val decline = GameplayInteractionPulse.ChoiceSelected.fromValidated(model.choices.indexOfFirst { it.omenId == null })
    return assertIs<GameplayDecision.Accepted>(GameplayNucleus.decide(this, GameplayNucleusPulse.Intent(decline))).frame.nextState
}
