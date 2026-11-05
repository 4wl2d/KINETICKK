// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

import kinetickk.gradle.CollectDesktopLicensesTask
import kinetickk.gradle.PackageSteamAppImageTask
import kinetickk.gradle.WriteDesktopBuildInfoTask
import kinetickk.gradle.IsolateDesktopRuntimeAction
import kinetickk.gradle.DeleteSuccessfulDesktopRuntimeAction
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

plugins {
    id("kinetickk.compose-desktop-application")
}

dependencies {
    implementation(projects.app.shared)
}

val buildInfo = tasks.register<WriteDesktopBuildInfoTask>("writeDesktopBuildInfo") {
    mustRunAfter(":updatePokeballResolvedManifest")
    gameVersion.set(project.version.toString())
    kotlinVersion.set(libs.versions.kotlin)
    composeVersion.set(libs.versions.compose)
    gitRevision.set(providers.exec {
        workingDir(rootDir)
        commandLine("git", "rev-parse", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText)
    gitStatus.set(providers.exec {
        workingDir(rootDir)
        commandLine("git", "status", "--porcelain=v1", "--untracked-files=normal")
        isIgnoreExitValue = true
    }.standardOutput.asText)
    repositoryDirectory.set(rootDir)
    buildSources.from(fileTree(rootDir) {
        include("**/src/**/*.kt", "**/*.gradle.kts", "gradle/libs.versions.toml", "gradle.properties")
        exclude("**/build/**", "**/.gradle/**")
    })
    outputDirectory.set(layout.buildDirectory.dir("generated/crash-build-info"))
}

sourceSets.main { resources.srcDir(buildInfo) }

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    providers.gradleProperty("crashDir").orNull?.let { systemProperty("kinetickk.crashDir", it) }
    // ./gradlew :app:desktop:run -Pkinetickk.uncapped=true turns V-Sync off (see DesktopFramePacing).
    providers.gradleProperty("kinetickk.uncapped").orNull?.let { systemProperty("kinetickk.uncapped", it) }
    doFirst(IsolateDesktopRuntimeAction(objects.fileCollection()))
    doLast(DeleteSuccessfulDesktopRuntimeAction())
}

val collectDesktopLicenses = tasks.register<CollectDesktopLicensesTask>("collectDesktopLicenses") {
    description = "Copies license texts for the desktop runtime and packaging JDK, or fails."
    artifacts.set(
        configurations.named("runtimeClasspath").flatMap { configuration ->
            configuration.incoming.artifacts.resolvedArtifacts.map { resolved ->
                resolved.map { artifact ->
                    val identifier = artifact.id.componentIdentifier
                    val kind = if (identifier is ProjectComponentIdentifier) "project" else "module"
                    val coordinates = when (identifier) {
                        is ModuleComponentIdentifier -> "${identifier.group}:${identifier.module}:${identifier.version}"
                        is ProjectComponentIdentifier -> "project${identifier.projectPath}"
                        else -> identifier.displayName
                    }
                    "$kind\t$coordinates\t${artifact.file.absolutePath}"
                }.sorted()
            }
        },
    )
    jdkHome.set(layout.dir(providers.systemProperty("java.home").map(::File)))
    projectLegalFiles.from(
        rootDir.resolve("LICENSE"),
        rootDir.resolve("NOTICE"),
        rootDir.resolve("docs/project/STEAMWORKS.md"),
        rootDir.resolve("docs/project/THIRD_PARTY_NOTICES.md"),
        rootDir.resolve("foundation/design/src/commonMain/composeResources/files/unbounded-OFL.txt"),
        rootDir.resolve("foundation/design/src/commonMain/composeResources/files/sofia-sans-extra-condensed-OFL.txt"),
        rootDir.resolve("foundation/design/src/commonMain/composeResources/files/sofia-sans-semi-condensed-OFL.txt"),
        rootDir.resolve("foundation/design/src/commonMain/composeResources/files/martian-mono-OFL.txt"),
    )
    standardLicenseTexts.from(
        rootDir.resolve("docs/legal/Apache-2.0.txt"),
        rootDir.resolve("docs/legal/Skia-LICENSE.txt"),
    )
    outputDirectory.set(layout.buildDirectory.dir("licenses"))
}

val packageSteamAppImage = tasks.register<PackageSteamAppImageTask>("packageSteamAppImage") {
    group = "distribution"
    description = "Packages the desktop app image with LICENSES/ and corresponding source."
    doNotTrackState("The task reads the Git repository and writes into the app image.")
    dependsOn(collectDesktopLicenses, "createDistributable")
    licensesDirectory.set(collectDesktopLicenses.flatMap { it.outputDirectory })
    distributableRoot.set(layout.buildDirectory.dir("compose/binaries/main/app"))
    repositoryDirectory.set(rootDir)
    gameVersion.set(project.version.toString())
    gitRevision.set(providers.exec {
        workingDir(rootDir)
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.map(String::trim))
    zipFile.set(layout.buildDirectory.file("steam/KINETICKK-windows-app-image.zip"))
    checksumFile.set(layout.buildDirectory.file("steam/KINETICKK-windows-app-image.sha256"))
}
