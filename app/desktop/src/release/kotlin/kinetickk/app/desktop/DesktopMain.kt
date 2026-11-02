// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.desktop

import androidx.compose.runtime.LaunchedEffect
import kinetickk.app.shared.enableKinetickkComposeRuntimeOptimizations
import kinetickk.app.shared.runKinetickkDesktop
import kotlinx.coroutines.delay

fun main(args: Array<String>) {
    enableKinetickkComposeRuntimeOptimizations()
    runKinetickkDesktop {
        if ("--smoke-test" in args) {
            LaunchedEffect(Unit) { delay(1500); exitApplication() }
        }
    }
}
