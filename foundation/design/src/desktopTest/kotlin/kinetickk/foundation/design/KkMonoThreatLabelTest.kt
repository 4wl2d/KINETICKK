// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mono hatches every threat, but threat-faced labels stay readable (SPEC 9): the hatch is a rim
 * band around a solid face, so the label sits on the same pixels as without the hatch.
 */
class KkMonoThreatLabelTest {
    private val density = 2f

    /** One threat-faced component: its outer face rect, rim width and slab cut (px). */
    private class Case(val name: String, val face: Rect, val rim: Float, val cut: Float, val draw: DrawScope.(CanvasTextMeasurer) -> Unit)

    private val cases = listOf(
        Case("hazard button", Rect(40f, 40f, 440f, 144f), 8f, 28f) { m ->
            drawKkButton(m, Rect(40f, 40f, 440f, 144f), "Replace", KkButtonVariant.HAZARD)
        },
        Case("armed button", Rect(40f, 40f, 440f, 144f), 8f, 28f) { m ->
            drawKkButton(m, Rect(40f, 40f, 440f, 144f), "Confirm tier 4", armed = true, time = 0.2f)
        },
        Case("threat stamp", Rect.Zero, 6f, 0f) { m ->
            drawKkStamp(m, "Overheat", Offset(40f, 40f), KkStampVariant.THREAT, rotationDeg = 0f)
        },
        Case("warning toast", Rect.Zero, 6f, 20f) { m ->
            drawKkToast(m, Offset(40f, 40f), "Singularity proximity", "Warden", tone = KkToastTone.WARNING)
        },
    )

    @Test
    fun monoHatchesOnlyTheRimAndKeepsTheLabelFaceSolid() {
        val mono = kkTestMeasurer(roles = KkRolePalette.Mono, density = density)
        val unhatched = kkTestMeasurer(roles = KkRolePalette.Mono.copy(hatchThreats = false), density = density)
        cases.forEach { case ->
            val face = faceOf(case, mono)
            val hatched = render { case.draw(this, mono) }
            val plain = render { case.draw(this, unhatched) }
            // Inside the rim: identical to the unhatched draw (solid face, same label pixels).
            val inner = innerRect(face, case)
            assertEquals(0, differing(hatched, plain, inner), "${case.name}: hatch stripes inside the rim")
            // The rim band carries the ink hatch; without the hatch it is a plain threat face.
            val band = rimBand(face, case)
            assertTrue(hatched.regionCountNear(Kk.Ink, band, 40) > 20, "${case.name}: the rim is hatched")
            assertEquals(0, plain.regionCountNear(Kk.Ink, band, 40), "${case.name}: no hatch without Mono")
        }
    }

    @Test
    fun otherPalettesDrawNoHatch() {
        listOf(KkRolePalette.Default, KkRolePalette.Protan).forEach { roles ->
            val measurer = kkTestMeasurer(roles = roles, density = density)
            cases.forEach { case ->
                val image = render { case.draw(this, measurer) }
                val band = rimBand(faceOf(case, measurer), case)
                assertEquals(0, image.regionCountNear(Kk.Ink, band, 40), "${case.name}: no hatch in $roles")
            }
        }
    }

    /** The drawn face rect: fixed for buttons, returned by the stamp and toast helpers. */
    private fun faceOf(case: Case, measurer: CanvasTextMeasurer): Rect {
        if (case.face != Rect.Zero) return case.face
        var rect = Rect.Zero
        render { rect = when (case.name) {
            "threat stamp" -> drawKkStamp(measurer, "Overheat", Offset(40f, 40f), KkStampVariant.THREAT, rotationDeg = 0f)
            else -> drawKkToast(measurer, Offset(40f, 40f), "Singularity proximity", "Warden", tone = KkToastTone.WARNING)
        } }
        return rect
    }

    /** Strictly inside the rim and clear of the sheared ends (1 px anti-aliasing margin). */
    private fun innerRect(face: Rect, case: Case) =
        Rect(face.left + case.rim + case.cut + 2f, face.top + case.rim + 1f, face.right - case.rim - case.cut - 2f, face.bottom - case.rim - 1f)

    /** The top rim rows across the straight part of the face. */
    private fun rimBand(face: Rect, case: Case) =
        Rect(face.left + case.cut + 2f, face.top + 1f, face.right - case.cut - 2f, face.top + case.rim - 1f)

    private fun render(draw: DrawScope.() -> Unit): ImageBitmap = kkRender(560, 200, density, draw = draw)

    private fun differing(a: ImageBitmap, b: ImageBitmap, rect: Rect): Int {
        val pa = a.argb()
        val pb = b.argb()
        var count = 0
        for (y in rect.top.toInt() until rect.bottom.toInt()) for (x in rect.left.toInt() until rect.right.toInt()) {
            if (pa[y * a.width + x] != pb[y * a.width + x]) count++
        }
        return count
    }

    private fun ImageBitmap.regionCountNear(color: androidx.compose.ui.graphics.Color, rect: Rect, tolerance: Int): Int =
        regionCountNear(color, rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt(), tolerance)
}
