<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK 0.1.0 privacy note

Effective: 15 July 2026

The KINETICKK `0.1.0` application code does not implement user accounts,
advertising, analytics, telemetry, tracking pixels, remote APIs, or its own
cookies. It does not transmit gameplay or settings to Vladislav Tomilov.

The desktop build stores settings and progress locally through JVM Preferences.
The browser build stores settings and progress locally in the browser's
`localStorage`. A user can remove that data with the operating system or browser
controls. Uninstalling the app or clearing site storage may also remove it.

This note covers only the application code in version `0.1.0`. GitHub, a web
host, an app store, a payment provider, or another distribution platform may
process logs, account data, cookies, or purchases under its own privacy terms.
Those services are outside this note.

This file must be reviewed before a release that adds networking, crash
reporting, telemetry, accounts, payments, cloud saves, advertising, or any other
collection or transmission of personal data.
