<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Pokeball conformance record

## Formal bounded claim

This project self-attestation is not external certification. It binds the exact
implementation freeze, the pinned Pokeball Core and Agent Pack inputs, the
declared four-authority Desktop/Web scope, and the evidence summarized below.

<!-- pokeball-conformance
schemaVersion=1
attestationStatus=PASS
implementationFreezeSha=803b0c20d459119d907ba2a2876f7ed702ee1455
productTreeSha256=5a69dbd3fbe8713fac28d9749263c301747001cd95ab002d020c62162a8cb47f
coreCommit=de9ef7384795680c836d5e6c2c9b394286058670
coreSha256=d7792cb6adfaf9d7e3cf0c59bcc40b1158200bfcd0496661d3293035917f352c
agentPackRevision=12
agentPackSha256=1332fbc4ccbc55112ea87fa902437e6bb27f043a67663ebbe6e11f3e4239089d
policySha256=135a8d045bfa7e8185f4a0f977d97c1161f388b5ce5ad59aed0625b19f6f6a2c
assemblySha256=ebf16a632e183a83ef1ecd4827a2140aa2a271d1780b7f60996d0a84af4c39d4
triggerAbsenceProofsSha256=2cc5407ebb96f3a2fc0473c6623b789af6213d0b1efbf969c413cc09686fc0ac
claimWording=KINETICKK on implementation SHA 803b0c20d459119d907ba2a2876f7ed702ee1455 conforms within the declared scope to Pokeball Core 1.4.0-draft at de9ef7384795680c836d5e6c2c9b394286058670
claimBoundary=Implementation freeze 803b0c20d459119d907ba2a2876f7ed702ee1455 bound only to Pokeball Core de9ef7384795680c836d5e6c2c9b394286058670
claimScope=Profile GameplayRun AppSession ContentCatalog four-authority application across Desktop and Web
claimBinding=Inline+Transient+InProcess+Standard+Static
claimMechanism=Pure Nucleus decisions, bounded accepted frames, closed Resource outcomes, same-stack result delivery, commit-before-dispatch, and static Assembly binding
claimAssumptions=Pinned same-build single-process source and Core inputs with trusted platform brokers constrained to exact classified operations
claimRetention=Implementation freeze and attestation commits retained in Git with CI logs and non-normative rendered QA evidence under project policy
claimEvidence=RG-01..RG-10; verifyArchitecture; verifyPokeballArchitecture; desktopTest; wasmJsBrowserTest; wasmJsBrowserDistribution; rendered browser QA
claimEnvironment=JDK:17.0.20+8;Gradle:8.14.3;Kotlin:2.3.20;Node:25.0.0;Chromium:150.0.7871.24
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

- Implementation freeze: `803b0c20d459119d907ba2a2876f7ed702ee1455`.
- Freeze product-tree digest:
  `5a69dbd3fbe8713fac28d9749263c301747001cd95ab002d020c62162a8cb47f`.
- Pokeball Core: `1.4.0-draft`, canonical draft at
  `de9ef7384795680c836d5e6c2c9b394286058670`; ordered 25-file digest
  `d7792cb6adfaf9d7e3cf0c59bcc40b1158200bfcd0496661d3293035917f352c`.
- Agent Pack: revision 12, 25 Markdown files, digest
  `1332fbc4ccbc55112ea87fa902437e6bb27f043a67663ebbe6e11f3e4239089d`.
- Resolved application: 22 leaf modules, 77 declared project edges, six
  direct-control edges, 14 read routes, 10 command mappings, two Flow
  participation pairs, four Application Surfaces, 15 output executors, seven UI
  destinations, 81 executable bounds, and 12 mechanically derived bounds.
- Effective profile for ContentCatalog, Profile, GameplayRun, and AppSession:
  `Inline + Transient + InProcess + Standard + Static`.
- Project extensions: **NONE**.
- WaiverRecords and waiver effects: **NONE**.

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
| RG-01 Scope | PASS | Four authorities and the Desktop/Web boundary are closed by policy, Assembly, applicability, manifest, and baseline inventories. |
| RG-02 Protocols | PASS | Canonical target-owned ModuleCommand/ModuleResult envelopes, source-owned result Pulses, and distinct preaccept carriers are closed and correlated. |
| RG-03 Ownership | PASS | Each mutable authority has one sovereign writer; ContentCatalog is immutable and query-only. |
| RG-04 Decisions | PASS | Candidate decisions are pure, revisions publish once before bounded ordered output dispatch, and one causal scope is preserved across same-stack hops. |
| RG-05 Resources | PASS | Closed provider outcomes preserve failure-before-execution versus possible execution; unclassified faults propagate and fabricate no Fact/result. |
| RG-06 Composition | PASS | Compile and direct-control DAGs are acyclic; 14 reads and 10 commands bind exact typed routes without caller-owned rich re-export. |
| RG-07 Capabilities | PASS | Ambient Storage, Preferences, AudioContext, and AudioSystem authority is confined to private App brokers that inject exact-key or typed-tone capabilities. |
| RG-08 Profiles | PASS | All four authorities resolve the declared effective profile; bounded mechanical Audio projection does not become semantic delivery. |
| RG-09 Limits | PASS | All executable collections, work, outputs, ingress, completion queues, causal depth, fanout, and derived copies have enforcement plus boundary evidence. |
| RG-10 Integrity | PASS | Pinned provenance/digests, strict schemas, DCO, manifest drift, frozen-tree identity, and docs-only attestation rules fail closed. |

Failed or partial units: **NONE**. Waiver effects: **NONE**.

## Executed evidence

All commands below ran from the clean freeze tree with Temurin `17.0.20+8`,
Gradle `8.14.3`, project Kotlin plugin `2.3.20`, Kotlin/Wasm Node `25.0.0`,
Google Chrome for Testing `150.0.7871.24`, and the pinned sibling Pokeball
snapshot.

1. `verifyArchitecture verifyPokeballArchitecture verifyPokeballConformance desktopTest compileTestKotlinWasmJs wasmJsBrowserTest wasmJsBrowserDistribution --rerun-tasks --no-parallel --no-daemon`
   passed 467 executed tasks. Conformance ran in prerequisite-no-claim mode at
   the implementation freeze and produced the recorded product-tree digest.
   Refreshed XML contained 1,282 tests across 183 suites with zero failures,
   errors, or skips.
2. Rendered production QA at `output/playwright/freeze-803b0c2/` used a fresh
   isolated profile, loopback origin, exact Chrome, and visible Canvas input.
   Sixteen captures prove Home, Gameplay, Settings, Lab, Armory, Rebirth,
   Codex; start, pause, exit, restart; retained SFX OFF; and reset Confirmation,
   Cancel, v4-write-before-classified-`SecurityError`, Needs Attention, and
   explicit Retry. Initial HTML, JavaScript, and both Wasm assets returned HTTP
   200. Console output contained only six WebGL debug-renderer warning entries
   and two optional favicon 404 entries.
3. Three independent read-only audits partitioned RG-01 through RG-10, then
   rechecked the exact final freeze. They found no failed or partial unit.

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
