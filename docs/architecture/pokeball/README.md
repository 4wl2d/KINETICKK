<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK Pokeball architecture record

This directory records the project-owned decisions and evidence for the full
KINETICKK migration to Pokeball Core `1.4.0-draft`. Typed Kotlin source remains
the authority for protocols and behavior. A generated `resolved-manifest.json`
will be a checked projection, not a second source of truth.

The record is split by ownership:

- `baseline.md` freezes the starting behavior, source provenance, and allowed
  product delta.
- `authority-map.md` assigns every business fact and writer to one authority.
- `policy.md` selects execution profiles, bounds, and project-local mechanisms.
- `assembly.md` owns the finite cross-authority graph and route bindings.
- `applicability.md` records triggered Core concerns, exclusions, and the
  absence-proof scopes that may be used only by the final conformance claim.

The Pokeball Core and Agent Pack are deliberately not copied into this
repository. Verification consumes the exact external immutable snapshot named
in `baseline.md` and fails closed if its commit or digests differ.
