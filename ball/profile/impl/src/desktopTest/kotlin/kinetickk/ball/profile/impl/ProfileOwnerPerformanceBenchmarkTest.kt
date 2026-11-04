// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.impl

import java.io.File
import java.lang.management.ManagementFactory
import kinetickk.ball.profile.api.ProfilePersistenceStatus
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.api.ProfileRefusal
import kinetickk.ball.profile.api.ProfileSettingsChanged
import kinetickk.ball.profile.api.ProfileSnapshot
import kinetickk.ball.profile.api.ProfileSnapshotReadResult
import kinetickk.ball.profile.api.ProfileWriteResult
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.dispatch.BoundedCompletionDeque
import kinetickk.foundation.dispatch.InlineAcceptance
import kinetickk.foundation.dispatch.InlineReply
import kinetickk.foundation.dispatch.call
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Opt-in paired owner benchmark: copy this exact fixture to both revisions and alternate fresh JVMs.
 * Both cases toggle mute through a real accepted caller and drain the save completion. The first
 * uses a bounded recording Resource; the second uses the public factory and real codec/readback
 * over one in-memory string. Neither opens player data. Queries/assertions stay outside timing.
 */
class ProfileOwnerPerformanceBenchmarkTest {
    @Test
    fun measureAcceptedMuteAndPersistenceCompletion() {
        if (System.getenv("KK_PROFILE_OWNER_BENCH") != "1") return
        val warmups = positiveOption("WARMUPS", 5)
        val samples = positiveOption("SAMPLES", 10)
        val iterationMillis = positiveOption("ITERATION_MS", 180)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(threads.isThreadAllocatedMemorySupported)
        if (!threads.isThreadAllocatedMemoryEnabled) threads.isThreadAllocatedMemoryEnabled = true
        val threadId = Thread.currentThread().id
        val scenarios = listOf(ownerOnlyFixture(), ownerWithCodecFixture())
        for (fixture in scenarios) {
            fixture.caller.toggle()
            fixture.verify()
            repeat(warmups) {
                measureBatch(fixture.caller, iterationMillis)
                fixture.verify()
            }
            val elapsed = DoubleArray(samples)
            val allocated = DoubleArray(samples)
            val operations = LongArray(samples)
            repeat(samples) { sample ->
                val allocatedBefore = threads.getThreadAllocatedBytes(threadId)
                val batch = measureBatch(fixture.caller, iterationMillis)
                val allocatedAfter = threads.getThreadAllocatedBytes(threadId)
                check(allocatedBefore >= 0L && allocatedAfter >= allocatedBefore)
                operations[sample] = batch.operations
                elapsed[sample] = batch.nanos.toDouble() / batch.operations
                allocated[sample] = (allocatedAfter - allocatedBefore).toDouble() / batch.operations
                fixture.verify()
            }
            val result = "{\"suite\":\"profile-owner-mute-v1\",\"scenario\":${json(fixture.name)}," +
                "\"label\":${json(System.getenv("KK_PROFILE_OWNER_LABEL") ?: "run")}," +
                "\"revision\":${json(System.getenv("KK_PROFILE_OWNER_REVISION") ?: "unknown")}," +
                "\"dirty\":${json(System.getenv("KK_PROFILE_OWNER_DIRTY") ?: "unknown")}," +
                "\"fork\":${positiveOption("FORK", 1)}," +
                "\"javaVersion\":${json(System.getProperty("java.version"))}," +
                "\"vmName\":${json(System.getProperty("java.vm.name"))}," +
                "\"osName\":${json(System.getProperty("os.name"))}," +
                "\"osArch\":${json(System.getProperty("os.arch"))}," +
                "\"processors\":${Runtime.getRuntime().availableProcessors()}," +
                "\"warmups\":$warmups,\"samples\":$samples,\"iterationMillis\":$iterationMillis," +
                "\"validatedOperations\":${fixture.caller.accepted}," +
                "\"operations\":[${operations.joinToString()}]," +
                "\"nsPerOperation\":[${elapsed.joinToString()}]," +
                "\"allocatedBytesPerOperation\":[${allocated.joinToString()}]}"
            println(result)
            File(System.getenv("KK_PROFILE_OWNER_OUT") ?: "build/profile-owner-benchmark.jsonl").apply {
                parentFile?.mkdirs()
                appendText(result + "\n")
            }
        }
    }

    private fun positiveOption(suffix: String, default: Int): Int =
        (System.getenv("KK_PROFILE_OWNER_$suffix")?.toInt() ?: default).also { require(it > 0) }
}

private data class OwnerBenchmarkBatch(val operations: Long, val nanos: Long)

private fun measureBatch(caller: OwnerBenchmarkCaller, iterationMillis: Int): OwnerBenchmarkBatch {
    val targetNanos = iterationMillis.toLong() * 1_000_000L
    val acceptedBefore = caller.accepted
    var operations = 0L
    val started = System.nanoTime()
    var elapsed: Long
    do {
        repeat(32) { caller.toggle() }
        operations += 32L
        elapsed = System.nanoTime() - started
    } while (elapsed < targetNanos)
    check(caller.accepted - acceptedBefore == operations) { "Not every timed call completed exactly once" }
    return OwnerBenchmarkBatch(operations, elapsed)
}

/** Same accepted-call contract as the application, retaining only the latest reply. */
private class OwnerBenchmarkCaller(private val component: ProfileComponent) {
    var accepted = 0L
        private set
    var last: ProfileSettingsChanged? = null
        private set
    private val acceptance = InlineAcceptance(BoundedCompletionDeque<Input>(1))
    private val output = immutableListOf(Unit)
    private val noOutputs = immutableListOf<Unit>()
    private val invoke: (InlineReply<ProfileSettingsChanged, ProfileRefusal>) -> Unit = component::toggleMute

    fun toggle() = acceptance.dispatch {
        acceptance.acceptAndDrain(
            rootItem = Input.Requested,
            rootFrame = output,
            outputs = { it },
            acceptFrame = { input, _ ->
                if (input is Input.Changed) {
                    accepted++
                    last = input.result
                }
            },
            decideCompletion = { noOutputs },
            execute = { _, _ ->
                acceptance.call(
                    invoke = invoke,
                    acceptedInput = { Input.Changed(it) },
                    refusedInput = { error("Benchmark mute was refused: $it") },
                )
            },
        )
    }

    private sealed interface Input {
        data object Requested : Input
        data class Changed(val result: ProfileSettingsChanged) : Input
    }
}

private class OwnerBenchmarkFixture(
    val name: String,
    val component: ProfileComponent,
    private val writes: () -> Long,
    private val snapshot: () -> ProfileSnapshot,
) {
    val caller = OwnerBenchmarkCaller(component)
    private val initial = component.query(ProfileQuery.GetPreferences)

    fun verify() {
        val persisted = snapshot()
        val reply = checkNotNull(caller.last)
        val query = component.query(ProfileQuery.GetPreferences)
        assertEquals(caller.accepted, writes(), "Exactly one save per accepted toggle")
        val soundEnabled = initial.preferences.soundEnabled xor (caller.accepted % 2L != 0L)
        assertEquals(initial.preferences.copy(soundEnabled = soundEnabled, musicEnabled = soundEnabled), query.preferences)
        assertEquals(initial.revision.value + caller.accepted * 2L, query.revision.value)
        assertEquals(query.preferences, persisted.profile.preferences)
        assertEquals(query.preferences, reply.preferences)
        assertEquals(persisted.revision, reply.revision)
        assertEquals(query.revision.value - 1L, persisted.revision.value)
        assertEquals(
            ProfilePersistenceStatus.Persisted(persisted.revision),
            component.query(ProfileQuery.GetPersistenceStatus).persistence,
        )
    }
}

private fun ownerOnlyFixture(): OwnerBenchmarkFixture {
    var writes = 0L
    var last: ProfileSnapshot? = null
    val resource = object : ProfileResource {
        override fun readSnapshot() = ProfileSnapshotReadResult.Observed(null)
        override fun writeSnapshot(snapshot: ProfileSnapshot): ProfileWriteResult {
            writes++
            last = snapshot
            return ProfileWriteResult.Written(snapshot.revision)
        }
    }
    return OwnerBenchmarkFixture(
        "owner_with_fake_resource",
        DefaultProfileComponent(resource, TestProfilePolicy),
        writes = { writes },
        snapshot = { checkNotNull(last) },
    )
}

private fun ownerWithCodecFixture(): OwnerBenchmarkFixture {
    var writes = 0L
    var storedPayload: String? = null
    val persistence = object : ProfilePersistenceCapability {
        override fun readSnapshot() = ProfilePersistenceReadResult.Observed(storedPayload)
        override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult {
            writes++
            storedPayload = payload
            return ProfilePersistenceMutationResult.COMPLETED
        }
    }
    val verificationReader = createProfileResource(object : ProfilePersistenceCapability {
        override fun readSnapshot() = ProfilePersistenceReadResult.Observed(storedPayload)
        override fun writeSnapshot(payload: String): ProfilePersistenceMutationResult =
            error("The verification reader must never write")
    })
    return OwnerBenchmarkFixture(
        "owner_with_codec_and_readback",
        createProfileComponent(persistence, TestProfilePolicy),
        writes = { writes },
        snapshot = {
            val read = verificationReader.readSnapshot()
            check(read is ProfileSnapshotReadResult.Observed) { "Benchmark stored an invalid profile: $read" }
            checkNotNull(read.snapshot)
        },
    )
}

private fun json(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        append(when (character) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> character.toString()
        })
    }
    append('"')
}
