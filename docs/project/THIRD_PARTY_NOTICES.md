<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Third-party notices

KINETICKK's original project material is licensed under GPL-3.0-or-later.
Third-party components keep their own copyright and license terms; the GPL does
not replace them.

This file records the source-tree components and the main dependency families
resolved for KINETICKK `0.1.0`. It is not a substitute for the complete license
texts, copyright notices, and corresponding source offer required for a binary
release.

## Files stored in this repository

| Component | Version | Files | License |
|---|---:|---|---|
| Gradle Wrapper | 8.14.3 | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` | [Apache License 2.0](https://github.com/gradle/gradle/blob/v8.14.3/LICENSE) |

The wrapper scripts contain their own Apache-2.0 headers, and
`gradle-wrapper.jar` contains the full license at `META-INF/LICENSE`. Those
notices must remain intact.

## Downloaded build and runtime components

These components are fetched by Gradle and are not relicensed by KINETICKK:

| Component family | Resolved version used by 0.1.0 | License / upstream |
|---|---:|---|
| Kotlin standard library and Gradle plugin | 2.3.20 | [Apache License 2.0](https://github.com/JetBrains/kotlin/blob/v2.3.20/license/LICENSE.txt) |
| Compose Multiplatform | 1.11.0 | [Apache License 2.0](https://github.com/JetBrains/compose-multiplatform/blob/v1.11.0/LICENSE.txt) |
| AndroidX Compose runtime | 1.11.1 | [Apache License 2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| AndroidX Collection / Annotation | 1.5.0 / 1.9.1 | [AndroidX licenses](https://github.com/androidx/androidx) |
| AndroidX Lifecycle / Saved State / Navigation Event / Arch Core | 2.9.4 / 1.4.0 / 1.0.1 / 2.2.0 | [AndroidX licenses](https://github.com/androidx/androidx) |
| JetBrains AndroidX Lifecycle / Saved State ports | 2.9.6 / 1.3.6 | [Compose Multiplatform dependencies](https://github.com/JetBrains/compose-multiplatform) |
| kotlinx.coroutines | 1.9.0 | [Apache License 2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/LICENSE.txt) |
| kotlinx.serialization | 1.7.3 | [Apache License 2.0](https://github.com/Kotlin/kotlinx.serialization/blob/v1.7.3/LICENSE.txt) |
| kotlinx.atomicfu | 0.28.0 | [Apache License 2.0](https://github.com/Kotlin/kotlinx-atomicfu/blob/0.28.0/LICENSE.txt) |
| kotlinx-browser | 0.5.0 | [Apache License 2.0](https://github.com/Kotlin/kotlinx-browser/blob/master/LICENSE) |
| Skiko | 0.144.6 | [Apache License 2.0](https://github.com/JetBrains/skiko/blob/v0.144.6/LICENSE) and its [NOTICE](https://github.com/JetBrains/skiko/blob/v0.144.6/NOTICE) |
| Skia, used through Skiko | version bundled by Skiko | [BSD 3-Clause](https://github.com/google/skia/blob/main/LICENSE) |
| JetBrains Annotations / JBR API | 23.0.0 / 1.9.0 | Terms and notices shipped by the corresponding JetBrains artifacts |
| JSpecify annotations | 1.0.0 | [Apache License 2.0](https://github.com/jspecify/jspecify/blob/main/LICENSE) |

Additional platform-specific and transitive artifacts may be present. Artifact
metadata may be absent or incomplete; verify every shipped artifact against its
versioned upstream source and actual license text before distribution.

## Release requirement

The current build copies the KINETICKK legal documents into `META-INF`, but it
does not yet assemble a complete release-grade `LICENSES/` directory. Do not
treat a development artifact as ready for public distribution until this gate
is complete.

Before distributing any desktop or web build:

1. publish or offer the complete corresponding source for the exact binary,
   including the build scripts and installation information required by GPLv3;
2. regenerate both runtime graphs and identify every shipped artifact;
3. retain all required third-party copyright and NOTICE text; and
4. package the applicable third-party license texts under a `LICENSES/`
   directory in the build.

Create the desktop inventory on every release OS:
`compose.desktop.currentOs` resolves different native artifacts on macOS,
Windows, and Linux. A self-contained desktop package also includes a Java
runtime, whose license and notices must be inventoried and shipped. At minimum,
review:

```bash
./gradlew :app:desktop:dependencies --configuration runtimeClasspath
./gradlew :app:web:dependencies --configuration wasmJsRuntimeClasspath
```

A dependency update makes the versions above stale and requires this file and
the release notices to be updated before distribution.
