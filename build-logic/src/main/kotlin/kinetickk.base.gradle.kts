// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.plugins.BasePluginExtension

// Leaf names such as `api` and `impl` repeat across module families. Giving every
// family its own Maven group prevents Kotlin MPP metadata from conflating those projects.
group = path
    .removePrefix(":")
    .split(':')
    .dropLast(1)
    .filter(String::isNotBlank)
    .joinToString(separator = ".", prefix = "kinetickk.")
    .removeSuffix(".")
version = "0.1.0"

pluginManager.withPlugin("base") {
    extensions.configure<BasePluginExtension> {
        archivesName.set(path.removePrefix(":").replace(':', '-'))
    }
}
