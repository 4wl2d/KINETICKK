// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.interaction.rewards

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kinetickk.ball.content.api.ItemRarity
import kinetickk.ball.gameplay.interaction.layout.choiceLayoutGeometry
import kinetickk.ball.gameplay.nucleus.render.ChoiceOption
import kinetickk.ball.gameplay.nucleus.render.ChoiceType
import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.LocalAppLanguage
import kotlin.math.roundToInt

/**
 * Initializes reward UI before the run's first frame. The measured slot is never placed or drawn,
 * has no gameplay callbacks, and is disposed before [onReady]. No loot is rolled or accepted.
 */
@Composable
internal fun RewardUiPreparation(engine: GameplayRenderModel, onReady: () -> Unit) {
    val slots = remember { SubcomposeLayoutState() }
    val language = LocalAppLanguage.current
    val density = LocalDensity.current.density
    var placed by remember { mutableStateOf(false) }
    SubcomposeLayout(
        state = slots,
        modifier = Modifier.requiredSize(0.dp).clearAndSetSemantics { }.onPlaced { placed = true },
    ) { layout(0, 0) {} }
    LaunchedEffect(slots, placed) {
        if (!placed) return@LaunchedEffect
        // premeasure needs the owner's completed first layout, not the onPlaced callback itself.
        withFrameNanos { }
        val epics = engine.content.items.filter { it.rarity == ItemRarity.EPIC }.take(4)
        val items = epics.ifEmpty { engine.content.items.take(4) }
        if (items.isNotEmpty()) {
            val cards = items.mapIndexed { index, item ->
                engine.rewardCardPresentation(
                    ChoiceOption(ChoiceType.ITEM, item.name, item.description, item.rarity.displayLabel, itemId = item.id),
                    index, language,
                )
            }
            val width = (1440f * density).roundToInt()
            val height = (810f * density).roundToInt()
            val geometry = choiceLayoutGeometry(width.toFloat(), height.toFloat(), density, cards.size, false)
            val presentation = RewardPresentation("", "", cards, Kk.Bone, Kk.Bone, 0, level = engine.level)
            val handle = slots.precompose(Unit) {
                RewardContent(presentation, geometry, width.toFloat(), density, engine.settings.textScale,
                    0f, enabled = true, onSelect = {}, onReroll = {})
            }
            try {
                check(handle.placeablesCount > 0) { "Reward preparation host must be attached" }
                repeat(handle.placeablesCount) { index ->
                    handle.premeasure(index, Constraints.fixed(width, height))
                    check(handle.getSize(index).width == width && handle.getSize(index).height == height) {
                        "Reward preparation must complete before gameplay frames start"
                    }
                }
            } finally {
                handle.dispose()
            }
        }
        onReady()
    }
}
