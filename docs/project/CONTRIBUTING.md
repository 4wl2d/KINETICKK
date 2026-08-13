<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Contributing to KINETICKK

Bug reports, ideas, documentation, tests, fixes, and new features are welcome.
The goal is to keep the public game useful for learning, easy to improve, and
consistently available under GPL-3.0-or-later.

## Before opening a pull request

1. Discuss large features in an issue before writing them.
2. Run the cross-target **complete local gate** documented in the
   [project README](../../README.md#verification-and-packaging).
3. If the change touches a runtime or build hot path, run the relevant
   [`tools/performance`](../../tools/performance/README.md) comparison and record
   its exact revisions, semantic compatibility, and material tradeoffs.
4. Keep each change focused and explain its purpose.
5. Do not submit code, art, audio, text, or other material unless you have the
   right to license it on the terms below.
6. Disclose copied, generated, or adapted material and record its source and
   license. Do not submit confidential material or output whose rights are
   unclear.

New project-authored files must use
`SPDX-License-Identifier: GPL-3.0-or-later` and an accurate
`SPDX-FileCopyrightText` line. If you are unsure how to write a header, leave it
for review; the absence of a hand-written notice does not erase authorship, and
Git history remains the detailed contribution record.

## Contribution license

KINETICKK uses a simple **inbound=outbound** model. By intentionally submitting
copyrightable material for inclusion in the project, you license that material
under GPL-3.0-or-later, the same terms used for the public project. Clearly
identified third-party material remains under its own compatible license. You
keep copyright in your work.

No Developer Certificate of Origin sign-off or separate Contributor License
Agreement is required. The project receives no special right to relicense your
contribution under proprietary terms. If an employer, client, coauthor, or
another party owns or controls the material, you must have their permission
before submitting it; the single statement in the pull-request template covers
this without a separate checklist.

Nobody is required to modify KINETICKK, publish private changes, or send
improvements upstream. If someone distributes the game or a covered derivative,
the GPL's notice, corresponding-source, and copyleft conditions apply to that
distribution.

## Review and project identity

Submitting work does not guarantee that it will be reviewed or merged. The
maintainer may ask for changes or close a proposal.

The GPL does not grant trademark rights. A contribution does not authorize use
of the KINETICKK name, logo, or branding for an unofficial release. See
[TRADEMARKS.md](TRADEMARKS.md).
