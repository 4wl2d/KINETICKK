// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.toImmutableList

data class RebirthPolicySnapshot(
    val minimumLevel: Int,
    val maximumLevel: Int,
    val profiles: ImmutableList<RebirthProfile>,
    val maxActiveEnemies: Int,
    val minSpawnIntervalSeconds: Float,
    val minEliteIntervalSeconds: Float,
) {
    init {
        require(minimumLevel >= 0) { "Minimum Rebirth level must be non-negative" }
        require(maximumLevel >= minimumLevel) {
            "Maximum Rebirth level must not precede minimum level"
        }
        require(profiles.isNotEmpty()) { "Rebirth profiles must not be empty" }
        require(maxActiveEnemies > 0) { "Rebirth enemy cap must be positive" }
        require(minSpawnIntervalSeconds.isFinite() && minSpawnIntervalSeconds > 0f) {
            "Minimum spawn interval must be finite and positive"
        }
        require(minEliteIntervalSeconds.isFinite() && minEliteIntervalSeconds > 0f) {
            "Minimum elite interval must be finite and positive"
        }
    }

    /** Preserves the previous behavior of clamping out-of-range requested levels. */
    fun profile(level: Int): RebirthProfile = profiles[level.coerceIn(minimumLevel, maximumLevel) - minimumLevel]
}

data class ProfilePolicySnapshot(
    val version: ContentVersion,
    val itemCount: Int,
    val coreShapes: ImmutableList<CoreShapeDefinition>,
    val weapons: ImmutableList<WeaponDefinition>,
    val rebirth: RebirthPolicySnapshot,
    /** Core Matrix nodes in [MatrixNodeId] order. */
    val matrixNodes: ImmutableList<MatrixNodeDefinition> = defaultMatrixNodes(),
    /** Refit and Honing prices and maximum level; bases live on the character and weapon definitions. */
    val upgrades: UpgradePolicy = UpgradePolicy(),
) {
    init {
        require(itemCount >= 0) { "Item count must be non-negative" }
    }

    fun containsItem(id: Int): Boolean = id in 0 until itemCount

    fun coreShape(id: CoreShape): CoreShapeDefinition =
        coreShapes.first { definition -> definition.id == id }

    fun weapon(id: WeaponId): WeaponDefinition =
        weapons.first { definition -> definition.id == id }

    fun matrixNode(id: MatrixNodeId): MatrixNodeDefinition =
        matrixNodes.first { definition -> definition.id == id }
}

data class GameplayContentSnapshot(
    val version: ContentVersion,
    val items: ImmutableList<ItemDefinition>,
    val weapons: ImmutableList<WeaponDefinition>,
    val weaponMasteries: ImmutableList<WeaponMastery>,
    val relics: ImmutableList<RelicDefinition>,
    val rebirth: RebirthPolicySnapshot,
    val relicPolicy: RelicPolicy,
    /** Every site: POI windows, races, challenges, bounties, caches, eggs and sigils. */
    val sites: SitePolicy = SitePolicy(),
    val tempo: RunTempoProfile = RunTempoProfile(),
    val build: BuildCatalog = defaultBuildCatalog(),
    val coreShapes: ImmutableList<CoreShapeDefinition> = defaultCoreShapeDefinitions(),
    /** Enemy roster in [EnemyType] order; the spawn director reads gates and weights from it. */
    val enemies: ImmutableList<EnemyDefinition> = defaultEnemyDefinitions(),
    /** Core Matrix nodes in [MatrixNodeId] order; a run applies their effects from the profile ranks. */
    val matrixNodes: ImmutableList<MatrixNodeDefinition> = defaultMatrixNodes(),
    /** World obstacle generation, collisions and the minimap range. */
    val obstacles: ObstacleTuning = ObstacleTuning(),
    /** Data pickup magnet, vacuum and collection. */
    val pickups: PickupTuning = PickupTuning(),
    /** Active-effect table in [SkillId] order; the items carrying the effects are in [items]. */
    val skills: ImmutableList<SkillDefinition> = defaultSkillDefinitions(),
    /** Refit and Honing effects a run applies from the profile's upgrade levels. */
    val upgrades: UpgradePolicy = UpgradePolicy(),
    /** Structures and dungeons far from the start. */
    val structures: StructurePolicy = StructurePolicy(),
    /** World layers, rifts and their visits. */
    val dimensions: DimensionPolicy = DimensionPolicy(),
    /** Dimension Blessings and Pacts, plus the unique dungeon Aspects. */
    val boons: ImmutableList<RunBoonDefinition> = defaultRunBoons(),
    /** Omens, director personalities, elite affixes, champions, Keynote, world events and signals. */
    val variety: RunVarietyContent = RunVarietyContent(),
    /** Baby mode rules; STANDARD runs never read them. */
    val easyMode: EasyModeTuning = EasyModeTuning(),
) {
    init {
        require(enemies.size == EnemyType.entries.size && enemies.withIndex().all { (index, enemy) -> enemy.type.ordinal == index }) {
            "Enemy definitions must follow the EnemyType order"
        }
        require(skills.hasOneDefinitionPerSkillIdInOrder()) { "Skill definitions must follow the SkillId order" }
    }

    fun enemy(type: EnemyType): EnemyDefinition = enemies[type.ordinal]

    fun skill(id: SkillId): SkillDefinition = skills[id.ordinal]

    /** The Blessing, Pact or dungeon Aspect with [id], or null when the catalog has none. */
    fun boon(id: Int): RunBoonDefinition? = boons.firstOrNull { it.id == id }

    /** Global clause index of each item's first clause; derived once per immutable snapshot. */
    val clauseOffsets: ImmutableList<Int> = items.runningFold(0) { offset, item -> offset + item.clauses.size }
        .dropLast(1).toImmutableList()

    val totalClauseCount: Int = items.sumOf { it.clauses.size }

    fun clauseIndex(itemId: Int, clause: Int): Int = clauseOffsets[itemId] + clause

    /**
     * The id of the item carrying each active effect, by [SkillId] ordinal (−1 when no item carries it); derived once
     * per immutable snapshot. At most one item carries an effect; a catalog without active items still constructs.
     */
    val activeItemIds: ImmutableList<Int> = activeItemIdsOf(items)

    /** The item carrying [id], or null when the catalog has none. */
    fun activeItem(id: SkillId): ItemDefinition? = activeItemIds[id.ordinal].takeIf { it >= 0 }?.let(::item)

    fun pairing(a: RelicId, b: RelicId): RelicPairingDefinition? = build.pairing(a, b)

    fun coreShape(id: CoreShape): CoreShapeDefinition = coreShapes.first { it.id == id }

    fun item(id: Int): ItemDefinition? = items.getOrNull(id)?.takeIf { item -> item.id == id }

    fun weapon(id: WeaponId): WeaponDefinition =
        weapons.first { definition -> definition.id == id }

    fun weaponMasteryForLevel(level: Int): WeaponMastery =
        weaponMasteries.last { mastery -> level >= mastery.minimumLevel }

    fun weaponMasteryAfter(level: Int): WeaponMastery? =
        weaponMasteries.firstOrNull { mastery -> level < mastery.minimumLevel }

    fun matrixNode(id: MatrixNodeId): MatrixNodeDefinition =
        matrixNodes.first { definition -> definition.id == id }

    fun relic(id: RelicId): RelicDefinition =
        relics.first { definition -> definition.id == id }
}

data class UiCatalogSnapshot(
    val version: ContentVersion,
    val items: ImmutableList<ItemDefinition>,
    val weapons: ImmutableList<WeaponDefinition>,
    val weaponMasteries: ImmutableList<WeaponMastery>,
    val relics: ImmutableList<RelicDefinition>,
    val coreShapes: ImmutableList<CoreShapeDefinition>,
    val rebirth: RebirthPolicySnapshot,
    val relicPolicy: RelicPolicy,
    val sites: SitePolicy = SitePolicy(),
    val tempo: RunTempoProfile = RunTempoProfile(),
    val build: BuildCatalog = defaultBuildCatalog(),
    /** Enemy roster in [EnemyType] order, for the Codex. */
    val enemies: ImmutableList<EnemyDefinition> = defaultEnemyDefinitions(),
    /** Core Matrix nodes in [MatrixNodeId] order. */
    val matrixNodes: ImmutableList<MatrixNodeDefinition> = defaultMatrixNodes(),
    /** Active-effect table in [SkillId] order, for the HUD, cards and the Codex. */
    val skills: ImmutableList<SkillDefinition> = defaultSkillDefinitions(),
    /** Refit and Honing prices and effects, for the Workshop. */
    val upgrades: UpgradePolicy = UpgradePolicy(),
    /** Codex texts of the persisted world discoveries, in [WorldDiscovery] order. */
    val worldDiscoveries: ImmutableList<WorldDiscoveryDefinition> = defaultWorldDiscoveryDefinitions(),
) {
    init {
        require(skills.hasOneDefinitionPerSkillIdInOrder()) { "Skill definitions must follow the SkillId order" }
        require(worldDiscoveries.map { it.id } == WorldDiscovery.entries) { "World discoveries must follow the WorldDiscovery order" }
    }

    fun item(id: Int): ItemDefinition? = items.getOrNull(id)?.takeIf { item -> item.id == id }

    fun skill(id: SkillId): SkillDefinition = skills[id.ordinal]

    fun enemy(type: EnemyType): EnemyDefinition = enemies.first { definition -> definition.type == type }

    fun weapon(id: WeaponId): WeaponDefinition =
        weapons.first { definition -> definition.id == id }

    fun weaponMasteryForLevel(level: Int): WeaponMastery =
        weaponMasteries.last { mastery -> level >= mastery.minimumLevel }

    fun weaponMasteryAfter(level: Int): WeaponMastery? =
        weaponMasteries.firstOrNull { mastery -> level < mastery.minimumLevel }

    fun matrixNode(id: MatrixNodeId): MatrixNodeDefinition =
        matrixNodes.first { definition -> definition.id == id }

    fun relic(id: RelicId): RelicDefinition =
        relics.first { definition -> definition.id == id }

    fun coreShape(id: CoreShape): CoreShapeDefinition =
        coreShapes.first { definition -> definition.id == id }
}

private fun activeItemIdsOf(items: List<ItemDefinition>): ImmutableList<Int> {
    val ids = IntArray(SkillId.entries.size) { NO_ACTIVE_ITEM }
    items.forEach { item ->
        val active = item.active ?: return@forEach
        require(ids[active.ordinal] == NO_ACTIVE_ITEM) { "Active effect $active is carried by more than one item" }
        ids[active.ordinal] = item.id
    }
    return ids.toList().toImmutableList()
}

private const val NO_ACTIVE_ITEM = -1

private fun List<SkillDefinition>.hasOneDefinitionPerSkillIdInOrder(): Boolean =
    size == SkillId.entries.size && size <= ContentBounds.MAX_SKILLS &&
        withIndex().all { (index, skill) -> skill.id.ordinal == index }

/** Application-lifetime, query-only authority over shared game content. */
interface ContentCatalog {
    val version: ContentVersion

    fun profilePolicy(): ProfilePolicySnapshot

    fun gameplayContent(): GameplayContentSnapshot

    fun uiCatalog(): UiCatalogSnapshot
}
