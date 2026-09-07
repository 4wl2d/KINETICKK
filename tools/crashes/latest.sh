#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

crash_root="${KINETICKK_CRASH_DIR:-$HOME/.kinetickk/crashes}"
if [[ ! -f "$crash_root/latest.txt" ]]; then
  printf 'No recorded KINETICKK crash in %s\n' "$crash_root" >&2
  exit 1
fi
IFS= read -r crash_report < "$crash_root/latest.txt"
if [[ ! -f "$crash_report" ]]; then
  printf 'Latest crash report is missing: %s\n' "$crash_report" >&2
  exit 1
fi
case "${1:---prompt}" in
  --path) printf '%s\n' "$crash_report" ;;
  --report) cat "$crash_report" ;;
  --prompt) cat "$(dirname "$crash_report")/agent-prompt.txt"; printf '\n' ;;
  *) printf 'Usage: %s [--prompt|--path|--report]\n' "$0" >&2; exit 2 ;;
esac
