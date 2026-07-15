<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Open-source and release guide

This page explains the repository's legal structure. The full
[GNU GPL version 3](../LICENSE) controls if this summary differs from it.

## Project status

KINETICKK is free and open-source software. Its original code, tests, build
files, docs, game content, and project-made assets are offered under
GPL-3.0-or-later unless a file clearly says otherwise.

Copyright stays with its authors. The GPL is the permission that lets everyone
use the work while requiring distributed derivatives to remain free. The
[NOTICE](../NOTICE) also offers the original material authored by Vladislav
Tomilov in the reachable history through revision `3abbfea` under the GPL,
despite the older proprietary notices in those revisions.

Third-party components keep their own licenses. See
[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

## What anyone may do

Subject to the GPL, anyone may:

- inspect and study the source;
- build and run the game locally for any purpose;
- modify it privately without publishing the changes;
- share original or modified source;
- distribute binaries and forks; and
- charge money for copies, support, or modified versions.

These rights are part of open source. An educational-only, noncommercial, or
no-forks rule would conflict with that model.

## Duties when sharing a copy or fork

The exact duties depend on how the work is distributed. In general, a person
who conveys KINETICKK or a covered derivative must:

1. preserve the copyright, license, and warranty notices;
2. give recipients a copy of the GPL;
3. mark modified files or versions and give a relevant change date;
4. license the covered work as a whole under GPLv3 or a permitted later version;
5. provide the complete corresponding source for the exact binary, including
   the scripts needed to control compilation and installation; and
6. avoid extra terms or technical limits that take away the recipients' GPL
   rights.

Putting a modified binary online without its exact source, removing notices,
hiding that a version was changed, or distributing a closed covered derivative
can violate the GPL. Section 8 of the GPL governs termination and cure. The
available remedies come from applicable law; this project does not invent a
private penalty clause.

A web build sends JavaScript and WebAssembly copies to the browser, so a public
web distribution must offer the corresponding source for that exact build.
Pure network interaction with server-side code is treated differently by GPL;
KINETICKK does not currently contain a game server.

## Copyright, ideas, and the KINETICKK brand

Open source does not erase authorship or transfer copyright to users. It grants
rights on stated terms. Copyright protects original expression such as code,
graphics, audio, and text, but not an abstract idea, genre, rule, system, or
gameplay mechanic.

The GPL also does not license the KINETICKK name, logo, or claim of official
status. A lawful fork may use the GPL-covered work, but it may not mislead users
into thinking that Vladislav Tomilov published or endorsed it. See
[TRADEMARKS.md](../TRADEMARKS.md).

## Contributions and future licensing

Every pull-request commit must carry a Developer Certificate of Origin sign-off.
Before copyrightable work is merged, its author must also sign the
[Contributor License Agreement](../CONTRIBUTOR_LICENSE_AGREEMENT.md).

The contributor keeps copyright. The CLA gives Vladislav Tomilov a broad right
to license accepted work under the public GPL and under separate store or
platform terms. A DCO alone confirms the right to submit a patch but does not
grant that relicensing right.

The CLA in this repository is a project template. Before the first outside
contribution is merged, a lawyer should review it for the contributor's and
maintainer's countries and set up a valid electronic-signature process.

## Steam and other stores

Valve warns that copyleft licenses such as GPL can be incompatible with a
combined Steamworks SDK build. Before adding Steamworks, choose and review one
of these paths:

1. ship the GPL game without linking the Steamworks SDK;
2. grant a narrow, lawyer-reviewed GPL compatibility exception; or
3. distribute an official Steam build under separate terms using rights held by
   Vladislav Tomilov and granted through signed contributor agreements.

Do not assume that a normal GPL pull request can later be relicensed. Do not add
third-party GPL-only code to a build intended for separate store licensing
without checking that every required right is available.

An EULA or store rule attached to a GPL-covered copy must not remove the GPL
rights or add a further restriction. A separately licensed official build is a
different legal path and is possible only for material whose rights allow it.

## Release gate

Before a public desktop, web, Steam, or other store release:

1. build from a signed tag and record the source and binary hashes;
2. publish the complete corresponding source for that exact binary and keep it
   available for the period required by the chosen GPL section 6 method;
3. package the GPL text, project notices, every required third-party license,
   and all required NOTICE text;
4. confirm that each contribution and asset has a clean rights record;
5. review every SDK, DRM feature, store term, and EULA for GPL compatibility;
6. clear the KINETICKK name and logo in the target markets;
7. update the privacy note for telemetry, accounts, payments, cloud saves, or
   other data flows; and
8. archive the signed contributor agreements, editable asset sources, receipts,
   release source, binaries, and hashes.

See [SOURCE.md](../SOURCE.md) for the corresponding-source plan and
[GOVERNANCE.md](../GOVERNANCE.md) for control of the official project.

## Primary references

- [Open Source Initiative: GPL-3.0](https://opensource.org/license/gpl-3-0)
- [GNU: GPLv3 text and application guide](https://www.gnu.org/licenses/gpl-3.0.html)
- [GNU: GPL frequently asked questions](https://www.gnu.org/licenses/gpl-faq.html)
- [Steamworks: distributing open-source applications](https://partner.steamgames.com/doc/sdk/uploading/distributing_opensource)
- [Developer Certificate of Origin 1.1](https://developercertificate.org/)
- [WIPO: copyright protection](https://www.wipo.int/en/web/copyright/protection)
- [WIPO: trademark protection](https://www.wipo.int/en/web/trademarks/protection)

This guide is a project-maintenance record, not legal advice for a specific
country or release.
