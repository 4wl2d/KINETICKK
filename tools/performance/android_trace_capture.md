<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Android Perfetto diagnostic capture

`android_trace_capture.py` records an overhead-bearing Perfetto trace from one
physical Android device. It is deliberately separate from
`android_device_benchmark.py`: the trace run does not call `gfxinfo`, emit a
frame/jank score, or produce a performance verdict. Never merge its timings into
the benchmark sample set.

Use the trace only after a normal same-serial benchmark identifies something
worth diagnosing. Do not run both tools concurrently against the same device.

## Run

Build the non-debuggable, shell-profileable APK first. The exact APK must already
be installed on the selected unlocked device; this tool never installs it.

```bash
./gradlew :app:shared:assembleBenchmark

python3 tools/performance/android_trace_capture.py \
  --apk app/shared/build/outputs/apk/benchmark/app-shared-benchmark.apk \
  --serial NVYXIJQGTOMVNZZH \
  --output build/performance/android-trace/realme-diagnostic \
  --label feature/pokeball-full-refactor
```

`--serial` is mandatory and singular. This prevents a diagnostic request from
silently tracing the whole lab matrix. The default trace duration is 45 seconds;
`--duration-millis` accepts 1,000–60,000 ms and must leave at least one second
after `--perfetto-startup-settle-millis`. The output directory must be empty.
An exclusive host lock keyed by the serial prevents two tool processes from
entering the preflight/start window together.

The APK, gameplay flow, and Perfetto template are each read once through a
single file descriptor, checked for stable inode/size/mtime during that read,
and copied into the run directory. All parsing, execution, and hashes use those
snapshots rather than reopening a possibly rebuilt source path.

The tool fails unless all of these identity facts are observed:

- local `aapt2` decodes the supplied APK manifest;
- the manifest package equals `--package`;
- the APK is non-debuggable and declares
  `<profileable android:shell="true">`;
- the installed `base.apk`, streamed back through adb, has the exact same
  SHA-256 as `--apk`;
- `pm path` reports that base as the only installed APK (split installs are a
  different effective artifact and are rejected);
- `dumpsys package` does not contradict the manifest's profileable status;
- the platform tracer is the readable `/system/bin/perfetto` binary.

After the diagnostic session—even when the session itself fails—the tool reads
`pm path`, the complete installed APK bytes, and package identity again. A
successful report requires the pre/post path, SHA-256, size, version,
debuggable, and profileable facts to match the snapshotted APK. This bounds the
trace against concurrent package replacement by another adb process.

There is no install fallback. A mismatch stops before tracing so that a trace can
never be attributed to the wrong binary.

## Trace protocol

The normal gameplay flow is reused with the same setup boundary. Home → Gameplay
runs before Perfetto starts. The measurement portion then runs under Perfetto and
the process remains alive until the configured trace naturally completes.

The trace executor intentionally captures a fresh UIAutomator hierarchy before
every tap or long press, even when the benchmark flow requests
`reusePreviousHierarchy`. That extra work is acceptable in a diagnostic trace
and prevents stale coordinates from becoming input authority. A tap/swipe adb
command is accepted by the command guard only inside the semantic-selector
authorization scope.

Any visible UI node owned by a package other than the expected game package is
a hard stop before input. This catches localized and previously unknown OEM
package-installers, permission controllers, Play Protect, security, or consent
UI without relying on English text. The tool never chooses a scan, install
override, permission, privacy agreement, or similar prompt. Sensitive consent
text is blocked regardless of package attribution, and a package-less node is
blocked whenever it is meaningful or actionable.

The default config template is
[`android_trace_perfetto.pbtxt.in`](android_trace_perfetto.pbtxt.in). It captures
scheduler wake/switch activity, CPU frequency/idle state, Binder activity,
Android `gfx`/`view`/`wm`/`input`/`dalvik` atrace categories, process stats,
package metadata, and track events. Exactly one `{{PACKAGE_NAME}}` and one
`{{DURATION_MILLIS}}` placeholder are required. The materialized config is saved
verbatim and hashed before capture. Buffers are flushed to the owned trace file
periodically so the early gameplay interaction is not overwritten while the
fixed-duration session completes; that I/O is another reason the run is
diagnostic-only.

Perfetto runs inside one foreground adb shell with the text config supplied over
stdin. This is compatible with the lab's API 30–35 platform binaries; API 30–32
do not expose `--background-wait`, and API 30 does not expose `--version`.

Before startup the tool records `perfetto --query`. It begins selector input only
after all of these fail-closed readiness checks hold: active sessions are exactly
one, started sessions are exactly one, exactly one
new `perfetto` PID is alive, the owned remote trace file exists, and an additional
configurable settle period has passed. This proves the session started but, unlike
newer `--background-wait`, it
does not claim that every data source separately acknowledged startup; the
weaker readiness strength is explicit in the report. Both legacy API 30–32 query
format and modern API 33–35 format are parsed strictly.

The baseline probe must report zero active and zero started Perfetto sessions.
A detached/service-owned session active at that probe is rejected even when
there is no `perfetto` CLI PID. Readiness then proves this run's exact `0/0 →
1/1` transition, while the final probe proves restoration to `0/0` and absence
of the attributed PID.

PID liveness is checked by exact membership in `pidof perfetto`. The tool does
not use `kill -0`, whose handling differs between Android shell/toybox builds;
`kill -TERM` is reserved for the already-attributed PID during owned cleanup.

The tool verifies the tracer PID, foreground adb process, and at least one
original application PID remain continuous when the flow ends; a crash followed
by a package restart is rejected. It then waits for natural process/channel completion. Before reading or
removing the remote trace it records a final `perfetto --query` and requires the
owned PID to be absent, active sessions to return to zero, and started sessions
to return to zero. (`started` is current service state, not a monotonic lifetime
counter.) These are endpoint/current-state probes, not continuous observation:
a brief third-party detached session that both starts and finishes between
probes is theoretically unobservable. The trace therefore remains
diagnostic-only and must not be treated as proof of a contention-free benchmark
environment. The tool then verifies
remote and downloaded byte counts, enforces a minimum trace size, hashes the host
file, and removes only its exact owned temporary path:

```text
/data/misc/perfetto-traces/kinetickk-<16 lowercase hex>.perfetto-trace
```

The owned trace-file cleanup is not application-data clearing. No app files,
preferences, databases, caches, compiled code, or unrelated device files are
removed.

## Non-mutation and verdict contract

The adb command boundary rejects all operations outside the diagnostic protocol,
including:

- APK install/uninstall and `pm clear`;
- setting writes, `setprop`, display overrides, and navigation/rotation changes;
- wake/unlock key events and arbitrary text/key injection;
- input not immediately authorized by a fresh semantic selector match;
- arbitrary process signals or file removal;
- every `dumpsys gfxinfo`/framestats call.

The only intentional app-state effects are `am force-stop` for a process-cold
start, launching the declared activity, and the selector-authorized gameplay
flow. Persisted app data is preserved. The display must already be awake and
unlocked; otherwise capture fails without attempting to change it.

The report records these facts as constants:

```text
separateFromGfxinfoBenchmark = true
traceOverheadExpected        = true
eligibleForGfxinfoVerdict    = false
collectsGfxinfo              = false
```

## Evidence

The output root contains:

- `android-trace-capture.json`, validated by
  [`android_trace_capture.schema.json`](android_trace_capture.schema.json);
- `android-trace-capture.md`;
- the exact source flow, flow schema, config template, materialized config, APK
  manifest dump, and their hashes;
- exact Git/dirty-state identity, retained input snapshots, pre/post installed
  APK identity and hashes, adb/aapt2 hashes
  and versions, host environment, full live device environment, the imported
  benchmark-helper hash, and the device Perfetto binary/help/version-probe
  evidence (version is nullable on API 30);
- raw startup, selector hierarchies/events, pre/post display/battery/thermal
  snapshots, final UI hierarchy, and `diagnostic.perfetto-trace`.

The remote name includes a cryptographic nonce, ownership is acquired only after
an absence preflight, and cleanup is limited to that exact regex-validated path.
Every post-start failure closes/reaps the foreground adb session and re-queries
the tracing service before cleanup. If closure cannot be proven, the tool fails
and deliberately leaves the remote file in place instead of unlinking a
potentially live trace. `traceCleanupEvidence` reports the exact owned path and
observed state (`removed`, `retained-unclosed`, or `cleanup-failed`); the protocol
field describes the success policy and is not used as a claim that cleanup
already happened.
Startup failures also retain query-before/query-ready/query-last plus foreground
stdout/stderr as hashed text artifacts, so a device/config rejection remains
diagnosable after safe cleanup.

The trace report is fail-closed. Missing identity, unavailable fields, an OEM
prompt, low battery/power mode, unacceptable thermal status, selector ambiguity,
an early tracer/app exit, a short/mismatched trace, or failed owned-file cleanup
returns non-zero while retaining all host evidence collected so far.

Open `diagnostic.perfetto-trace` in the
[Perfetto UI](https://ui.perfetto.dev/) for diagnosis. The capture mechanics and
foreground/config/query behavior follows the
[Perfetto command-line reference](https://perfetto.dev/docs/reference/perfetto-cli).

## Tests

The tests are device-independent and do not invoke adb:

```bash
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=tools/performance \
  python3 tools/performance/test_android_trace_capture.py
```

They validate config materialization, profileable identity gates, command
allowlisting, consent detection even over visible app nodes, fresh-hierarchy
input authorization, trace size/hash/cleanup evidence, JSON Schema validity, and
the absence of gfxinfo/framestats from the trace session.
