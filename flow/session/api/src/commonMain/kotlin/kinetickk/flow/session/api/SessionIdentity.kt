// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

/** Monotonic revision of accepted AppSession frames. */
data class SessionRevision(
    val value: Long,
) {
    init {
        require(value >= 0L) { "Session revision must be non-negative" }
    }

    companion object {
        val ZERO: SessionRevision = SessionRevision(0L)
    }
}

/** UI lifecycle token derived from the Session route revision. */
data class SessionRouteToken(
    val value: Long,
) {
    init {
        require(value >= 0L) { "Session route token must be non-negative" }
    }

    companion object {
        fun from(revision: SessionRevision): SessionRouteToken =
            SessionRouteToken(revision.value)
    }
}
