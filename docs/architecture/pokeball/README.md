<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# KINETICKK Pokeball architecture record

This directory records the project-owned decisions and evidence for the full
KINETICKK migration to Pokeball Core `1.4.0-draft`. Typed Kotlin source remains
the authority for protocols and behavior. The generated
[`resolved-manifest.json`](resolved-manifest.json) is a checked projection, not
a second source of truth.

Before KINETICKK `1.0.0`, persisted Profile data has one current schema, not a
version family. The authoritative value is `ProfileSnapshot`; its Profile data
is the `ProfileSnapshot.profile` field, and persistence is accessed only through
`readSnapshot`/`writeSnapshot`. Platform composition owns the one
physical storage authority for that value. Historical keys and payload shapes
are outside the supported contract: they are neither read nor removed.

The record is split by ownership:

- `baseline.md` freezes the starting behavior, source provenance, and allowed
  product delta.
- `authority-map.md` assigns every business fact and writer to one authority.
- `policy.md` selects execution profiles, bounds, and project-local mechanisms.
- `assembly.md` owns the finite cross-authority graph and route bindings.
- `applicability.md` records triggered Core concerns, exclusions, and the
  absence-proof scopes that may be used only by the final conformance claim.
- `browser-qa.md` defines the isolated production-Wasm rendered smoke and its
  relationship to the automated Chromium suite.
- `resolved-manifest.json` deterministically projects modules, compile and
  direct-control edges, Application Surfaces, routes, and selected bounds.

The verified physical graph contains exactly 23 leaf modules. `app:android` is
the mechanical Android application host and has exactly one production project
edge, `implementation -> :app:shared`. The shared KMP leaf retains Compose
Assembly and Android/Desktop/Web capability bindings, so the host split adds no
business authority or semantic direct-control edge.

The Pokeball Core and Agent Pack are deliberately not copied into this
repository. Verification consumes the exact external immutable snapshot named
in `baseline.md` and fails closed if its commit or digests differ. Supply that
checkout through `-PpokeballSnapshotDir=/absolute/path`, through
`POKEBALL_SNAPSHOT_DIR`, or as sibling `../Pokeball`, in that precedence order.

`verifyPokeballConformance` has two explicit modes. Before a claim record
exists, it verifies prerequisites and reports that no formal claim has been
issued. Once `conformance-record.md` exists, it validates the docs-only
attestation commit, its implementation-freeze parent and tree digest, the
closed review-gate inventory, environment and evidence records, and the exact
TriggerAbsenceProof set.

There is currently no conformance record. The previous freeze-bound
self-attestation and its trigger-absence proofs were invalidated by the
current-only persistence change and removed; a later claim requires a new
implementation freeze and newly generated evidence.
