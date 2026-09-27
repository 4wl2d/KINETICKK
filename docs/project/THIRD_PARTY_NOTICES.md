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
| Gradle Wrapper | 9.7.1 | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` | [Apache License 2.0](https://github.com/gradle/gradle/blob/v9.7.1/LICENSE) |
| Unbounded | Static weights 900 and 700, subset, obtained 2026-09-27 | `foundation/design/src/commonMain/composeResources/font/kk_wide_black.ttf`, `kk_wide_bold.ttf` | [SIL Open Font License 1.1](https://github.com/google/fonts/tree/main/ofl/unbounded); complete notice in `composeResources/files/unbounded-OFL.txt` |
| Sofia Sans Extra Condensed | Static weights 900 italic, 900 and 800, subset, obtained 2026-09-27 | `foundation/design/src/commonMain/composeResources/font/kk_cond_black_italic.ttf`, `kk_cond_black.ttf`, `kk_cond_extrabold.ttf` | [SIL Open Font License 1.1](https://github.com/google/fonts/tree/main/ofl/sofiasansextracondensed); complete notice in `composeResources/files/sofia-sans-extra-condensed-OFL.txt` |
| Sofia Sans Semi Condensed | Static weights 400, 500 and 700, subset, obtained 2026-09-27 | `foundation/design/src/commonMain/composeResources/font/kk_body_regular.ttf`, `kk_body_medium.ttf`, `kk_body_bold.ttf` | [SIL Open Font License 1.1](https://github.com/google/fonts/tree/main/ofl/sofiasanssemicondensed); complete notice in `composeResources/files/sofia-sans-semi-condensed-OFL.txt` |
| Martian Mono | Static weights 500 and 700, subset, obtained 2026-09-27 | `foundation/design/src/commonMain/composeResources/font/kk_mono_medium.ttf`, `kk_mono_bold.ttf` | [SIL Open Font License 1.1](https://github.com/google/fonts/tree/main/ofl/martianmono); complete notice in `composeResources/files/martian-mono-OFL.txt` |

The wrapper scripts contain their own Apache-2.0 headers, and
`gradle-wrapper.jar` contains the full license at `META-INF/LICENSE`. Those
notices must remain intact.

The bundled font files are static instances of the upstream Google Fonts variable
fonts. The redesign fonts (Unbounded, Sofia Sans Extra Condensed, Sofia Sans Semi
Condensed and Martian Mono) are modified: they were instanced to static weights and
subset with fontTools to Latin, Latin Extended-A, Cyrillic and the punctuation and
symbols the interface uses, keeping the OpenType layout features (including tabular
figures). Their complete OFL notices are packaged with the Compose resources on every
target. They are bundled locally; the interface does not fetch them from a font CDN.

## Downloaded build and runtime components

These components are fetched by Gradle and are not relicensed by KINETICKK:

| Component family | Resolved version used by 0.1.0 | License / upstream |
|---|---:|---|
| Android Gradle Plugin | 9.4.0 | [Apache License 2.0](https://android.googlesource.com/platform/tools/base/+/studio-main/LICENSE.txt) |
| Kotlin standard library and Gradle plugin | 2.4.20-RC3 | [Apache License 2.0](https://github.com/JetBrains/kotlin/blob/v2.4.20-RC3/license/LICENSE.txt) |
| Compose Multiplatform | 1.12.0 | [Apache License 2.0](https://github.com/JetBrains/compose-multiplatform/blob/v1.12.0/LICENSE.txt) |
| AndroidX Compose runtime | 1.12.0 | [Apache License 2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt) |
| AndroidX Activity | 1.13.0 | [AndroidX licenses](https://github.com/androidx/androidx) |
| AndroidX Test runner / rules / JUnit extension | 1.7.0 / 1.7.0 / 1.3.0 | [AndroidX licenses](https://github.com/android/android-test) |
| AndroidX Collection / Annotation | 1.5.0 / 1.9.1 | [AndroidX licenses](https://github.com/androidx/androidx) |
| AndroidX Lifecycle / Saved State / Navigation Event / Arch Core (Android) | 2.9.4 / 1.4.0 / 1.0.0 / 2.2.0 | [AndroidX licenses](https://github.com/androidx/androidx) |
| AndroidX Lifecycle / Saved State / Navigation Event (Desktop) | 2.11.0 / 1.4.0 / 1.1.1 | [AndroidX licenses](https://github.com/androidx/androidx) |
| JetBrains AndroidX Lifecycle / Saved State / Navigation Event ports | 2.9.6 / 1.3.6 / 1.1.0 | [Compose Multiplatform dependencies](https://github.com/JetBrains/compose-multiplatform) |
| kotlinx.coroutines | 1.9.0 | [Apache License 2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/LICENSE.txt) |
| kotlinx.serialization | 1.11.0 | [Apache License 2.0](https://github.com/Kotlin/kotlinx.serialization/blob/v1.11.0/LICENSE.txt) |
| kotlinx.atomicfu | 0.28.0 | [Apache License 2.0](https://github.com/Kotlin/kotlinx-atomicfu/blob/0.28.0/LICENSE.txt) |
| kotlinx-browser | 0.5.0 | [Apache License 2.0](https://github.com/Kotlin/kotlinx-browser/blob/master/LICENSE) |
| Skiko | 0.150.1 | [Apache License 2.0](https://github.com/JetBrains/skiko/blob/v0.150.1/LICENSE) and its [NOTICE](https://github.com/JetBrains/skiko/blob/v0.150.1/NOTICE) |
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

Before distributing any Android, desktop, or web build:

1. publish or offer the complete corresponding source for the exact binary,
   including the build scripts and installation information required by GPLv3;
2. regenerate every shipping runtime graph and identify every shipped artifact;
3. retain all required third-party copyright and NOTICE text; and
4. package the applicable third-party license texts under a `LICENSES/`
   directory in the build.

Create the desktop inventory on every release OS:
`compose.desktop.currentOs` resolves different native artifacts on macOS,
Windows, and Linux. A self-contained desktop package also includes a Java
runtime, whose license and notices must be inventoried and shipped. At minimum,
review:

```bash
./gradlew :app:android:dependencies --configuration releaseRuntimeClasspath
./gradlew :app:desktop:dependencies --configuration runtimeClasspath
./gradlew :app:web:dependencies --configuration wasmJsRuntimeClasspath
```

A dependency update makes the versions above stale and requires this file and
the release notices to be updated before distribution.
