<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Corresponding source

The canonical public repository is:

<https://github.com/4wl2d/KINETICKK>

The repository contains the Kotlin source, tests, Gradle build files, wrapper,
web host page, docs, and project-made assets needed to build KINETICKK.

## Official releases

Every official binary release must identify an exact signed Git tag. The source
archive attached to that release must match the tag and include every file
needed to build the released binary. The download page for the binary must link
to that exact source, not merely to the moving default branch.

Release notes must record:

- the Git tag and commit ID;
- source archive and binary SHA-256 hashes;
- the JDK, Gradle, Kotlin, and Compose versions;
- target operating system and architecture; and
- any patch, platform adapter, SDK binding, or build input used for that binary.

Prototype artifacts built from an uncommitted working tree are development
builds, not official releases.

The current project version is `0.1.0`. The macOS `jpackage` tool rejects an
application version whose first number is zero, so `createDistributable` cannot
produce the native app until the release owner chooses a positive-major macOS
package version. Record that mapping in the release notes; do not silently
present the prototype as version `1.0.0`.

## Building from source

With JDK 17 or newer:

```bash
./gradlew desktopTest
./gradlew run
./gradlew wasmJsBrowserDistribution
```

The desktop and web build details are documented in
[README.md](../../README.md); the implementation is organized as application,
core, and feature modules.

## Forks and other distributors

A distributor of a modified or repackaged binary must provide the complete
corresponding source for that exact binary under GPLv3. Linking only to the
official KINETICKK repository is not enough when the distributed build contains
different code, scripts, settings, or required installation information.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the separate dependency
inventory and release notice gate.
