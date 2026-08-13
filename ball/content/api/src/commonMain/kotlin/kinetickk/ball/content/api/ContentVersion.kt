// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kotlin.jvm.JvmInline

/** Stable identity of the content captured by a Ball at bootstrap. */
@JvmInline
value class ContentVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "Content version must not be blank" }
    }

    override fun toString(): String = value
}

/** Canonical version published by the current Content implementation. */
val KINETICKK_CONTENT_VERSION: ContentVersion = ContentVersion("kinetickk-content-1")

/** Bootstrap limits. Runtime decisions use the corresponding captured snapshot fields. */
object ContentBounds {
    const val MAX_ITEMS: Int = 400
    const val MAX_WEAPONS: Int = 12
    const val MAX_META_UPGRADES: Int = 8
    const val MAX_RELICS: Int = 40
    const val MIN_REBIRTH_LEVEL: Int = 0
    const val MAX_REBIRTH_LEVEL: Int = 10
}
