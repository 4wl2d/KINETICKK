// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.api

/** The only AppSession instance supported by this application. */
enum class AppSessionInstanceId(
    val canonicalValue: String,
) {
    LOCAL_SESSION("kinetickk.local/AppSession/local-session"),
}

val LOCAL_APP_SESSION_INSTANCE_ID: AppSessionInstanceId =
    AppSessionInstanceId.LOCAL_SESSION

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

/** UI lifecycle token derived from, but distinct from, the stable AppSession identity. */
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
