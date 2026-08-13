// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF05080D))
                    .semantics { testTagsAsResourceId = true },
            ) {
                val safePadding = WindowInsets.safeContent.asPaddingValues()
                val layoutDirection = LocalLayoutDirection.current
                val left = safePadding.calculateLeftPadding(layoutDirection)
                val right = safePadding.calculateRightPadding(layoutDirection)
                val top = safePadding.calculateTopPadding()
                val bottom = safePadding.calculateBottomPadding()
                Box(
                    modifier = Modifier
                        .offset(x = left, y = top)
                        .requiredSize(
                            width = (maxWidth - left - right).coerceAtLeast(0.dp),
                            height = (maxHeight - top - bottom).coerceAtLeast(0.dp),
                        )
                        .clipToBounds(),
                ) {
                    KinetickkApp()
                }
            }
        }
    }
}
