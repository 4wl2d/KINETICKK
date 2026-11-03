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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kinetickk.ball.content.api.CoreShape
import kinetickk.ball.content.api.CoreShapeDefinition
import kinetickk.ball.content.api.MatrixNodeDefinition
import kinetickk.ball.content.api.RelicDefinition
import kinetickk.ball.content.api.UpgradePolicy
import kinetickk.ball.content.api.WeaponDefinition
import kinetickk.ball.content.api.WeaponId
import kinetickk.ball.content.api.WeaponMastery
import kinetickk.ball.content.api.localizedContent
import kinetickk.ball.profile.api.ProfileAcceptance
import kinetickk.ball.profile.api.ProfilePort
import kinetickk.ball.profile.api.ProfilePulse
import kinetickk.ball.profile.api.ProfileQuery
import kinetickk.ball.profile.interaction.ProfileFrame
import kinetickk.ball.profile.interaction.audio.ProfileAudioCue
import kinetickk.ball.profile.interaction.audio.ProfileAudioExecutor
import kinetickk.ball.profile.interaction.formatMatter
import kinetickk.ball.profile.interaction.localization.ProfileScreensRedesignText
import kinetickk.ball.profile.interaction.profileTextScale
import kinetickk.ball.profile.interaction.workshop.api.WorkshopFeature
import kinetickk.ball.profile.interaction.workshop.api.WorkshopOutput
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryAction
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryBody
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryEffect
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryGridStepper
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryLayoutHolder
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryOutput
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryPager
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryReducer
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryRenderModel
import kinetickk.ball.profile.interaction.workshop.armory.ArmoryViewState
import kinetickk.ball.profile.interaction.workshop.armory.armoryGridScrollTarget
import kinetickk.ball.profile.interaction.workshop.armory.armoryNoticeText
import kinetickk.ball.profile.interaction.workshop.armory.armoryPagerWidth
import kinetickk.ball.profile.interaction.workshop.armory.drawArmoryBackground
import kinetickk.ball.profile.interaction.workshop.armory.rememberArmoryGridStepper
import kinetickk.ball.profile.interaction.workshop.hangar.HangarAction
import kinetickk.ball.profile.interaction.workshop.hangar.HangarBody
import kinetickk.ball.profile.interaction.workshop.hangar.HangarLayoutHolder
import kinetickk.ball.profile.interaction.workshop.hangar.HangarOutput
import kinetickk.ball.profile.interaction.workshop.hangar.HangarReducer
import kinetickk.ball.profile.interaction.workshop.hangar.HangarRenderModel
import kinetickk.ball.profile.interaction.workshop.hangar.HangarState
import kinetickk.ball.profile.interaction.workshop.hangar.HangarUiEffect
import kinetickk.ball.profile.interaction.workshop.hangar.drawHangarBackground
import kinetickk.ball.profile.interaction.workshop.hangar.hangarWeapons
import kinetickk.ball.profile.interaction.workshop.hangar.toRenderModel
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixAction
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixBody
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixLayoutHolder
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixOutput
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixReducer
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixRenderModel
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixResetButton
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixState
import kinetickk.ball.profile.interaction.workshop.matrix.MatrixUiEffect
import kinetickk.ball.profile.interaction.workshop.matrix.drawMatrixBackground
import kinetickk.ball.profile.interaction.workshop.matrix.matrixResetWidth
import kinetickk.ball.profile.interaction.workshop.matrix.nodeTotals
import kinetickk.ball.profile.interaction.workshop.matrix.toRenderModel
import kinetickk.foundation.collections.ImmutableList
import kinetickk.foundation.collections.immutableListOf
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.common.localization.text
import kinetickk.foundation.design.CanvasTextMeasurer
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.rememberKkCanvasMeasurer
import kinetickk.resource.audio.api.AudioService
import kotlinx.coroutines.delay

/**
 * The Workshop: the Characters (Hangar), Weapons (Armory) and Core Matrix bodies behind one header
 * with tabs. Weapon unlocks, Matrix ranks, resets and Refit / Honing levels write their typed
 * [ProfilePulse]s (prices from [upgrades], the content rule the Profile charges); character selection
 * is a [WorkshopOutput.SelectCharacter] for Session, the authority on "between runs".
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
    private val upgrades: UpgradePolicy = UpgradePolicy(),
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
        val opening = remember(routeToken) { WorkshopOpening(WorkshopState(lastTab, lastSerial)) }
        // A new shell request shows its tab in this very frame; its effects (announcement, focus) follow.
        val shown = tabRequest?.let { WorkshopReducer.reduce(opening.workshop, WorkshopAction.Request(it)).state } ?: opening.workshop
        LaunchedEffect(opening.polite) { if (opening.polite != null) { delay(WORKSHOP_ANNOUNCE_MS); opening.polite = null } }
        LaunchedEffect(opening.alert) { if (opening.alert != null) { delay(WORKSHOP_ANNOUNCE_MS); opening.alert = null } }
        val input = WorkshopInput(opening, language, onOutput)
        LaunchedEffect(tabRequest) { tabRequest?.let { input.dispatchWorkshop(WorkshopAction.Request(it)) } }

        val hangarHolder = remember { HangarLayoutHolder() }
        val armoryHolder = remember { ArmoryLayoutHolder() }
        val matrixHolder = remember { MatrixLayoutHolder() }
        val focusSerial = opening.focusSerial
        val revision = opening.revision
        val face = when (shown.tab) {
            WorkshopTab.CHARACTERS -> {
                val model = remember(routeToken, revision) { hangarModel() }
                input.hangarFace(model, shown, characterSelectionAvailable, textScale, focusSerial, hangarHolder)
            }
            WorkshopTab.WEAPONS -> {
                val loadout = remember(routeToken, revision) { profilePort.query(ProfileQuery.GetLoadout).snapshot }
                // Heirloom candidates only change with a run's end, never while the Workshop is open.
                val heirlooms = remember(routeToken) { profilePort.query(ProfileQuery.GetCollection).collection.heirloomRelicIds }
                val model = armoryReducer.renderModel(loadout, activeRunWeapon, heirlooms)
                val stepper = rememberArmoryGridStepper(opening.armoryGrid)
                input.armoryFace(model, shown, textScale, scale, focusSerial, measurer, armoryHolder, stepper)
            }
            WorkshopTab.MATRIX -> {
                val model = remember(routeToken, revision) { matrixModel() }
                input.matrixFace(model, textScale, scale, focusSerial, measurer, matrixHolder)
            }
        }

        WorkshopPanel(
            tab = shown.tab,
            count = face.count,
            info = face.info,
            matter = face.matter,
            scale = scale,
            onBack = face.onBack,
            onAction = input::dispatchWorkshop,
            background = face.background,
            contentKey = routeToken,
            headerExtra = face.headerExtra,
            headerExtraWidth = face.headerExtraWidth,
            announcement = opening.polite?.text,
            alert = opening.alert?.text,
            onPanelKey = face.onPanelKey,
        ) { frame, header -> face.body(frame, header) }
    }

    private fun hangarModel(): HangarRenderModel {
        val loadout = profilePort.query(ProfileQuery.GetLoadout).snapshot
        // Without a weapon catalog there is no signature row to fill (and no heirloom to read).
        val signatureWeapons = if (weapons.isEmpty()) emptyList() else hangarWeapons(weapons, loadout,
            profilePort.query(ProfileQuery.GetCollection).collection.heirloomRelicIds, relics)
        return profilePort.query(ProfileQuery.GetHangar).toRenderModel(coreShapes, loadout.economy.matter, signatureWeapons,
            loadout.upgrades)
    }

    /** The track of [target] as the Profile holds it now (level, ownership and balance from the loadout). */
    private fun upgradeTrack(target: UpgradeTarget): UpgradeTrackRenderModel {
        val loadout = profilePort.query(ProfileQuery.GetLoadout).snapshot
        val matter = loadout.economy.matter
        return when (target) {
            is UpgradeTarget.Character -> {
                val definition = coreShapes.first { it.id == target.shape }
                val unlocked = profilePort.query(ProfileQuery.GetHangar).shapes.firstOrNull { it.shape == target.shape }?.unlocked
                    ?: (definition.unlockTarget == 0)
                characterUpgradeTrack(upgrades, definition, loadout.upgrades.characterLevel(target.shape), matter, unlocked)
            }
            is UpgradeTarget.Weapon -> weaponUpgradeTrack(upgrades, weapons.first { it.id == target.id },
                loadout.upgrades.weaponLevel(target.id), matter, target.id in loadout.loadout.unlockedWeapons)
        }
    }

    private fun upgradeName(target: UpgradeTarget, language: AppLanguage): String = when (target) {
        is UpgradeTarget.Character -> coreShapes.firstOrNull { it.id == target.shape }?.displayName?.localizedContent(language)
            ?: target.shape.name
        is UpgradeTarget.Weapon -> weapons.firstOrNull { it.id == target.id }?.name?.localizedContent(language) ?: target.id.name
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
    /**
     * The Workshop's handlers for one composition: they read and write [opening], announce in this
     * composition's [language] and send outputs to its [onOutput]. They run outside composition
     * (presses, keys and effects); the tab faces they build are read during composition.
     */
    private inner class WorkshopInput(
        val opening: WorkshopOpening,
        val language: AppLanguage,
        val onOutput: (WorkshopOutput) -> Unit,
    ) {
        // The Profile decides a Refit or Honing purchase; the answer is announced and the tabs re-read.
        fun purchaseUpgrade(target: UpgradeTarget) {
            val acceptance = profilePort.accept(target.pulse())
            opening.revision += 1
            val name = upgradeName(target, language)
            when (acceptance) {
                is ProfileAcceptance.Accepted -> {
                    audioExecutor.play(ProfileAudioCue.PURCHASE)
                    val after = upgradeTrack(target)
                    val matter = profilePort.query(ProfileQuery.GetLoadout).snapshot.economy.matter
                    opening.announce(language.text(ProfileScreensRedesignText.AnnounceUpgrade, name, after.level, formatMatter(matter, language)))
                }
                is ProfileAcceptance.Rejected -> {
                    audioExecutor.play(ProfileAudioCue.REFUSED)
                    opening.refusalSerial += 1
                    opening.announce(upgradeRefusalText(acceptance.reason, upgradeTrack(target), language), assertive = true)
                }
            }
        }

        fun dispatchWorkshop(action: WorkshopAction) {
            val reduction = WorkshopReducer.reduce(opening.workshop, action)
            opening.workshop = reduction.state
            lastTab = reduction.state.tab
            lastSerial = reduction.state.appliedSerial
            reduction.effects.forEach { effect ->
                when (effect) {
                    is WorkshopEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is WorkshopEffect.AnnounceTab -> opening.announce(tabAnnouncement(effect.tab, language))
                    WorkshopEffect.FocusBody -> opening.focusSerial += 1
                    is WorkshopEffect.PurchaseUpgrade -> purchaseUpgrade(effect.target)
                }
            }
        }

        /** The Characters tab: the Hangar of [model] and its own presses. */
        fun hangarFace(
            model: HangarRenderModel,
            shown: WorkshopState,
            characterSelectionAvailable: Boolean,
            textScale: Float,
            focusSerial: Int,
            holder: HangarLayoutHolder,
        ): WorkshopTabFace {
            val state = HangarState(model, opening.hangarInspected ?: model.selected, characterSelectionAvailable)
            fun dispatch(action: HangarAction) = dispatchHangar(state, model, action)
            return WorkshopTabFace(
                count = language.text(ProfileScreensRedesignText.CountOf, model.unlockedCount, model.shapes.size) +
                    if (model.ascendant) ", " + language.text(ProfileScreensRedesignText.Ascendant) else "",
                info = language.text(ProfileScreensRedesignText.WorkshopCharactersInfo),
                matter = model.matter,
                background = { frame -> drawHangarBackground(frame, holder.layout) },
                onBack = { dispatch(HangarAction.Back) },
                // Enter selects the inspected character even while focus rests on the panel (after opening);
                // an unconsumed Enter would reach Session and close the Workshop.
                onPanelKey = { event ->
                    (event.key == Key.Enter || event.key == Key.NumPadEnter).also { enter ->
                        if (enter && event.type == KeyEventType.KeyDown) dispatch(HangarAction.SelectInspected)
                    }
                },
                body = { frame, _ ->
                    HangarBody(frame, state, shown.characterSegment, textScale, opening.hangarDetail, holder, focusSerial,
                        upgrades = upgrades, refusalSerial = opening.refusalSerial, onWorkshop = ::dispatchWorkshop, onAction = ::dispatch)
                },
            )
        }

        fun dispatchHangar(state: HangarState, model: HangarRenderModel, action: HangarAction) {
            // Events between two frames act on the latest inspection, not on this frame's copy.
            val current = state.copy(inspected = opening.hangarInspected ?: model.selected)
            val reduction = HangarReducer.reduce(current, action)
            opening.hangarInspected = reduction.state.inspected
            reduction.effects.forEach { effect ->
                when (effect) {
                    is HangarUiEffect.Select -> selectCharacter(effect.shape)
                    is HangarUiEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is HangarUiEffect.Emit -> when (effect.output) {
                        HangarOutput.Back -> onOutput(WorkshopOutput.Back)
                    }
                }
            }
        }

        private fun selectCharacter(shape: CoreShape) {
            // Session decides; its participant command has run once the output returns.
            onOutput(WorkshopOutput.SelectCharacter(shape))
            val selected = profilePort.query(ProfileQuery.GetLoadout).snapshot.loadout.coreShape == shape
            opening.revision += 1
            val name = coreShapes.firstOrNull { it.id == shape }?.displayName?.localizedContent(language)
                ?: shape.name
            if (selected) {
                audioExecutor.play(ProfileAudioCue.PURCHASE)
                opening.announce(language.text(ProfileScreensRedesignText.AnnounceSelected, name))
            } else {
                audioExecutor.play(ProfileAudioCue.REFUSED)
                opening.announce(language.text(ProfileScreensRedesignText.AnnounceSelectionInRun), assertive = true)
            }
        }

        /** The Weapons tab: the Armory of [model], its pager and its own presses. */
        fun armoryFace(
            model: ArmoryRenderModel,
            shown: WorkshopState,
            textScale: Float,
            scale: Float,
            focusSerial: Int,
            measurer: CanvasTextMeasurer,
            holder: ArmoryLayoutHolder,
            stepper: ArmoryGridStepper,
        ): WorkshopTabFace {
            val view = opening.armoryView?.takeIf { current -> weapons.any { it.id == current.inspected } }
                ?: ArmoryViewState(weapons.firstOrNull { it.id == model.selectedWeapon }?.id ?: weapons.first().id)
            fun dispatch(action: ArmoryAction) = dispatchArmory(view, model, holder, stepper, action)
            val title = WorkshopTab.WEAPONS.label(language)
            val pager: @Composable RowScope.(ProfileFrame) -> Unit = { frame ->
                ArmoryPager(frame, opening.armoryGrid, holder, scale, ::dispatch)
            }
            return WorkshopTabFace(
                count = language.text(ProfileScreensRedesignText.CountOf, weapons.count { it.id in model.unlockedWeapons }, weapons.size),
                info = language.text(ProfileScreensRedesignText.WorkshopWeaponsInfo),
                matter = model.totalMatter,
                background = { frame -> drawArmoryBackground(frame, holder.layout, measurer, title) },
                onBack = { dispatch(ArmoryAction.Back) },
                headerExtra = pager,
                headerExtraWidth = { frame -> armoryPagerWidth(frame, opening.armoryGrid, scale) },
                body = { frame, header ->
                    ArmoryBody(frame, model, weapons, weaponMasteries, view, shown.weaponSegment, textScale, opening.armoryGrid, holder,
                        coreShapes, relics, focusSerial = focusSerial,
                        gridExtra = if (header.extraInBody) ({ pager(frame) }) else null,
                        upgrades = upgrades, refusalSerial = opening.refusalSerial, onWorkshop = ::dispatchWorkshop, onAction = ::dispatch)
                },
            )
        }

        fun dispatchArmory(
            view: ArmoryViewState,
            model: ArmoryRenderModel,
            holder: ArmoryLayoutHolder,
            stepper: ArmoryGridStepper,
            action: ArmoryAction,
        ) {
            val current = opening.armoryView ?: view
            val reduction = armoryReducer.reduce(current, model, action)
            opening.armoryView = reduction.state
            var asked = false
            reduction.effects.forEach { effect ->
                when (effect) {
                    is ArmoryEffect.PurchaseOrEquipWeapon -> {
                        asked = true
                        purchaseOrEquipWeapon(reduction.state, model, effect.id)
                    }
                    is ArmoryEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is ArmoryEffect.Emit -> when (effect.output) {
                        ArmoryOutput.Back -> onOutput(WorkshopOutput.Back)
                    }
                    is ArmoryEffect.ScrollGrid -> {
                        val layout = holder.layout ?: return@forEach
                        val armoryGrid = opening.armoryGrid
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
                    opening.announce(armoryNoticeText(notice, definition, model.totalMatter, language), assertive = true)
                }
            }
        }

        private fun purchaseOrEquipWeapon(reduced: ArmoryViewState, model: ArmoryRenderModel, id: WeaponId) {
            val owned = id in model.unlockedWeapons
            val acceptance = profilePort.accept(ProfilePulse.PurchaseOrEquipWeapon(id))
            opening.revision += 1
            // A refusal (a screen older than the Profile) is named beside the action, never silent.
            val rejection = (acceptance as? ProfileAcceptance.Rejected)?.reason
            val answered = armoryReducer.answered(reduced, id, rejection)
            opening.armoryView = answered
            val notice = answered.notice
            audioExecutor.play(when {
                rejection == null -> ProfileAudioCue.PURCHASE
                notice != null -> ProfileAudioCue.REFUSED
                else -> ProfileAudioCue.UI_CLICK
            })
            val definition = weapons.first { it.id == id }
            val name = definition.name.localizedContent(language)
            when {
                rejection == null -> opening.announce(language.text(
                    if (owned) ProfileScreensRedesignText.AnnounceEquipped else ProfileScreensRedesignText.AnnounceUnlocked,
                    name,
                ))
                notice != null -> opening.announce(armoryNoticeText(notice, definition, model.totalMatter, language), assertive = true)
            }
        }

        /** The Core Matrix tab: the Matrix of [model], its reset and its own presses. */
        fun matrixFace(
            model: MatrixRenderModel,
            textScale: Float,
            scale: Float,
            focusSerial: Int,
            measurer: CanvasTextMeasurer,
            holder: MatrixLayoutHolder,
        ): WorkshopTabFace {
            val state = (opening.matrix ?: MatrixState(model)).copy(model = model)
            fun dispatch(action: MatrixAction) = dispatchMatrix(state, model, action)
            val reset: @Composable RowScope.(ProfileFrame) -> Unit = { frame ->
                MatrixResetButton(frame, state, measurer, scale, language, ::dispatch)
            }
            val (owned, total) = model.nodeTotals()
            return WorkshopTabFace(
                count = language.text(ProfileScreensRedesignText.CountOf, owned, total),
                info = language.text(ProfileScreensRedesignText.WorkshopMatrixInfo),
                matter = model.matter,
                background = { frame -> drawMatrixBackground(frame, holder.layout) },
                onBack = { dispatch(MatrixAction.Back) },
                headerExtra = reset,
                headerExtraWidth = { frame -> matrixResetWidth(measurer, frame, language) },
                body = { frame, header ->
                    MatrixBody(frame, state, textScale, opening.matrixGrid, holder, focusSerial,
                        gridExtra = if (header.extraInBody) ({ reset(frame) }) else null, onAction = ::dispatch)
                },
            )
        }

        fun dispatchMatrix(state: MatrixState, model: MatrixRenderModel, action: MatrixAction) {
            val current = (opening.matrix ?: state).copy(model = model)
            val reduction = MatrixReducer.reduce(current, action)
            opening.matrix = reduction.state
            reduction.effects.forEach { effect ->
                when (effect) {
                    is MatrixUiEffect.Purchase -> {
                        val accepted = profilePort.accept(ProfilePulse.PurchaseMatrixNode(effect.id)) is ProfileAcceptance.Accepted
                        opening.revision += 1
                        val next = MatrixReducer.purchased(reduction.state, effect.id, matrixModel(), accepted)
                        opening.matrix = next
                        if (accepted) {
                            audioExecutor.play(ProfileAudioCue.PURCHASE)
                            next.model.node(effect.id)?.let { node ->
                                opening.announce(language.text(ProfileScreensRedesignText.AnnounceRank, node.name.localizedContent(language), node.rank))
                            }
                        }
                    }
                    MatrixUiEffect.Reset -> {
                        val refund = reduction.state.model.refund
                        val accepted = profilePort.accept(ProfilePulse.ResetMatrix) is ProfileAcceptance.Accepted
                        opening.revision += 1
                        opening.matrix = MatrixReducer.reset(reduction.state, matrixModel())
                        audioExecutor.play(if (accepted) ProfileAudioCue.PURCHASE else ProfileAudioCue.UI_CLICK)
                        if (accepted) opening.announce(language.text(ProfileScreensRedesignText.AnnounceReset, formatMatter(refund, language)))
                    }
                    is MatrixUiEffect.PlayAudio -> audioExecutor.play(effect.cue)
                    is MatrixUiEffect.Emit -> when (effect.output) {
                        MatrixOutput.Back -> onOutput(WorkshopOutput.Back)
                    }
                }
            }
        }
    }

    /**
     * One opening of the Workshop ([WorkshopFeature.Content]'s `routeToken`): the shown tab, the
     * live-region notices, the serials that move focus and flash a refusal, and the per-tab memory
     * (inspected tiles, segments and scrolls) kept while another tab is shown.
     */
    private class WorkshopOpening(state: WorkshopState) {
        var workshop by mutableStateOf(state)
        var focusSerial by mutableIntStateOf(0)
        // Bumped after every Profile answer: each tab re-reads its query.
        var revision by mutableIntStateOf(0)
        var polite by mutableStateOf<WorkshopNotice?>(null)
        var alert by mutableStateOf<WorkshopNotice?>(null)
        var noticeSequence by mutableIntStateOf(0)
        // Bumped after each refused upgrade purchase: the buy action's face flashes once.
        var refusalSerial by mutableIntStateOf(0)
        var hangarInspected by mutableStateOf<CoreShape?>(null)
        var armoryView by mutableStateOf<ArmoryViewState?>(null)
        var matrix by mutableStateOf<MatrixState?>(null)
        val hangarDetail = ScrollState(0)
        val armoryGrid = ScrollState(0)
        val matrixGrid = ScrollState(0)

        fun announce(text: String, assertive: Boolean = false) {
            noticeSequence += 1
            val notice = WorkshopNotice(text, noticeSequence)
            if (assertive) alert = notice else polite = notice
        }
    }

    /** What the shown tab puts in the panel: header texts, background, Back, extras and body. */
    private class WorkshopTabFace(
        val count: String?,
        val info: String,
        val matter: Long,
        val background: DrawScope.(ProfileFrame) -> Unit,
        val onBack: () -> Unit,
        val onPanelKey: (KeyEvent) -> Boolean = { false },
        val headerExtra: (@Composable RowScope.(ProfileFrame) -> Unit)? = null,
        val headerExtraWidth: (ProfileFrame) -> Float = { 0f },
        val body: @Composable (ProfileFrame, WorkshopHeaderLayout) -> Unit,
    )
}

/** One live-region announcement; [sequence] lets the same words announce again. */
internal data class WorkshopNotice(val text: String, val sequence: Int)

/** Announcements clear after this much presentation time. */
internal const val WORKSHOP_ANNOUNCE_MS = 4_000L
