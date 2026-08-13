# Android Perfetto diagnostic

Status: **ok**.

This is a separate, overhead-bearing diagnostic run. It has no gfxinfo score or benchmark verdict and must not be merged into the physical-device benchmark sample set.

- Source: `feature/pokeball-full-refactor` at `20a3e2f78988419f5263247085a9a4b3900f4a6e`
- Serial: `R58R603CSEY`
- Device: samsung SM-A325F / API 33
- Exact APK SHA-256: `ae18ba15339c9276ccbefddb0bcdce86c39f6df9eb761b73dd76fd484c394c25`
- Perfetto config SHA-256: `6ceb820a0b6eb51c44642df9753ad333b2fa801b39e695b5f9a065ad0943289b`
- Trace: `diagnostic.perfetto-trace`, 116053354 bytes, SHA-256 `3a27694dba0d56ae5c9e75bf0588b46efa648b2c9f986fefa1e0e3dee5aef54a`

## Safety and interpretation

- The already-installed base APK had to match `--apk` byte-for-byte and be non-debuggable plus shell-profileable.
- No APK install/uninstall, app-data clear, setting write, wake/unlock key, permission grant, or consent action is performed.
- The tool process-cold-starts only this package and injects only selector-authorized gameplay taps/long presses.
- The exact remote trace temporary file was removed after its size and host bytes were verified; application data was untouched.
- Use the trace for diagnosis in Perfetto UI, never as a frame/jank verdict.
