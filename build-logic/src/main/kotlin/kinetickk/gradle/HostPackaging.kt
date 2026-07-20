// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.Copy

private val legalDocumentPaths = listOf(
    "LICENSE",
    "NOTICE",
    "docs/project/AUTHORS.md",
    "docs/project/CONTRIBUTING.md",
    "docs/project/CONTRIBUTOR_LICENSE_AGREEMENT.md",
    "docs/project/GOVERNANCE.md",
    "docs/project/SOURCE.md",
    "docs/project/TRADEMARKS.md",
    "docs/project/THIRD_PARTY_NOTICES.md",
    "docs/project/ASSET_PROVENANCE.md",
    "docs/project/PRIVACY.md",
    "docs/project/LEGAL.md",
)

internal fun Project.packageLegalDocuments() {
    tasks.withType(Copy::class.java).configureEach {
        if (name.endsWith("processResources", ignoreCase = true)) {
            from(rootProject.projectDir) {
                include(legalDocumentPaths)
                into("META-INF")
            }
        }
    }
}
