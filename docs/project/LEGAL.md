<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Open-source and release guide

This page explains the repository's legal structure. The full
[GNU GPL version 3](../../LICENSE) controls if this summary differs from it.

## Project status

KINETICKK is free and open-source software. Its original code, tests, build
files, docs, game content, and project-made assets are offered under
GPL-3.0-or-later unless a file clearly says otherwise.

Copyright stays with its authors. The GPL is the permission that lets everyone
use the work while requiring distributed derivatives to remain free. The
[NOTICE](../../NOTICE) also offers the original material authored by Vladislav
Tomilov in the reachable history through revision `3abbfea` under the GPL,
despite the older proprietary notices in those revisions.

Third-party components keep their own licenses. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Why GPL-3.0-or-later

KINETICKK is a distributed client game, so GPL-3.0 provides strong copyleft for
the source and binaries that users receive. Version 3 also contains the current
GPL patent, installation-information, and violation-cure provisions. The
`or-later` choice lets contributors and recipients use a future GPL version
without collecting a new agreement from every copyright holder.

AGPL is not used because KINETICKK has no server component. The WebAssembly and
JavaScript web build is delivered to each browser and is already distribution
under GPL. If a network game server is added later, reassess whether GPL or AGPL
best matches that component before accepting contributions to it.

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
[TRADEMARKS.md](TRADEMARKS.md).

## Contributions and future licensing

Contributions use the same GPL-3.0-or-later terms as the public project
(`inbound=outbound`). Contributors retain copyright, and neither a DCO sign-off
nor a separate CLA is required. Submitting a contribution does not grant the
maintainer an additional right to distribute it under proprietary terms.

The `or-later` grant supplies the planned upgrade path: any contributor or
recipient may choose a later version published by the Free Software Foundation,
as GPLv3 section 14 permits. A different license, a platform exception, or a
proprietary build containing outside contributions would require permission from
every relevant copyright holder.

## Steam and other stores

Valve warns that copyleft licenses such as GPL can be incompatible with a
combined Steamworks SDK build. The current project grants no Steamworks or DRM
exception. A GPL build may be distributed without linking the Steamworks SDK;
before adding the SDK, obtain a license-compatibility review and permission from
every relevant copyright holder for any needed exception.

Do not assume that the maintainer can add an exception or relicense a normal GPL
pull request later. Do not add third-party GPL-only code to a build intended for
separate store licensing without checking that every required right is
available.

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
8. archive the authorship and provenance records, editable asset sources,
   receipts, release source, binaries, and hashes.

See [SOURCE.md](SOURCE.md) for the corresponding-source plan and
[GOVERNANCE.md](GOVERNANCE.md) for control of the official project.

## Primary references

- [Open Source Initiative: GPL-3.0](https://opensource.org/license/gpl-3-0)
- [GNU: GPLv3 text and application guide](https://www.gnu.org/licenses/gpl-3.0.html)
- [GNU: GPL frequently asked questions](https://www.gnu.org/licenses/gpl-faq.html)
- [GNU: license recommendations](https://www.gnu.org/licenses/license-recommendations.html)
- [Steamworks: distributing open-source applications](https://partner.steamgames.com/doc/sdk/uploading/distributing_opensource)
- [WIPO: copyright protection](https://www.wipo.int/en/web/copyright/protection)
- [WIPO: trademark protection](https://www.wipo.int/en/web/trademarks/protection)

This guide is a project-maintenance record, not legal advice for a specific
country or release.
