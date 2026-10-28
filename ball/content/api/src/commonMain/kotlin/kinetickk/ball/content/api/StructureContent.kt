// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/** Structure templates (append-only: a placement hashes the template ordinal). */
enum class StructureTemplateId { DERELICT_STATION, VAULT, SPIRE, LABYRINTH, FOUNDRY, RELIQUARY, ARCHITECTS_DOODLE }

/** Structures and dungeons; disabled with no templates until the structures lane ships them. */
data class StructurePolicy(val enabled: Boolean = false)

/** Checks the structure policy against the roster and the obstacle field at bootstrap (nothing to check while disabled). */
@Suppress("UNUSED_PARAMETER")
fun validateStructurePolicy(policy: StructurePolicy, enemies: List<EnemyDefinition>, obstacles: ObstacleTuning) = Unit
