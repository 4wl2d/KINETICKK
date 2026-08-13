// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.app.shared

import android.app.Application
import android.content.Context

class KinetickkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        enableKinetickkComposeRuntimeOptimizations()
        AndroidApplicationContext.install(applicationContext)
    }
}

internal object AndroidApplicationContext {
    @Volatile
    private var installedContext: Context? = null

    fun install(context: Context) {
        val applicationContext = context.applicationContext
        synchronized(this) {
            val current = installedContext
            check(current == null || current === applicationContext) {
                "Android application context may be installed only once per process"
            }
            installedContext = applicationContext
        }
    }

    fun requireContext(): Context = checkNotNull(installedContext) {
        "KINETICKK Android application context is not installed"
    }
}
