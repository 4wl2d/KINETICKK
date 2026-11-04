// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.GradleException

const val DESKTOP_TEST_SHARD_PROPERTY: String = "kinetickk.desktopTest.shard"

private const val NUCLEUS = ":ball:gameplay:nucleus"
private const val SIMULATION = "kinetickk.ball.gameplay.nucleus.simulation"

/**
 * The parallel CI jobs that together run every desktop test exactly once (`.github/workflows/ci.yml`).
 *
 * A gate shard runs exactly the test classes it lists: the seeded balance gates whose populations set the suite's
 * runtime. [REST] runs every class that no shard lists, so a class can never fall between shards, and a new test
 * lands in [REST] until someone lists it. While the lists stay disjoint no class runs twice. DesktopTestShardsTest
 * checks that, that every listed class exists in its shard's project, that every desktop test class of the tree runs
 * in exactly one shard, and that ci.yml runs each shard; CI's union job checks the reports of every run. Without the
 * property, `desktopTest` runs every class, as it always did.
 *
 * The gates run their seeds on a pool of one thread per processor, so the lists balance CPU time on the 4-vCPU
 * GitHub runner, not local wall time. A gate shard's job runs only [projectPath]'s `desktopTest`, so it compiles
 * only that project's graph.
 */
enum class DesktopTestShard(
    val id: String,
    /** The project whose `desktopTest` the shard's CI job runs; null for [REST], which runs every project's. */
    val projectPath: String?,
    val testClasses: List<String>,
) {
    // Measured on this host with the CI pool size of four threads, each gate shard plays 1,080-1,360 CPU-seconds of
    // tests, which comes to 25-31 min of the runner with its setup and nucleus compile: the runner's 4 vCPUs share 2
    // SMT cores, and CI's gameplay benchmarks run 2.7 times slower per thread than this host. Keep a shard under about
    // 1,350 CPU-seconds; a class that size needs a shard of its own.
    GATES_1("gates-1", NUCLEUS, listOf(
        "$SIMULATION.variety.RebirthBalanceGateTest",
    )),
    GATES_2("gates-2", NUCLEUS, listOf(
        "$SIMULATION.characters.ApexArchitectOffenceTest",
    )),
    GATES_3("gates-3", NUCLEUS, listOf(
        "$SIMULATION.characters.ApexArchitectDiamondDefenceTest",
    )),
    GATES_4("gates-4", NUCLEUS, listOf(
        "$SIMULATION.characters.ApexArchitectPrismDefenceTest",
        "$SIMULATION.characters.CharacterBalanceTest",
        "$SIMULATION.progression.RarityDistributionGateTest",
    )),
    GATES_5("gates-5", NUCLEUS, listOf(
        "$SIMULATION.characters.ApexArchitectCastTest",
        "$SIMULATION.sites.BountyGateTest",
        "$SIMULATION.variety.DirectorPersonalityBudgetGateTest",
    )),
    GATES_6("gates-6", NUCLEUS, listOf(
        "$SIMULATION.characters.ApexArchitectFightTest",
        "$SIMULATION.characters.UpgradeBalanceGateTest",
        "$SIMULATION.enemies.EncounterDuelGateTest",
    )),
    GATES_7("gates-7", NUCLEUS, listOf(
        "$SIMULATION.variety.RunOmenArmsGateTest",
        "$SIMULATION.dimensions.DimensionRunOutcomeGateTest",
        "$SIMULATION.structures.DungeonPilotGateTest",
    )),
    GATES_8("gates-8", NUCLEUS, listOf(
        "$SIMULATION.variety.RunOmenArmsBGateTest",
        "$SIMULATION.dimensions.DimensionPilotGateTest",
    )),
    GATES_9("gates-9", NUCLEUS, listOf(
        "$SIMULATION.variety.RunOmenArmsCGateTest",
        "$SIMULATION.NoPoiPlaythroughTest",
    )),
    GATES_10("gates-10", NUCLEUS, listOf(
        "$SIMULATION.skills.SingularityContactGateTest",
        "$SIMULATION.skills.SkillShareGateTest",
        "$SIMULATION.builds.PerceptibleItemImpactTest",
    )),
    GATES_11("gates-11", NUCLEUS, listOf(
        "$SIMULATION.progression.ItemVarietyTest",
        "$SIMULATION.progression.RunLevelCalibrationGateTest",
    )),
    GATES_12("gates-12", NUCLEUS, listOf(
        "$SIMULATION.MatterIncomeProbeTest",
        "$SIMULATION.variety.EventSignalEngagementGateTest",
        "$SIMULATION.variety.RunSkeletonVarietyTest",
    )),
    GATES_13("gates-13", NUCLEUS, listOf(
        "$SIMULATION.sites.SiteEngagementGateTest",
        "$SIMULATION.progression.ActiveItemFrequencyGateTest",
        "$SIMULATION.skills.SkillBalanceTest",
    )),
    REST("rest", null, emptyList()),
    ;

    /** Exact class names this shard's `desktopTest` excludes: [REST] leaves out every listed class. */
    val excludedTestClasses: List<String>
        get() = if (this == REST) entries.flatMap(DesktopTestShard::testClasses) else emptyList()

    companion object {
        fun parse(value: String): DesktopTestShard =
            entries.firstOrNull { it.id == value }
                ?: throw GradleException(
                    "Gradle property '$DESKTOP_TEST_SHARD_PROPERTY' must be one of " +
                        "${entries.joinToString { "'${it.id}'" }}, but was '$value'.",
                )
    }
}
