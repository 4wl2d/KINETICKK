#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

MAIN_REVISION="fedceb8e2d9009d805d70249e10c77e424447945"
SUPPORTED_SCENARIOS=(
    profile_encode_default
    profile_decode_default
    profile_roundtrip_default
    profile_encode_logical_maximum
    profile_decode_logical_maximum
    profile_roundtrip_logical_maximum
)

usage() {
    cat <<'EOF'
Usage: bash tools/performance/scripts/compare-main-refactor-profile.sh [options]

Options:
  --profile smoke|standard|deep  Harness profile (default: standard).
  --scenario NAME               Select one scenario; may be repeated.
  --scenarios A,B,C             Select a comma-separated scenario list.
  --cycles COUNT                A-B-B-A cycles (defaults: smoke=1, standard=2, deep=3).
  --output DIRECTORY            New result directory (default: timestamped under build/performance/results).
  --help                        Show this help.

A is the current feature worktree and B is pinned main. Each cycle launches fresh JVM forks in
feature-main-main-feature order. Both sides use their branch-native profile wire format over the
same logical fixture. The detached main worktree is retained at build/performance/worktrees/main.
EOF
}

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

script_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
repository_root="$(git -C "$script_directory" rev-parse --show-toplevel)"
profile="standard"
cycles=""
scenario_csv=""
output_option=""

while (($# > 0)); do
    case "$1" in
        --profile)
            (($# >= 2)) || fail "--profile requires a value"
            profile="$2"
            shift 2
            ;;
        --scenario)
            (($# >= 2)) || fail "--scenario requires a value"
            if [[ -z "$scenario_csv" ]]; then
                scenario_csv="$2"
            else
                scenario_csv="$scenario_csv,$2"
            fi
            shift 2
            ;;
        --scenarios)
            (($# >= 2)) || fail "--scenarios requires a value"
            [[ -z "$scenario_csv" ]] || fail "Use either --scenario or --scenarios, not both"
            scenario_csv="$2"
            shift 2
            ;;
        --cycles)
            (($# >= 2)) || fail "--cycles requires a value"
            cycles="$2"
            shift 2
            ;;
        --output)
            (($# >= 2)) || fail "--output requires a value"
            output_option="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            fail "Unknown option: $1"
            ;;
    esac
done

case "$profile" in
    smoke)
        [[ -n "$cycles" ]] || cycles=1
        ;;
    standard)
        [[ -n "$cycles" ]] || cycles=2
        ;;
    deep)
        [[ -n "$cycles" ]] || cycles=3
        ;;
    *)
        fail "--profile must be smoke, standard, or deep"
        ;;
esac
[[ "$cycles" =~ ^[1-9][0-9]*$ ]] || fail "--cycles must be a positive integer"

is_supported_scenario() {
    local requested="$1"
    local supported
    for supported in "${SUPPORTED_SCENARIOS[@]}"; do
        [[ "$requested" == "$supported" ]] && return 0
    done
    return 1
}

if [[ -z "$scenario_csv" ]]; then
    scenario_csv="$(IFS=,; printf '%s' "${SUPPORTED_SCENARIOS[*]}")"
else
    IFS=',' read -r -a requested_scenarios <<< "$scenario_csv"
    ((${#requested_scenarios[@]} > 0)) || fail "At least one scenario is required"
    for requested_scenario in "${requested_scenarios[@]}"; do
        [[ "$requested_scenario" =~ ^[a-z0-9_]+$ ]] || fail \
            "Invalid scenario name: $requested_scenario"
        is_supported_scenario "$requested_scenario" || fail \
            "Unsupported profile comparison scenario: $requested_scenario"
    done
fi

if [[ -z "$output_option" ]]; then
    output_option="build/performance/results/$(date -u +%Y%m%dT%H%M%SZ)-profile-main-vs-refactor"
elif [[ "$output_option" != /* ]]; then
    output_option="$repository_root/$output_option"
fi
mkdir -p "$output_option"
output_directory="$(CDPATH= cd -- "$output_option" && pwd -P)"
if find "$output_directory" -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
    fail "Output directory must be empty: $output_directory"
fi

main_worktree="$repository_root/build/performance/worktrees/main"
main_worktree_parent="$(dirname -- "$main_worktree")"
compatibility_source="$repository_root/tools/performance/compat/main/$MAIN_REVISION"
harness_source="$repository_root/tools/performance/harness"
aggregate_script="$repository_root/tools/performance/scripts/aggregate-profile-comparison.py"

[[ -d "$compatibility_source" ]] || fail "Missing main compatibility overlay: $compatibility_source"
[[ -d "$harness_source" ]] || fail "Missing shared benchmark harness: $harness_source"
[[ -f "$aggregate_script" ]] || fail "Missing profile comparison aggregator: $aggregate_script"

mkdir -p "$main_worktree_parent"
if [[ ! -e "$main_worktree" ]]; then
    printf 'Creating detached main worktree at %s\n' "$main_worktree"
    git -C "$repository_root" worktree add --detach "$main_worktree" "$MAIN_REVISION"
else
    [[ -f "$main_worktree/.git" ]] || fail "Existing main path is not a Git worktree: $main_worktree"
    resolved_worktree="$(git -C "$main_worktree" rev-parse --show-toplevel)"
    [[ "$resolved_worktree" == "$main_worktree" ]] || fail \
        "Unexpected worktree root: $resolved_worktree"
    actual_main_revision="$(git -C "$main_worktree" rev-parse HEAD)"
    [[ "$actual_main_revision" == "$MAIN_REVISION" ]] || fail \
        "Main worktree has $actual_main_revision, expected $MAIN_REVISION"
fi

check_main_worktree_changes() {
    local status_line
    local changed_file
    while IFS= read -r status_line; do
        [[ -n "$status_line" ]] || continue
        changed_file="${status_line:3}"
        case "$changed_file" in
            build.gradle.kts|tools/performance/harness/*|tools/performance/compat/main/"$MAIN_REVISION"/*)
                ;;
            *)
                fail "Unexpected change in managed main worktree: $status_line"
                ;;
        esac
    done < <(git -C "$main_worktree" status --porcelain=v1 --untracked-files=all)
}

copy_overlay_tree() {
    local source_directory="$1"
    local destination_directory="$2"
    if [[ -e "$destination_directory" ]]; then
        [[ -d "$destination_directory" ]] || fail \
            "Overlay destination is not a directory: $destination_directory"
        diff -qr "$source_directory" "$destination_directory" >/dev/null || fail \
            "Managed overlay differs in $destination_directory; preserving it and stopping"
    else
        mkdir -p "$(dirname -- "$destination_directory")"
        cp -R "$source_directory" "$destination_directory"
    fi
}

check_main_worktree_changes
original_build_hash="$(git -C "$repository_root" rev-parse "$MAIN_REVISION:build.gradle.kts")"
overlay_build_hash="$(git hash-object "$compatibility_source/root.build.gradle.kts")"
worktree_build_hash="$(git hash-object "$main_worktree/build.gradle.kts")"
if [[ "$worktree_build_hash" != "$original_build_hash" && \
      "$worktree_build_hash" != "$overlay_build_hash" ]]; then
    fail "Managed main build.gradle.kts differs from both pinned main and the compatibility overlay"
fi

copy_overlay_tree "$harness_source" "$main_worktree/tools/performance/harness"
copy_overlay_tree \
    "$compatibility_source" \
    "$main_worktree/tools/performance/compat/main/$MAIN_REVISION"
cp "$compatibility_source/root.build.gradle.kts" "$main_worktree/build.gradle.kts"
git -C "$main_worktree" diff --check
check_main_worktree_changes

feature_revision="$(git -C "$repository_root" rev-parse HEAD)"
feature_branch="$(git -C "$repository_root" branch --show-current)"
[[ -n "$feature_branch" ]] || feature_branch="detached-feature"
feature_dirty="false"
if [[ -n "$(git -C "$repository_root" status --porcelain=v1 --untracked-files=all)" ]]; then
    feature_dirty="true"
fi

printf 'Preparing feature profile benchmark compilation...\n'
(cd "$repository_root" && ./gradlew \
    --no-daemon --console=plain \
    :ball:profile:resource:compileTestKotlinDesktop)
printf 'Preparing pinned main profile benchmark compilation...\n'
(cd "$main_worktree" && ./gradlew \
    --no-daemon --console=plain \
    compileTestKotlinDesktop)

sequence=0
run_feature() {
    local fork_number="$1"
    sequence=$((sequence + 1))
    local sequence_label
    local fork_label
    printf -v sequence_label '%02d' "$sequence"
    printf -v fork_label '%02d' "$fork_number"
    local result_file="$output_directory/$sequence_label-feature-fork-$fork_label.json"
    printf '[%s] A feature profile fork %s -> %s\n' \
        "$sequence_label" "$fork_label" "$result_file"
    (cd "$repository_root" && ./gradlew \
        --no-daemon --offline --console=plain \
        :ball:profile:resource:profilePerformanceBenchmark \
        -PbenchmarkProfile="$profile" \
        -PbenchmarkOutput="$result_file" \
        -PbenchmarkLabel="$feature_branch" \
        -PbenchmarkRevision="$feature_revision" \
        -PbenchmarkDirty="$feature_dirty" \
        -PbenchmarkFork="$fork_number" \
        -PbenchmarkScenarios="$scenario_csv")
}

run_main() {
    local fork_number="$1"
    sequence=$((sequence + 1))
    local sequence_label
    local fork_label
    printf -v sequence_label '%02d' "$sequence"
    printf -v fork_label '%02d' "$fork_number"
    local result_file="$output_directory/$sequence_label-main-fork-$fork_label.json"
    printf '[%s] B main profile fork %s -> %s\n' \
        "$sequence_label" "$fork_label" "$result_file"
    (cd "$main_worktree" && ./gradlew \
        --no-daemon --offline --console=plain \
        profilePerformanceBenchmark \
        -PbenchmarkProfile="$profile" \
        -PbenchmarkOutput="$result_file" \
        -PbenchmarkLabel="main" \
        -PbenchmarkRevision="$MAIN_REVISION" \
        -PbenchmarkDirty="false" \
        -PbenchmarkFork="$fork_number" \
        -PbenchmarkScenarios="$scenario_csv")
}

for ((cycle = 1; cycle <= cycles; cycle++)); do
    odd_fork=$((cycle * 2 - 1))
    even_fork=$((cycle * 2))
    run_feature "$odd_fork"
    run_main "$odd_fork"
    run_main "$even_fork"
    run_feature "$even_fork"
done

PYTHONDONTWRITEBYTECODE=1 python3 "$aggregate_script" \
    --input "$output_directory" \
    --output-json "$output_directory/comparison.json" \
    --output-markdown "$output_directory/comparison.md"

printf '\nProfile comparison complete.\n'
printf 'Results: %s\n' "$output_directory"
printf 'Report: %s\n' "$output_directory/comparison.md"
printf 'Detached main worktree retained at: %s\n' "$main_worktree"
