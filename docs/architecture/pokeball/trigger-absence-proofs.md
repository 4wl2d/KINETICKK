<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Pokeball trigger-absence proofs

These proofs bind the KINETICKK four-authority Desktop/Web application at
implementation freeze `4f4b285dc8c8f36f786391b2968bf6e92df5e903` to closed,
versioned inventories. They cover only triggers that are absent in the declared
effective profile. The legacy-purge and reset-write semantic-retry families are
PRESENT under PBA-24 and are therefore governed by the frozen policy rather
than claimed absent here.

## TA-01 — Actors

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-01-actors
triggerClass=risk-triggered
triggerAnchor=Core §11.2 / PBA-44
exactScopeAndEffectiveProfile=KINETICKK four-authority Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=RiskInventoryRef:docs/architecture/pokeball/applicability.md
inventoryRevisionsOrDigests=git:4f4b285dc8c8f36f786391b2968bf6e92df5e903;sha256:59413b6215e1c36e99637a9165093530d9882a689eb807add1cae68a05ad7ea5
evaluatedPredicate=Absent iff the closed inventory contains no actor-dependent semantic path
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->

## TA-02 — Authentication, grants, and secrets

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-02-auth-grants-secrets
triggerClass=risk-triggered
triggerAnchor=Core §11.2–§11.3, §11.7, §11.9 / PBA-33, PBA-36, PBA-44
exactScopeAndEffectiveProfile=KINETICKK four-authority Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=RiskInventoryRef:docs/architecture/pokeball/applicability.md
inventoryRevisionsOrDigests=git:4f4b285dc8c8f36f786391b2968bf6e92df5e903;sha256:59413b6215e1c36e99637a9165093530d9882a689eb807add1cae68a05ad7ea5
evaluatedPredicate=Absent iff the closed inventory contains no authentication, grant, or secret semantic path
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->

## TA-03 — Network, remote, and IPC paths

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-03-network-remote
triggerClass=path-triggered
triggerAnchor=Core §10.2, §12.7–§12.8
exactScopeAndEffectiveProfile=KINETICKK four-authority Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=PathInventoryRef:docs/architecture/pokeball/assembly.md
inventoryRevisionsOrDigests=git:4f4b285dc8c8f36f786391b2968bf6e92df5e903;sha256:ebf16a632e183a83ef1ecd4827a2140aa2a271d1780b7f60996d0a84af4c39d4
evaluatedPredicate=Absent iff the closed inventory contains no network, remote, or IPC semantic path
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->

## TA-04 — Detached asynchronous semantic delivery

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-04-detached-async
triggerClass=path-triggered
triggerAnchor=Core §9.1–§9.2, §9.12–§9.13, §12.2–§12.3
exactScopeAndEffectiveProfile=KINETICKK four-authority Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=PathInventoryRef:docs/architecture/pokeball/assembly.md
inventoryRevisionsOrDigests=git:4f4b285dc8c8f36f786391b2968bf6e92df5e903;sha256:ebf16a632e183a83ef1ecd4827a2140aa2a271d1780b7f60996d0a84af4c39d4
evaluatedPredicate=Absent iff the closed inventory contains no detached asynchronous semantic delivery path
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->

## TA-06 — Dynamic registry and wildcard routing

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-06-dynamic-registry
triggerClass=path-triggered
triggerAnchor=Core §10.4, §10.7 / PBA-27
exactScopeAndEffectiveProfile=KINETICKK four-authority Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=PathInventoryRef:docs/architecture/pokeball/resolved-manifest.json
inventoryRevisionsOrDigests=git:4f4b285dc8c8f36f786391b2968bf6e92df5e903;sha256:c17ce0dd7f515fa07741de8fb40441e4d92a386b18ea56860c9ab1bc1ca64af7
evaluatedPredicate=Absent iff the closed inventory contains no dynamic registry or wildcard route
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->

## TA-07 — Process and security isolation

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-07-isolation
triggerClass=path-triggered
triggerAnchor=Core §11.8, §12.7–§12.9
exactScopeAndEffectiveProfile=KINETICKK four-authority Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=DenialByConstructionRef:docs/architecture/pokeball/policy.md
inventoryRevisionsOrDigests=git:4f4b285dc8c8f36f786391b2968bf6e92df5e903;sha256:135a8d045bfa7e8185f4a0f977d97c1161f388b5ce5ad59aed0625b19f6f6a2c
evaluatedPredicate=Absent iff the closed inventory contains no process or security isolation boundary
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->

## TA-08 — Durable outbox, journal, and status materializer

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-08-durable-outbox
triggerClass=path-triggered
triggerAnchor=Core §9.11, §9.13, §12.4–§12.6 / PBA-42
exactScopeAndEffectiveProfile=KINETICKK four-authority Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=DenialByConstructionRef:docs/architecture/pokeball/policy.md
inventoryRevisionsOrDigests=git:4f4b285dc8c8f36f786391b2968bf6e92df5e903;sha256:135a8d045bfa7e8185f4a0f977d97c1161f388b5ce5ad59aed0625b19f6f6a2c
evaluatedPredicate=Absent iff the closed inventory contains no durable outbox, journal, or status materializer
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->
