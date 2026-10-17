// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.ball.content.api.ContentCatalog
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.gameplay.impl.GameplayCompositionComponent
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.impl.ProfileComponent
import kinetickk.ball.profile.impl.ProfilePersistenceCapability
import kinetickk.ball.profile.impl.createProfileComponent
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.impl.DefaultArmoryFeature
import kinetickk.ball.profile.interaction.hangar.api.HangarFeature
import kinetickk.ball.profile.interaction.hangar.impl.DefaultHangarFeature
import kinetickk.ball.profile.interaction.matrix.api.MatrixFeature
import kinetickk.ball.profile.interaction.matrix.impl.DefaultMatrixFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.impl.DefaultRebirthFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.impl.DefaultSettingsFeature
import kinetickk.flow.session.api.AppSessionPort
import kinetickk.flow.session.impl.createAppSessionComponent
import kinetickk.flow.session.interaction.AppSessionContent
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.impl.DefaultCodexFeature
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.impl.DefaultHomeFeature
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature
import kinetickk.flow.session.interaction.profile.impl.DefaultProfileUnavailableFeature
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.impl.DefaultAudioService
import kinetickk.resource.audio.impl.TonePlaybackCapability

/** The single UI entry point used by Android, Desktop, and Web hosts. */
@Composable
fun KinetickkApp(onLanguageChanged: (String) -> Unit = {}) {
    val diagnostics = LocalCrashDiagnostics.current
    val ownerValue = remember { AppCompositionOwner(diagnostics = diagnostics) }
    DisposableEffect(ownerValue) {
        onDispose(ownerValue::close)
    }
    ownerValue.Content(onLanguageChanged)
}

/** Static Assembly: constructs owners and passes their capabilities to collaborators. */
internal class AppCompositionOwner(
    contentCatalog: ContentCatalog = createContentCatalog(),
    profileComponent: ProfileComponent? = null,
    audioService: AudioService? = null,
    gameplayComponent: GameplayCompositionComponent? = null,
    appSessionComponent: AppSessionPort? = null,
    homeFeature: HomeFeature? = null,
    settingsFeature: SettingsFeature? = null,
    matrixFeature: MatrixFeature? = null,
    armoryFeature: ArmoryFeature? = null,
    hangarFeature: HangarFeature? = null,
    rebirthFeature: RebirthFeature? = null,
    codexFeature: CodexFeature? = null,
    profileUnavailableFeature: ProfileUnavailableFeature? = null,
    private val diagnostics: CrashDiagnostics = CrashDiagnostics.None,
) {
    private val profilePolicy = contentCatalog.profilePolicy()
    private val gameplayContent = contentCatalog.gameplayContent()
    private val uiCatalog = contentCatalog.uiCatalog()

    private val profileComponent: ProfileComponent = profileComponent ?: createProfileComponent(
        persistence = createPlatformProfilePersistenceCapability(diagnostics),
        policy = profilePolicy,
    )
    private val profilePort: ProfilePort = this.profileComponent
    private val audioService: AudioService = audioService ?: DefaultAudioService(
        createPlatformTonePlaybackCapability(),
    )
    private val sessionAudioExecutor = SessionAudioExecutor(this.audioService)
    private val gameplayComponent: GameplayCompositionComponent = gameplayComponent ?:
        DefaultGameplayFeature(
            gameplayContent = gameplayContent,
            profilePort = this.profileComponent,
            profileProgress = this.profileComponent,
            audioService = this.audioService,
            diagnostics = diagnostics,
            seedSource = ::varyingGameplaySeed,
        )
    private val homeFeature: HomeFeature = homeFeature ?: DefaultHomeFeature(
        profilePort = this.profilePort,
        uiCatalog = uiCatalog,
        audioService = this.audioService,
    )
    private val settingsFeature: SettingsFeature = settingsFeature ?: DefaultSettingsFeature(
        this.profilePort,
        this.audioService,
    )
    private val matrixFeature: MatrixFeature = matrixFeature ?: DefaultMatrixFeature(
        profilePort = this.profilePort,
        matrixNodes = uiCatalog.matrixNodes,
        audioService = this.audioService,
    )
    private val armoryFeature: ArmoryFeature = armoryFeature ?: DefaultArmoryFeature(
        profilePort = this.profilePort,
        weapons = uiCatalog.weapons,
        weaponMasteries = uiCatalog.weaponMasteries,
        audioService = this.audioService,
        coreShapes = uiCatalog.coreShapes,
        relics = uiCatalog.relics,
    )
    private val hangarFeature: HangarFeature = hangarFeature ?: DefaultHangarFeature(
        profilePort = this.profilePort,
        coreShapes = uiCatalog.coreShapes,
        audioService = this.audioService,
        weapons = uiCatalog.weapons,
        relics = uiCatalog.relics,
    )
    private val rebirthFeature: RebirthFeature = rebirthFeature ?: DefaultRebirthFeature(
        profilePort = this.profilePort,
        rebirthPolicy = uiCatalog.rebirth,
        audioService = this.audioService,
        relics = uiCatalog.relics,
        items = uiCatalog.items,
        buildPolicy = uiCatalog.build.policy,
    )
    private val codexFeature: CodexFeature = codexFeature ?: DefaultCodexFeature(
        profilePort = this.profilePort,
        collectionVisits = this.profileComponent,
        uiCatalog = uiCatalog,
        audioService = this.audioService,
    )
    private val profileUnavailableFeature: ProfileUnavailableFeature =
        profileUnavailableFeature ?: DefaultProfileUnavailableFeature()
    private val appSessionComponent: AppSessionPort = appSessionComponent ?:
        createAppSessionComponent(
            profilePort = this.profileComponent,
            profileSettings = this.profileComponent,
            profileLoadout = this.profileComponent,
            profileRebirth = this.profileComponent,
            gameplayRunHost = this.gameplayComponent,
            updateAudioPreferences = sessionAudioExecutor::updatePreferences,
            playMuteFeedback = sessionAudioExecutor::playUiClick,
            playRebirthAcceptedFeedback = this.rebirthFeature::playAcceptedFeedback,
        )

    internal val sessionPort
        get() = appSessionComponent

    @Composable
    @NonRestartableComposable
    fun Content(onLanguageChanged: (String) -> Unit = {}) {
        AppSessionContent(
            profileReadPort = profilePort,
            onLanguageChanged = { language -> onLanguageChanged(language.code) },
            sessionPort = appSessionComponent,
            audioExecutor = sessionAudioExecutor,
            gameplayPresentation = gameplayComponent,
            homeFeature = homeFeature,
            settingsFeature = settingsFeature,
            matrixFeature = matrixFeature,
            armoryFeature = armoryFeature,
            hangarFeature = hangarFeature,
            rebirthFeature = rebirthFeature,
            codexFeature = codexFeature,
            profileUnavailableFeature = profileUnavailableFeature,
        )
    }

    fun close() {
        audioService.close()
    }
}

/** Platform authority is acquired only by app composition actuals. */
internal expect fun createPlatformProfilePersistenceCapability(diagnostics: CrashDiagnostics): ProfilePersistenceCapability

/** Platform authority is acquired only by app composition actuals. */
internal expect fun createPlatformTonePlaybackCapability(): TonePlaybackCapability

/**
 * Composition's source of run seeds: each run gets a fresh platform-random seed mixed with its id, so
 * spawns, wave shapes and Elite aspects differ between runs. Gameplay logs the seed it received.
 */
internal fun varyingGameplaySeed(runId: kinetickk.ball.gameplay.api.RunId): Int =
    kotlin.random.Random.nextInt() xor (runId.value * 0x9E37_79B9L).toInt()
