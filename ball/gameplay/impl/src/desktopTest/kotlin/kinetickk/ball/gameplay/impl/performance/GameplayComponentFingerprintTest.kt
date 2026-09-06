// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.impl.performance

import kinetickk.ball.content.api.EquippedRelic
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.PointOfInterestKind
import kinetickk.ball.content.api.RewardFocus
import kinetickk.ball.gameplay.interaction.fx.BuildNotificationProjection
import kinetickk.ball.gameplay.interaction.fx.VisualFxProjection
import kinetickk.ball.gameplay.nucleus.render.CharacterAbilityProjection
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
        val expected = -3_124_477_553_521_026_709L
        // Fixed witnesses also fail if a future refactor reintroduces JVM identity hash codes.
        assertEquals(expected, canonicalPreferencesFingerprint(preferences))
        assertEquals(expected, canonicalPreferencesFingerprint(preferences.copy()))
        listOf(
            preferences.copy(particleDensity = ParticleDensity.HIGH),
            preferences.copy(damageNumberSize = DamageNumberSize.HUGE),
            preferences.copy(damageNumberFormat = DamageNumberFormat.FULL),
            preferences.copy(masterVolume = -0f),
            preferences.copy(simulationSpeed = 1f),
        ).forEach { changed ->
            assertNotEquals(expected, canonicalPreferencesFingerprint(changed))
        }
    }

    @Test
    fun relicWitnessPreservesBothIdentityAndRankWithoutObjectHashes() {
        val relic = EquippedRelic(RelicId.ECHO_CHAMBER, rank = 2)
        val expected = 8_528_754_464_390_207_192L
        assertEquals(expected, canonicalRelicFingerprint(relic))
        assertEquals(expected, canonicalRelicFingerprint(relic.copy()))
        assertNotEquals(expected, canonicalRelicFingerprint(relic.copy(id = RelicId.GHOST_VECTOR)))
        assertNotEquals(expected, canonicalRelicFingerprint(relic.copy(rank = 3)))
    }

    @Test
    fun choiceWitnessPreservesTypedActionsAndValuesWithoutObjectHashes() {
        val choice = ChoiceOption(
            type = ChoiceType.RELIC,
            title = "Echo",
            description = "Repeat",
            tag = "RIFT",
            relicId = RelicId.ECHO_CHAMBER,
            relicAction = RelicChoiceAction.MELD,
            relicSlot = 0,
        )
        // Previous witness plus the newly owned nullable rewardFocus field (-1 when absent).
        val expected = 3_144_022_955_760_528_523L
        assertEquals(expected, canonicalChoiceFingerprint(choice))
        assertEquals(expected, canonicalChoiceFingerprint(choice.copy()))
        listOf(
            choice.copy(type = ChoiceType.RELIC_BIND),
            choice.copy(weaponId = WeaponId.MORNINGSTAR),
            choice.copy(totemAction = TotemAction.CHANGE_WEAPON),
            choice.copy(relicId = RelicId.GHOST_VECTOR),
            choice.copy(relicAction = RelicChoiceAction.REPLACE),
            choice.copy(relicSlot = 1),
            choice.copy(title = "Changed"),
            choice.copy(rewardFocus = RewardFocus.RIFT),
            choice.copy(rewardFocus = RewardFocus.ENTROPY),
        ).forEach { changed ->
            assertNotEquals(expected, canonicalChoiceFingerprint(changed))
        }
    }

    @Test
    fun characterAbilityWitnessRetainsChargeDefenseGeometryAndLatticeOrder() {
        val first = CharacterLatticePoint(12f, 34f)
        val second = CharacterLatticePoint(56f, 78f)
        val ability = CharacterAbilityProjection(0.5f, 20f, 155f, 0.18f, immutableListOf(first, second))
        val expected = canonicalCharacterAbilityFingerprint(ability)
        assertEquals(expected, canonicalCharacterAbilityFingerprint(ability.copy(lattice = immutableListOf(first.copy(), second.copy()))))
        listOf(
            ability.copy(charge = 0.6f),
            ability.copy(barrier = 21f),
            ability.copy(ringRadius = 156f),
            ability.copy(parryWindow = 0.17f),
            ability.copy(lattice = immutableListOf(first)),
            ability.copy(lattice = immutableListOf(second, first)),
            ability.copy(lattice = immutableListOf(first.copy(x = 13f), second)),
            ability.copy(lattice = immutableListOf(first.copy(y = 35f), second)),
        ).forEach { changed -> assertNotEquals(expected, canonicalCharacterAbilityFingerprint(changed)) }
    }

    @Test
    fun pointWitnessRetainsOfferTrialDefendersAndVolleyTelegraph() {
        val point = PointOfInterestProjection(
            PointOfInterestKind.SEALED_ANOMALY, "Vault", 10f, 20f, true, 24f,
            1, 0.3f, immutableListOf(11, 12, 13), 0.8f, 1.2f,
        )
        val expected = canonicalPointOfInterestFingerprint(point)
        assertEquals(expected, canonicalPointOfInterestFingerprint(point.copy(defenderIds = immutableListOf(11, 12, 13))))
        listOf(
            point.copy(kind = PointOfInterestKind.COLLAPSING_ORBIT),
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
