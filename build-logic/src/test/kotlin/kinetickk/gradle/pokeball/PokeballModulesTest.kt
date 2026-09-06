// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PokeballModulesTest {
    @Test
    fun aModuleCannotBeRegisteredTwiceOrOwnedByTwoAuthorities() {
        listOf(
            mapOf("Profile" to listOf(":ball:profile:api", ":ball:profile:api")),
            mapOf(
                "Profile" to listOf(":ball:profile:api"),
                "GameplayRun" to listOf(":ball:profile:api"),
            ),
        ).forEach { modules ->
            val failure = assertFailsWith<IllegalArgumentException> { projectAuthorities(modules) }
            assertTrue(":ball:profile:api" in failure.message.orEmpty())
        }
    }

    @Test
    fun invalidOrUnownedModuleDeclarationsFailBeforeProjection() {
        listOf(
            mapOf("Profile" to emptyList<String>()),
            mapOf("" to listOf(":ball:profile:api")),
            mapOf("Profile" to listOf("ball:profile:api")),
            mapOf("Profile" to listOf(":ball:profile:api\n")),
        ).forEach { modules ->
            assertFailsWith<IllegalArgumentException> { projectAuthorities(modules) }
        }
    }

    @Test
    fun anUnknownRoleDoesNotInheritAuthorityFromItsDirectoryPrefix() {
        assertEquals("Profile", authorityFor(":ball:profile:nucleus"))
        assertFailsWith<IllegalStateException> { authorityFor(":ball:profile:undeclared") }
    }

    @Test
    fun everyInternalRoleRemainsProtectedWhilePublicAndAssemblyModulesStayOutside() {
        assertEquals(
            setOf(
                ":ball:content:impl",
                ":ball:gameplay:nucleus", ":ball:gameplay:interaction", ":ball:gameplay:impl",
                ":ball:profile:nucleus", ":ball:profile:resource", ":ball:profile:interaction", ":ball:profile:impl",
                ":flow:session:nucleus", ":flow:session:interaction", ":flow:session:impl",
                ":resource:audio:impl",
            ),
            internalProjectPackages.keys,
        )
        assertEquals("kinetickk.ball.profile.resource", internalProjectPackages[":ball:profile:resource"])
    }
}
