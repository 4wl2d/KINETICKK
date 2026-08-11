// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ItemModifier
import kinetickk.ball.content.api.MetaUpgradeDefinition
import kinetickk.ball.content.api.MetaUpgradeId
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.profile.api.LabProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileBootstrapResourceResult
import kinetickk.ball.profile.api.ProfileLegacyKeys
import kinetickk.ball.profile.api.ProfileLegacyPurgeResult
import kinetickk.ball.profile.api.ProfileModuleResultDelivery
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileV4Snapshot
import kinetickk.ball.profile.api.ProfileV4WriteResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.resource.ProfileResource
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.assertEquals

internal val TestProfilePolicy: ProfilePolicySnapshot by lazy(::profilePolicyFixture)

internal fun testProfileComponent(
    resource: ProfileResource = RecordingProfileResource(),
    policy: ProfilePolicySnapshot = TestProfilePolicy,
    commandResultSink: (ProfileModuleResultDelivery) -> Unit = {},
): DefaultProfileComponent = DefaultProfileComponent(resource, policy, commandResultSink)

internal fun testDefaultProfile(policy: ProfilePolicySnapshot = TestProfilePolicy): PlayerProfile {
    val defaultWeapon = policy.weapons.first().id
    return PlayerProfile(
        loadout = PlayerLoadout(
            coreShape = policy.coreShapes.first().id,
            selectedWeapon = defaultWeapon,
            unlockedWeapons = setOf(defaultWeapon),
        ),
        labProgress = LabProgress(List(policy.metaUpgrades.size) { 0 }),
        rebirthProgress = RebirthProgress(
            level = policy.rebirth.minimumLevel,
            highestCleared = -1,
        ),
    )
}

internal fun representativeProfile(policy: ProfilePolicySnapshot = TestProfilePolicy): PlayerProfile =
    testDefaultProfile(policy).copy(
        preferences = PlayerPreferences(
            soundEnabled = true,
            musicEnabled = false,
            masterVolume = 0.75f,
            simulationSpeed = 1.35f,
            textScale = 1.5f,
            screenShake = false,
        ),
        economy = PlayerEconomy(matter = 5_000L, lifetimeMatter = 10_000L),
        loadout = PlayerLoadout(
            coreShape = CoreShape.SHARD,
            selectedWeapon = WeaponId.MORNINGSTAR,
            unlockedWeapons = setOf(WeaponId.FLUX_WAKE, WeaponId.MORNINGSTAR),
        ),
        labProgress = LabProgress(List(policy.metaUpgrades.size) { 1 }),
        collection = PlayerCollection(setOf(0, policy.itemCount / 2, policy.itemCount - 1)),
        rebirthProgress = RebirthProgress(level = 2, highestCleared = 2),
    )

internal fun v4Snapshot(
    profile: PlayerProfile = representativeProfile(),
    revision: Long = 10L,
    legacyResetConfirmed: Boolean = false,
    policy: ProfilePolicySnapshot = TestProfilePolicy,
    contentVersion: ContentVersion = policy.version,
): ProfileV4Snapshot = ProfileV4Snapshot(
    contentVersion = contentVersion,
    revision = ProfileRevision(revision),
    legacyResetConfirmed = legacyResetConfirmed,
    profile = profile,
)

internal fun queriedProfile(component: DefaultProfileComponent): PlayerProfile {
    val preferences = component.query(ProfileQuery.GetPreferences)
    val lab = component.query(ProfileQuery.GetLabProgress)
    val loadout = component.query(ProfileQuery.GetLoadout)
    val collection = component.query(ProfileQuery.GetCollection)
    val rebirth = component.query(ProfileQuery.GetRebirthProgress)
    return PlayerProfile(
        preferences = preferences.preferences,
        economy = lab.snapshot.economy,
        loadout = loadout.snapshot.loadout,
        labProgress = lab.snapshot.progress,
        collection = collection.collection,
        rebirthProgress = rebirth.snapshot.progress,
    )
}

internal fun assertProfileQueries(
    expected: PlayerProfile,
    component: DefaultProfileComponent,
    expectedRevision: ProfileRevision? = null,
) {
    val preferences = component.query(ProfileQuery.GetPreferences)
    val home = component.query(ProfileQuery.GetHomeProgress)
    val lab = component.query(ProfileQuery.GetLabProgress)
    val loadout = component.query(ProfileQuery.GetLoadout)
    val collection = component.query(ProfileQuery.GetCollection)
    val rebirth = component.query(ProfileQuery.GetRebirthProgress)

    assertEquals(expected.preferences, preferences.preferences)
    assertEquals(expected.economy, home.economy)
    assertEquals(expected.loadout, home.loadout)
    assertEquals(expected.collection, home.collection)
    assertEquals(expected.rebirthProgress, home.rebirthProgress)
    assertEquals(expected.economy, lab.snapshot.economy)
    assertEquals(expected.labProgress, lab.snapshot.progress)
    assertEquals(expected.economy, loadout.snapshot.economy)
    assertEquals(expected.loadout, loadout.snapshot.loadout)
    assertEquals(expected.collection, collection.collection)
    assertEquals(expected.rebirthProgress, rebirth.snapshot.progress)

    val projections = listOf(preferences, home, lab, loadout, collection, rebirth)
    projections.forEach { projection ->
        assertEquals(component.instanceId, projection.instanceId)
        expectedRevision?.let { assertEquals(it, projection.revision) }
    }
    assertEquals(1, projections.map { projection -> projection.revision }.distinct().size)
}

internal class RecordingProfileResource(
    var bootstrapResult: ProfileBootstrapResourceResult = ProfileBootstrapResourceResult.Observed(
        snapshot = null,
        legacyKeys = ProfileLegacyKeys.NONE,
    ),
) : ProfileResource {
    var readBehavior: () -> ProfileBootstrapResourceResult = { bootstrapResult }
    var writeBehavior: (ProfileV4Snapshot) -> ProfileV4WriteResult = { snapshot ->
        ProfileV4WriteResult.Written(snapshot.revision)
    }
    var purgeBehavior: () -> ProfileLegacyPurgeResult = { ProfileLegacyPurgeResult.Purged }
    var beforeWrite: ((ProfileV4Snapshot) -> Unit)? = null
    var beforePurge: (() -> Unit)? = null

    var readCount: Int = 0
        private set
    val writes: MutableList<ProfileV4Snapshot> = mutableListOf()
    var purgeCount: Int = 0
        private set
    val events: MutableList<String> = mutableListOf()

    override fun readBootstrap(): ProfileBootstrapResourceResult {
        readCount += 1
        events += "read"
        return readBehavior()
    }

    override fun writeV4(snapshot: ProfileV4Snapshot): ProfileV4WriteResult {
        writes += snapshot
        events += "write"
        beforeWrite?.invoke(snapshot)
        return writeBehavior(snapshot)
    }

    override fun purgeLegacy(): ProfileLegacyPurgeResult {
        purgeCount += 1
        events += "purge"
        beforePurge?.invoke()
        return purgeBehavior()
    }
}

private fun profilePolicyFixture(): ProfilePolicySnapshot = ProfilePolicySnapshot(
    version = ContentVersion("test-content"),
    itemCount = 400,
    coreShapes = listOf(0L, 25L, 90L).mapIndexed { index, cost ->
        CoreShapeDefinition(CoreShape.entries[index], cost)
    }.toImmutableList(),
    weapons = intArrayOf(0, 25, 55, 95, 145, 215, 305, 430, 610, 860, 1_200, 1_650)
        .mapIndexed { index, cost ->
            WeaponDefinition(
                WeaponId.entries[index],
                "Weapon $index",
                "Test weapon $index",
                listOf("TEST"),
                cost,
            )
        }.toImmutableList(),
    metaUpgrades = listOf(10 to 18, 10 to 22, 8 to 24, 8 to 26, 8 to 30, 10 to 34, 10 to 38, 12 to 45)
        .mapIndexed { index, (maxRanks, baseCost) ->
            MetaUpgradeDefinition(
                MetaUpgradeId.entries[index],
                "Upgrade $index",
                "Test upgrade $index",
                maxRanks,
                baseCost,
                ItemModifier(ItemEffect.MAX_INTEGRITY, 1f),
            )
        }.toImmutableList(),
    rebirth = testRebirthPolicy(),
)

internal fun testRebirthPolicy(
    minimumLevel: Int = 0,
    maximumLevel: Int = 10,
): RebirthPolicySnapshot = RebirthPolicySnapshot(
    minimumLevel = minimumLevel,
    maximumLevel = maximumLevel,
    profiles = (minimumLevel..maximumLevel).map { tier ->
        RebirthProfile(
            tier, RebirthDirective.BASELINE, 5, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 0,
            120, 0.09f, 24f,
        )
    }.toImmutableList(),
    maxActiveEnemies = 120,
    minSpawnIntervalSeconds = 0.09f,
    minEliteIntervalSeconds = 24f,
)
