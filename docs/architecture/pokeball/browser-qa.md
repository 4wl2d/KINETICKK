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
- Never attach to an existing Chrome session, reuse its storage, or use the
  platform Preferences provider.
- Drive only visible keyboard and pointer input. No production test registry,
  global bridge, hidden route mutation, or direct component call is permitted.

Local screenshots and browser traces are written beneath
`output/playwright/phase8-rendered-qa/`, which is ignored by Git. The formal
claim record names the exact freeze SHA, browser version, command result, and
observed evidence; it does not treat these transient files as product source.

## Rendered scenario inventory

| Scenario | Visible evidence and storage assertion |
|---|---|
| fresh launch | Home renders and no reset modal is present |
| legacy reset confirmation | an isolated legacy key blocks Home with `SAVE RESET REQUIRED` |
| cancel | `CANCEL` leaves the confirmation modal blocking and deletes nothing |
| write-before-purge failure | a one-shot isolated purge failure leaves a valid default v4 snapshot and renders `RESET NEEDS ATTENTION` |
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

`BrowserRuntimeQaTest` exercises the same reset and workflow branches with
isolated in-memory providers inside the Wasm test runtime. The rendered smoke
above proves that production host, Canvas composition, input mapping, Session
workflow, and browser storage binding are connected end to end.
