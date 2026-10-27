// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.EffectAction
import kinetickk.ball.content.api.EffectTrigger
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPairingId
import kinetickk.ball.content.api.StatusElement
import kinetickk.ball.gameplay.nucleus.model.CombatField
import kinetickk.ball.gameplay.nucleus.model.CombatFieldKind
import kinetickk.ball.gameplay.nucleus.model.StatusCell
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.EnemyType
import kinetickk.ball.gameplay.nucleus.render.ForecastKind
import kinetickk.ball.gameplay.nucleus.render.GamePhase
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each rule applies once, where its text says, and nothing it spawns or spends is silently lost. */
class BuildRuleIntegrityTest {
    @Test
    fun fieldsSpawnedWhileFieldsTickAreKeptForTheNextStep() {
        val state = buildTestState()
        val target = state.dummy()
        target.status[StatusCell.STACKS + StatusElement.GRAVITIC.ordinal] = 2f
        state.addTestField(
            CombatFieldKind.FUSION, x = target.x, y = target.y, radius = 120f, damagePerSecond = 1f,
            element = StatusElement.THERMAL, stacksPerTick = 1, generation = 1, tickClock = CombatField.TICK_SECONDS,
        )

        state.updateCombatFields(0.01f)

        assertEquals(0, target.stacksOf(StatusElement.GRAVITIC), "The tick's Thermal reacted with the Gravitic stacks")
        assertEquals(2, state.combatFieldTable.count)
        assertTrue(state.fieldRecords.any { it.kind == CombatFieldKind.FUSION && it.generation == 2 })
    }

    @Test
    fun echoesRepeatAShareOfTheResolvedHitWithoutRunningTheMultipliersAgain() {
        val state = buildTestState(RelicId.ECHO_CHAMBER, RelicId.GLASS_COMET)
        val target = state.dummy()
        state.applyStatus(target, StatusElement.ENTROPY, 3, generation = 0)
        assertTrue(state.relicSurge() > 1f && state.vulnerability(target, HitSource.WEAPON) > 0f)
        val before = target.hp

        val hit = state.dealWeaponDamage(target, 40f).amount
        state.updateRelicRuntime(0.4f)

        val share = state.relicValue(RelicId.ECHO_CHAMBER, 0) / 100f
        assertEquals(before - hit * (1f + share), target.hp, hit * 0.001f)
    }

    @Test
    fun echoesNeverAdvanceGatedWeaponClauses() {
        val state = buildTestState(RelicId.ECHO_CHAMBER)
        val coil = state.own("Calibrated Coil")
        val counter = state.content.clauseIndex(coil.id, 0)
        val target = state.dummy()

        state.dealWeaponDamage(target, 10f)
        state.updateRelicRuntime(0.4f)

        assertEquals(1, state.clauseCounters[counter], "Only the real hit counts toward every sixth")
    }

    @Test
    fun darkStarWellsTakeTheRelicSurgeOnlyWhenTheyTick() {
        val state = buildTestState(RelicId.GLASS_COMET)
        val star = state.own("Dark Star")
        val target = state.dummy()
        assertTrue(state.relicSurge() > 1f)

        state.fireClauses(EffectTrigger.BREAK, target, 0f, HitSource.STATUS, StatusElement.GRAVITIC)

        val action = star.clauses.single().actions.single() as EffectAction.Well
        val well = state.fieldRecords.single { it.kind == CombatFieldKind.DARK_STAR }
        assertEquals(state.runScale() * action.scale * state.build.magnitude(star.id) * state.build.bonus(star.id), well.damagePerSecond, 0.001f)
    }

    @Test
    fun maxIntegrityProcsCapTheirGrowthAndIgnoreRebornAndAttunement() {
        val state = buildTestState(RelicId.PANDEMIC_CODEX)
        state.own("Chain Detonation", stacks = 2, reborn = true)
        val detonation = state.itemNamed("Chain Detonation")
        assertTrue(state.build.magnitude(detonation.id) * state.build.bonus(detonation.id) > MAX_HP_PROC_SCALE)
        val victim = state.dummy(hp = 100f)
        val neighbour = state.dummy(x = victim.x + 40f, hp = 100f)

        state.fireClauses(EffectTrigger.KILL, victim, 0f, HitSource.WEAPON)

        assertEquals(100f - 100f * 0.3f * MAX_HP_PROC_SCALE, neighbour.hp, 0.01f)
    }

    @Test
    fun assassinsLineForcesOnlyTheBlinkStrikeToCrit() {
        val state = buildTestState(RelicId.BLINK_DRIVE, RelicId.EXECUTIONERS_PRISM)
        assertTrue(state.build.pairing(RelicPairingId.ASSASSINS_LINE))
        val target = state.dummy(x = 130f)
        state.updatePointer(640f + 600f, 360f)
        state.setVelocityForTesting(1_000f, 0f)

        assertTrue(state.relicOnDash(1f, 0f))

        assertEquals(0f, target.status[StatusCell.MARKED_UNTIL])
        // The Blink leaves the target Brittle: Executioner's Prism crits its next hit once, then ordinary rules return.
        assertTrue(state.dealHit(target, 10f, HitSource.WEAPON).critical)
        assertFalse(state.dealHit(target, 10f, HitSource.WEAPON).critical)
    }

    @Test
    fun absoluteZeroShatterStillCountsTheTargetFrozenForItsOwnHit() {
        val state = buildTestState(RelicId.ABSOLUTE_ZERO, rebirth = 2)
        state.own("Glacial Heart")
        val frozen = state.dummy()
        val neighbour = state.dummy(x = frozen.x + 60f)
        frozen.status[StatusCell.FROZEN_UNTIL] = state.elapsed + 2f

        state.dealHit(frozen, 50f, HitSource.IMPACT, canCrit = false)

        assertFalse(state.isFrozen(frozen), "The hit shattered the Freeze")
        assertTrue(neighbour.hp < neighbour.maxHp, "Glacial Heart's frozen-impact burst fired on the shattering hit")
        assertEquals(-1, state.shatteringEnemyId)
    }

    @Test
    fun brittleStrengthensTheNextDirectHitInsideTheVulnerabilityCap() {
        val state = buildTestState()
        val plain = state.dummy()
        val brittle = state.dummy(y = 300f)
        for (target in listOf(plain, brittle)) {
            target.status[StatusCell.STACKS + StatusElement.ENTROPY.ordinal] = 12f
            target.status[StatusCell.STACKS + StatusElement.GRAVITIC.ordinal] = 12f
            target.status[StatusCell.EMBRITTLED_UNTIL] = state.elapsed + 4f
        }
        brittle.status[StatusCell.BRITTLE] = 1f
        val cap = state.content.build.policy.vulnerabilityCap
        val base = state.vulnerability(plain, HitSource.IMPACT)
        val strengthened = state.vulnerability(brittle, HitSource.IMPACT)
        assertEquals(min(cap, base + BRITTLE_VULNERABILITY), strengthened, 0.0001f)

        val plainHit = state.dealHit(plain, 100f, HitSource.IMPACT, canCrit = false, qualified = false).amount
        val brittleHit = state.dealHit(brittle, 100f, HitSource.IMPACT, canCrit = false, qualified = false).amount

        assertEquals(plainHit * (1f + strengthened) / (1f + base), brittleHit, 0.01f)
        assertTrue(brittleHit <= 100f * (1f + cap) + 0.01f)
        assertEquals(0f, brittle.status[StatusCell.BRITTLE])
    }

    @Test
    fun selfInflictedBurnsBypassShieldsAndNeverBlockTheNextHit() {
        val state = buildTestState(RelicId.CINDER_CROWN)
        state.shield = 40f
        val target = state.dummy(x = 50f)
        val before = state.hp

        state.applyStatus(target, StatusElement.THERMAL, state.breakThreshold(StatusElement.THERMAL, target), generation = 0)

        assertEquals(before - state.maxHp * 0.04f, state.hp, 0.01f)
        assertEquals(40f, state.shield, "The burn is not an incoming hit")
        assertEquals(0f, state.hurtCooldown)
        state.takeDamage(5f, kind = IncomingDamage.CONTACT)
        assertTrue(state.shield < 40f, "A real hit right after the burn still lands")
    }

    @Test
    fun severedRelicsKeepTheirLiabilitiesUntilSeveranceEnds() {
        val cinder = buildTestState(RelicId.CINDER_CROWN, RelicId.GYRE_ENGINE)
        cinder.replaceRelic(0, RelicId.STASIS_ANCHOR)
        val target = cinder.dummy(x = 50f)
        val before = cinder.hp
        cinder.applyStatus(target, StatusElement.THERMAL, cinder.breakThreshold(StatusElement.THERMAL, target), generation = 0)
        assertEquals(before - cinder.maxHp * 0.04f, cinder.hp, 0.01f, "A severed Cinder Crown still burns")

        val crowned = buildTestState(RelicId.HOLLOW_CROWN, RelicId.GYRE_ENGINE, rebirth = 3)
        crowned.replaceRelic(0, RelicId.STASIS_ANCHOR)
        val reference = buildTestState(RelicId.STASIS_ANCHOR, RelicId.GYRE_ENGINE)
        assertEquals(reference.build.relicUpside(RelicId.GYRE_ENGINE) * 0.75f, crowned.build.relicUpside(RelicId.GYRE_ENGINE), 0.0001f)

        crowned.elapsed += crowned.content.build.policy.severanceSeconds + 1f
        crowned.updateRelicRuntime(0.01f)
        assertEquals(reference.build.relicUpside(RelicId.GYRE_ENGINE), crowned.build.relicUpside(RelicId.GYRE_ENGINE), 0.0001f)
    }

    @Test
    fun faradayCageSpendsWhatShieldIsLeftAndDischargesTheRestIntoIntegrity() {
        val state = buildTestState(RelicId.STORMHEART_CAPACITOR, RelicId.AEGIS_INVERSION)
        assertTrue(state.build.pairing(RelicPairingId.FARADAY_CAGE))
        state.stormCharge = 1f
        state.shield = 1f
        val before = state.hp

        state.takeDamage(0f, kind = IncomingDamage.PROJECTILE)

        assertEquals(0f, state.shield)
        assertEquals(before - (state.maxHp * 0.1f - 1f), state.hp, 0.01f)
        assertEquals(0f, state.stormCharge)
    }

    @Test
    fun aegisNovasCountOnlyDamageAbsorbedWhileAegisIsBound() {
        val plain = buildTestState()
        plain.shield = 50f
        plain.takeDamage(30f, kind = IncomingDamage.PROJECTILE)
        assertEquals(0f, plain.aegisAbsorbed)

        val aegis = buildTestState(RelicId.AEGIS_INVERSION)
        aegis.shield = 50f
        aegis.takeDamage(30f, kind = IncomingDamage.PROJECTILE)
        assertTrue(aegis.aegisAbsorbed > 0f)
    }

    @Test
    fun aegisNeverRestoresIntegrityFromItemsOrLevels() {
        val state = buildTestState(RelicId.AEGIS_INVERSION)
        state.hp = 50f
        val ceiling = state.maxHp

        state.own("Nanite Mesh")
        state.onLevelUp()

        assertEquals(50f, state.hp)
        assertTrue(state.maxHp > ceiling)
    }

    @Test
    fun killRulesReadTheKillingBlowNotAnEarlierImpact() {
        val state = buildTestState(RelicId.GLASS_COMET)
        state.setVelocityForTesting(1_200f, 0f)
        val burning = state.dummy(hp = 200f)
        state.dealHit(burning, 1f, HitSource.IMPACT, canCrit = false)
        state.dealStatusDamage(burning, 1_000f, StatusElement.THERMAL)
        state.sweepKills()
        assertEquals(0f, state.glassInvulnerabilityReadyAt, "A burn kill is not an impact kill")

        val struck = state.dummy(hp = 1f)
        state.dealHit(struck, 50f, HitSource.IMPACT, canCrit = false)
        state.sweepKills()
        assertTrue(state.invulnerableUntil > state.elapsed, "An impact kill above 900 u/s still grants the window")
    }

    @Test
    fun thermalParadoxVentsOnlyForConvertedStacks() {
        val state = buildTestState(RelicId.FURNACE_COVENANT, RelicId.ABSOLUTE_ZERO)
        assertTrue(state.build.pairing(RelicPairingId.THERMAL_PARADOX))
        val target = state.dummy()
        state.heat = 50f

        state.applyStatus(target, StatusElement.THERMAL, 2, generation = 0)
        assertEquals(47f, state.heat, 0.001f)
        state.applyStatus(target, StatusElement.THERMAL, 1, generation = 0)
        assertEquals(47f, state.heat, 0.001f, "A single Thermal converts nothing and vents nothing")
    }

    @Test
    fun ouroborosCutsDataFromItemClausesAsWellAsPickups() {
        val sealed = buildTestState(RelicId.OUROBOROS_SEAL, rebirth = 5)
        val open = buildTestState()

        sealed.gainData(40f)
        open.gainData(40f)

        assertEquals(30L, sealed.dataCollected)
        assertEquals(40L, open.dataCollected)
    }

    @Test
    fun weaponHeatReachingFullOverheatsAndFiresOverheatClauses() {
        val state = buildTestState(RelicId.AGONY_SCEPTER, rebirth = 1)
        state.own("Thermal Vent")
        val near = state.dummy(x = 100f)
        state.heat = 96f

        state.onWeaponActivation()

        assertTrue(state.overheated)
        assertFalse(state.overheatArmed)
        assertEquals(3, near.stacksOf(StatusElement.THERMAL), "Thermal Vent answered the overheat")
    }

    @Test
    fun endlessGyreSendsItsSecondRingAMomentLater() {
        val state = buildTestState(RelicId.PERPETUAL_VECTOR, RelicId.GYRE_ENGINE)
        assertTrue(state.build.pairing(RelicPairingId.ENDLESS_GYRE))
        state.setVelocityForTesting(1_500f, 0f)
        val outer = state.dummy(x = 200f)

        state.relicOnTurn()
        assertEquals(outer.maxHp, outer.hp, "Only the inner ring fires at once")

        state.elapsed += GYRE_ECHO_DELAY + 0.01f
        state.updateRelicRuntime(0.01f)
        assertTrue(outer.hp < outer.maxHp)
        assertEquals(-1f, state.gyreEchoAt)
    }

    @Test
    fun fullSlotsAlwaysOfferAMeldOrSalvageInsteadOfForcingAReplacement() {
        val state = buildTestState()
        listOf(RelicId.GYRE_ENGINE, RelicId.BLINK_DRIVE, RelicId.STASIS_ANCHOR, RelicId.EVENT_HORIZON).forEach { id ->
            state.acquireRelic(id)
            state.acquireRelic(id)
        }
        assertTrue(state.equippedRelics.none { state.canMeld(it.id) })

        state.buildRelicChoices()

        val tail = state.choices.last()
        assertEquals(RelicChoiceAction.MELD, tail.relicAction)
        assertEquals("Salvage resonance", tail.title)
    }

    @Test
    fun weaponOffersAndRerollsNeverShiftTheGameplayStream() {
        val drafted = buildTestState(seed = 77)
        val untouched = buildTestState(seed = 77)

        drafted.buildWeaponChoices()
        drafted.buildWeaponChoices()

        repeat(8) { assertEquals(untouched.gameplayRandom.nextInt(), drafted.gameplayRandom.nextInt()) }
    }

    @Test
    fun relicForecastsNameTheBoundPartnerOfTheIncomingRelic() {
        val state = buildTestState(RelicId.PERPETUAL_VECTOR)
        state.phase = GamePhase.CHOICE
        state.choices = listOf(
            ChoiceOption(ChoiceType.RELIC, "Gyre Engine", "", "KINETIC", relicId = RelicId.GYRE_ENGINE, relicAction = RelicChoiceAction.ACQUIRE),
        )

        val concord = state.rewardPreviews().single().forecasts.single { it.kind == ForecastKind.CONCORD }

        assertEquals(RelicPairingId.ENDLESS_GYRE, concord.pairing)
        assertEquals(RelicId.PERPETUAL_VECTOR, concord.relic)
    }

    @Test
    fun runMatterBanksOnlyWhatIsNewAndAVictoryNeverOverridesADefeat() {
        val state = buildTestState()
        state.runMatter = 10L
        state.bankRunMatter()
        state.runMatter = 15L
        state.bankRunMatter()
        state.bankRunMatter()
        assertEquals(15L, state.pendingBankedMatter)

        state.phase = GamePhase.GAME_OVER
        val architect = state.dummy(type = EnemyType.ARCHITECT)
        architect.hp = -1f
        state.sweepKills()
        assertEquals(GamePhase.GAME_OVER, state.phase)
    }
}
