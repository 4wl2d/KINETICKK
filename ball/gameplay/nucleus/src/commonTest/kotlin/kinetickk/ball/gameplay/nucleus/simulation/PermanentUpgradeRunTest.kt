// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.content.api.CharacterUpgradeSignature
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.gameplay.api.BuildStatSource
import kinetickk.ball.gameplay.api.GameplayInstanceId
import kinetickk.ball.gameplay.api.GameplayRevision
import kinetickk.ball.gameplay.api.RunId
import kinetickk.ball.gameplay.nucleus.testing.canonicalGameplayContent
import kinetickk.ball.profile.api.GameplayProfileSnapshot
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PermanentUpgrades
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.foundation.collections.toImmutableList
import kinetickk.foundation.common.localization.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Refit and Honing as a run applies them (economy §4.1, §4.4): run-constant multipliers and one signature each. */
class PermanentUpgradeRunTest {
    private fun run(
        shape: CoreShape = CoreShape.ORB,
        weapon: WeaponId = WeaponId.FLUX_WAKE,
        refit: Int = 0,
        honing: Map<WeaponId, Int> = emptyMap(),
        content: GameplayContentSnapshot = canonicalGameplayContent,
    ) = MutableGameState(
        content,
        bootstrapProgress = GameplayProfileSnapshot(
            preferences = PlayerPreferences(),
            economy = PlayerEconomy(),
            loadout = PlayerLoadout(shape, weapon, WeaponId.entries.toSet()),
            matrix = MatrixProgress(content.matrixNodes.map { 0 }),
            collection = PlayerCollection(),
            rebirthProgress = RebirthProgress(),
            upgrades = PermanentUpgrades(
                List(CoreShape.entries.size) { if (it == shape.ordinal) refit else 0 },
                List(WeaponId.entries.size) { honing[WeaponId.entries[it]] ?: 0 },
            ),
        ),
    ).apply {
        startRun()
        enemies.clear()
        projectiles.clear()
    }

    @Test fun refitAddsIntegrityAndScalesOnlyCharacterMechanicDamage() {
        val plain = run()
        val refitted = run(refit = 10)
        assertEquals(plain.maxHp + 20f, refitted.maxHp)
        assertEquals(refitted.maxHp, refitted.hp)
        assertEquals(10, refitted.characterUpgradeLevel)
        assertEquals(1.2f, refitted.characterUpgradePower, 1e-6f)
        // Character damage takes ×1.2; weapon and ability power (items, weapons) never see Refit.
        val plainHit = plain.dealCharacterDamage(plain.addEnemyForTesting(400f, 0f, hp = 10_000f), 50f).amount
        val refittedHit = refitted.dealCharacterDamage(refitted.addEnemyForTesting(400f, 0f, hp = 10_000f), 50f).amount
        assertEquals(plainHit * 1.2f, refittedHit, 1e-3f)
        assertEquals(plain.effectiveWeaponPower(), refitted.effectiveWeaponPower())
        assertEquals(plain.abilityPower(), refitted.abilityPower())
        // Another character's Refit does nothing for the selected one.
        val otherRefit = MutableGameState(
            canonicalGameplayContent,
            bootstrapProgress = GameplayProfileSnapshot(
                PlayerPreferences(), PlayerEconomy(), PlayerLoadout(CoreShape.ORB), MatrixProgress(canonicalGameplayContent.matrixNodes.map { 0 }),
                PlayerCollection(), RebirthProgress(),
                upgrades = PermanentUpgrades(List(CoreShape.entries.size) { if (it == CoreShape.PRISM.ordinal) 10 else 0 }, List(WeaponId.entries.size) { 0 }),
            ),
        ).apply { startRun() }
        assertEquals(0, otherRefit.characterUpgradeLevel)
        assertEquals(plain.maxHp, otherRefit.maxHp)
        assertEquals(1f, otherRefit.characterUpgradePower)
    }

    @Test fun honingScalesTheCurrentWeaponOnlyAndFollowsAMidRunSwitch() {
        val plain = run()
        val honed = run(honing = mapOf(WeaponId.FLUX_WAKE to 10, WeaponId.MORNINGSTAR to 5))
        assertEquals(1.3f, honed.weaponUpgradePower(), 1e-6f)
        assertEquals(plain.effectiveWeaponPower() * 1.3f, honed.effectiveWeaponPower(), 1e-5f)
        // Honing is the weapon's own hits only: abilities and weapon-based item effects use abilityPower.
        assertEquals(plain.abilityPower(), honed.abilityPower())
        assertEquals(plain.maxHp, honed.maxHp)
        honed.equipRunWeapon(WeaponId.MORNINGSTAR)
        plain.equipRunWeapon(WeaponId.MORNINGSTAR)
        assertEquals(1.15f, honed.weaponUpgradePower(), 1e-6f)
        assertEquals(plain.effectiveWeaponPower() * 1.15f, honed.effectiveWeaponPower(), 1e-5f)
        honed.equipRunWeapon(WeaponId.ARC_COIL)
        assertEquals(1f, honed.weaponUpgradePower())
    }

    @Test fun levelsAreCapturedOnceClampedToTheContentMaximum() {
        val state = MutableGameState(
            canonicalGameplayContent,
            bootstrapProgress = GameplayProfileSnapshot(
                PlayerPreferences(), PlayerEconomy(), PlayerLoadout(CoreShape.ORB), MatrixProgress(canonicalGameplayContent.matrixNodes.map { 0 }),
                PlayerCollection(), RebirthProgress(),
                upgrades = PermanentUpgrades(List(CoreShape.entries.size) { 15 }, List(WeaponId.entries.size) { -3 }),
            ),
        )
        assertTrue(state.characterUpgradeLevels.all { it == canonicalGameplayContent.upgrades.maxLevel })
        assertTrue(state.weaponUpgradeLevels.all { it == 0 })
        state.startRun()
        assertEquals(canonicalGameplayContent.upgrades.maxLevel, state.characterUpgradeLevel)
        // A fresh state without a profile has nothing bought.
        val fresh = MutableGameState(canonicalGameplayContent).apply { startRun() }
        assertEquals(CoreShape.entries.size, fresh.characterUpgradeLevels.size)
        assertEquals(WeaponId.entries.size, fresh.weaponUpgradeLevels.size)
        assertTrue(fresh.characterUpgradeLevels.all { it == 0 } && fresh.weaponUpgradeLevels.all { it == 0 })
        assertEquals(1f, fresh.characterUpgradePower)
        assertEquals(1f, fresh.weaponUpgradePower())
    }

    @Test fun everySignatureMovesItsMechanicByTheContentStepForTheSelectedCharacterOnly() {
        fun at(shape: CoreShape, level: Int = 10) = run(shape = shape, refit = level)
        assertEquals(0.698f * 0.8f, at(CoreShape.ORB).orbitArc(), 1e-5f)
        assertEquals(4.2f, at(CoreShape.PRISM).facetRegenSeconds(), 1e-5f)
        assertEquals(20f, at(CoreShape.SHARD).punctureHeatRefund(), 1e-4f)
        assertEquals(140f, at(CoreShape.RING).haloExtraRadius(), 1e-4f)
        assertEquals(0.22f, at(CoreShape.DIAMOND).parryWindowSeconds(), 1e-5f)
        assertEquals(9f, at(CoreShape.TESSERACT).latticeLifeSeconds(), 1e-4f)
        assertEquals(200f, at(CoreShape.STAR).supernovaBaseRadius(), 1e-4f)
        assertEquals(1.2f, at(CoreShape.SPIRAL).ghostAbsorbInterval(), 1e-5f)
        assertEquals(340f, at(CoreShape.DIPOLE).attractRadius(), 1e-4f)
        assertEquals(250f, at(CoreShape.DIPOLE).repelRadius(), 1e-4f)
        assertEquals(110f, at(CoreShape.PENTAGON).beatBurstRadius(), 1e-4f)
        assertEquals(1.8f, at(CoreShape.CRESCENT).blinkRechargeSeconds(), 1e-5f)
        assertEquals(16f, at(CoreShape.HEXAGON).droneLifeSeconds(), 1e-4f)
        // One level moves one step; level 0 and every other character read the base exactly.
        assertEquals(12.8f, at(CoreShape.SHARD, level = 1).punctureHeatRefund(), 1e-5f)
        assertEquals(PUNCTURE_HEAT_REFUND, at(CoreShape.SHARD, level = 0).punctureHeatRefund())
        val orb = at(CoreShape.ORB)
        assertEquals(BLINK_RECHARGE_SECONDS, orb.blinkRechargeSeconds())
        assertEquals(FACET_REGEN_SECONDS, orb.facetRegenSeconds())
        assertEquals(ATTRACT_RADIUS, orb.attractRadius())
        assertEquals(REPEL_RADIUS, orb.repelRadius())
        assertEquals(PARRY_WINDOW_SECONDS, orb.parryWindowSeconds())
    }

    @Test fun aSignatureStepNeverTakesItsMechanicBelowHalfTheBase() {
        val steep = canonicalGameplayContent.copy(
            coreShapes = canonicalGameplayContent.coreShapes.map { definition ->
                when (definition.id) {
                    CoreShape.CRESCENT -> definition.copy(upgradeSignature = CharacterUpgradeSignature(-0.5f))
                    CoreShape.ORB -> definition.copy(upgradeSignature = CharacterUpgradeSignature(-0.2f))
                    else -> definition
                }
            }.toImmutableList(),
        )
        assertEquals(BLINK_RECHARGE_SECONDS * 0.5f, run(CoreShape.CRESCENT, refit = 10, content = steep).blinkRechargeSeconds())
        // The floor binds only past it: one steep level still moves the full step.
        assertEquals(1.7f, run(CoreShape.CRESCENT, refit = 1, content = steep).blinkRechargeSeconds(), 1e-5f)
        assertEquals(0.698f * 0.5f, run(CoreShape.ORB, refit = 10, content = steep).orbitArc(), 1e-6f)
    }

    @Test fun theRefittedMechanicsRunOnTheirAccessors() {
        // Crescent: a spent Blink comes back after 1.8 s at Refit 10, and the HUD fraction uses the same base.
        val crescent = run(CoreShape.CRESCENT, refit = 10)
        crescent.characterRuntime.blinkCharges = 0
        crescent.characterRuntime.blinkRecharge = 0f
        val steps = (1.8f / MutableGameState.FIXED_STEP).toInt()
        repeat(steps / 2) { crescent.stepCharacter() }
        assertEquals(crescent.characterRuntime.blinkRecharge / 1.8f, crescent.toRenderModel().characterAbility.blinkRecharge, 1e-5f)
        repeat(steps - steps / 2 + 2) { crescent.stepCharacter() }
        assertEquals(1, crescent.characterRuntime.blinkCharges)
        // Shard: a puncture vents 20 heat at Refit 10.
        val shard = run(CoreShape.SHARD, refit = 10)
        val marked = shard.addEnemyForTesting(50f, 0f, hp = 10_000f)
        marked.characterMarkTime = 3f
        shard.heat = 50f
        shard.onCharacterPrimaryHit(marked)
        assertEquals(30f, shard.heat, 1e-4f)
        // Prism: the facet progress the HUD draws is measured against the shortened regrowth.
        val prism = run(CoreShape.PRISM, refit = 10)
        prism.characterRuntime.facets = 0
        prism.characterRuntime.facetTimer = 2.1f
        assertEquals(0.5f, prism.toRenderModel().characterAbility.facetProgress, 1e-5f)
    }

    @Test fun theBuildSummaryCarriesTheUpgradeLineAndAttributesBothTracks() {
        val plain = run()
        val upgraded = run(refit = 3, honing = mapOf(WeaponId.FLUX_WAKE to 5))
        val summary = upgraded.buildSummary(GameplayInstanceId(RunId(1)), GameplayRevision(1))
        assertEquals("Refit 3 · Honing 5", summary.upgrades)
        assertEquals("Доработка 3 · Заточка 5", summary.upgrades.localizedContent(AppLanguage.Russian))
        assertEquals("Refit 0 · Honing 0", plain.buildSummary(GameplayInstanceId(RunId(2)), GameplayRevision(1)).upgrades)
        // Refit integrity is the character's own; nothing leaks into the item column.
        val integrity = summary.stats.single { it.name == "Integrity" }
        val plainIntegrity = plain.buildStats().single { it.name == "Integrity" }
        assertEquals(upgraded.maxHp, integrity.value)
        assertEquals(
            plainIntegrity.contributions.single { it.source == BuildStatSource.CHARACTER }.amount + 6f,
            integrity.contributions.single { it.source == BuildStatSource.CHARACTER }.amount,
        )
        assertNull(integrity.contributions.firstOrNull { it.source == BuildStatSource.ITEMS })
        // Honing reports with the weapon's own progression, never as a temporary effect.
        val power = summary.stats.single { it.name == "Weapon power" }
        assertEquals(upgraded.effectiveWeaponPower(), power.value)
        assertNull(power.contributions.firstOrNull { it.source == BuildStatSource.TEMPORARY })
        assertEquals(power.value, power.contributions.sumOf { it.amount.toDouble() }.toFloat(), 1e-5f)
        // A mid-run weapon switch reads the new weapon's Honing.
        upgraded.equipRunWeapon(WeaponId.MORNINGSTAR)
        assertEquals("Refit 3 · Honing 0", upgraded.upgradeLine())
    }

    private fun MutableGameState.stepCharacter() {
        elapsed += MutableGameState.FIXED_STEP
        ageCharacterStepFlags()
        updateCharacterRuntime(MutableGameState.FIXED_STEP)
    }
}
