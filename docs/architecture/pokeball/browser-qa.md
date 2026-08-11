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
- After the isolated storage precondition is installed, drive application
  behavior only through visible keyboard and pointer input. No production test
  registry, global bridge, hidden route mutation, or direct component call is
  permitted.

Local screenshots and browser traces are written beneath
`output/playwright/freeze-<short-sha>/`, which is ignored by Git. The formal
claim record names the exact freeze SHA, browser version, command result, and
observed evidence; it does not treat these transient files as product source.

### Isolated storage precondition

Reset evidence requires a controlled precondition that the product UI cannot
create. Browser automation may use page evaluation only to prepare and inspect
the fresh temporary profile on the loopback app origin:

1. load the production app once, set only the declared legacy key
   `kinetickk_progress_v2`, and reload;
2. for the partial-purge scenario, wrap that isolated page's
   `Storage.prototype.removeItem` so the first removal of exactly that key
   throws `new DOMException("QA one-shot purge failure", "SecurityError")`
   exactly once; a generic `Error` or another DOMException name is not a valid
   fixture because unclassified faults propagate;
3. after `RESET NEEDS ATTENTION` is observed, restore the original
   `removeItem` method before Retry and never affect another origin;
4. after setup, use visible Canvas input for Cancel, Confirm, and Retry; and
5. inspect the same origin's storage after each step to prove Cancel retained
   the key, Confirm wrote a valid v4 snapshot before the injected purge
   failure, and Retry removed only the declared legacy key.

This setup is test-fixture preparation and observation, not a production
control path. It must never reuse a developer profile, mutate a route, call a
component, or install an application-visible test API.

## Rendered scenario inventory

| Scenario | Visible evidence and storage assertion |
|---|---|
| fresh launch | Home renders and no reset modal is present |
| legacy reset confirmation | an isolated legacy key blocks Home with `SAVE RESET REQUIRED` |
| cancel | `CANCEL` leaves the confirmation modal blocking and deletes nothing |
| write-before-purge failure | a one-shot isolated `SecurityError` purge failure leaves a valid default v4 snapshot and renders `RESET NEEDS ATTENTION` |
| explicit retry | `RETRY PURGE` removes only the declared legacy key and returns to Home |
| seven routes | Home, Gameplay, Settings, Lab, Armory, Rebirth, and Codex each render through public keyboard/pointer input |
| Gameplay lifecycle | start, pause, exit, and restart render the corresponding accepted Session/Gameplay state |
| Settings retention | an accepted SFX preference remains visible after close and reopen |

Expected browser console noise is limited to an optional `favicon.ico` 404 and
browser WebGL debug-renderer warnings. The application JavaScript and both Wasm
assets must load with HTTP 200; an uncaught exception, failed application
asset, blank Canvas, or unexpected modal is a failure.

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
reset and storage branches in that same automated browser gate. The rendered
smoke above joins those layers and proves that the production host, Canvas
composition, visible input mapping, Session workflow, and browser storage
binding are connected end to end.
