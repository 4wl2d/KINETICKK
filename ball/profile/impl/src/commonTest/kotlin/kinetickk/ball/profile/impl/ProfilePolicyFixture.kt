// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import kinetickk.ball.content.api.BuildTag
import kinetickk.ball.content.api.ContentVersion
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.ItemEffect
import kinetickk.ball.content.api.ProfilePolicySnapshot
import kinetickk.ball.content.api.RebirthDirective
import kinetickk.ball.content.api.RebirthPolicySnapshot
import kinetickk.ball.content.api.RebirthProfile
import kinetickk.ball.content.api.RelicKey
import kinetickk.ball.content.api.WeaponCadence
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponFamily
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponScaling
import kinetickk.ball.profile.api.MatrixProgress
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.PlayerEconomy
import kinetickk.ball.profile.api.PlayerLoadout
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.PlayerProfile
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.ball.profile.api.RebirthProgress
import kinetickk.ball.profile.resource.ProfileResource
import kinetickk.foundation.collections.toImmutableList
import kotlin.test.assertEquals

internal val TestProfilePolicy: ProfilePolicySnapshot by lazy(::profilePolicyFixture)

internal fun testProfileComponent(
    resource: ProfileResource = RecordingProfileResource(),
    policy: ProfilePolicySnapshot = TestProfilePolicy,
): DefaultProfileComponent = DefaultProfileComponent(resource, policy)

internal fun testDefaultProfile(policy: ProfilePolicySnapshot = TestProfilePolicy): PlayerProfile {
    val defaultWeapon = policy.weapons.first().id
    return PlayerProfile(
        loadout = PlayerLoadout(
            coreShape = policy.coreShapes.first().id,
            selectedWeapon = defaultWeapon,
            unlockedWeapons = setOf(defaultWeapon),
        ),
        matrix = MatrixProgress(),
        rebirthProgress = RebirthProgress(
            level = policy.rebirth.minimumLevel,
            highestCleared = -1,
        ),
    )
}

internal fun representativeProfile(policy: ProfilePolicySnapshot = TestProfilePolicy): PlayerProfile =
    testDefaultProfile(policy).copy(
        characterAchievements = kinetickk.ball.profile.api.CharacterAchievementProgress(eliteKills = 3, dashHits = 20),
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
        matrix = MatrixProgress(kinetickk.ball.content.api.MatrixNodeId.entries.map { if (it.ordinal % 8 < 2) 1 else 0 }),
        collection = PlayerCollection(setOf(0, policy.itemCount / 2, policy.itemCount - 1)),
        rebirthProgress = RebirthProgress(level = 2, highestCleared = 2),
    )

internal fun profileSnapshot(
    profile: PlayerProfile = representativeProfile(),
    revision: Long = 10L,
): ProfileSnapshot = ProfileSnapshot(
    revision = ProfileRevision(revision),
    profile = profile,
)

internal fun queriedProfile(component: DefaultProfileComponent): PlayerProfile {
    val preferences = component.query(ProfileQuery.GetPreferences)
    val matrix = component.query(ProfileQuery.GetMatrixProgress)
    val loadout = component.query(ProfileQuery.GetLoadout)
    val collection = component.query(ProfileQuery.GetCollection)
    val rebirth = component.query(ProfileQuery.GetRebirthProgress)
    return PlayerProfile(
        preferences = preferences.preferences,
        economy = matrix.snapshot.economy,
        loadout = loadout.snapshot.loadout,
        matrix = matrix.snapshot.progress,
        collection = collection.collection,
        rebirthProgress = rebirth.snapshot.progress,
        characterAchievements = component.query(ProfileQuery.GetHomeProgress).characterAchievements,
    )
}

internal fun assertProfileQueries(
    expected: PlayerProfile,
    component: DefaultProfileComponent,
    expectedRevision: ProfileRevision? = null,
) {
    val preferences = component.query(ProfileQuery.GetPreferences)
    val home = component.query(ProfileQuery.GetHomeProgress)
    val lab = component.query(ProfileQuery.GetMatrixProgress)
    val loadout = component.query(ProfileQuery.GetLoadout)
    val collection = component.query(ProfileQuery.GetCollection)
    val rebirth = component.query(ProfileQuery.GetRebirthProgress)

    assertEquals(expected.preferences, preferences.preferences)
    assertEquals(expected.economy, home.economy)
    assertEquals(expected.loadout, home.loadout)
    assertEquals(expected.collection, home.collection)
    assertEquals(expected.rebirthProgress, home.rebirthProgress)
    assertEquals(expected.economy, lab.snapshot.economy)
    assertEquals(expected.matrix, lab.snapshot.progress)
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
    var snapshotReadResult: ProfileSnapshotReadResult = ProfileSnapshotReadResult.Observed(null),
) : ProfileResource {
    var readBehavior: () -> ProfileSnapshotReadResult = { snapshotReadResult }
    var writeBehavior: (ProfileSnapshot) -> ProfileWriteResult = { snapshot ->
        ProfileWriteResult.Written(snapshot.revision)
    }
    var beforeWrite: ((ProfileSnapshot) -> Unit)? = null

    var readCount: Int = 0
        private set
    val writes: MutableList<ProfileSnapshot> = mutableListOf()
    val events: MutableList<String> = mutableListOf()

    override fun readSnapshot(): ProfileSnapshotReadResult {
        readCount += 1
        events += "read"
        return readBehavior()
    }

    override fun writeSnapshot(snapshot: ProfileSnapshot): ProfileWriteResult {
        writes += snapshot
        events += "write"
        beforeWrite?.invoke(snapshot)
        return writeBehavior(snapshot)
    }

}

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
