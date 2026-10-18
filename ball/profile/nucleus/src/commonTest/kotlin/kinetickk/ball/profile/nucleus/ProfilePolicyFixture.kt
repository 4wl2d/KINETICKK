// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.nucleus

import kinetickk.ball.content.api.*
import kinetickk.foundation.collections.toImmutableList

internal val TestProfilePolicy: ProfilePolicySnapshot by lazy(::profilePolicyFixture)

private fun profilePolicyFixture(): ProfilePolicySnapshot = ProfilePolicySnapshot(
    version = ContentVersion("test-content"),
    itemCount = 80,
    coreShapes = CoreShape.entries.map { CoreShapeDefinition(it) }.toImmutableList(),
    weapons = WeaponId.entries.map { id ->
        val (cost, rebirth) = TestWeaponPrices[id.ordinal]
        testWeapon(id, cost, rebirth)
    }.toImmutableList(),
    rebirth = testRebirthPolicy(),
)

/** Matter cost and Rebirth gate per weapon in [WeaponId] order, priced like the shipped catalog. */
private val TestWeaponPrices: List<Pair<Int, Int>> = listOf(
    0 to 0, 25 to 0, 55 to 0, 95 to 0, 145 to 0, 215 to 0, 305 to 0, 430 to 0, 610 to 0, 860 to 0, 1_200 to 0, 1_650 to 0,
    120 to 0, 260 to 0, 900 to 2, 380 to 0, 1_300 to 4, 520 to 0, 700 to 1, 1_100 to 3, 1_500 to 5, 1_800 to 6,
    2_100 to 7, 2_600 to 9,
)

internal fun testWeapon(id: WeaponId, cost: Int, minimumRebirth: Int = 0): WeaponDefinition = WeaponDefinition(
    id = id,
    name = "Weapon ${id.ordinal}",
    description = "Test weapon ${id.ordinal}",
    family = WeaponFamily.PROJECTILE,
    element = null,
    tags = listOf(BuildTag.DRIVE, BuildTag.ARSENAL),
    key = RelicKey.MOTION,
    signature = CoreShape.entries[id.ordinal % CoreShape.entries.size],
    scaling = WeaponScaling.SPEED,
    cadence = WeaponCadence.DISCRETE,
    permanentUnlockCost = cost,
    minimumRebirth = minimumRebirth,
    masteryForms = listOf("Form one", "Form two", "Form three"),
    signatureText = "Signature twist",
)

private fun testRebirthPolicy(): RebirthPolicySnapshot = RebirthPolicySnapshot(
    minimumLevel = 0,
    maximumLevel = 10,
    profiles = (0..10).map { tier ->
        RebirthProfile(
            tier, "Baseline", 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )
    }.toImmutableList(),
    maxActiveEnemies = 120,
    minSpawnIntervalSeconds = 0.09f,
    minEliteIntervalSeconds = 24f,
)
