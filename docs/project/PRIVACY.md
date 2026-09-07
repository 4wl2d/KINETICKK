<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK 0.2.0 privacy note

Updated: 7 September 2026

The game has no user accounts, advertising, analytics service, tracking pixels,
remote APIs, or application cookies. Gameplay and settings are not uploaded.

Progress and settings stay on the device: Android uses SharedPreferences,
desktop uses JVM Preferences, and the web build uses browser localStorage.
Clearing application or site data may remove saved progress.

Desktop also keeps local crash reports and session logs in
`~/.kinetickk/crashes/` (or a configured location). Reports can contain exception
stacks, system and runtime details, recent game inputs, console output, and
copies of save payloads. Ten crash reports and three completed session logs
are retained. No report is sent automatically; sharing one is a user action.
Android and web do not use the desktop crash reporter.

The optional performance HUD measures frame timing and entity counts locally.
It does not transmit measurements.

This note covers the 0.2.0 application code. GitHub, hosting providers, and app
stores may process their own logs, account information, or purchases under
their separate privacy terms.
