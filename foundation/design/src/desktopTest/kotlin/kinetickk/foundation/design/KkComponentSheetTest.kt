// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.foundation.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders review sheets to `foundation/design/build/redesign-shots/` for side-by-side comparison
 * with `docs/design/redesign/screenshots/boards/Components.png` (and Main/Motion for the extras).
 */
class KkComponentSheetTest {
    @Test
    fun rendersTheComponentsSheet() {
        val measurer = kkTestMeasurer()
        val sheet = kkRender(1440, 3060) { drawKkComponentsSheet(measurer, time = 3f) }
        sheet.writePng(File("build/redesign-shots/components.png"))
        // The sheet shows every role: bone type, volt faces, hazard faces and rarity colors.
        assertTrue(sheet.countNear(Kk.Volt) > 5_000)
        assertTrue(sheet.countNear(Kk.Hazard) > 2_000)
        assertTrue(sheet.countNear(Kk.Bone) > 10_000)
        assertTrue(sheet.countNear(Kk.RLegend, 20) > 500)
    }

    @Test
    fun rendersTheExtrasSheetForEveryColorVisionMode() {
        KkVisionMode.entries.forEach { mode ->
            val measurer = kkTestMeasurer(roles = mode.palette)
            val sheet = kkRender(1440, 1640) { drawKkFoundationExtrasSheet(measurer, time = 1.2f) }
            sheet.writePng(File("build/redesign-shots/foundation-extras-${mode.name.lowercase()}.png"))
            assertTrue(sheet.countNear(mode.palette.you) > 1_000, "$mode you color is visible")
        }
        val protan = kkTestMeasurer(roles = KkRolePalette.Protan)
        kkRender(1440, 3060) { drawKkComponentsSheet(protan, time = 3f) }
            .writePng(File("build/redesign-shots/components-protan.png"))
        // Mono: hazard/armed buttons, threat stamps and warning toasts keep a hatched rim only.
        val mono = kkTestMeasurer(roles = KkRolePalette.Mono)
        kkRender(1440, 3060) { drawKkComponentsSheet(mono, time = 3f) }
            .writePng(File("build/redesign-shots/components-mono.png"))
    }
}
