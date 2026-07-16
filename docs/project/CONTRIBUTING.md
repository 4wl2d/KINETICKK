<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Contributing to KINETICKK

Bug reports, ideas, documentation, tests, fixes, and new features are welcome.
The goal is to keep the public game useful for learning while preserving a
clear copyright record and the option to ship official store builds.

## Before opening a pull request

1. Discuss large features in an issue before writing them.
2. Build the project and run `./gradlew desktopTest`.
3. Keep each change focused and explain its purpose.
4. Do not submit code, art, audio, text, or other material unless you have the
   right to license it on the terms below.
5. Disclose copied, generated, or adapted material and record its source and
   license. Do not submit confidential material or output whose rights are
   unclear.

New project files must include `SPDX-License-Identifier: GPL-3.0-or-later` and
an accurate `SPDX-FileCopyrightText` line. For a copyrightable change to an
existing file, add your own accurate copyright line without deleting earlier
authors' notices. Mechanical or trivial edits do not always create new
copyright.

## Developer Certificate of Origin

Every commit in a pull request must contain a `Signed-off-by` line. Add it with:

```bash
git commit -s
```

The sign-off certifies the
[Developer Certificate of Origin 1.1](https://developercertificate.org/): you
created the contribution or are allowed to submit it under the license stated
in the files, and you understand that the contribution and sign-off are public.

The DCO does not transfer copyright and does not grant the project a right to
relicense your work. That is why the CLA below is also required.

## Contributor License Agreement

Before any copyrightable contribution is merged, every copyright holder must
sign the [KINETICKK Contributor License Agreement](CONTRIBUTOR_LICENSE_AGREEMENT.md).
If an employer, client, or other entity may own the work, that rights holder
must also give the required permission.

The CLA leaves copyright with the contributor. It gives Vladislav Tomilov the
right to keep the contribution under GPL-3.0-or-later and to license official
store or platform builds on other terms. This matters because some platform
SDKs cannot be combined safely with GPL code without extra permission.

A pull-request checkbox or GitHub sign-off is not a substitute for the signed
CLA. Arrange a verifiable signature with the maintainer before merge. Signed
agreements must be stored privately; do not commit personal addresses or other
private data to this repository.

## License of accepted work

Accepted contributions are included in the public project under
GPL-3.0-or-later. Unless a file clearly says otherwise, its preferred source
form, tests, docs, and project-made assets use that license.

The GPL allows other people to use, change, and redistribute accepted work. A
distributed derivative must comply with the GPL, including its notice,
source-code, and copyleft terms.

## Review and project identity

Submitting work does not guarantee that it will be reviewed or merged. The
maintainer may ask for changes or close a proposal.

The GPL does not grant trademark rights. A contribution does not authorize use
of the KINETICKK name, logo, or branding for an unofficial release. See
[TRADEMARKS.md](TRADEMARKS.md).
