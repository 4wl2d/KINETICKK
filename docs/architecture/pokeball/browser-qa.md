<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Isolated browser QA

This procedure validates the production Wasm distribution in a real Chromium
without reading or changing a developer's live browser profile or save. It is
additional rendered evidence; the Gradle `wasmJsBrowserTest` suite remains the
repeatable automated browser gate.

## Isolation and build

- Build `:app:web:wasmJsBrowserDistribution` from the candidate tree.
- Serve only `app/web/build/dist/wasmJs/productionExecutable` on loopback.
- Launch the exact declared Chrome for Testing binary with a fresh temporary
  browser profile and a new loopback origin.
- Never attach to an existing Chrome session, reuse its storage, or reuse a
  live app platform-storage broker.
- After an isolated storage precondition is installed, drive application
  behavior only through visible keyboard and pointer input. No production test
  registry, global bridge, hidden route mutation, or direct component call is
  permitted.

Local screenshots and browser traces are written beneath
`output/playwright/freeze-<short-sha>/`, which is ignored by Git. A future
formal claim record must name its exact freeze SHA, browser version, command
result, and observed evidence; transient files are not product source.

## Isolated storage preconditions

Browser automation may use page evaluation only to prepare and inspect the
fresh temporary profile on the loopback origin:

1. current-value scenarios may set only `kinetickk_profile` before reload;
2. incompatible-current scenarios set a malformed, non-canonical, or otherwise
   rejected payload at that exact key and then prove the application reaches
   Home with default Profile state;
3. provider-read-failure scenarios wrap `Storage.prototype.getItem` so a read
   of exactly `kinetickk_profile` throws
   `new DOMException("QA profile read failure", "SecurityError")`, then prove
   the blocking `PROFILE UNAVAILABLE` UI is rendered and consumes input;
4. old-key isolation scenarios may set `kinetickk_progress_v2`,
   `kinetickk_matter`, or another unrelated sentinel, then prove launch and
   current-snapshot writes leave every sentinel byte-for-byte unchanged; and
5. restore every temporary prototype wrapper before leaving the scenario and
   never affect another origin.

This setup is test-fixture preparation and observation, not a production
control path. It must never reuse a developer profile, mutate a route, call a
component, or install an application-visible test API. There is no production
reset, purge, migration, or retry control to exercise.

## Rendered scenario inventory

| Scenario | Visible evidence and storage assertion |
|---|---|
| fresh launch | Home renders and no blocking UI is present |
| valid current snapshot | `kinetickk_profile` is decoded as `ProfileSnapshot` and its state is visible |
| incompatible current payload | Home renders with default Profile state; no old or unrelated key is changed |
| provider read failure | `PROFILE UNAVAILABLE` blocks the app, pointer input is consumed, and storage is unchanged |
| old-key isolation | old and unrelated sentinels are ignored and remain untouched across launch and a current Profile write |
| seven routes | Home, Gameplay, Settings, Lab, Armory, Rebirth, and Codex each render through public keyboard/pointer input |
| Gameplay lifecycle | start, pause, exit, and restart render the corresponding accepted Session/Gameplay state |
| Settings retention | an accepted SFX preference remains visible after close and reopen |

Expected browser console noise is limited to an optional `favicon.ico` 404 and
browser WebGL debug-renderer warnings. The application JavaScript and both Wasm
assets must load with HTTP 200; an uncaught exception, failed application
asset, blank Canvas, or unexpected blocking UI is a failure except in the
declared provider-read-failure scenario.

## Repeatable browser gate

```text
CHROME_BIN=/absolute/path/to/chrome ./gradlew \
  compileTestKotlinWasmJs \
  wasmJsBrowserTest \
  wasmJsBrowserDistribution \
  --rerun-tasks
```

`BrowserRuntimeQaTest` exercises the seven-route and core Session lifecycle
branches with isolated in-memory participants inside the Wasm test runtime.
Profile Resource, Session component, and App platform-broker tests exercise the
current snapshot, default fallback, blocked read, and old-key non-mutation
branches in that same automated browser gate. The rendered smoke joins those
layers and proves that the production host, Canvas composition, visible input
mapping, Session workflow, and Web storage binding are connected end to end.
