// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.profile.interaction.workshop.impl

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.armory.api.ArmoryOutput
import kinetickk.ball.profile.interaction.armory.impl.ArmoryAction
import kinetickk.ball.profile.interaction.armory.impl.ArmoryBody
import kinetickk.ball.profile.interaction.armory.impl.ArmoryEffect
import kinetickk.ball.profile.interaction.armory.impl.ArmoryLayoutHolder
import kinetickk.ball.profile.interaction.armory.impl.ArmoryPager
import kinetickk.ball.profile.interaction.armory.impl.ArmoryReducer
import kinetickk.ball.profile.interaction.armory.impl.ArmoryViewState
import kinetickk.ball.profile.interaction.armory.impl.armoryGridScrollTarget
import kinetickk.ball.profile.interaction.armory.impl.armoryNoticeText
import kinetickk.ball.profile.interaction.armory.impl.armoryPagerWidth
import kinetickk.ball.profile.interaction.armory.impl.drawArmoryBackground
import kinetickk.ball.profile.interaction.armory.impl.rememberArmoryGridStepper
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.hangar.api.HangarOutput
import kinetickk.ball.profile.interaction.hangar.api.HangarRenderModel
import kinetickk.ball.profile.interaction.hangar.impl.HangarAction
import kinetickk.ball.profile.interaction.hangar.impl.HangarBody
import kinetickk.ball.profile.interaction.hangar.impl.HangarLayoutHolder
import kinetickk.ball.profile.interaction.hangar.impl.HangarReducer
import kinetickk.ball.profile.interaction.hangar.impl.HangarState
import kinetickk.ball.profile.interaction.hangar.impl.HangarUiEffect
import kinetickk.ball.profile.interaction.hangar.impl.drawHangarBackground
import kinetickk.ball.profile.interaction.hangar.impl.hangarWeapons
import kinetickk.ball.profile.interaction.hangar.impl.toRenderModel
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.matrix.api.MatrixOutput
import kinetickk.ball.profile.interaction.matrix.impl.MatrixAction
import kinetickk.ball.profile.interaction.matrix.impl.MatrixBody
import kinetickk.ball.profile.interaction.matrix.impl.MatrixLayoutHolder
import kinetickk.ball.profile.interaction.matrix.impl.MatrixReducer
import kinetickk.ball.profile.interaction.matrix.impl.MatrixResetButton
import kinetickk.ball.profile.interaction.matrix.impl.MatrixState
import kinetickk.ball.profile.interaction.matrix.impl.MatrixUiEffect
import kinetickk.ball.profile.interaction.matrix.impl.drawMatrixBackground
import kinetickk.ball.profile.interaction.matrix.impl.matrixResetWidth
import kinetickk.ball.profile.interaction.matrix.impl.nodeTotals
import kinetickk.ball.profile.interaction.matrix.impl.toRenderModel
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.api.WorkshopFeature
import kinetickk.ball.profile.interaction.workshop.api.WorkshopOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.resource.audio.api.AudioService
import kotlinx.coroutines.delay

/**
 * The Workshop: the Characters (Hangar), Weapons (Armory) and Core Matrix bodies behind one header
 * with tabs. Weapon unlocks, Matrix ranks and resets write their typed [ProfilePulse]s; character
 * selection is a [WorkshopOutput.SelectCharacter] for Session, the authority on "between runs".
 *
 * The shown tab and the serial of the last applied shell request live on this instance, which
 * Assembly keeps for the whole app session; inspected tiles, segments and scrolls are remembered per
 * opening ([WorkshopFeature.Content]'s `routeToken`). Each tab reads its Profile query when shown and
 * after every answer, so a purchase on one tab is current on the next.
 */
class DefaultWorkshopFeature(
    private val profilePort: ProfilePort,
    private val matrixNodes: ImmutableList<MatrixNodeDefinition>,
    private val weapons: ImmutableList<WeaponDefinition>,
    private val weaponMasteries: ImmutableList<WeaponMastery>,
    private val coreShapes: ImmutableList<CoreShapeDefinition>,
    audioService: AudioService,
    private val relics: ImmutableList<RelicDefinition> = immutableListOf(),
) : WorkshopFeature {
    private val audioExecutor = ProfileAudioExecutor(audioService)
    private val armoryReducer = ArmoryReducer(weapons)
    private var lastTab = WorkshopTab.CHARACTERS
    private var lastSerial: Int? = null

    @Composable
    override fun Content(
        routeToken: Long,
        tabRequest: WorkshopTabRequest?,
        characterSelectionAvailable: Boolean,
        activeRunWeapon: WeaponId?,
        onOutput: (WorkshopOutput) -> Unit,
    ) {
        val language = LocalAppLanguage.current
        val textScale = remember(profilePort, routeToken) { profilePort.query(ProfileQuery.GetPreferences).preferences.textScale }
        val scale = profileTextScale(textScale)
        val measurer = rememberKkCanvasMeasurer(scale)
        var workshopValue by remember(routeToken) { mutableStateOf(WorkshopState(lastTab, lastSerial)) }
        // A new shell request shows its tab in this very frame; its effects (announcement, focus) follow.
        val shown = tabRequest?.let { WorkshopReducer.reduce(workshopValue, WorkshopAction.Request(it)).state } ?: workshopValue
        var focusSerialValue by remember(routeToken) { mutableIntStateOf(0) }
        // Bumped after every Profile answer: each tab re-reads its query.
        var revisionValue by remember(routeToken) { mutableIntStateOf(0) }
        var politeValue by remember(routeToken) { mutableStateOf<WorkshopNotice?>(null) }
        var alertValue by remember(routeToken) { mutableStateOf<WorkshopNotice?>(null) }
        LaunchedEffect(politeValue) { if (politeValue != null) { delay(WORKSHOP_ANNOUNCE_MS); politeValue = null } }
        LaunchedEffect(alertValue) { if (alertValue != null) { delay(WORKSHOP_ANNOUNCE_MS); alertValue = null } }
        var noticeSequence by remember(routeToken) { mutableIntStateOf(0) }

        fun announce(text: String, assertive: Boolean = false) {
            noticeSequence += 1
            val notice = WorkshopNotice(text, noticeSequence)
            if (assertive) alertValue = notice else politeValue = notice
        }

        fun dispatchWorkshop(action: WorkshopAction) {
            val reduction = WorkshopReducer.reduce(workshopValue, action)
            workshopValue = reduction.state
            lastTab = reduction.state.tab
            lastSerial = reduction.state.appliedSerial
            reduction.effects.forEach { effect ->
                when (effect) {
                    is WorkshopEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is WorkshopEffect.AnnounceTab -> announce(tabAnnouncement(effect.tab, language))
                    WorkshopEffect.FocusBody -> focusSerialValue += 1
                }
            }
        }
        LaunchedEffect(tabRequest) { tabRequest?.let { dispatchWorkshop(WorkshopAction.Request(it)) } }

        // Per-opening memory of every tab, kept while another tab is shown.
        var hangarInspectedValue by remember(routeToken) { mutableStateOf<CoreShape?>(null) }
        var armoryViewValue by remember(routeToken) { mutableStateOf<ArmoryViewState?>(null) }
        var matrixValue by remember(routeToken) { mutableStateOf<MatrixState?>(null) }
        val hangarDetail = remember(routeToken) { ScrollState(0) }
        val armoryGrid = remember(routeToken) { ScrollState(0) }
        val matrixGrid = remember(routeToken) { ScrollState(0) }
        val hangarHolder = remember { HangarLayoutHolder() }
        val armoryHolder = remember { ArmoryLayoutHolder() }
        val matrixHolder = remember { MatrixLayoutHolder() }
        val focusSerial = focusSerialValue
        val revision = revisionValue

        val count: String?
        val info: String
        val matter: Long
        val background: DrawScope.(ProfileFrame) -> Unit
        val onBack: () -> Unit
        var headerExtra: (@Composable RowScope.(ProfileFrame) -> Unit)? = null
        var headerExtraWidth: (ProfileFrame) -> Float = { 0f }
        val body: @Composable (ProfileFrame, WorkshopHeaderLayout) -> Unit
        when (shown.tab) {
            WorkshopTab.CHARACTERS -> {
                val model = remember(routeToken, revision) { hangarModel() }
                val state = HangarState(model, hangarInspectedValue ?: model.selected, characterSelectionAvailable)
                fun dispatch(action: HangarAction) {
                    // Events between two frames act on the latest inspection, not on this frame's copy.
                    val current = state.copy(inspected = hangarInspectedValue ?: model.selected)
                    val reduction = HangarReducer.reduce(current, action)
                    hangarInspectedValue = reduction.state.inspected
                    reduction.effects.forEach { effect ->
                        when (effect) {
                            is HangarUiEffect.Select -> {
                                // Session decides; its participant command has run once the output returns.
                                onOutput(WorkshopOutput.SelectCharacter(effect.shape))
                                val selected = profilePort.query(ProfileQuery.GetLoadout).snapshot.loadout.coreShape == effect.shape
                                revisionValue += 1
                                val name = coreShapes.firstOrNull { it.id == effect.shape }?.displayName?.localizedContent(language)
                                    ?: effect.shape.name
                                if (selected) {
                                    audioExecutor.play(ProfileAudioCue.PURCHASE)
                                    announce(language.text(ProfileScreensRedesignText.AnnounceSelected, name))
                                } else {
                                    audioExecutor.play(ProfileAudioCue.REFUSED)
                                    announce(language.text(ProfileScreensRedesignText.AnnounceSelectionInRun), assertive = true)
                                }
                            }
                            is HangarUiEffect.PlayAudio -> audioExecutor.play(effect.cue)
                            is HangarUiEffect.Emit -> when (effect.output) {
                                HangarOutput.Back -> onOutput(WorkshopOutput.Back)
                            }
                        }
                    }
                }
                count = language.text(ProfileScreensRedesignText.CountOf, model.unlockedCount, model.shapes.size) +
                    if (model.ascendant) ", " + language.text(ProfileScreensRedesignText.Ascendant) else ""
                info = language.text(ProfileScreensRedesignText.WorkshopCharactersInfo)
                matter = model.matter
                background = { frame -> drawHangarBackground(frame, hangarHolder.layout) }
                onBack = { dispatch(HangarAction.Back) }
                body = { frame, _ ->
                    HangarBody(frame, state, shown.characterSegment, textScale, hangarDetail, hangarHolder, focusSerial,
                        onSegment = ::dispatchWorkshop, onAction = ::dispatch)
                }
            }
            WorkshopTab.WEAPONS -> {
                val loadout = remember(routeToken, revision) { profilePort.query(ProfileQuery.GetLoadout).snapshot }
                // Heirloom candidates only change with a run's end, never while the Workshop is open.
                val heirlooms = remember(routeToken) { profilePort.query(ProfileQuery.GetCollection).collection.heirloomRelicIds }
                val model = armoryReducer.renderModel(loadout, activeRunWeapon, heirlooms)
                val view = armoryViewValue?.takeIf { current -> weapons.any { it.id == current.inspected } }
                    ?: ArmoryViewState(weapons.firstOrNull { it.id == model.selectedWeapon }?.id ?: weapons.first().id)
                val stepper = rememberArmoryGridStepper(armoryGrid)
                fun dispatch(action: ArmoryAction) {
                    val current = armoryViewValue ?: view
                    val reduction = armoryReducer.reduce(current, model, action)
                    armoryViewValue = reduction.state
                    var asked = false
                    reduction.effects.forEach { effect ->
                        when (effect) {
                            is ArmoryEffect.PurchaseOrEquipWeapon -> {
                                asked = true
                                val owned = effect.id in model.unlockedWeapons
                                val acceptance = profilePort.accept(ProfilePulse.PurchaseOrEquipWeapon(effect.id))
                                revisionValue += 1
                                // A refusal (a screen older than the Profile) is named beside the action, never silent.
                                val rejection = (acceptance as? ProfileAcceptance.Rejected)?.reason
                                val answered = armoryReducer.answered(reduction.state, effect.id, rejection)
                                armoryViewValue = answered
                                val notice = answered.notice
                                audioExecutor.play(when {
                                    rejection == null -> ProfileAudioCue.PURCHASE
                                    notice != null -> ProfileAudioCue.REFUSED
                                    else -> ProfileAudioCue.UI_CLICK
                                })
                                val definition = weapons.first { it.id == effect.id }
                                val name = definition.name.localizedContent(language)
                                when {
                                    rejection == null -> announce(language.text(
                                        if (owned) ProfileScreensRedesignText.AnnounceEquipped else ProfileScreensRedesignText.AnnounceUnlocked,
                                        name,
                                    ))
                                    notice != null -> announce(armoryNoticeText(notice, definition, model.totalMatter, language), assertive = true)
                                }
                            }
                            is ArmoryEffect.PlayAudio -> audioExecutor.play(effect.cue)
                            is ArmoryEffect.Emit -> when (effect.output) {
                                ArmoryOutput.Back -> onOutput(WorkshopOutput.Back)
                            }
                            is ArmoryEffect.ScrollGrid -> {
                                val layout = armoryHolder.layout ?: return@forEach
                                val target = armoryGridScrollTarget(armoryGrid.value.toFloat(), armoryGrid.maxValue.toFloat(),
                                    layout.gridViewport.height, layout.rowPitch, effect.forward)
                                stepper.step(target.toInt())
                            }
                        }
                    }
                    // A press on a blocked unlock names its reason without asking the Profile: announce it too.
                    val notice = reduction.state.notice
                    if (!asked && notice != null && notice != current.notice) {
                        weapons.firstOrNull { it.id == notice.weapon }?.let { definition ->
                            announce(armoryNoticeText(notice, definition, model.totalMatter, language), assertive = true)
                        }
                    }
                }
                count = language.text(ProfileScreensRedesignText.CountOf, weapons.count { it.id in model.unlockedWeapons }, weapons.size)
                info = language.text(ProfileScreensRedesignText.WorkshopWeaponsInfo)
                matter = model.totalMatter
                val title = WorkshopTab.WEAPONS.label(language)
                background = { frame -> drawArmoryBackground(frame, armoryHolder.layout, measurer, title) }
                onBack = { dispatch(ArmoryAction.Back) }
                val pager: @Composable RowScope.(ProfileFrame) -> Unit = { frame ->
                    ArmoryPager(frame, armoryGrid, armoryHolder, scale, ::dispatch)
                }
                headerExtra = pager
                headerExtraWidth = { frame -> armoryPagerWidth(frame, armoryGrid, scale) }
                body = { frame, header ->
                    ArmoryBody(frame, model, weapons, weaponMasteries, view, shown.weaponSegment, textScale, armoryGrid, armoryHolder,
                        coreShapes, relics, focusSerial = focusSerial,
                        gridExtra = if (header.extraInBody) ({ pager(frame) }) else null,
                        onSegment = ::dispatchWorkshop, onAction = ::dispatch)
                }
            }
            WorkshopTab.MATRIX -> {
                val model = remember(routeToken, revision) { matrixModel() }
                val state = (matrixValue ?: MatrixState(model)).copy(model = model)
                fun dispatch(action: MatrixAction) {
                    val current = (matrixValue ?: state).copy(model = model)
                    val reduction = MatrixReducer.reduce(current, action)
                    matrixValue = reduction.state
                    reduction.effects.forEach { effect ->
                        when (effect) {
                            is MatrixUiEffect.Purchase -> {
                                val accepted = profilePort.accept(ProfilePulse.PurchaseMatrixNode(effect.id)) is ProfileAcceptance.Accepted
                                revisionValue += 1
                                val next = MatrixReducer.purchased(reduction.state, effect.id, matrixModel(), accepted)
                                matrixValue = next
                                if (accepted) {
                                    audioExecutor.play(ProfileAudioCue.PURCHASE)
                                    next.model.node(effect.id)?.let { node ->
                                        announce(language.text(ProfileScreensRedesignText.AnnounceRank, node.name.localizedContent(language), node.rank))
                                    }
                                }
                            }
                            MatrixUiEffect.Reset -> {
                                val refund = reduction.state.model.refund
                                val accepted = profilePort.accept(ProfilePulse.ResetMatrix) is ProfileAcceptance.Accepted
                                revisionValue += 1
                                matrixValue = MatrixReducer.reset(reduction.state, matrixModel())
                                audioExecutor.play(if (accepted) ProfileAudioCue.PURCHASE else ProfileAudioCue.UI_CLICK)
                                if (accepted) announce(language.text(ProfileScreensRedesignText.AnnounceReset, formatMatter(refund, language)))
                            }
                            is MatrixUiEffect.PlayAudio -> audioExecutor.play(effect.cue)
                            is MatrixUiEffect.Emit -> when (effect.output) {
                                MatrixOutput.Back -> onOutput(WorkshopOutput.Back)
                            }
                        }
                    }
                }
                val (owned, total) = model.nodeTotals()
                count = language.text(ProfileScreensRedesignText.CountOf, owned, total)
                info = language.text(ProfileScreensRedesignText.WorkshopMatrixInfo)
                matter = model.matter
                background = { frame -> drawMatrixBackground(frame, matrixHolder.layout) }
                onBack = { dispatch(MatrixAction.Back) }
                val reset: @Composable RowScope.(ProfileFrame) -> Unit = { frame ->
                    MatrixResetButton(frame, state, measurer, scale, language, ::dispatch)
                }
                headerExtra = reset
                headerExtraWidth = { frame -> matrixResetWidth(measurer, frame, language) }
                body = { frame, header ->
                    MatrixBody(frame, state, textScale, matrixGrid, matrixHolder, focusSerial,
                        gridExtra = if (header.extraInBody) ({ reset(frame) }) else null, onAction = ::dispatch)
                }
            }
        }

        WorkshopPanel(
            tab = shown.tab,
            count = count,
            info = info,
            matter = matter,
            scale = scale,
            onBack = onBack,
            onAction = ::dispatchWorkshop,
            background = background,
            contentKey = routeToken,
            headerExtra = headerExtra,
            headerExtraWidth = headerExtraWidth,
            announcement = politeValue?.text,
            alert = alertValue?.text,
        ) { frame, header -> body(frame, header) }
    }

    private fun hangarModel(): HangarRenderModel {
        val loadout = profilePort.query(ProfileQuery.GetLoadout).snapshot
        // Without a weapon catalog there is no signature row to fill (and no heirloom to read).
        val signatureWeapons = if (weapons.isEmpty()) emptyList() else hangarWeapons(weapons, loadout,
            profilePort.query(ProfileQuery.GetCollection).collection.heirloomRelicIds, relics)
        return profilePort.query(ProfileQuery.GetHangar).toRenderModel(coreShapes, loadout.economy.matter, signatureWeapons)
    }

    private fun matrixModel() = profilePort.query(ProfileQuery.GetMatrixProgress).snapshot.toRenderModel(matrixNodes)

    /** "Weapons, 7 of 24": the tab and its owned count, read when the tab is announced. */
    private fun tabAnnouncement(tab: WorkshopTab, language: AppLanguage): String {
        val (owned, total) = when (tab) {
            WorkshopTab.CHARACTERS -> hangarModel().let { it.unlockedCount to it.shapes.size }
            WorkshopTab.WEAPONS -> profilePort.query(ProfileQuery.GetLoadout).snapshot.loadout.unlockedWeapons
                .let { owned -> weapons.count { it.id in owned } to weapons.size }
            WorkshopTab.MATRIX -> matrixModel().nodeTotals()
        }
        return language.text(ProfileScreensRedesignText.AnnounceTab, tab.label(language), owned, total)
    }
}

/** One live-region announcement; [sequence] lets the same words announce again. */
internal data class WorkshopNotice(val text: String, val sequence: Int)

/** Announcements clear after this much presentation time. */
internal const val WORKSHOP_ANNOUNCE_MS = 4_000L
