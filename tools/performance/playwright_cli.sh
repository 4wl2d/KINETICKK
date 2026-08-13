#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

if ! command -v npx >/dev/null 2>&1; then
    printf 'error: npx is required for Playwright CLI\n' >&2
    exit 1
fi

has_session_flag="false"
for argument in "$@"; do
    case "$argument" in
        --session|--session=*|-s=*)
            has_session_flag="true"
            break
            ;;
    esac
done

command=(npx --yes --package @playwright/cli@0.1.18 playwright-cli)
if [[ "$has_session_flag" != "true" && -n "${PLAYWRIGHT_CLI_SESSION:-}" ]]; then
    command+=(--session "$PLAYWRIGHT_CLI_SESSION")
fi
command+=("$@")
exec "${command[@]}"
