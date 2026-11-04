// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

import kinetickk.foundation.common.localization.AppLanguage
import java.net.URL
import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentLocalizationInitializationTest {
    @Test
    fun englishMappingWorksWithoutLoadingRussianTablesAndRussianStillTranslates() {
        val parent = javaClass.classLoader
        val facadeName = "kinetickk.ball.content.api.ContentLocalizationKt"
        // A fresh child loader keeps this independent of other localization tests and their class initializers.
        val code = Class.forName(facadeName, false, parent).protectionDomain.codeSource.location
        IsolatedContentLoader(code, parent).use { loader ->
            val facade = Class.forName(facadeName, true, loader)
            val localize = facade.getMethod("localizedContent", String::class.java, AppLanguage::class.java)
            fun translated(text: String, language: AppLanguage) = localize.invoke(null, text, language) as String

            // Russian phrase classes are deliberately unavailable: an eager initializer fails this call.
            for (text in listOf("Flux Wake", "DRIFTER", "+0.2/s Integrity regeneration", "Unlisted text", "")) {
                assertEquals(text, translated(text, AppLanguage.English))
            }
            assertTrue(loader.loadedTables.isEmpty(), "English must not load the Russian dictionary as a side effect")

            loader.allowRussianTables = true
            assertEquals("Потоковый след", translated("Flux Wake", AppLanguage.Russian))
            assertEquals("ДРЕЙФЕР", translated("DRIFTER", AppLanguage.Russian))
            assertEquals("Восстановление прочности: +0,2/с", translated("+0.2/s Integrity regeneration", AppLanguage.Russian))
            assertEquals("Unlisted text", translated("Unlisted text", AppLanguage.Russian))
            assertTrue(loader.loadedTables.isNotEmpty(), "Russian lookup must actually exercise the table-loading path")
            assertEquals("Flux Wake", translated("Flux Wake", AppLanguage.English), "Switching back preserves English identities")
        }
    }

    private class IsolatedContentLoader(code: URL, parent: ClassLoader) : URLClassLoader(arrayOf(code), parent) {
        var allowRussianTables = false
        val loadedTables = mutableSetOf<String>()

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (!name.startsWith("kinetickk.ball.content.api.")) return super.loadClass(name, resolve)
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                if (name.endsWith("PhrasesInRussianKt")) {
                    check(allowRussianTables) { "English mapping tried to load $name" }
                    loadedTables += name
                }
                return findClass(name).also { if (resolve) resolveClass(it) }
            }
        }
    }
}
