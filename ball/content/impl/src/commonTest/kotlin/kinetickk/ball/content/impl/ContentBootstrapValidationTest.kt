// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentBootstrapValidationTest {
    @Test
    fun exactCatalogBoundsAreAccepted() {
        val content = createContentCatalogForTesting(defaultContentBootstrapData())
        val gameplay = content.gameplayContent()

        assertEquals(ContentBounds.MAX_ITEMS, gameplay.items.size)
        assertEquals(ContentBounds.MAX_WEAPONS, gameplay.weapons.size)
        assertEquals(ContentBounds.MAX_META_UPGRADES, gameplay.metaUpgrades.size)
        assertEquals(ContentBounds.MAX_RELICS, gameplay.relics.size)
        assertEquals(
            ContentBounds.MAX_REBIRTH_LEVEL - ContentBounds.MIN_REBIRTH_LEVEL + 1,
            gameplay.rebirth.profiles.size,
        )
    }

    @Test
    fun itemBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val extra = data.items.last().copy(
            id = ContentBounds.MAX_ITEMS,
            name = "Overflow Item",
            description = "Only used to exercise the bootstrap item bound.",
        )

        assertRejected(data.copy(items = data.items + extra), "items size 401")
    }

    @Test
    fun weaponBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val extra = WeaponDefinition(
            id = WeaponId.FLUX_WAKE,
            name = "Overflow Weapon",
            description = "Only used to exercise the bootstrap weapon bound.",
            tags = listOf("TEST"),
            permanentUnlockCost = 0,
        )

        assertRejected(data.copy(weapons = data.weapons + extra), "weapons size 13")
    }

    @Test
    fun metaUpgradeBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val extra = data.metaUpgrades.first().copy(id = MetaUpgradeId.CORE_INTEGRITY)

        assertRejected(data.copy(metaUpgrades = data.metaUpgrades + extra), "meta upgrades size 9")
    }

    @Test
    fun relicBoundRejectsNPlusOne() {
        val data = defaultContentBootstrapData()
        val extra = data.relics.first().copy(id = RelicId.KINETIC_FLYWHEEL)

        assertRejected(data.copy(relics = data.relics + extra), "relics size 41")
    }

    @Test
    fun rebirthBoundRejectsLevelEleven() {
        val data = defaultContentBootstrapData()
        val extra = data.rebirthProfiles.last().copy(tier = ContentBounds.MAX_REBIRTH_LEVEL + 1)

        assertRejected(data.copy(rebirthProfiles = data.rebirthProfiles + extra), "Rebirth profiles size 12")
    }

    @Test
    fun bootstrapRejectsEveryCatalogIdCollision() {
        val data = defaultContentBootstrapData()

        assertRejected(
            data.copy(items = data.items.replaceLast(data.items.last().copy(id = 0))),
            "item ids must be unique",
        )
        assertRejected(
            data.copy(
                weapons = data.weapons.replaceLast(
                    WeaponDefinition(
                        id = WeaponId.FLUX_WAKE,
                        name = data.weapons.last().name,
                        description = data.weapons.last().description,
                        tags = data.weapons.last().tags,
                        permanentUnlockCost = data.weapons.last().permanentUnlockCost,
                    ),
                ),
            ),
            "weapon ids must be unique",
        )
        assertRejected(
            data.copy(
                metaUpgrades = data.metaUpgrades.replaceLast(
                    data.metaUpgrades.last().copy(id = MetaUpgradeId.CORE_INTEGRITY),
                ),
            ),
            "meta-upgrade ids must be unique",
        )
        assertRejected(
            data.copy(
                relics = data.relics.replaceLast(
                    data.relics.last().copy(id = RelicId.KINETIC_FLYWHEEL),
                ),
            ),
            "relic ids must be unique",
        )
        assertRejected(
            data.copy(
                rebirthProfiles = data.rebirthProfiles.replaceLast(
                    data.rebirthProfiles.last().copy(tier = 0),
                ),
            ),
            "Rebirth ids must be unique",
        )
    }

    @Test
    fun bootstrapRejectsAnyNonCanonicalContentVersion() {
        val data = defaultContentBootstrapData().copy(
            version = ContentVersion("kinetickk-content-2"),
        )

        assertRejected(data, "kinetickk-content-1")
    }

    private fun assertRejected(data: ContentBootstrapData, expectedMessage: String) {
        val failure = assertFailsWith<IllegalArgumentException> {
            createContentCatalogForTesting(data)
        }
        assertTrue(
            failure.message.orEmpty().contains(expectedMessage),
            "Expected '$expectedMessage' in '${failure.message}'",
        )
    }
}

private fun <Value> List<Value>.replaceLast(replacement: Value): List<Value> =
    dropLast(1) + replacement
