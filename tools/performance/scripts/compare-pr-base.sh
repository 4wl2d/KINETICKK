#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

DEFAULT_SEED="731991"
CAPABILITY_MARKER="tools/performance/contracts/incremental-gate-v2.json"
ATTESTED_SOURCE_PATHS=(
    "tools/performance/harness/src/main/kotlin/kinetickk/performance/BenchmarkHarness.kt"
    "tools/performance/compare_results.py"
    "build-logic/src/main/kotlin/kinetickk/gradle/BenchmarkProvenance.kt"
    "ball/gameplay/nucleus/build.gradle.kts"
    "ball/gameplay/nucleus/src/desktopTest/kotlin/kinetickk/ball/gameplay/nucleus/performance/GameplayPerformanceBenchmark.kt"
    "ball/profile/resource/build.gradle.kts"
    "ball/profile/resource/src/desktopTest/kotlin/kinetickk/ball/profile/resource/performance/ProfilePerformanceBenchmark.kt"
)

usage() {
    cat <<'EOF'
Usage: bash tools/performance/scripts/compare-pr-base.sh --base REVISION [options]

Options:
  --base REVISION               Exact pull_request.base.sha to benchmark.
  --profile smoke|standard|deep Harness profile (default: smoke).
  --cycles COUNT                A-B-B-A cycles (defaults: smoke=1, standard=2, deep=3).
  --seed INTEGER                Shared gameplay seed (default: 731991).
  --output DIRECTORY            New result directory (default: timestamped under build/performance/results).
  --check-only                  Only report whether REVISION supports the incremental gate.
  --help                        Show this help.

Every cycle launches fresh JVMs in candidate-base-base-candidate order for both gameplay and
profile suites. The exact base is retained as a clean detached worktree. This script never resets,
cleans, removes, or commits either worktree.
EOF
}

fail() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

require_clean_worktree() {
    local worktree="$1"
    local label="$2"
    local worktree_status
    if ! worktree_status="$(git -C "$worktree" status --porcelain=v1 --untracked-files=all)"; then
        fail "Unable to inspect $label worktree status: $worktree"
    fi
    if [[ -n "$worktree_status" ]]; then
        fail "$label worktree is not clean; preserving it and stopping: $worktree"
    fi
}

script_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
repository_root="$(git -C "$script_directory" rev-parse --show-toplevel)"
comparison_script="$repository_root/tools/performance/compare_results.py"
base_revision=""
profile="smoke"
cycles=""
seed="$DEFAULT_SEED"
output_option=""
check_only="false"

while (($# > 0)); do
    case "$1" in
        --base)
            (($# >= 2)) || fail "--base requires a value"
            base_revision="$2"
            shift 2
            ;;
        --profile)
            (($# >= 2)) || fail "--profile requires a value"
            profile="$2"
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
        --check-only)
            check_only="true"
            shift
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

[[ "$base_revision" =~ ^[0-9a-f]{40,64}$ ]] || fail \
    "--base must be a full lowercase Git object ID"
git -C "$repository_root" cat-file -e "$base_revision^{commit}" 2>/dev/null || fail \
    "Base commit is not available locally: $base_revision"
resolved_base_revision="$(git -C "$repository_root" rev-parse "$base_revision^{commit}")"
[[ "$resolved_base_revision" == "$base_revision" ]] || fail \
    "Base revision resolved to $resolved_base_revision instead of $base_revision"

capability_source="$repository_root/$CAPABILITY_MARKER"
[[ -f "$capability_source" ]] || fail "Missing candidate capability marker: $capability_source"
if ! git -C "$repository_root" cat-file -e "$base_revision:$CAPABILITY_MARKER" 2>/dev/null; then
    printf 'Base %s predates incremental performance capability v2.\n' "$base_revision" >&2
    exit 3
fi
expected_capability_blob="$(git -C "$repository_root" hash-object "$capability_source")"
actual_capability_blob="$(git -C "$repository_root" rev-parse "$base_revision:$CAPABILITY_MARKER")"
if [[ "$actual_capability_blob" != "$expected_capability_blob" ]]; then
    printf 'Base %s has a different incremental performance capability v2 contract.\n' \
        "$base_revision" >&2
    exit 3
fi

missing_paths=()
drifted_paths=()
for required_path in "${ATTESTED_SOURCE_PATHS[@]}"; do
    if ! git -C "$repository_root" cat-file -e "$base_revision:$required_path" 2>/dev/null; then
        missing_paths+=("$required_path")
        continue
    fi
    candidate_blob="$(git -C "$repository_root" hash-object "$repository_root/$required_path")"
    base_blob="$(git -C "$repository_root" rev-parse "$base_revision:$required_path")"
    if [[ "$candidate_blob" != "$base_blob" ]]; then
        drifted_paths+=("$required_path")
    fi
done
if ((${#missing_paths[@]} > 0)); then
    printf 'Base %s declares capability v2 but is missing attested suite files.\n' \
        "$base_revision" >&2
    printf 'Missing: %s\n' "${missing_paths[@]}" >&2
    exit 3
fi
if ((${#drifted_paths[@]} > 0)); then
    printf 'Base %s uses different benchmark source contracts; selecting candidate-only bootstrap evidence.\n' \
        "$base_revision" >&2
    printf 'Drifted: %s\n' "${drifted_paths[@]}" >&2
    exit 3
fi
if [[ "$check_only" == "true" ]]; then
    printf 'Base %s supports the incremental performance gate.\n' "$base_revision"
    exit 0
fi

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
[[ -f "$comparison_script" ]] || fail "Missing statistical comparator: $comparison_script"

candidate_revision="$(git -C "$repository_root" rev-parse HEAD)"
[[ "$candidate_revision" != "$base_revision" ]] || fail \
    "Candidate and base revisions are identical: $candidate_revision"
require_clean_worktree "$repository_root" "Candidate"
candidate_label="$(git -C "$repository_root" branch --show-current)"
[[ -n "$candidate_label" ]] || candidate_label="pr-candidate"

if [[ -z "$output_option" ]]; then
    output_option="build/performance/results/$(date -u +%Y%m%dT%H%M%SZ)-pr-base"
elif [[ "$output_option" != /* ]]; then
    output_option="$repository_root/$output_option"
fi
mkdir -p "$output_option"
output_directory="$(CDPATH= cd -- "$output_option" && pwd -P)"
if find "$output_directory" -mindepth 1 -maxdepth 1 -print -quit | grep -q .; then
    fail "Output directory must be empty: $output_directory"
fi

base_worktree="$repository_root/build/performance/worktrees/pr-base-$base_revision"
mkdir -p "$(dirname -- "$base_worktree")"
if [[ ! -e "$base_worktree" ]]; then
    printf 'Creating detached PR base worktree at %s\n' "$base_worktree"
    git -C "$repository_root" worktree add --detach "$base_worktree" "$base_revision"
else
    [[ -f "$base_worktree/.git" ]] || fail \
        "Existing base path is not a linked Git worktree: $base_worktree"
    resolved_worktree="$(git -C "$base_worktree" rev-parse --show-toplevel)"
    [[ "$resolved_worktree" == "$base_worktree" ]] || fail \
        "Unexpected base worktree root: $resolved_worktree"
fi
actual_base_revision="$(git -C "$base_worktree" rev-parse HEAD)"
[[ "$actual_base_revision" == "$base_revision" ]] || fail \
    "Base worktree has $actual_base_revision, expected $base_revision"
if git -C "$base_worktree" symbolic-ref -q HEAD >/dev/null; then
    fail "Base worktree must remain detached at $base_revision"
fi
require_clean_worktree "$base_worktree" "Base"

runtime_prime_directory="$(mktemp -d "${TMPDIR:-/tmp}/kinetickk-pr-runtime.XXXXXX")"

prime_benchmark_runtime() {
    local worktree="$1"
    local side="$2"
    local revision="$3"
    local suite="$4"
    local task
    local scenario
    local extra_properties=()
    if [[ "$suite" == "gameplay" ]]; then
        task=":ball:gameplay:nucleus:performanceBenchmark"
        scenario="harness_control"
        extra_properties+=("-PbenchmarkSeed=$seed")
    else
        task=":ball:profile:resource:profilePerformanceBenchmark"
        scenario="profile_encode_default"
    fi
    printf 'Priming the complete %s %s runtime classpath online...\n' "$side" "$suite"
    (cd "$worktree" && ./gradlew \
        --no-daemon --no-parallel --console=plain \
        "$task" \
        -PbenchmarkProfile=smoke \
        -PbenchmarkOutput="$runtime_prime_directory/$side-$suite.json" \
        -PbenchmarkLabel="$side-runtime-prime" \
        -PbenchmarkRevision="$revision" \
        -PbenchmarkDirty=false \
        -PbenchmarkFork=1 \
        -PbenchmarkScenarios="$scenario" \
        "${extra_properties[@]}")
}

prime_benchmark_runtime "$repository_root" candidate "$candidate_revision" gameplay
prime_benchmark_runtime "$repository_root" candidate "$candidate_revision" profile
prime_benchmark_runtime "$base_worktree" base "$base_revision" gameplay
prime_benchmark_runtime "$base_worktree" base "$base_revision" profile

gate_status=0

record_comparator_status() {
    local status="$1"
    if ((status > gate_status)); then
        gate_status="$status"
    fi
}

run_benchmark() {
    local side="$1"
    local suite="$2"
    local fork_number="$3"
    local sequence_number="$4"
    local worktree
    local task
    local label
    local revision
    local suite_directory="$output_directory/$suite"
    local extra_properties=()
    if [[ "$side" == "candidate" ]]; then
        worktree="$repository_root"
        label="$candidate_label"
        revision="$candidate_revision"
    else
        worktree="$base_worktree"
        label="pr-base"
        revision="$base_revision"
    fi
    if [[ "$suite" == "gameplay" ]]; then
        task=":ball:gameplay:nucleus:performanceBenchmark"
        extra_properties+=("-PbenchmarkSeed=$seed")
    else
        task=":ball:profile:resource:profilePerformanceBenchmark"
    fi
    local sequence_label
    local fork_label
    printf -v sequence_label '%02d' "$sequence_number"
    printf -v fork_label '%02d' "$fork_number"
    local result_file="$suite_directory/$sequence_label-$side-fork-$fork_label.json"
    printf '[%s/%s] %s fork %s -> %s\n' "$suite" "$sequence_label" "$side" "$fork_label" "$result_file"
    (cd "$worktree" && ./gradlew \
        --no-daemon --no-parallel --offline --console=plain \
        "$task" \
        -PbenchmarkProfile="$profile" \
        -PbenchmarkOutput="$result_file" \
        -PbenchmarkLabel="$label" \
        -PbenchmarkRevision="$revision" \
        -PbenchmarkDirty=false \
        -PbenchmarkFork="$fork_number" \
        "${extra_properties[@]}")
}

run_suite() {
    local suite="$1"
    local semantic_contract="$2"
    local suite_directory="$output_directory/$suite"
    mkdir -p "$suite_directory"
    local sequence=0
    local cycle
    for ((cycle = 1; cycle <= cycles; cycle++)); do
        local odd_fork=$((cycle * 2 - 1))
        local even_fork=$((cycle * 2))
        sequence=$((sequence + 1))
        run_benchmark candidate "$suite" "$odd_fork" "$sequence"
        sequence=$((sequence + 1))
        run_benchmark base "$suite" "$odd_fork" "$sequence"
        sequence=$((sequence + 1))
        run_benchmark base "$suite" "$even_fork" "$sequence"
        sequence=$((sequence + 1))
        run_benchmark candidate "$suite" "$even_fork" "$sequence"
    done

    local candidate_results=("$suite_directory"/*-candidate-fork-*.json)
    local base_results=("$suite_directory"/*-base-fork-*.json)
    local expected_forks=""
    local expected_fork
    for ((expected_fork = 1; expected_fork <= cycles * 2; expected_fork++)); do
        if [[ -n "$expected_forks" ]]; then
            expected_forks+=","
        fi
        expected_forks+="$expected_fork"
    done
    local comparator_status=0
    if PYTHONDONTWRITEBYTECODE=1 python3 "$comparison_script" \
        --baseline "${base_results[@]}" \
        --candidate "${candidate_results[@]}" \
        --baseline-name "pr-base@$base_revision" \
        --candidate-name "$candidate_label@$candidate_revision" \
        --expected-baseline-revision "$base_revision" \
        --expected-candidate-revision "$candidate_revision" \
        --expected-baseline-label pr-base \
        --expected-candidate-label "$candidate_label" \
        --expected-forks "$expected_forks" \
        --require-clean-inputs \
        --semantic-contract "$semantic_contract" \
        --fail-on-regression \
        --fail-on-incomparable \
        --output-json "$suite_directory/comparison.json" \
        --output-markdown "$suite_directory/comparison.md"; then
        comparator_status=0
    else
        comparator_status=$?
    fi
    record_comparator_status "$comparator_status"
}

run_suite gameplay outcome-fingerprint
run_suite profile exact-metadata

actual_base_revision="$(git -C "$base_worktree" rev-parse HEAD)"
[[ "$actual_base_revision" == "$base_revision" ]] || fail \
    "Base worktree moved to $actual_base_revision during benchmark execution"
actual_candidate_revision="$(git -C "$repository_root" rev-parse HEAD)"
[[ "$actual_candidate_revision" == "$candidate_revision" ]] || fail \
    "Candidate worktree moved to $actual_candidate_revision during benchmark execution"
if git -C "$base_worktree" symbolic-ref -q HEAD >/dev/null; then
    fail "Base worktree attached to a branch during benchmark execution"
fi
require_clean_worktree "$base_worktree" "Base after benchmark execution"
require_clean_worktree "$repository_root" "Candidate after benchmark execution"

printf '\nIncremental PR comparison complete.\n'
printf 'Candidate: %s\n' "$candidate_revision"
printf 'Base: %s\n' "$base_revision"
printf 'Results: %s\n' "$output_directory"
printf 'Detached base worktree retained at: %s\n' "$base_worktree"
if ((gate_status != 0)); then
    printf 'Gate failed: a comparator reported a regression, semantic mismatch, or invalid evidence.\n' >&2
fi
exit "$gate_status"
