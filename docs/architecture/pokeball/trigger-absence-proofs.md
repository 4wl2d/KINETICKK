<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Pokeball trigger-absence proofs

These proofs bind the KINETICKK four-authority Android/Desktop/Web application
at implementation freeze `5c3496a3af3523758513177561e116217306cf8a` to
closed, versioned inventories. They cover only triggers that are absent in the
declared effective profile. The legacy-purge and reset-write semantic-retry
families are PRESENT under PBA-24 and are therefore governed by the frozen
policy rather than claimed absent here.

## TA-01 — Actors

<!-- pokeball-trigger-absence-proof
schemaVersion=1
id=TA-01-actors
triggerClass=risk-triggered
triggerAnchor=Core §11.2 / PBA-44
exactScopeAndEffectiveProfile=KINETICKK four-authority Android/Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=RiskInventoryRef:docs/architecture/pokeball/applicability.md
inventoryRevisionsOrDigests=git:5c3496a3af3523758513177561e116217306cf8a;sha256:3ec297c3b03dcf8363d1639872c75930c4bdb44517ec431b32aa4a35cb401684
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
exactScopeAndEffectiveProfile=KINETICKK four-authority Android/Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=RiskInventoryRef:docs/architecture/pokeball/applicability.md
inventoryRevisionsOrDigests=git:5c3496a3af3523758513177561e116217306cf8a;sha256:3ec297c3b03dcf8363d1639872c75930c4bdb44517ec431b32aa4a35cb401684
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
exactScopeAndEffectiveProfile=KINETICKK four-authority Android/Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=PathInventoryRef:docs/architecture/pokeball/assembly.md
inventoryRevisionsOrDigests=git:5c3496a3af3523758513177561e116217306cf8a;sha256:6ace696e70ded523f5462899917a13a7a81582354c84441fda56c231c434c1dc
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
exactScopeAndEffectiveProfile=KINETICKK four-authority Android/Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=PathInventoryRef:docs/architecture/pokeball/assembly.md
inventoryRevisionsOrDigests=git:5c3496a3af3523758513177561e116217306cf8a;sha256:6ace696e70ded523f5462899917a13a7a81582354c84441fda56c231c434c1dc
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
exactScopeAndEffectiveProfile=KINETICKK four-authority Android/Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=PathInventoryRef:docs/architecture/pokeball/resolved-manifest.json
inventoryRevisionsOrDigests=git:5c3496a3af3523758513177561e116217306cf8a;sha256:17f708873e34ebb0f3a93ed9703b29f68f1f4db3669cf1130d01f02df8bc63c2
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
exactScopeAndEffectiveProfile=KINETICKK four-authority Android/Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=DenialByConstructionRef:docs/architecture/pokeball/policy.md
inventoryRevisionsOrDigests=git:5c3496a3af3523758513177561e116217306cf8a;sha256:96d76eac99e62b06dfd9595f77552b9ff89e06d6a6e05084d822ff1135a56b48
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
exactScopeAndEffectiveProfile=KINETICKK four-authority Android/Desktop/Web application | Inline+Transient+InProcess+Standard+Static
inventoryEvidence=DenialByConstructionRef:docs/architecture/pokeball/policy.md
inventoryRevisionsOrDigests=git:5c3496a3af3523758513177561e116217306cf8a;sha256:96d76eac99e62b06dfd9595f77552b9ff89e06d6a6e05084d822ff1135a56b48
evaluatedPredicate=Absent iff the closed inventory contains no durable outbox, journal, or status materializer
conclusion=Absent
evidenceOwner=KINETICKK project
invalidationConditions=scope-change|profile-change|version-change|inventory-change|digest-change|unresolved-reference|conflicting-evidence
-->
