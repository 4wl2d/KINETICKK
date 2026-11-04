#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

usage() {
    cat <<'HELP'
Usage: bash tools/build/variant.sh debug|release desktop|android|web build|run|package|test [Gradle arguments...]

Debug and Release use separate outputs: build-debug/ and build/ in each module.
Desktop build creates a runnable JAR; package creates an application with a compact JDK.
Android build creates an APK (Release unsigned); package creates an AAB.
Android run signs Release with the local developer key, installs and launches it.
Select an Android device with ANDROID_SERIAL when multiple devices are connected.
Web build/package creates a browser distribution; run starts the Gradle development server.
HELP
}

if [[ ${1:-} == --help || ${1:-} == -h ]]; then usage; exit 0; fi
if [[ $# -lt 3 ]]; then usage >&2; exit 2; fi
game_variant=$1
game_platform=$2
game_action=$3
shift 3
case "$game_variant" in debug|release) ;; *) usage >&2; exit 2 ;; esac
case "$game_platform" in desktop|android|web) ;; *) usage >&2; exit 2 ;; esac
for argument in "$@"; do
    case "$argument" in
        -Pkinetickk.variant*|kinetickk.variant=*|--project-prop*kinetickk.variant*)
            printf '%s\n' 'Select the variant with the first argument, not a second Gradle property.' >&2
            exit 2 ;;
    esac
done

repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd -- "$repository_root"
gradle_arguments=("-Pkinetickk.variant=$game_variant" --console=plain)
variant_title=Release
build_directory=build
if [[ $game_variant == debug ]]; then variant_title=Debug; build_directory=build-debug; fi

case "$game_platform:$game_action" in
    desktop:build)
        desktop_task=packageReleaseUberJarForCurrentOS
        if [[ $game_variant == debug ]]; then desktop_task=packageUberJarForCurrentOS; fi
        exec ./gradlew ":app:desktop:$desktop_task" "${gradle_arguments[@]}" "$@" ;;
    desktop:package)
        desktop_task=createReleaseDistributable
        if [[ $game_variant == debug ]]; then desktop_task=createDistributable; fi
        exec ./gradlew ":app:desktop:$desktop_task" "${gradle_arguments[@]}" "$@" ;;
    desktop:run)
        desktop_task=runRelease
        if [[ $game_variant == debug ]]; then desktop_task=run; fi
        exec ./gradlew ":app:desktop:$desktop_task" "${gradle_arguments[@]}" "$@" ;;
    desktop:test)
        exec ./gradlew desktopTest "${gradle_arguments[@]}" "$@" ;;
    android:build)
        exec ./gradlew ":app:android:assemble$variant_title" "${gradle_arguments[@]}" "$@" ;;
    android:package)
        exec ./gradlew ":app:android:bundle$variant_title" "${gradle_arguments[@]}" "$@" ;;
    android:test)
        exec ./gradlew testAndroidHostTest "${gradle_arguments[@]}" "$@" ;;
    android:run)
        command -v adb >/dev/null || { printf '%s\n' 'adb is required for Android run.' >&2; exit 1; }
        ./gradlew ":app:android:assemble$variant_title" "${gradle_arguments[@]}" \
            -Pkinetickk.localSigning=true "$@"
        apk="app/android/$build_directory/outputs/apk/$game_variant/app-android-$game_variant.apk"
        test -f "$apk"
        adb_command=(adb)
        if [[ -n ${ANDROID_SERIAL:-} ]]; then adb_command+=(-s "$ANDROID_SERIAL"); fi
        "${adb_command[@]}" install -r "$apk"
        package_name=com.vladislavtomilov.kinetickk
        if [[ $game_variant == debug ]]; then package_name+=.debug; fi
        exec "${adb_command[@]}" shell am start -W \
            -n "$package_name/kinetickk.app.shared.MainActivity" ;;
    web:build|web:package)
        web_task=wasmJsBrowserDistribution
        if [[ $game_variant == debug ]]; then web_task=wasmJsBrowserDevelopmentExecutableDistribution; fi
        exec ./gradlew ":app:web:$web_task" "${gradle_arguments[@]}" "$@" ;;
    web:run)
        web_task=wasmJsBrowserProductionRun
        if [[ $game_variant == debug ]]; then web_task=wasmJsBrowserDevelopmentRun; fi
        exec ./gradlew ":app:web:$web_task" "${gradle_arguments[@]}" "$@" ;;
    web:test)
        exec ./gradlew wasmJsBrowserTest "${gradle_arguments[@]}" "$@" ;;
    *) usage >&2; exit 2 ;;
esac
