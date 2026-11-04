// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.simulation

import kinetickk.ball.gameplay.nucleus.model.SiteState

/** An ordered list of named simulation facts; Floats are kept by their raw bits. */
internal class ExactFingerprint {
    val fields = mutableListOf<Pair<String, Any?>>()

    fun value(name: String, value: Any?) { fields += name to value }

    fun float(name: String, value: Float) {
        fields += name to value.toRawBits()
    }

    fun ints(name: String, values: Iterable<Int>) {
        var index = 0
        values.forEach { value("$name[$index]", it); index++ }
    }

    fun floats(name: String, values: Iterable<Float>) {
        var index = 0
        values.forEach { float("$name[$index]", it); index++ }
    }

    /** Every field of each site, Floats by their bits. */
    fun site(name: String, sites: List<SiteState>) {
        value("$name.size", sites.size)
        sites.forEachIndexed { index, site ->
            val prefix = "$name[$index]"
            value("$prefix.id", site.id)
            value("$prefix.kind", site.kind)
            float("$prefix.x", site.x)
            float("$prefix.y", site.y)
            float("$prefix.expiresAt", site.expiresAt)
            value("$prefix.active", site.active)
            float("$prefix.remaining", site.remaining)
            float("$prefix.clock", site.clock)
            value("$prefix.step", site.step)
            value("$prefix.count", site.count)
            float("$prefix.meter", site.meter)
            float("$prefix.volleyClock", site.volleyClock)
            float("$prefix.warningRemaining", site.warningRemaining)
            float("$prefix.volleyAngle", site.volleyAngle)
            float("$prefix.aimX", site.aimX)
            float("$prefix.aimY", site.aimY)
            value("$prefix.foeIds", site.foeIds.toList())
            site.course.forEachIndexed { mark, point ->
                float("$prefix.course[$mark].dx", point.dx)
                float("$prefix.course[$mark].dy", point.dy)
            }
            value("$prefix.course.size", site.course.size)
            value("$prefix.sides", site.sides)
            float("$prefix.par", site.par)
            float("$prefix.axis", site.axis)
            value("$prefix.hurtBaseline", site.hurtBaseline.toRawBits())
            value("$prefix.settled", site.settled)
        }
    }
}
