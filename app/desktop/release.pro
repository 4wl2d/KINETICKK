# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

# Preserve useful exception locations while allowing shrinking, inlining and renaming.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-printmapping build/compose/release-mapping.txt
-printusage build/compose/release-usage.txt

# Unique-callsite inlining produces invalid stack-map frames for this Kotlin 2.4 /
# Compose 1.12 graph (related upstream report: CMP-8625). Disabling only this pass
# passes the complete application's JVM verifier; disabling local-slot allocation
# or short-method inlining does not. Keep shrinking, short/tail inlining, constant
# folding and every other optimization enabled. The JVM can still inline at runtime.
-optimizations !method/inlining/unique

# Compose's bundled rules preserve Skiko JNI and Kotlin serialization bridges.
# Do not keep the game's packages: their unused classes and branches are shrinkable.
