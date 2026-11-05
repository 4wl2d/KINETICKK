<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Standard license texts

These files are verbatim third-party license texts. They are copied into a
desktop package only when a shipped artifact does not contain its own license
text and its published POM names one of these licenses exactly.

`Apache-2.0.txt` is the `META-INF/LICENSE` text from `gradle/wrapper/gradle-wrapper.jar`.
Do not edit it.

`Skia-LICENSE.txt` is the verbatim `LICENSE` from `google/skia` `main`, saved
because the Skiko native runtime jar ships Skia without that text. It is
copied beside every `org.jetbrains.skiko:skiko-awt-runtime` artifact. Do not
edit it.
