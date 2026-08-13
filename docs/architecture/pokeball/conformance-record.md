<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Pokeball conformance record

## Formal bounded claim

This project self-attestation is not external certification. It binds the exact
implementation freeze, the pinned Pokeball Core and Agent Pack inputs, the
declared four-authority Android/Desktop/Web scope, and the evidence summarized
below.

<!-- pokeball-conformance
schemaVersion=1
attestationStatus=PASS
implementationFreezeSha=0e74db5395b601421152f877abe6187f74a69f39
productTreeSha256=513ae2731d9a1216170205ad70a7b6447f6dbc614f2f24e068353bf138f9d560
coreCommit=de9ef7384795680c836d5e6c2c9b394286058670
coreSha256=d7792cb6adfaf9d7e3cf0c59bcc40b1158200bfcd0496661d3293035917f352c
agentPackRevision=12
agentPackSha256=1332fbc4ccbc55112ea87fa902437e6bb27f043a67663ebbe6e11f3e4239089d
policySha256=96d76eac99e62b06dfd9595f77552b9ff89e06d6a6e05084d822ff1135a56b48
assemblySha256=6ace696e70ded523f5462899917a13a7a81582354c84441fda56c231c434c1dc
triggerAbsenceProofsSha256=fef859f485bf99a7fd0dd03a75763122f41cc7625e9e84f70e0349bafdcd0425
claimWording=KINETICKK on implementation SHA 0e74db5395b601421152f877abe6187f74a69f39 conforms within the declared scope to Pokeball Core 1.4.0-draft at de9ef7384795680c836d5e6c2c9b394286058670
claimBoundary=Implementation freeze 0e74db5395b601421152f877abe6187f74a69f39 bound only to Pokeball Core de9ef7384795680c836d5e6c2c9b394286058670
claimScope=Profile GameplayRun AppSession ContentCatalog four-authority application across Android Desktop and Web
claimBinding=Inline+Transient+InProcess+Standard+Static
claimMechanism=Pure Nucleus decisions, bounded accepted frames, closed Resource outcomes, same-stack result delivery, commit-before-dispatch, and static Assembly binding
claimAssumptions=Pinned same-build single-process source and Core inputs with trusted platform brokers constrained to exact classified operations
claimRetention=Implementation freeze and attestation commits retained in Git with CI logs and exact local artifact evidence under project policy
claimEvidence=RG-01..RG-10; verifyArchitecture; verifyPokeballArchitecture; desktopTest; wasmJsBrowserTest; wasmJsBrowserDistribution; Android assemblies; physical-device instrumentation; artifact inventory
claimEnvironment=JDK:21.0.11+10;Gradle:9.7.0;Kotlin:2.4.20-RC;Node:25.0.0;Chromium:152.0.7977.8
claimNonGuarantees=crash-atomic persistence; exactly-once; eventual delivery; cross-device storage; external audio/storage providers
waiverEffects=NONE
failedOrPartialUnits=NONE
rg01=PASS
rg02=PASS
rg03=PASS
rg04=PASS
rg05=PASS
rg06=PASS
rg07=PASS
rg08=PASS
rg09=PASS
rg10=PASS
-->

## Frozen inputs and topology

- Implementation freeze: `0e74db5395b601421152f877abe6187f74a69f39`.
- Freeze product-tree digest:
  `513ae2731d9a1216170205ad70a7b6447f6dbc614f2f24e068353bf138f9d560`.
- Pokeball Core: `1.4.0-draft`, canonical draft at
  `de9ef7384795680c836d5e6c2c9b394286058670`; ordered 25-file digest
  `d7792cb6adfaf9d7e3cf0c59bcc40b1158200bfcd0496661d3293035917f352c`.
- Agent Pack: revision 12, 25 Markdown files, digest
  `1332fbc4ccbc55112ea87fa902437e6bb27f043a67663ebbe6e11f3e4239089d`.
- Resolved application: 23 leaf modules, 78 declared project edges, six
  direct-control edges, 14 read routes, 10 command mappings, two Flow
  participation pairs, four Application Surfaces, 15 output executors, seven UI
  destinations, 85 executable bounds, and 14 mechanically derived bounds.
- Effective profile for ContentCatalog, Profile, GameplayRun, and AppSession:
  `Inline + Transient + InProcess + Standard + Static`.
- Project extensions: **NONE**.
- WaiverRecords and waiver effects: **NONE**.

The `app:android` host is mechanical AppAssembly infrastructure. Its complete
production project-dependency set is the single edge to `app:shared`; it adds
no business authority, semantic route, output route, read route, or delivery
hop. The same four authorities are bound in process on Android, Desktop, and
Web.

PBA-24 is PRESENT for two explicit user-owned semantic-retry families.
`SessionInteractionPulse.ResetRetryRequested` creates one fresh
`ProfileModuleCommand.RetryLegacyPurge` and one purge attempt. After
`ResetWriteRejected`, `ResetWriteResourceFailure`, or
`ResetWriteOutcomeUnknown`, a later explicit `ResetConfirmed` creates one fresh
`ConfirmLegacyReset` command, semantic handle, Profile revision, effect
reference, and Profile Resource write invocation. Local encode rejection causes
zero provider mutation calls; otherwise there is at most one. AppSession is the
primary policy owner, Profile is the target, prior OutcomeUnknown ambiguity
remains historical, and transport, executor, SDK/provider, reconciliation, and
same-identity retries are disabled. Neither family is represented by a
trigger-absence proof.

## Independent review-gate verdicts

| Gate | Verdict | Frozen evidence |
|---|---|---|
| RG-01 Scope | PASS | Four authorities and the Android/Desktop/Web boundary are closed by policy, Assembly, applicability, manifest, and baseline inventories. |
| RG-02 Protocol | PASS | Canonical target-owned ModuleCommand/ModuleResult envelopes, source-owned result Pulses, and distinct preaccept carriers are closed and correlated. |
| RG-03 Authority | PASS | Each mutable authority has one sovereign writer; ContentCatalog is immutable and query-only. |
| RG-04 Decision | PASS | Candidate decisions are pure, revisions publish once before bounded ordered output dispatch, and one causal scope is preserved across same-stack hops. |
| RG-05 Async | PASS | Inline result delivery and the two user-owned reset-retry families are closed; provider outcomes preserve failure-before-execution versus possible execution, while unclassified faults propagate without fabricating Fact/result evidence. |
| RG-06 Composition | PASS | Compile and direct-control DAGs are acyclic; 14 reads and 10 commands bind exact typed routes without caller-owned rich re-export. |
| RG-07 Security | PASS | Trusted-boundary and actor-independent paths are closed; ambient Storage, Preferences, AudioContext, and AudioSystem authority is confined to private App brokers that inject exact-key or typed-tone capabilities. |
| RG-08 Profiles | PASS | All four authorities resolve the declared effective profile; bounded mechanical Audio projection does not become semantic delivery. |
| RG-09 Limits | PASS | All executable collections, work, outputs, ingress, completion queues, causal depth, fanout, and derived copies have enforcement plus boundary evidence. |
| RG-10 Integrity | PASS | Pinned provenance/digests, strict schemas, DCO, manifest drift, frozen-tree identity, and docs-only attestation rules fail closed. |

Failed or partial units: **NONE**. Waiver effects: **NONE**.

## Executed evidence

All commands below ran against clean implementation freeze
`0e74db5395b601421152f877abe6187f74a69f39` with Temurin `21.0.11+10`,
Gradle `9.7.0`, project Kotlin plugin `2.4.20-RC`, Kotlin/Wasm Node `25.0.0`,
Chrome for Testing `152.0.7977.8`, and the pinned sibling Pokeball snapshot.

1. `verifyArchitecture verifyPokeballArchitecture verifyPokeballConformance desktopTest` with the strict isolated profile, `--isolated-projects`, and configuration-cache problems set to fail passed. Architecture closed at 23 leaf modules and 78 declared edges; the conformance task ran in prerequisite-no-claim mode and produced the product-tree digest recorded above.
2. The strict isolated Android graph assembled the debug, minified benchmark, application UI-test, and separate shared capability-test APKs. Exact repeats reused configuration cache. Static delivery checks verified application identities, non-debuggable and shell-profileable benchmark manifest, non-empty R8 mapping/usage, v2 signing, 16 KiB alignment, resource-task isolation, and LinkBuffer-only Compose runtime selection.
3. The normal Web profile ran every registered `wasmJsBrowserTest` and built the production distribution. The exact repeat reused configuration cache in 788 ms. Schema-v3 artifact inventory recorded 16 files, 11,571,597 raw bytes, and 4,264,900 deterministic gzip bytes. The distribution contains exactly one byte-identical 2,533,670-byte application Wasm and one byte-identical 8,640,316-byte Skiko Wasm; both are referenced by production JavaScript, with no source maps or `sourceMappingURL` directives.
4. The exact locally built application and test APK hashes were installed on OnePlus CPH2411/API 35, realme RMX2002/API 30, and Samsung SM-A325F/API 33. Each device passed the two application UI tests and three shared platform-capability tests. Initial UI timeouts on two sleeping devices disappeared after a normal wake/unlock and unchanged-test rerun; no data clear, permission override, or installation bypass was used. Redmi Note 9 Pro/API 31 is not claimed because MIUI rejected normal USB installation.
5. Build-logic tests, 140 performance-tool tests, shell syntax, workflow YAML, manifest drift, wrapper checksum, artifact provenance, and independent read-only audits passed without weakening any test, outcome witness, or architecture bound.

The checked trigger-absence document contains exactly seven proofs in the
required order: TA-01, TA-02, TA-03, TA-04, TA-06, TA-07, and TA-08. Each proof
binds a closed inventory blob at the implementation freeze by Git revision and
SHA-256 digest, states an explicit `Absent iff` predicate, and names
invalidation conditions.

## Boundaries and non-guarantees

This claim is limited to the declared repository scope, versions, profile,
mechanism, and evidence. It is a project self-attestation, not external
certification. It does not guarantee crash-atomic persistence, exactly-once
behavior, eventual delivery, cross-device storage, or behavior of external
audio/storage providers. Changes to scope, profile, versions, inventories,
digests, policy, Assembly, platform brokers, evidence, or any unresolved or
conflicting reference invalidate the claim and require a new implementation
freeze and attestation.
