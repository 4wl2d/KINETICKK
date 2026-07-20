// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.feature.codex.impl

import kinetickk.core.collections.ImmutableList
import kinetickk.core.profile.api.CollectionCapability
import kinetickk.core.profile.api.PlayerCollection
import kinetickk.feature.codex.api.CodexRunStacks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodexReducerTest {
    @Test
    fun modelCombinesProfileDiscoveryWithShellRunStacks() {
        val reducer = CodexReducer(FakeCollection(PlayerCollection(setOf(2, 399))))
        val stacks = MutableList(400) { 0 }.also { it[2] = 4 }

        val model = reducer.renderModel(CodexRunStacks(ImmutableList.copyOf(stacks)))

        assertTrue(model.isDiscovered(2))
        assertEquals(4, model.itemStack(2))
        assertEquals(0, model.itemStack(399))
    }

    @Test
    fun pageReducerAndFooterPointerAreLocal() {
        val reducer = CodexReducer(FakeCollection(PlayerCollection()))
        assertEquals(1, reducer.reduce(0, CodexAction.NextPage).page)
        assertEquals(reducer.maxPage, reducer.reduce(Int.MAX_VALUE, CodexAction.NextPage).page)
        assertTrue(reducer.reduce(3, CodexAction.Back).close)

        val viewport = CodexViewport(1_280f, 720f, 1f)
        assertIs<CodexAction.Back>(resolveCodexPress(viewport, 250f, 690f))
        assertIs<CodexAction.PreviousPage>(resolveCodexPress(viewport, 700f, 690f))
        assertIs<CodexAction.NextPage>(resolveCodexPress(viewport, 1_050f, 690f))
    }
}

private class FakeCollection(private val collection: PlayerCollection) : CollectionCapability {
    override fun collectionSnapshot(): PlayerCollection = collection
}
