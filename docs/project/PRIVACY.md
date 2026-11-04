<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK 0.6.0 privacy note

Updated: 30 October 2026

The game has no user accounts, advertising, analytics service, tracking pixels,
remote APIs, or application cookies. Gameplay and settings are not uploaded.

Progress and settings stay on the device: Android uses SharedPreferences,
desktop uses JVM Preferences, and the web build uses browser localStorage.
Clearing application or site data may remove saved progress.

Desktop Debug also keeps local crash reports and session logs in
`~/.kinetickk/crashes/` (or a configured location). Reports can contain exception
stacks, system and runtime details, recent game inputs, console output, and
copies of save payloads. Ten crash reports and three completed session logs
are retained. No report is sent automatically; sharing one is a user action.
Android and web do not use the desktop crash reporter. Release does not include
the diagnostic recorder, diagnostic UI, or desktop session logger.

Debug's optional diagnostic session records frame and CPU phase timing, entity
counts, game events, player actions, and available warnings, errors and runtime
details in bounded memory. Copying the report places it in the system clipboard;
the game does not transmit it. Review a report before sharing it.

This note covers the 0.6.0 application code. GitHub, hosting providers, and app
stores may process their own logs, account information, or purchases under
their separate privacy terms.
