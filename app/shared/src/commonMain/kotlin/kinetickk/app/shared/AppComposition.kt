// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kinetickk.ball.content.api.ContentCatalog
import kinetickk.ball.content.impl.createContentCatalog
import kinetickk.ball.gameplay.impl.DefaultGameplayFeature
import kinetickk.ball.gameplay.interaction.GameplayFeature
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.impl.createPlatformProfileComponent
import kinetickk.ball.profile.interaction.armory.api.ArmoryFeature
import kinetickk.ball.profile.interaction.armory.impl.DefaultArmoryFeature
import kinetickk.ball.profile.interaction.lab.api.LabFeature
import kinetickk.ball.profile.interaction.lab.impl.DefaultLabFeature
import kinetickk.ball.profile.interaction.rebirth.api.RebirthFeature
import kinetickk.ball.profile.interaction.rebirth.impl.DefaultRebirthFeature
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.settings.impl.DefaultSettingsFeature
import kinetickk.flow.session.impl.AppSessionComponent
import kinetickk.flow.session.impl.createAppSessionComponent
import kinetickk.flow.session.interaction.AppSessionContent
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.impl.DefaultCodexFeature
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.impl.DefaultHomeFeature
import kinetickk.flow.session.interaction.reset.api.ResetModalFeature
import kinetickk.flow.session.interaction.reset.impl.DefaultResetModalFeature
import kinetickk.resource.audio.api.AudioService
import kinetickk.resource.audio.impl.DefaultAudioService

/** The single UI entry point used by Desktop and Web hosts. */
@Composable
fun KinetickkApp() {
    val ownerValue = remember { AppCompositionOwner() }
    DisposableEffect(ownerValue) {
        onDispose(ownerValue::close)
    }
    ownerValue.Content()
}

/** Static Assembly: constructs components and binds the two declared result routes. */
internal class AppCompositionOwner(
    contentCatalog: ContentCatalog = createContentCatalog(),
    profilePort: ProfilePort? = null,
    audioService: AudioService? = null,
    gameplayFeature: GameplayFeature? = null,
    appSessionComponent: AppSessionComponent? = null,
    homeFeature: HomeFeature? = null,
    settingsFeature: SettingsFeature? = null,
    labFeature: LabFeature? = null,
    armoryFeature: ArmoryFeature? = null,
    rebirthFeature: RebirthFeature? = null,
    codexFeature: CodexFeature? = null,
    resetModalFeature: ResetModalFeature? = null,
) {
    private val profilePolicy = contentCatalog.profilePolicy()
    private val gameplayContent = contentCatalog.gameplayContent()
    private val uiCatalog = contentCatalog.uiCatalog()
    private val profileResultRouter = ProfileCommandResultRouter()

    private val profilePort: ProfilePort = profilePort ?: createPlatformProfileComponent(
        policy = profilePolicy,
        commandResultSink = profileResultRouter::route,
    )
    private val audioService: AudioService = audioService ?: DefaultAudioService()
    private val sessionAudioExecutor = SessionAudioExecutor(this.audioService)
    private val gameplayFeature: GameplayFeature = gameplayFeature ?: DefaultGameplayFeature(
        this.profilePort,
        this.audioService,
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
    private val labFeature: LabFeature = labFeature ?: DefaultLabFeature(
        profilePort = this.profilePort,
        metaUpgrades = uiCatalog.metaUpgrades,
        audioService = this.audioService,
    )
    private val armoryFeature: ArmoryFeature = armoryFeature ?: DefaultArmoryFeature(
        profilePort = this.profilePort,
        weapons = uiCatalog.weapons,
        weaponMasteries = uiCatalog.weaponMasteries,
        audioService = this.audioService,
    )
    private val rebirthFeature: RebirthFeature = rebirthFeature ?: DefaultRebirthFeature(
        profilePort = this.profilePort,
        rebirthPolicy = uiCatalog.rebirth,
        audioService = this.audioService,
    )
    private val codexFeature: CodexFeature = codexFeature ?: DefaultCodexFeature(
        profilePort = this.profilePort,
        uiCatalog = uiCatalog,
        audioService = this.audioService,
    )
    private val resetModalFeature: ResetModalFeature = resetModalFeature ?: DefaultResetModalFeature(
        this.audioService,
    )
    private val appSessionComponent: AppSessionComponent = appSessionComponent ?:
        createAppSessionComponent(
            gameplayContent = gameplayContent,
            profilePort = this.profilePort,
            gameplayFeature = this.gameplayFeature,
            updateAudioPreferences = sessionAudioExecutor::updatePreferences,
            playMuteFeedback = sessionAudioExecutor::playUiClick,
            playRebirthAcceptedFeedback = this.rebirthFeature::playAcceptedFeedback,
        )

    init {
        profileResultRouter.bind(
            sessionSink = this.appSessionComponent::receiveProfileCommandResult,
            gameplaySink = this.gameplayFeature::receiveProfileCommandResult,
        )
    }

    internal val sessionPort
        get() = appSessionComponent

    @Composable
    fun Content() {
        AppSessionContent(
            sessionPort = appSessionComponent,
            audioExecutor = sessionAudioExecutor,
            gameplayFeature = gameplayFeature,
            homeFeature = homeFeature,
            settingsFeature = settingsFeature,
            labFeature = labFeature,
            armoryFeature = armoryFeature,
            rebirthFeature = rebirthFeature,
            codexFeature = codexFeature,
            resetModalFeature = resetModalFeature,
        )
    }

    fun close() {
        audioService.close()
    }
}
