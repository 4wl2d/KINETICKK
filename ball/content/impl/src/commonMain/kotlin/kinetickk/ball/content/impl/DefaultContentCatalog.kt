// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.impl

import kinetickk.ball.content.api.BuildCatalog
import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.ContentCatalog
import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.defaultCoreShapeDefinitions
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.GameplayContentSnapshot
import kinetickk.ball.content.api.ItemDefinition
import kinetickk.ball.content.api.KINETICKK_CONTENT_VERSION
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.MatrixNodeId
import kinetickk.ball.content.api.defaultMatrixNodes
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.RelicId
import kinetickk.ball.content.api.RelicPolicy
import kinetickk.ball.content.api.UiCatalogSnapshot
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.RunTempoProfile
import kinetickk.ball.content.api.defaultBuildCatalog
import kinetickk.foundation.collections.toImmutableList

/** Creates the query-only Content authority that application composition owns for its lifetime. */
fun createContentCatalog(): ContentCatalog =
    DefaultContentCatalog(defaultContentBootstrapData())

internal data class ContentBootstrapData(
    val version: ContentVersion,
    val items: List<ItemDefinition>,
    val weapons: List<WeaponDefinition>,
    val weaponMasteries: List<WeaponMastery>,
    val matrixNodes: List<MatrixNodeDefinition>,
    val relics: List<RelicDefinition>,
    val coreShapes: List<CoreShapeDefinition>,
    val rebirthProfiles: List<RebirthProfile>,
    val relicPolicy: RelicPolicy,
    val maxActiveEnemies: Int,
    val minSpawnIntervalSeconds: Float,
    val minEliteIntervalSeconds: Float,
    val tempo: RunTempoProfile = RunTempoProfile(),
    val build: BuildCatalog = defaultBuildCatalog(),
)

internal fun defaultContentBootstrapData(): ContentBootstrapData = ContentBootstrapData(
    version = KINETICKK_CONTENT_VERSION,
    items = defaultItems(),
    weapons = defaultWeapons(),
    weaponMasteries = WeaponMastery.entries,
    matrixNodes = defaultMatrixNodes(),
    relics = defaultRelics(),
    coreShapes = defaultCoreShapeDefinitions(),
    rebirthProfiles = defaultRebirthProfiles(),
    relicPolicy = RelicPolicy(maxSlots = 4, maxRank = 3),
    maxActiveEnemies = DEFAULT_MAX_ACTIVE_ENEMIES,
    minSpawnIntervalSeconds = DEFAULT_MIN_SPAWN_INTERVAL_SECONDS,
    minEliteIntervalSeconds = DEFAULT_MIN_ELITE_INTERVAL_SECONDS,
)

internal fun createContentCatalogForTesting(data: ContentBootstrapData): ContentCatalog =
    DefaultContentCatalog(data)

private class DefaultContentCatalog(data: ContentBootstrapData) : ContentCatalog {
    init {
        validateBootstrap(data)
    }

    override val version: ContentVersion = data.version

    private val items = data.items.toImmutableList()
    private val weapons = data.weapons.toImmutableList()
    private val weaponMasteries = data.weaponMasteries.toImmutableList()
    private val matrixNodes = data.matrixNodes.toImmutableList()
    private val relics = data.relics.toImmutableList()
    private val coreShapes = data.coreShapes.toImmutableList()
    private val rebirthProfiles = data.rebirthProfiles.toImmutableList()

    private val rebirth = RebirthPolicySnapshot(
        minimumLevel = ContentBounds.MIN_REBIRTH_LEVEL,
        maximumLevel = ContentBounds.MAX_REBIRTH_LEVEL,
        profiles = rebirthProfiles,
        maxActiveEnemies = data.maxActiveEnemies,
        minSpawnIntervalSeconds = data.minSpawnIntervalSeconds,
        minEliteIntervalSeconds = data.minEliteIntervalSeconds,
    )

    private val profilePolicy = ProfilePolicySnapshot(
        version = version,
        itemCount = items.size,
        coreShapes = coreShapes,
        weapons = weapons,
        rebirth = rebirth,
        matrixNodes = matrixNodes,
    )

    private val gameplayContent = GameplayContentSnapshot(
        version = version,
        items = items,
        weapons = weapons,
        weaponMasteries = weaponMasteries,
        relics = relics,
        rebirth = rebirth,
        relicPolicy = data.relicPolicy,
        tempo = data.tempo,
        build = data.build,
        coreShapes = coreShapes,
        matrixNodes = matrixNodes,
    )

    private val uiCatalog = UiCatalogSnapshot(
        version = version,
        items = items,
        weapons = weapons,
        weaponMasteries = weaponMasteries,
        relics = relics,
        coreShapes = coreShapes,
        rebirth = rebirth,
        relicPolicy = data.relicPolicy,
        tempo = data.tempo,
        build = data.build,
        matrixNodes = matrixNodes,
    )

    override fun profilePolicy(): ProfilePolicySnapshot = profilePolicy

    override fun gameplayContent(): GameplayContentSnapshot = gameplayContent

    override fun uiCatalog(): UiCatalogSnapshot = uiCatalog
}

private fun validateBootstrap(data: ContentBootstrapData) {
    require(data.version == KINETICKK_CONTENT_VERSION) {
        "Content version must be exactly ${KINETICKK_CONTENT_VERSION.value}"
    }

    requireBound("items", data.items.size, ContentBounds.MAX_ITEMS)
    requireUniqueIds("item", data.items.map(ItemDefinition::id))
    require(data.items.withIndex().all { (index, item) -> item.id == index }) {
        "Item ids must be contiguous and ordered from zero"
    }

    requireBound("weapons", data.weapons.size, ContentBounds.MAX_WEAPONS)
    requireUniqueIds("weapon", data.weapons.map(WeaponDefinition::id))
    require(data.weapons.map(WeaponDefinition::id) == WeaponId.entries.toList()) {
        "Weapon ids must match the stable WeaponId order"
    }

    requireBound("matrix nodes", data.matrixNodes.size, ContentBounds.MAX_MATRIX_NODES)
    requireUniqueIds("matrix-node", data.matrixNodes.map(MatrixNodeDefinition::id))
    require(data.matrixNodes.map(MatrixNodeDefinition::id) == MatrixNodeId.entries.toList()) {
        "Matrix node ids must match the stable MatrixNodeId order"
    }
    validateMatrix(data.matrixNodes)

    requireBound("relics", data.relics.size, ContentBounds.MAX_RELICS)
    requireUniqueIds("relic", data.relics.map(RelicDefinition::id))
    require(data.relics.map(RelicDefinition::id) == RelicId.entries.toList()) {
        "Relic ids must match the stable RelicId order"
    }

    validatePairings(data.relics, data.build)
    require(data.build.pairings.all { pairing ->
        data.relics.any { it.id == pairing.first } && data.relics.any { it.id == pairing.second }
    }) { "Pairings must join catalog Relics" }
    validateBuildReferences(data.items, data.build)

    requireUniqueIds("core-shape", data.coreShapes.map(CoreShapeDefinition::id))
    require(data.coreShapes.map(CoreShapeDefinition::id) == CoreShape.entries.toList()) {
        "Core shape ids must match the stable CoreShape order"
    }

    requireUniqueIds("weapon-mastery", data.weaponMasteries)
    require(data.weaponMasteries == WeaponMastery.entries.toList()) {
        "Weapon mastery ids must match the stable WeaponMastery order"
    }

    val maximumRebirthProfiles =
        ContentBounds.MAX_REBIRTH_LEVEL - ContentBounds.MIN_REBIRTH_LEVEL + 1
    requireBound("Rebirth profiles", data.rebirthProfiles.size, maximumRebirthProfiles)
    requireUniqueIds("Rebirth", data.rebirthProfiles.map(RebirthProfile::tier))
    require(
        data.rebirthProfiles.map(RebirthProfile::tier) ==
            (ContentBounds.MIN_REBIRTH_LEVEL..ContentBounds.MAX_REBIRTH_LEVEL).toList(),
    ) {
        "Rebirth tiers must be ordered and cover exactly 0..10"
    }
    require(data.rebirthProfiles.all { profile ->
        profile.maximumActiveEnemies == data.maxActiveEnemies &&
            profile.minimumSpawnIntervalSeconds == data.minSpawnIntervalSeconds &&
            profile.minimumEliteIntervalSeconds == data.minEliteIntervalSeconds
    }) {
        "Every Rebirth profile must capture the published Rebirth policy limits"
    }

    require(data.relicPolicy.maxSlots <= data.relics.size) {
        "Relic maxSlots must not exceed the relic catalog size"
    }
}

/**
 * Every prerequisite is a node of the same branch on an earlier row, and a node's row is one below its
 * deepest prerequisite, so each branch is a tree its screen column can draw top to bottom.
 */
private fun validateMatrix(nodes: List<MatrixNodeDefinition>) {
    val byId = nodes.associateBy(MatrixNodeDefinition::id)
    require(nodes.map(MatrixNodeDefinition::name).toSet().size == nodes.size) { "Matrix node names must be unique" }
    nodes.forEach { node ->
        val prerequisites = node.prerequisites.map { id ->
            requireNotNull(byId[id]) { "Matrix node ${node.id} requires unknown node $id" }
        }
        require(prerequisites.all { it.branch == node.branch }) { "Matrix node ${node.id} requires a node of another branch" }
        if (prerequisites.isNotEmpty()) {
            require(node.row == prerequisites.maxOf(MatrixNodeDefinition::row) + 1) {
                "Matrix node ${node.id} must sit one row below its deepest prerequisite"
            }
        }
    }
}

private fun requireBound(name: String, size: Int, maximum: Int) {
    require(size <= maximum) { "$name size $size exceeds bootstrap bound $maximum" }
}

private fun requireUniqueIds(name: String, ids: List<*>) {
    require(ids.toSet().size == ids.size) { "$name ids must be unique" }
}
