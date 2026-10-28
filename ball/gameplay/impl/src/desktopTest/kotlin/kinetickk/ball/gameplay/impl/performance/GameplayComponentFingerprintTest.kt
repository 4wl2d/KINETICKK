// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl.performance

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.SiteKind
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterAbilityProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterDroneProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterScarProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterLatticePoint
import kinetickk.ball.gameplay.nucleus.render.PointOfInterestProjection
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.RelicChoiceAction
import kinetickk.ball.gameplay.nucleus.render.TotemAction
import kinetickk.ball.profile.api.DamageNumberFormat
import kinetickk.ball.profile.api.DamageNumberSize
import kinetickk.ball.profile.api.ParticleDensity
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.foundation.collections.immutableListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GameplayComponentFingerprintTest {
    @Test
    fun preferencesUsePortableValueWitnessAndRetainEveryEnumSelection() {
        // This fixture witnesses portable hashing; the fresh-profile 1x default has its own test.
        val preferences = PlayerPreferences(simulationSpeed = 1.15f)
        val expected = 167_376_744_718_396_850L
        // Fixed witnesses also fail if a future refactor reintroduces JVM identity hash codes.
        assertEquals(expected, canonicalPreferencesFingerprint(preferences))
        assertEquals(expected, canonicalPreferencesFingerprint(preferences.copy()))
        listOf(
            preferences.copy(particleDensity = ParticleDensity.HIGH),
            preferences.copy(damageNumberSize = DamageNumberSize.HUGE),
            preferences.copy(damageNumberFormat = DamageNumberFormat.FULL),
            preferences.copy(masterVolume = -0f),
            preferences.copy(simulationSpeed = 1f),
            preferences.copy(minimapEnabled = false),
        ).forEach { changed ->
            assertNotEquals(expected, canonicalPreferencesFingerprint(changed))
        }
    }

    @Test
    fun relicWitnessPreservesBothIdentityAndRankWithoutObjectHashes() {
        val relic = EquippedRelic(RelicId.ECHO_CHAMBER, rank = 2)
        val expected = -7_438_867_821_674_710_841L
        assertEquals(expected, canonicalRelicFingerprint(relic))
        assertEquals(expected, canonicalRelicFingerprint(relic.copy()))
        assertNotEquals(expected, canonicalRelicFingerprint(relic.copy(id = RelicId.BLINK_DRIVE)))
        assertNotEquals(expected, canonicalRelicFingerprint(relic.copy(rank = 3)))
    }

    @Test
    fun choiceWitnessPreservesTypedActionsAndValuesWithoutObjectHashes() {
        val choice = ChoiceOption(
            type = ChoiceType.RELIC,
            title = "Echo",
            description = "Repeat",
            tag = "KINETIC",
            relicId = RelicId.ECHO_CHAMBER,
            relicAction = RelicChoiceAction.MELD,
            relicSlot = 0,
        )
        // Previous witness plus the nullable rewardFocus field (-1 when absent) and the Reborn and Heirloom flags.
        val expected = -8_789_539_912_842_911_048L
        assertEquals(expected, canonicalChoiceFingerprint(choice))
        assertEquals(expected, canonicalChoiceFingerprint(choice.copy()))
        listOf(
            choice.copy(type = ChoiceType.RELIC_BIND),
            choice.copy(weaponId = WeaponId.MORNINGSTAR),
            choice.copy(totemAction = TotemAction.CHANGE_WEAPON),
            choice.copy(relicId = RelicId.BLINK_DRIVE),
            choice.copy(relicAction = RelicChoiceAction.REPLACE),
            choice.copy(relicSlot = 1),
            choice.copy(title = "Changed"),
            choice.copy(rewardFocus = RewardFocus.THERMAL),
            choice.copy(reborn = true),
            choice.copy(heirloom = true),
            choice.copy(rewardFocus = RewardFocus.ENTROPY),
        ).forEach { changed ->
            assertNotEquals(expected, canonicalChoiceFingerprint(changed))
        }
    }

    @Test
    fun characterAbilityWitnessRetainsEveryMechanicFieldAndListOrder() {
        val first = CharacterLatticePoint(12f, 34f)
        val second = CharacterLatticePoint(56f, 78f)
        val scar = CharacterScarProjection(1f, 2f, 3f, 4f, 0.5f, 0f)
        val drone = CharacterDroneProjection(5f, 6f, 7f, 8f, false)
        val ability = CharacterAbilityProjection(
            charge = 0.5f, ringRadius = 155f, parryWindow = 0.18f, lattice = immutableListOf(first, second),
            facets = 3, facetProgress = 0.2f, edge = 2, stellarMass = 7, novaFlash = 0.1f, haloBurstFlash = 0.3f,
            polarity = 1, polarityFlash = 0.4f, beatPeriod = 0.75f, beatWindow = 0.11f, beatStreak = 2,
            blinkCharges = 1, blinkRecharge = 0.6f, scars = immutableListOf(scar), drones = immutableListOf(drone),
            ghostActive = true, ghostX = 9f, ghostY = 10f, previousGhostX = 11f, previousGhostY = 12f,
        )
        val expected = canonicalCharacterAbilityFingerprint(ability)
        assertEquals(expected, canonicalCharacterAbilityFingerprint(ability.copy(lattice = immutableListOf(first.copy(), second.copy()))))
        listOf(
            ability.copy(charge = 0.6f),
            ability.copy(ringRadius = 156f),
            ability.copy(parryWindow = 0.17f),
            ability.copy(lattice = immutableListOf(first)),
            ability.copy(lattice = immutableListOf(second, first)),
            ability.copy(lattice = immutableListOf(first.copy(x = 13f), second)),
            ability.copy(lattice = immutableListOf(first.copy(y = 35f), second)),
            ability.copy(facets = 4),
            ability.copy(facetProgress = 0.3f),
            ability.copy(edge = 3),
            ability.copy(stellarMass = 8),
            ability.copy(novaFlash = 0.2f),
            ability.copy(haloBurstFlash = 0.4f),
            ability.copy(polarity = 0),
            ability.copy(polarityFlash = 0.5f),
            ability.copy(beatPeriod = 0f),
            ability.copy(beatWindow = 0f),
            ability.copy(beatStreak = 3),
            ability.copy(blinkCharges = 2),
            ability.copy(blinkRecharge = 0.7f),
            ability.copy(scars = immutableListOf()),
            ability.copy(scars = immutableListOf(scar.copy(fuse = 0.6f))),
            ability.copy(scars = immutableListOf(scar.copy(fade = 0.1f))),
            ability.copy(drones = immutableListOf(drone.copy(darting = true))),
            ability.copy(drones = immutableListOf(drone.copy(previousX = 1f))),
            ability.copy(ghostActive = false),
            ability.copy(ghostX = 1f),
            ability.copy(previousGhostY = 1f),
        ).forEach { changed -> assertNotEquals(expected, canonicalCharacterAbilityFingerprint(changed)) }
    }

    @Test
    fun pointWitnessRetainsOfferTrialDefendersAndVolleyTelegraph() {
        val point = PointOfInterestProjection(
            SiteKind.SEALED_ANOMALY, "Vault", 10f, 20f, true, 24f,
            1, 0.3f, immutableListOf(11, 12, 13), 0.8f, 1.2f,
        )
        val expected = canonicalPointOfInterestFingerprint(point)
        assertEquals(expected, canonicalPointOfInterestFingerprint(point.copy(defenderIds = immutableListOf(11, 12, 13))))
        listOf(
            point.copy(kind = SiteKind.COLLAPSING_ORBIT),
            point.copy(name = "Orbit"),
            point.copy(x = 11f),
            point.copy(y = 21f),
            point.copy(active = false),
            point.copy(remaining = 23f),
            point.copy(nextBeacon = 2),
            point.copy(progress = 0.4f),
            point.copy(defenderIds = immutableListOf(11, 12)),
            point.copy(defenderIds = immutableListOf(13, 12, 11)),
            point.copy(warningRemaining = 0.7f),
            point.copy(volleyAngle = 1.3f),
        ).forEach { changed -> assertNotEquals(expected, canonicalPointOfInterestFingerprint(changed)) }
    }

    @Test
    fun visualFxWitnessIncludesNotificationTextDetailsLifetimeAndOrder() {
        val notification = BuildNotificationProjection("Bound", immutableListOf("Power +1", "Resonance enabled"), 3f)
        val visual = VisualFxProjection.EMPTY.copy(buildNotifications = immutableListOf(notification))
        val expected = canonicalVisualFxFingerprint(visual)
        assertNotEquals(canonicalVisualFxFingerprint(VisualFxProjection.EMPTY), expected)
        assertEquals(expected, canonicalVisualFxFingerprint(visual.copy(buildNotifications = immutableListOf(notification.copy()))))
        listOf(
            notification.copy(title = "Replaced"),
            notification.copy(details = immutableListOf("Power +2", "Resonance enabled")),
            notification.copy(details = immutableListOf("Resonance enabled", "Power +1")),
            notification.copy(details = immutableListOf("Power +1")),
            notification.copy(life = 2f),
        ).forEach { changed ->
            assertNotEquals(expected, canonicalVisualFxFingerprint(visual.copy(buildNotifications = immutableListOf(changed))))
        }
        val second = notification.copy(title = "Amplified")
        assertNotEquals(
            canonicalVisualFxFingerprint(visual.copy(buildNotifications = immutableListOf(notification, second))),
            canonicalVisualFxFingerprint(visual.copy(buildNotifications = immutableListOf(second, notification))),
        )
    }
}
