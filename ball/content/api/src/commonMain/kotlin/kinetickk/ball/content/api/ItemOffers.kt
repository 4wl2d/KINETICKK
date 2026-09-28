// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/**
 * Catalog level that a profile's [lifetimeMatter] unlocks for ordinary item offers: one level per
 * 40 Matter, up to 80. A run offers an item whose unlock level is at most the higher of this level
 * and the run level.
 */
fun lifetimeMatterOfferLevel(lifetimeMatter: Long): Int =
    (1L + lifetimeMatter / 40L).coerceAtMost(80L).toInt()
