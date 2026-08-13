#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

LITERAL_MAIN_REVISION="fedceb8e2d9009d805d70249e10c77e424447945"
ORIGIN_MAIN_REVISION="a0762dd40df50a06f48f31f2916960ea04992dc2"
DEFAULT_SEED="731991"
SUPPORTED_SCENARIOS=(
    harness_control
    state_initialization
    run_start
    copy_idle
    copy_capacity
    render_model_idle
    render_model_capacity
    reducer_frame_60hz_idle
    reducer_frame_100ms_idle
    fixed_step_collision_miss
    fixed_step_collision_hit
    nucleus_frame_60hz_idle
    nucleus_frame_60hz_capacity
    nucleus_pointer_move_idle
    nucleus_viewport_change_idle
    nucleus_frame_paused
    published_frame_60hz_idle
    published_pointer_move_idle
    published_frame_paused
    trace_2s_60hz
)

usage() {
    cat <<'EOF'
Usage: bash tools/performance/scripts/compare-main-refactor.sh [options]

Options:
  --baseline literal-main|origin-main
                                Pinned baseline (default: literal-main).
  --profile smoke|standard|deep  Harness profile (default: standard).
  --scenario NAME               Select one scenario; may be repeated.
  --scenarios A,B,C             Select a comma-separated scenario list.
  --cycles COUNT                A-B-B-A cycles (defaults: smoke=1, standard=2, deep=3).
  --seed INTEGER                Shared deterministic seed (default: 731991).
  --output DIRECTORY            New result directory (default: timestamped under build/performance/results).
  --help                        Show this help.

A is the current feature worktree and B is the selected pinned baseline. Each cycle launches fresh
JVM forks in feature-baseline-baseline-feature order. Detached baseline worktrees are retained at
build/performance/worktrees/{main,origin-main} and are never committed or removed by this script.
EOF
}

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

script_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
repository_root="$(git -C "$script_directory" rev-parse --show-toplevel)"
profile="standard"
baseline="literal-main"
cycles=""
seed="$DEFAULT_SEED"
scenario_csv=""
output_option=""

while (($# > 0)); do
    case "$1" in
        --baseline)
            (($# >= 2)) || fail "--baseline requires a value"
            baseline="$2"
            shift 2
            ;;
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
        --seed)
            (($# >= 2)) || fail "--seed requires a value"
            seed="$2"
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

if [[ -f "$repository_root/tools/performance/contracts/incremental-gate-v2.json" ]]; then
    fail "This archived raw-schema-v1 comparison cannot run with the strict v2 harness; use compare-pr-base.sh for v2-to-v2 comparisons or the committed historical reports"
fi

case "$baseline" in
    literal-main)
        baseline_revision="$LITERAL_MAIN_REVISION"
        baseline_label="main"
        baseline_worktree_name="main"
        compatibility_relative="main/$baseline_revision"
        overlay_build_file="root.build.gradle.kts"
        baseline_build_relative="build.gradle.kts"
        baseline_compile_task="compileTestKotlinDesktop"
        baseline_benchmark_task="performanceBenchmark"
        ;;
    origin-main)
        baseline_revision="$ORIGIN_MAIN_REVISION"
        baseline_label="origin/main"
        baseline_worktree_name="origin-main"
        compatibility_relative="origin-main/$baseline_revision"
        overlay_build_file="feature-gameplay-domain.build.gradle.kts"
        baseline_build_relative="feature/gameplay/domain/build.gradle.kts"
        baseline_compile_task=":feature:gameplay:domain:compileTestKotlinDesktop"
        baseline_benchmark_task=":feature:gameplay:domain:performanceBenchmark"
        ;;
    *)
        fail "--baseline must be literal-main or origin-main"
        ;;
esac

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
[[ "$seed" =~ ^-?[0-9]+$ ]] || fail "--seed must be an integer"

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
        [[ "$requested_scenario" =~ ^[a-z0-9_]+$ ]] || fail "Invalid scenario name: $requested_scenario"
        is_supported_scenario "$requested_scenario" || fail "Unsupported comparison scenario: $requested_scenario"
    done
fi

if [[ -z "$output_option" ]]; then
    output_option="build/performance/results/$(date -u +%Y%m%dT%H%M%SZ)-${baseline}-vs-refactor"
elif [[ "$output_option" != /* ]]; then
    output_option="$repository_root/$output_option"
fi
mkdir -p "$output_option"
output_directory="$(CDPATH= cd -- "$output_option" && pwd -P)"
if find "$output_directory" -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
    fail "Output directory must be empty: $output_directory"
fi

baseline_worktree="$repository_root/build/performance/worktrees/$baseline_worktree_name"
baseline_worktree_parent="$(dirname -- "$baseline_worktree")"
compatibility_source="$repository_root/tools/performance/compat/$compatibility_relative"
harness_source="$repository_root/tools/performance/harness"
aggregate_script="$repository_root/tools/performance/scripts/aggregate-gameplay-comparison.py"
comparison_script="$repository_root/tools/performance/compare_results.py"

[[ -d "$compatibility_source" ]] || fail "Missing baseline compatibility overlay: $compatibility_source"
[[ -f "$compatibility_source/$overlay_build_file" ]] || fail \
    "Missing baseline build overlay: $compatibility_source/$overlay_build_file"
[[ -d "$harness_source" ]] || fail "Missing shared benchmark harness: $harness_source"
[[ -f "$aggregate_script" ]] || fail "Missing comparison aggregator: $aggregate_script"
[[ -f "$comparison_script" ]] || fail "Missing statistical comparator: $comparison_script"

mkdir -p "$baseline_worktree_parent"
if [[ ! -e "$baseline_worktree" ]]; then
    printf 'Creating detached %s worktree at %s\n' "$baseline_label" "$baseline_worktree"
    git -C "$repository_root" worktree add --detach "$baseline_worktree" "$baseline_revision"
else
    [[ -f "$baseline_worktree/.git" ]] || fail \
        "Existing baseline path is not a Git worktree: $baseline_worktree"
    resolved_worktree="$(git -C "$baseline_worktree" rev-parse --show-toplevel)"
    [[ "$resolved_worktree" == "$baseline_worktree" ]] || fail \
        "Unexpected worktree root: $resolved_worktree"
    actual_baseline_revision="$(git -C "$baseline_worktree" rev-parse HEAD)"
    [[ "$actual_baseline_revision" == "$baseline_revision" ]] || fail \
        "Baseline worktree has $actual_baseline_revision, expected $baseline_revision"
fi

check_baseline_worktree_changes() {
    local status_line
    local changed_file
    while IFS= read -r status_line; do
        [[ -n "$status_line" ]] || continue
        changed_file="${status_line:3}"
        if [[ "$changed_file" == "$baseline_build_relative" ||
            "$changed_file" == tools/performance/harness/* ||
            "$changed_file" == tools/performance/compat/"$compatibility_relative"/* ]]; then
            continue
        fi
        fail "Unexpected change in managed baseline worktree: $status_line"
    done < <(git -C "$baseline_worktree" status --porcelain=v1 --untracked-files=all)
}

copy_overlay_tree() {
    local source_directory="$1"
    local destination_directory="$2"
    if [[ -e "$destination_directory" ]]; then
        [[ -d "$destination_directory" ]] || fail "Overlay destination is not a directory: $destination_directory"
        diff -qr "$source_directory" "$destination_directory" >/dev/null || fail \
            "Managed overlay differs in $destination_directory; preserving it and stopping"
    else
        mkdir -p "$(dirname -- "$destination_directory")"
        cp -R "$source_directory" "$destination_directory"
    fi
}

check_baseline_worktree_changes
original_build_hash="$(git -C "$repository_root" rev-parse \
    "$baseline_revision:$baseline_build_relative")"
overlay_build_hash="$(git hash-object "$compatibility_source/$overlay_build_file")"
worktree_build_hash="$(git hash-object "$baseline_worktree/$baseline_build_relative")"
if [[ "$worktree_build_hash" != "$original_build_hash" && "$worktree_build_hash" != "$overlay_build_hash" ]]; then
    fail "Managed baseline build file differs from both the pinned revision and compatibility overlay"
fi

copy_overlay_tree "$harness_source" "$baseline_worktree/tools/performance/harness"
copy_overlay_tree \
    "$compatibility_source" \
    "$baseline_worktree/tools/performance/compat/$compatibility_relative"
cp "$compatibility_source/$overlay_build_file" "$baseline_worktree/$baseline_build_relative"
git -C "$baseline_worktree" diff --check
check_baseline_worktree_changes

feature_revision="$(git -C "$repository_root" rev-parse HEAD)"
feature_branch="$(git -C "$repository_root" branch --show-current)"
[[ -n "$feature_branch" ]] || feature_branch="detached-feature"
feature_dirty="false"
if [[ -n "$(git -C "$repository_root" status --porcelain=v1 --untracked-files=all)" ]]; then
    feature_dirty="true"
fi

runtime_prime_directory="$(mktemp -d "${TMPDIR:-/tmp}/kinetickk-gameplay-runtime.XXXXXX")"
printf 'Priming the complete feature benchmark runtime classpath online...\n'
(cd "$repository_root" && ./gradlew \
    --no-daemon --no-parallel --console=plain \
    :ball:gameplay:nucleus:performanceBenchmark \
    -PbenchmarkProfile=smoke \
    -PbenchmarkOutput="$runtime_prime_directory/feature.json" \
    -PbenchmarkLabel="$feature_branch-runtime-prime" \
    -PbenchmarkRevision="$feature_revision" \
    -PbenchmarkDirty="$feature_dirty" \
    -PbenchmarkFork=1 \
    -PbenchmarkSeed="$seed" \
    -PbenchmarkScenarios=harness_control)
printf 'Priming the complete pinned %s benchmark runtime classpath online...\n' "$baseline_label"
(cd "$baseline_worktree" && ./gradlew \
    --no-daemon --no-parallel --console=plain \
    "$baseline_benchmark_task" \
    -PbenchmarkProfile=smoke \
    -PbenchmarkOutput="$runtime_prime_directory/baseline.json" \
    -PbenchmarkLabel="$baseline_label-runtime-prime" \
    -PbenchmarkRevision="$baseline_revision" \
    -PbenchmarkDirty=false \
    -PbenchmarkFork=1 \
    -PbenchmarkSeed="$seed" \
    -PbenchmarkScenarios=harness_control)

sequence=0
run_feature() {
    local fork_number="$1"
    sequence=$((sequence + 1))
    local sequence_label
    local fork_label
    printf -v sequence_label '%02d' "$sequence"
    printf -v fork_label '%02d' "$fork_number"
    local result_file="$output_directory/$sequence_label-feature-fork-$fork_label.json"
    printf '[%s] A feature fork %s -> %s\n' "$sequence_label" "$fork_label" "$result_file"
    (cd "$repository_root" && ./gradlew \
        --no-daemon --no-parallel --offline --console=plain \
        :ball:gameplay:nucleus:performanceBenchmark \
        -PbenchmarkProfile="$profile" \
        -PbenchmarkOutput="$result_file" \
        -PbenchmarkLabel="$feature_branch" \
        -PbenchmarkRevision="$feature_revision" \
        -PbenchmarkDirty="$feature_dirty" \
        -PbenchmarkFork="$fork_number" \
        -PbenchmarkSeed="$seed" \
        -PbenchmarkScenarios="$scenario_csv")
}

run_baseline() {
    local fork_number="$1"
    sequence=$((sequence + 1))
    local sequence_label
    local fork_label
    printf -v sequence_label '%02d' "$sequence"
    printf -v fork_label '%02d' "$fork_number"
    local result_file="$output_directory/$sequence_label-main-fork-$fork_label.json"
    printf '[%s] B %s fork %s -> %s\n' "$sequence_label" "$baseline_label" "$fork_label" "$result_file"
    (cd "$baseline_worktree" && ./gradlew \
        --no-daemon --no-parallel --offline --console=plain \
        "$baseline_benchmark_task" \
        -PbenchmarkProfile="$profile" \
        -PbenchmarkOutput="$result_file" \
        -PbenchmarkLabel="$baseline_label" \
        -PbenchmarkRevision="$baseline_revision" \
        -PbenchmarkDirty="false" \
        -PbenchmarkFork="$fork_number" \
        -PbenchmarkSeed="$seed" \
        -PbenchmarkScenarios="$scenario_csv")
}

for ((cycle = 1; cycle <= cycles; cycle++)); do
    odd_fork=$((cycle * 2 - 1))
    even_fork=$((cycle * 2))
    run_feature "$odd_fork"
    run_baseline "$odd_fork"
    run_baseline "$even_fork"
    run_feature "$even_fork"
done

PYTHONDONTWRITEBYTECODE=1 python3 "$aggregate_script" \
    --input "$output_directory" \
    --output-json "$output_directory/pooled-comparison.json" \
    --output-markdown "$output_directory/pooled-comparison.md" \
    --baseline-kind "$baseline"

feature_results=("$output_directory"/*-feature-fork-*.json)
baseline_results=("$output_directory"/*-main-fork-*.json)
PYTHONDONTWRITEBYTECODE=1 python3 "$comparison_script" \
    --baseline "${baseline_results[@]}" \
    --candidate "${feature_results[@]}" \
    --baseline-name "$baseline_label@$baseline_revision" \
    --candidate-name "$feature_branch@$feature_revision" \
    --output-json "$output_directory/comparison.json" \
    --output-markdown "$output_directory/comparison.md"

printf '\nComparison complete.\n'
printf 'Results: %s\n' "$output_directory"
printf 'Report: %s\n' "$output_directory/comparison.md"
printf 'Detached %s worktree retained at: %s\n' "$baseline_label" "$baseline_worktree"
