// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.codex.impl

import kinetickk.foundation.collections.ImmutableList
import kinetickk.ball.profile.api.CollectionProjection
import kinetickk.ball.profile.api.LOCAL_PROFILE_INSTANCE_ID
import kinetickk.ball.profile.api.PlayerCollection
import kinetickk.ball.profile.api.ProfileRevision
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.testItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodexReducerTest {
    @Test
    fun modelCombinesProfileDiscoveryWithShellRunStacks() {
        val reducer = CodexReducer(testItems())
        val stacks = MutableList(400) { 0 }.also { it[2] = 4 }

        val model = reducer.renderModel(
            collectionProjection(PlayerCollection(setOf(2, 399))),
            CodexRunStacks(ImmutableList.copyOf(stacks)),
        )

        assertTrue(model.isDiscovered(2))
        assertEquals(4, model.itemStack(2))
        assertEquals(0, model.itemStack(399))
    }

    @Test
    fun pageReducerAndFooterPointerAreLocal() {
        val reducer = CodexReducer(testItems())
        assertEquals(1, reducer.reduce(0, CodexAction.NextPage).page)
        assertEquals(reducer.maxPage, reducer.reduce(Int.MAX_VALUE, CodexAction.NextPage).page)
        assertTrue(reducer.reduce(3, CodexAction.Back).close)

        val viewport = CodexViewport(1_280f, 720f, 1f)
        assertIs<CodexAction.Back>(resolveCodexPress(viewport, 250f, 690f))
        assertIs<CodexAction.PreviousPage>(resolveCodexPress(viewport, 700f, 690f))
        assertIs<CodexAction.NextPage>(resolveCodexPress(viewport, 1_050f, 690f))
    }
}

private fun collectionProjection(collection: PlayerCollection): CollectionProjection =
    CollectionProjection(
        instanceId = LOCAL_PROFILE_INSTANCE_ID,
        revision = ProfileRevision.ZERO,
        collection = collection,
    )
