// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertTrue

class AudioFaultStageVerifierTest {
    @Test
    fun exactLiveProjectionFaultSplitPassesAtAllFourLayers() {
        val violations = audioRuntimeFaultStageViolations(validAudioSources())

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun resourceRunCatchingCannotReplaceSynchronousFaultPropagation() {
        val sources = mutate(AUDIO_RESOURCE_PATH) { code ->
            code.replace("platform.close()", "runCatching { platform.close() }")
        }

        assertViolation(audioRuntimeFaultStageViolations(sources), "`runCatching`")
        assertViolation(audioRuntimeFaultStageViolations(sources), "Audio Resource capability calls")
    }

    @Test
    fun gameplayAudioBranchCannotCatchAcceptedFrameProjectionFault() {
        val sources = mutate(GAMEPLAY_PATH) { code ->
            code.replace(
                "audioExecutor.ensureUnlocked()",
                "try { audioExecutor.ensureUnlocked() } catch (_: Throwable) { Unit }",
            )
        }

        assertViolation(audioRuntimeFaultStageViolations(sources), "synchronous `Throwable` catch")
        assertViolation(audioRuntimeFaultStageViolations(sources), "Gameplay output audio branches")
    }

    @Test
    fun desktopWorkerCannotTurnSynthesisFaultIntoBestEffortSuccess() {
        val sources = mutate(DESKTOP_PATH) { code ->
            code.replace("synthesize(request)", "runCatching { synthesize(request) }")
        }

        assertViolation(audioRuntimeFaultStageViolations(sources), "`runCatching`")
        assertViolation(audioRuntimeFaultStageViolations(sources), "Desktop Tone submission/synthesis/close")
    }

    @Test
    fun webKotlinWrapperCannotCatchSynchronousInvocationFault() {
        val sources = mutate(WEB_PATH) { code ->
            code.replace(
                "closeWebAudio(context)",
                "try { closeWebAudio(context) } catch (_: Throwable) { Unit }",
            )
        }

        assertViolation(audioRuntimeFaultStageViolations(sources), "synchronous `Throwable` catch")
        assertViolation(audioRuntimeFaultStageViolations(sources), "Web Tone Kotlin wrappers")
    }

    @Test
    fun webJavaScriptHelperCannotCatchSynchronousGraphFault() {
        val sources = mutate(WEB_PATH) { code ->
            code.replace(
                "const oscillator = context.createOscillator();",
                "try { const oscillator = context.createOscillator(); } " +
                    "catch (failure) { return null; }",
            )
        }

        assertViolation(audioRuntimeFaultStageViolations(sources), "must let synchronous JavaScript faults propagate")
    }

    @Test
    fun webPromiseRejectionSinksRemainExactAndComplete() {
        val missing = mutate(WEB_PATH) { code ->
            code.replaceFirst("resume.catch(() => undefined);", "")
        }
        val wrong = mutate(WEB_PATH) { code ->
            code.replaceFirst("close.catch(() => undefined);", "close.catch(() => false);")
        }

        assertViolation(audioRuntimeFaultStageViolations(missing), "exactly three")
        assertViolation(audioRuntimeFaultStageViolations(wrong), "exactly three")
        assertViolation(audioRuntimeFaultStageViolations(wrong), "close.catch(() => undefined);")
    }

    @Test
    fun policyAndApplicabilityPinTheSameLiveProjectionSplit() {
        assertTrue(
            audioProjectionPolicyViolations(validPolicy(), validApplicability()).isEmpty(),
        )

        assertViolation(
            audioProjectionPolicyViolations(
                validPolicy().replace("no caller-propagation claim", "caller propagation"),
                validApplicability(),
            ),
            "no caller-propagation claim",
        )
        assertViolation(
            audioProjectionPolicyViolations(
                validPolicy(),
                validApplicability().replace("no typed Audio Fact/result/status", "typed Audio status"),
            ),
            "no typed Audio Fact/result/status",
        )
    }

    private fun validAudioSources(): List<SourceDocument> = listOf(
        SourceDocument(
            AUDIO_RESOURCE_PATH,
            """
                class DefaultAudioService {
                    fun advance() {
                        platform.playIfAllowed(request.copy(gain = request.gain * volume))
                    }

                    fun ensureUnlocked() {
                        if (!closed) platform.unlock()
                    }

                    fun close() {
                        platform.close()
                    }
                }

                private fun TonePlaybackCapability.playIfAllowed(request: ToneRequest) {
                    if (!isToneRequestAllowed(request)) return
                    play(request)
                }
            """.trimIndent(),
        ),
        SourceDocument(
            GAMEPLAY_PATH,
            """
                internal class GameComponent {
                    private fun execute(output: GameplayOutput, item: GameplayWorkItem) {
                        when (output) {
                            is GameplayOutput.AdvanceAudio ->
                                audioExecutor.advance(output.realDeltaSeconds, output.cues)
                            GameplayOutput.EnsureAudioUnlocked ->
                                audioExecutor.ensureUnlocked()
                        }
                    }

                    private fun next() = Unit
                }
            """.trimIndent(),
        ),
        SourceDocument(
            DESKTOP_PATH,
            """
                private class DesktopTonePlaybackCapability {
                    fun play(request: ToneRequest) {
                        executor.execute { synthesize(request) }
                    }

                    fun close() {
                        executor.shutdownNow()
                    }

                    private fun synthesize(request: ToneRequest) {
                        AudioSystem.getSourceDataLine(format).use { line ->
                            line.write(bytes, 0, bytes.size)
                        }
                    }
                }
            """.trimIndent(),
        ),
        SourceDocument(
            WEB_PATH,
            """
                private class WebTonePlaybackCapability {
                    fun unlock() {
                        context = unlockWebAudio(context)
                    }

                    fun play() {
                        context = playWebTone(context, frequency, duration, volume, wave)
                    }

                    fun close() {
                        closeWebAudio(context)
                        context = null
                    }
                }

                private fun unlockWebAudio() {
                    const context = current || new AudioContext();
                    const resume = context.resume();
                    resume.catch(() => undefined);
                }

                private fun playWebTone() {
                    const context = current || new AudioContext();
                    const resume = context.resume();
                    resume.catch(() => undefined);
                    const oscillator = context.createOscillator();
                    const gain = context.createGain();
                    oscillator.connect(gain);
                    gain.connect(context.destination);
                    oscillator.start();
                    oscillator.stop(context.currentTime + duration + 0.015);
                }

                private fun closeWebAudio() {
                    const close = current.close();
                    close.catch(() => undefined);
                }
            """.trimIndent(),
        ),
        SourceDocument(RESOURCE_TEST_PATH, RESOURCE_TEST_TOKEN),
        SourceDocument(GAMEPLAY_TEST_PATH, GAMEPLAY_TEST_TOKEN),
        SourceDocument(
            DESKTOP_TEST_PATH,
            listOf(DESKTOP_TEST_TOKEN, DESKTOP_QUEUE_TEST_TOKEN, DESKTOP_BUFFER_TEST_TOKEN).joinToString("\n"),
        ),
        SourceDocument(WEB_TEST_PATH, WEB_TEST_TOKEN),
    )

    private fun mutate(path: String, transform: (String) -> String): List<SourceDocument> =
        validAudioSources().map { source ->
            if (source.relativePath == path) source.copy(text = transform(source.text)) else source
        }

    private fun validPolicy(): String =
        """
            Core §9.13 live mechanical Projection
            Audio produces no typed Fact, result, or status
            Synchronous Audio Resource and platform calls propagate under runtime-fault policy
            a synthesis fault escapes that `Runnable` to the runtime
            no caller-propagation claim
            `.catch(() => undefined)`
            post-acceptance mechanical projection loss
            synchronous JavaScript invocation and graph faults still propagate
        """.trimIndent()

    private fun validApplicability(): String =
        """
            live mechanical Audio Projection (Core §9.13)
            no typed Audio Fact/result/status
            Desktop worker faults escape the detached `Runnable` to runtime
            Web native `resume()`/`close()` Promise rejections
            `.catch(() => undefined)`
            Synchronous JavaScript invocation/graph faults propagate
        """.trimIndent()

    private fun assertViolation(violations: List<String>, token: String) {
        assertTrue(violations.any { token in it }, violations.joinToString("\n"))
    }

    private companion object {
        const val AUDIO_RESOURCE_PATH =
            "resource/audio/impl/src/commonMain/kotlin/kinetickk/resource/audio/impl/DefaultAudioService.kt"
        const val GAMEPLAY_PATH =
            "ball/gameplay/impl/src/commonMain/kotlin/kinetickk/ball/gameplay/impl/GameComponent.kt"
        const val DESKTOP_PATH =
            "app/shared/src/desktopMain/kotlin/kinetickk/app/shared/PlatformCapabilities.desktop.kt"
        const val WEB_PATH =
            "app/shared/src/wasmJsMain/kotlin/kinetickk/app/shared/PlatformCapabilities.wasm.kt"
        const val RESOURCE_TEST_PATH =
            "resource/audio/impl/src/commonTest/kotlin/kinetickk/resource/audio/impl/DefaultAudioServiceTest.kt"
        const val GAMEPLAY_TEST_PATH =
            "ball/gameplay/impl/src/commonTest/kotlin/kinetickk/ball/gameplay/impl/GameComponentTest.kt"
        const val DESKTOP_TEST_PATH =
            "app/shared/src/desktopTest/kotlin/kinetickk/app/shared/PlatformCapabilitiesDesktopTest.kt"
        const val WEB_TEST_PATH =
            "app/shared/src/wasmJsTest/kotlin/kinetickk/app/shared/PlatformCapabilitiesWebTest.kt"
        const val RESOURCE_TEST_TOKEN =
            "capabilityFaultsPropagateForUnlockPlayAndCloseWithoutInventingClosedState"
        const val GAMEPLAY_TEST_TOKEN =
            "audioFaultsPropagateAfterAcceptedFramesCommitAndDrainExactResults"
        const val DESKTOP_TEST_TOKEN = "audioBrokerIsInstanceOwnedAndCloseIsIdempotent"
        const val DESKTOP_QUEUE_TEST_TOKEN = "workerAndDiscardOldestQueueEnforceOneAndTwentyFour"
        const val DESKTOP_BUFFER_TEST_TOKEN = "synthesisBufferAcceptsMaximumDurationAndRejectsNext"
        const val WEB_TEST_TOKEN =
            "webAudioSynchronousProviderFaultsPropagateWithoutFabricatingClosedState"
    }
}
