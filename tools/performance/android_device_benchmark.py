#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: GPL-3.0-or-later

"""Collect repeatable KINETICKK evidence from physical Android devices.

The runner is intentionally adb-only and does not mutate global device settings,
clear application data, or uninstall packages.  It installs the exact supplied
APK with replacement semantics (unless ``--skip-install`` is used and the
already-installed base APK has the same SHA-256), performs process-cold starts,
drives a selector-only UIAutomator flow, and retains all raw evidence.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import io
import json
import math
import os
import pathlib
import platform
import re
import shutil
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from typing import Any, Iterable, Sequence


SCHEMA_VERSION = 1
SUITE_NAME = "kinetickk-android-physical-device"
DEFAULT_PACKAGE = "com.vladislavtomilov.kinetickk"
DEFAULT_COMPONENT = (
    "com.vladislavtomilov.kinetickk/kinetickk.app.shared.MainActivity"
)
DEFAULT_SERIALS = (
    "7DTSXC49PZMRFUPJ",
    "NVYXIJQGTOMVNZZH",
    "R58R603CSEY",
    "c91ae939",
)
DEFAULT_FORKS = 3
DEFAULT_MIN_FRAMES = 30
DEFAULT_FLOW = pathlib.Path(__file__).resolve().with_name("android_gameplay_flow.json")
DEFAULT_FLOW_SCHEMA = pathlib.Path(__file__).resolve().with_name(
    "android_gameplay_flow.schema.json",
)
DEFAULT_SCHEMA = pathlib.Path(__file__).resolve().with_name(
    "android_device_benchmark.schema.json",
)
SERIAL_PATTERN = re.compile(r"^[A-Za-z0-9._:-]+$")
FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
BOUNDS_PATTERN = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")
SELECTOR_ATTRIBUTES = {
    "contentDescription": "content-desc",
    "resourceId": "resource-id",
    "text": "text",
    "className": "class",
    "package": "package",
    "clickable": "clickable",
    "enabled": "enabled",
}


class BenchmarkError(RuntimeError):
    """A missing or invalid datum that makes benchmark evidence unusable."""


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def parse_positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def parse_non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must not be negative")
    return parsed


def percentile(values: Iterable[float], quantile: float) -> float | None:
    ordered = sorted(float(value) for value in values)
    if not ordered:
        return None
    if len(ordered) == 1:
        return ordered[0]
    index = (len(ordered) - 1) * quantile
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return ordered[lower]
    fraction = index - lower
    return ordered[lower] * (1.0 - fraction) + ordered[upper] * fraction


def sample_statistics(values: Iterable[float]) -> dict[str, Any]:
    raw = [float(value) for value in values if math.isfinite(float(value))]
    if not raw:
        return {
            "sampleCount": 0,
            "rawSamples": [],
            "minimum": None,
            "maximum": None,
            "mean": None,
            "median": None,
            "p90": None,
            "p95": None,
            "p99": None,
            "standardDeviation": None,
            "coefficientOfVariation": None,
        }
    mean = statistics.fmean(raw)
    standard_deviation = statistics.stdev(raw) if len(raw) > 1 else 0.0
    return {
        "sampleCount": len(raw),
        "rawSamples": raw,
        "minimum": min(raw),
        "maximum": max(raw),
        "mean": mean,
        "median": statistics.median(raw),
        "p90": percentile(raw, 0.90),
        "p95": percentile(raw, 0.95),
        "p99": percentile(raw, 0.99),
        "standardDeviation": standard_deviation,
        "coefficientOfVariation": standard_deviation / mean if mean else None,
    }


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_text(path: pathlib.Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")


def write_json(path: pathlib.Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(value, indent=2, sort_keys=False, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def command_text(
    command: Sequence[str],
    *,
    timeout_seconds: float = 30.0,
    cwd: pathlib.Path | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    try:
        result = subprocess.run(
            list(command),
            cwd=cwd,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
            env=os.environ.copy(),
        )
    except FileNotFoundError as error:
        raise BenchmarkError(f"command not found: {command[0]}") from error
    except subprocess.TimeoutExpired as error:
        raise BenchmarkError(
            f"command timed out after {timeout_seconds:.1f}s: {' '.join(command)}",
        ) from error
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise BenchmarkError(
            f"command failed ({result.returncode}): {' '.join(command)}: {detail}",
        )
    return result


def git_text(repository: pathlib.Path, *arguments: str) -> str:
    result = command_text(["git", *arguments], cwd=repository)
    value = result.stdout.strip()
    if not value:
        raise BenchmarkError(f"git {' '.join(arguments)} returned no value")
    return value


def resolve_adb(explicit: str | None) -> pathlib.Path:
    candidates: list[pathlib.Path] = []
    if explicit:
        candidates.append(pathlib.Path(explicit).expanduser())
    for environment_name in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        if os.environ.get(environment_name):
            candidates.append(
                pathlib.Path(os.environ[environment_name]).expanduser()
                / "platform-tools"
                / "adb",
            )
    discovered = shutil.which("adb")
    if discovered:
        candidates.append(pathlib.Path(discovered))
    candidates.append(pathlib.Path.home() / "Library/Android/sdk/platform-tools/adb")
    for candidate in candidates:
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return candidate.resolve()
    raise BenchmarkError(
        "adb executable was not found; pass --adb or configure ANDROID_SDK_ROOT",
    )


def logical_artifact_path(
    path: pathlib.Path,
    repository: pathlib.Path,
    external_namespace: str,
) -> str:
    resolved = path.expanduser().resolve()
    try:
        return resolved.relative_to(repository.resolve()).as_posix()
    except ValueError:
        return f"{external_namespace}/{resolved.name}"


def resolve_aapt2(adb: pathlib.Path) -> pathlib.Path | None:
    sdk_root = adb.parent.parent
    candidates = [
        candidate
        for candidate in (sdk_root / "build-tools").glob("*/aapt2")
        if candidate.is_file() and os.access(candidate, os.X_OK)
    ]

    def version_key(path: pathlib.Path) -> tuple[int, ...]:
        numbers = tuple(int(value) for value in re.findall(r"\d+", path.parent.name))
        return numbers or (0,)

    return max(candidates, key=version_key) if candidates else None


def parse_apk_manifest_tree(output: str) -> dict[str, Any]:
    package_match = re.search(r'^\s*A: package="([^"]+)"', output, re.MULTILINE)
    if not package_match:
        raise BenchmarkError("aapt2 manifest tree did not report the package")

    def numeric_attribute(name: str) -> int | None:
        match = re.search(rf":{re.escape(name)}\([^)]*\)=([0-9]+)", output)
        return int(match.group(1)) if match else None

    def boolean_attribute_in_element(element_name: str, attribute_name: str) -> bool | None:
        lines = output.splitlines()
        for index, line in enumerate(lines):
            match = re.match(rf"^(\s*)E: {re.escape(element_name)}(?:\s|$)", line)
            if not match:
                continue
            element_indent = len(match.group(1))
            for candidate in lines[index + 1 :]:
                stripped = candidate.lstrip()
                indent = len(candidate) - len(stripped)
                if stripped.startswith("E: ") and indent <= element_indent:
                    break
                attribute = re.search(
                    rf":{re.escape(attribute_name)}\([^)]*\)=(true|false)",
                    candidate,
                )
                if attribute:
                    return attribute.group(1) == "true"
            return False
        return False

    return {
        "inspectionStatus": "observed",
        "package": package_match.group(1),
        "minSdk": numeric_attribute("minSdkVersion"),
        "targetSdk": numeric_attribute("targetSdkVersion"),
        "debuggableDeclared": boolean_attribute_in_element("application", "debuggable"),
        "profileableByShellDeclared": boolean_attribute_in_element("profileable", "shell"),
    }


def inspect_apk_manifest(
    apk: pathlib.Path,
    aapt2: pathlib.Path | None,
) -> tuple[dict[str, Any], str | None]:
    if aapt2 is None:
        return {
            "inspectionStatus": "tool-unavailable",
            "package": None,
            "minSdk": None,
            "targetSdk": None,
            "debuggableDeclared": None,
            "profileableByShellDeclared": None,
        }, None
    result = command_text(
        [str(aapt2), "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"],
        timeout_seconds=60.0,
    )
    return parse_apk_manifest_tree(result.stdout), result.stdout


class AdbTarget:
    def __init__(self, adb: pathlib.Path, serial: str, timeout_seconds: float) -> None:
        if not SERIAL_PATTERN.fullmatch(serial):
            raise BenchmarkError(f"invalid adb serial: {serial!r}")
        self.adb = adb
        self.serial = serial
        self.timeout_seconds = timeout_seconds

    def run(
        self,
        arguments: Sequence[str],
        *,
        timeout_seconds: float | None = None,
        check: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        return command_text(
            [str(self.adb), "-s", self.serial, *arguments],
            timeout_seconds=timeout_seconds or self.timeout_seconds,
            check=check,
        )

    def shell(
        self,
        *arguments: str,
        timeout_seconds: float | None = None,
        check: bool = True,
    ) -> str:
        return self.run(
            ["shell", *arguments],
            timeout_seconds=timeout_seconds,
            check=check,
        ).stdout.replace("\r\n", "\n")

    def exec_out_bytes(
        self,
        arguments: Sequence[str],
        *,
        timeout_seconds: float | None = None,
    ) -> bytes:
        command = [str(self.adb), "-s", self.serial, "exec-out", *arguments]
        try:
            result = subprocess.run(
                command,
                check=False,
                capture_output=True,
                timeout=timeout_seconds or self.timeout_seconds,
                env=os.environ.copy(),
            )
        except subprocess.TimeoutExpired as error:
            raise BenchmarkError(
                f"binary adb command timed out: {' '.join(command)}",
            ) from error
        if result.returncode != 0:
            raise BenchmarkError(
                f"binary adb command failed ({result.returncode}): "
                f"{' '.join(command)}: {result.stderr.decode(errors='replace').strip()}",
            )
        return result.stdout


def connected_devices(adb: pathlib.Path) -> dict[str, str]:
    output = command_text([str(adb), "devices"]).stdout
    devices: dict[str, str] = {}
    for line in output.splitlines()[1:]:
        if not line.strip():
            continue
        fields = line.split()
        if len(fields) >= 2:
            devices[fields[0]] = fields[1]
    return devices


def parse_getprop(output: str) -> dict[str, str]:
    return {
        match.group(1): match.group(2)
        for match in re.finditer(r"^\[([^]]+)\]: \[(.*)\]$", output, re.MULTILINE)
    }


def require_property(properties: dict[str, str], name: str) -> str:
    value = properties.get(name, "").strip()
    if not value:
        raise BenchmarkError(f"required Android property is missing: {name}")
    return value


def parse_wm_dimension(output: str, kind: str) -> dict[str, int]:
    if kind == "size":
        physical_match = re.search(r"Physical size:\s*(\d+)x(\d+)", output)
        override_match = re.search(r"Override size:\s*(\d+)x(\d+)", output)
        if not physical_match:
            raise BenchmarkError("wm size did not report a physical size")
        physical_width, physical_height = map(int, physical_match.groups())
        effective_width, effective_height = (
            map(int, override_match.groups()) if override_match else (physical_width, physical_height)
        )
        return {
            "physicalWidthPixels": physical_width,
            "physicalHeightPixels": physical_height,
            "effectiveWidthPixels": effective_width,
            "effectiveHeightPixels": effective_height,
        }
    physical_match = re.search(r"Physical density:\s*(\d+)", output)
    override_match = re.search(r"Override density:\s*(\d+)", output)
    if not physical_match:
        raise BenchmarkError("wm density did not report a physical density")
    physical = int(physical_match.group(1))
    effective = int(override_match.group(1)) if override_match else physical
    return {"physicalDensityDpi": physical, "effectiveDensityDpi": effective}


def parse_active_refresh_rate(display_output: str) -> float:
    built_in_line = next(
        (
            line
            for line in display_output.splitlines()
            if "DisplayDeviceInfo" in line and "Built-in Screen" in line
        ),
        None,
    )
    if built_in_line:
        rendered = re.search(r"renderFrameRate\s+([0-9.]+)", built_in_line)
        if rendered:
            value = float(rendered.group(1))
            if 20.0 <= value <= 240.0:
                return value
        mode_match = re.search(r"\bmodeId(?:=|\s+)(\d+)", built_in_line)
        modes = {
            int(identifier): float(refresh)
            for identifier, refresh in re.findall(
                r"\{id=(\d+),\s*width=\d+,\s*height=\d+,\s*fps=([0-9.]+)",
                built_in_line,
            )
        }
        if mode_match and int(mode_match.group(1)) in modes:
            return modes[int(mode_match.group(1))]
    for pattern in (
        r"mActiveSfDisplayMode=.*?refreshRate=([0-9.]+)",
        r"mActiveSfDisplayMode=.*?fps=([0-9.]+)",
        r"\bmRefreshRate=([0-9.]+)",
    ):
        match = re.search(pattern, display_output)
        if match:
            value = float(match.group(1))
            if 20.0 <= value <= 240.0:
                return value
    raise BenchmarkError("active display refresh rate could not be determined")


def parse_rotation(display_output: str) -> int | None:
    built_in_line = next(
        (
            line
            for line in display_output.splitlines()
            if "DisplayDeviceInfo" in line and "Built-in Screen" in line
        ),
        "",
    )
    match = re.search(r"\brotation(?:=|\s+)([0-3])\b", built_in_line)
    return int(match.group(1)) if match else None


def parse_navigation_mode(value: str) -> dict[str, Any]:
    normalized = value.strip()
    try:
        numeric = int(normalized)
    except ValueError as error:
        raise BenchmarkError(f"navigation_mode is not numeric: {normalized!r}") from error
    names = {0: "three-button", 1: "two-button", 2: "gesture"}
    if numeric not in names:
        raise BenchmarkError(f"unsupported navigation_mode value: {numeric}")
    return {"rawMode": numeric, "mode": names[numeric]}


def parse_battery(output: str) -> dict[str, Any]:
    values: dict[str, str] = {}
    for line in output.splitlines():
        match = re.match(r"\s*([^:]+):\s*(.+?)\s*$", line)
        if match:
            values[match.group(1)] = match.group(2)
    required = ("level", "status", "temperature")
    missing = [key for key in required if key not in values]
    if missing:
        raise BenchmarkError(f"battery metrics missing: {', '.join(missing)}")

    def boolean(key: str) -> bool:
        value = values.get(key)
        if value not in {"true", "false"}:
            raise BenchmarkError(f"battery metric {key!r} is missing or invalid")
        return value == "true"

    return {
        "levelPercent": int(values["level"]),
        "statusCode": int(values["status"]),
        "temperatureCelsius": int(values["temperature"]) / 10.0,
        "acPowered": boolean("AC powered"),
        "usbPowered": boolean("USB powered"),
        "wirelessPowered": boolean("Wireless powered"),
    }


def parse_thermal(output: str) -> dict[str, Any]:
    status_match = re.search(r"Thermal Status:\s*(\d+)", output)
    if not status_match:
        status_match = re.search(r"\bmStatus\s*[:=]\s*(\d+)", output)
    if not status_match:
        raise BenchmarkError("thermal service did not report an overall status")
    seen: set[tuple[float, int, str, int]] = set()
    temperatures: list[dict[str, Any]] = []
    for value, type_code, name, status in re.findall(
        r"Temperature\{mValue=([-+0-9.]+),\s*mType=(\d+),\s*"
        r"mName=([^,}]+),\s*mStatus=(\d+)\}",
        output,
    ):
        identity = (float(value), int(type_code), name.strip(), int(status))
        if identity in seen:
            continue
        seen.add(identity)
        temperatures.append(
            {
                "valueCelsius": identity[0],
                "typeCode": identity[1],
                "name": identity[2],
                "statusCode": identity[3],
            },
        )
    skin_values = [
        item["valueCelsius"]
        for item in temperatures
        if item["typeCode"] == 3 and item["valueCelsius"] > 0.0
    ]
    return {
        "statusCode": int(status_match.group(1)),
        "temperatures": temperatures,
        "maximumSkinTemperatureCelsius": max(skin_values) if skin_values else None,
    }


def parse_mem_total(output: str) -> int:
    match = re.search(r"^MemTotal:\s*(\d+)\s*kB", output, re.MULTILINE)
    if not match:
        raise BenchmarkError("/proc/meminfo did not report MemTotal")
    return int(match.group(1)) * 1024


def parse_integer_setting(value: str, name: str) -> int:
    try:
        return int(value.strip())
    except ValueError as error:
        raise BenchmarkError(f"setting {name} is missing or non-numeric") from error


def parse_window_policy(output: str) -> dict[str, Any]:
    screen_match = re.search(r"screenState=(SCREEN_STATE_[A-Z_]+)", output)
    interactive_match = re.search(r"interactiveState=(INTERACTIVE_STATE_[A-Z_]+)", output)
    showing_match = re.search(r"^\s*showing=(true|false)\s*$", output, re.MULTILINE)
    return {
        "screenState": screen_match.group(1) if screen_match else None,
        "interactiveState": interactive_match.group(1) if interactive_match else None,
        "keyguardShowing": showing_match.group(1) == "true" if showing_match else None,
    }


def capture_device_environment(target: AdbTarget, directory: pathlib.Path) -> dict[str, Any]:
    raw_directory = directory / "raw-environment"
    raw_directory.mkdir(parents=True, exist_ok=True)
    commands = {
        "getprop": ("getprop",),
        "wm-size": ("wm", "size"),
        "wm-density": ("wm", "density"),
        "display": ("dumpsys", "display"),
        "battery": ("dumpsys", "battery"),
        "thermal": ("dumpsys", "thermalservice"),
        "meminfo": ("cat", "/proc/meminfo"),
        "window-policy": ("dumpsys", "window", "policy"),
    }
    raw: dict[str, str] = {}
    for name, arguments in commands.items():
        raw[name] = target.shell(*arguments)
        write_text(raw_directory / f"{name}.txt", raw[name])
    navigation_value = target.shell("settings", "get", "secure", "navigation_mode")
    accelerometer_rotation = target.shell(
        "settings",
        "get",
        "system",
        "accelerometer_rotation",
    )
    low_power = target.shell("settings", "get", "global", "low_power")
    write_text(raw_directory / "navigation-mode.txt", navigation_value)
    write_text(raw_directory / "accelerometer-rotation.txt", accelerometer_rotation)
    write_text(raw_directory / "low-power.txt", low_power)

    properties = parse_getprop(raw["getprop"])
    sizes = parse_wm_dimension(raw["wm-size"], "size")
    densities = parse_wm_dimension(raw["wm-density"], "density")
    refresh_rate = parse_active_refresh_rate(raw["display"])
    density_scale = densities["effectiveDensityDpi"] / 160.0
    api_level = int(require_property(properties, "ro.build.version.sdk"))
    environment = {
        "serial": target.serial,
        "manufacturer": require_property(properties, "ro.product.manufacturer"),
        "model": require_property(properties, "ro.product.model"),
        "product": require_property(properties, "ro.product.name"),
        "device": require_property(properties, "ro.product.device"),
        "androidRelease": require_property(properties, "ro.build.version.release"),
        "apiLevel": api_level,
        "securityPatch": require_property(properties, "ro.build.version.security_patch"),
        "buildFingerprint": require_property(properties, "ro.build.fingerprint"),
        "primaryAbi": require_property(properties, "ro.product.cpu.abi"),
        "physicalMemoryBytes": parse_mem_total(raw["meminfo"]),
        "display": {
            **sizes,
            **densities,
            "effectiveWidthDp": sizes["effectiveWidthPixels"] / density_scale,
            "effectiveHeightDp": sizes["effectiveHeightPixels"] / density_scale,
            "activeRefreshRateHz": refresh_rate,
            "frameBudgetMillis": 1000.0 / refresh_rate,
            "rotation": parse_rotation(raw["display"]),
        },
        "navigation": parse_navigation_mode(navigation_value),
        "rotationPolicy": {
            "accelerometerRotationEnabled": bool(
                parse_integer_setting(accelerometer_rotation, "accelerometer_rotation"),
            ),
        },
        "battery": parse_battery(raw["battery"]),
        "thermal": parse_thermal(raw["thermal"]),
        "lowPowerModeEnabled": bool(parse_integer_setting(low_power, "low_power")),
        "windowPolicy": parse_window_policy(raw["window-policy"]),
        "rawArtifactDirectory": "raw-environment",
    }
    return environment


def capture_runtime_snapshot(
    target: AdbTarget,
    directory: pathlib.Path,
    name: str,
) -> dict[str, Any]:
    display = target.shell("dumpsys", "display")
    battery = target.shell("dumpsys", "battery")
    thermal = target.shell("dumpsys", "thermalservice")
    write_text(directory / f"{name}-display.txt", display)
    write_text(directory / f"{name}-battery.txt", battery)
    write_text(directory / f"{name}-thermal.txt", thermal)
    refresh_rate = parse_active_refresh_rate(display)
    return {
        "capturedAtUtc": utc_now(),
        "activeRefreshRateHz": refresh_rate,
        "frameBudgetMillis": 1000.0 / refresh_rate,
        "rotation": parse_rotation(display),
        "battery": parse_battery(battery),
        "thermal": parse_thermal(thermal),
    }


def parse_package_dump(output: str) -> dict[str, Any]:
    version_name = re.search(r"\bversionName=([^\s]+)", output)
    version_code = re.search(r"\bversionCode=(\d+)", output)
    flag_groups = re.findall(r"\b(?:pkgFlags|flags)=\[([^]]*)\]", output)
    if not version_name or not version_code or not flag_groups:
        raise BenchmarkError("installed package identity is incomplete")
    flags = {flag for group in flag_groups for flag in group.split()}
    profileable_match = re.search(
        r"\b(?:isProfileableByShell|profileableByShell)=(true|false|1|0)\b",
        output,
        re.IGNORECASE,
    )
    profileable_observed = (
        profileable_match.group(1) in {"true", "1"} if profileable_match else None
    )
    return {
        "versionName": version_name.group(1),
        "versionCode": int(version_code.group(1)),
        "debuggable": "DEBUGGABLE" in flags,
        "profileableByShellObserved": profileable_observed,
    }


def profileable_provenance(
    manifest_declared: bool | None,
    dumpsys_observed: bool | None,
) -> dict[str, Any]:
    if (
        manifest_declared is not None
        and dumpsys_observed is not None
        and manifest_declared != dumpsys_observed
    ):
        raise BenchmarkError(
            "profileableByShell provenance conflicts between APK manifest and dumpsys package: "
            f"manifest={manifest_declared}, dumpsys={dumpsys_observed}",
        )
    effective = manifest_declared if manifest_declared is not None else dumpsys_observed
    source = (
        "apk-manifest"
        if manifest_declared is not None
        else "dumpsys-package"
        if dumpsys_observed is not None
        else "unobserved"
    )
    return {
        "effective": effective,
        "source": source,
        "manifestDeclared": manifest_declared,
        "dumpsysObserved": dumpsys_observed,
    }


def installed_base_apk_path(target: AdbTarget, package_name: str) -> str:
    output = target.shell("pm", "path", package_name)
    paths = [
        line.removeprefix("package:").strip()
        for line in output.splitlines()
        if line.startswith("package:")
    ]
    base_paths = [path for path in paths if path.endswith("/base.apk")]
    if len(base_paths) != 1:
        raise BenchmarkError(
            f"expected exactly one installed base.apk for {package_name}, found {base_paths}",
        )
    return base_paths[0]


def installed_apk_sha256(target: AdbTarget, remote_path: str) -> tuple[str, int]:
    payload = target.exec_out_bytes(["cat", remote_path], timeout_seconds=180.0)
    if not payload:
        raise BenchmarkError(f"installed APK is empty or unreadable: {remote_path}")
    return hashlib.sha256(payload).hexdigest(), len(payload)


def install_and_verify_apk(
    target: AdbTarget,
    apk: pathlib.Path,
    apk_sha256: str,
    package_name: str,
    *,
    skip_install: bool,
    directory: pathlib.Path,
    manifest_profileable_by_shell: bool | None = None,
) -> dict[str, Any]:
    if not skip_install:
        blocker = inspect_known_blocking_ui(target, package_name)
        if blocker:
            raise BenchmarkError(blocker["message"])
        installation = target.run(
            ["install", "-r", "-t", "--no-incremental", str(apk)],
            timeout_seconds=240.0,
        )
        write_text(directory / "adb-install.txt", installation.stdout + installation.stderr)
        if "Success" not in installation.stdout:
            raise BenchmarkError("adb install did not report Success")
    base_path = installed_base_apk_path(target, package_name)
    installed_sha256, installed_size = installed_apk_sha256(target, base_path)
    if installed_sha256 != apk_sha256:
        raise BenchmarkError(
            "installed base APK SHA-256 does not match the supplied artifact: "
            f"installed={installed_sha256}, supplied={apk_sha256}",
        )
    package_dump = target.shell("dumpsys", "package", package_name)
    write_text(directory / "package-dump.txt", package_dump)
    package_identity = parse_package_dump(package_dump)
    profileable = profileable_provenance(
        manifest_profileable_by_shell,
        package_identity["profileableByShellObserved"],
    )
    return {
        "installMode": "verified-existing" if skip_install else "replace-preserve-data",
        "installedBaseApkPath": base_path,
        "installedBaseApkSha256": installed_sha256,
        "installedBaseApkBytes": installed_size,
        **package_identity,
        "profileableByShell": profileable["effective"],
        "profileableByShellProvenance": profileable,
    }


def validate_selector(selector: Any, context: str) -> dict[str, Any]:
    if not isinstance(selector, dict) or not selector:
        raise BenchmarkError(f"{context} must contain a non-empty selector object")
    if "anyOf" in selector:
        if set(selector) != {"anyOf"}:
            raise BenchmarkError(f"{context}.selector anyOf cannot be combined with other keys")
        alternatives = selector["anyOf"]
        if not isinstance(alternatives, list) or not 1 <= len(alternatives) <= 8:
            raise BenchmarkError(f"{context}.selector.anyOf must contain 1..8 alternatives")
        return {
            "anyOf": [
                validate_selector(alternative, f"{context}.selector.anyOf[{index}]")
                for index, alternative in enumerate(alternatives)
            ],
        }
    unknown = set(selector) - set(SELECTOR_ATTRIBUTES)
    if unknown:
        raise BenchmarkError(f"{context} contains unsupported selector keys: {sorted(unknown)}")
    normalized: dict[str, Any] = {}
    for key, value in selector.items():
        if key in {"clickable", "enabled"}:
            if not isinstance(value, bool):
                raise BenchmarkError(f"{context}.{key} must be boolean")
        elif not isinstance(value, str) or not value:
            raise BenchmarkError(f"{context}.{key} must be a non-empty string")
        normalized[key] = value
    return normalized


def load_flow(path: pathlib.Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BenchmarkError(f"cannot read UI flow {path}: {error}") from error
    if not isinstance(payload, dict):
        raise BenchmarkError("UI flow root must be an object")
    allowed_root = {"$schema", "schemaVersion", "name", "measurementStartStep", "steps"}
    unknown_root = set(payload) - allowed_root
    if unknown_root:
        raise BenchmarkError(f"UI flow has unknown root keys: {sorted(unknown_root)}")
    if payload.get("schemaVersion") != 1:
        raise BenchmarkError("UI flow schemaVersion must equal 1")
    if payload.get("$schema") not in {None, "android_gameplay_flow.schema.json"}:
        raise BenchmarkError("UI flow $schema must reference android_gameplay_flow.schema.json")
    if not isinstance(payload.get("name"), str) or not payload["name"]:
        raise BenchmarkError("UI flow name must be a non-empty string")
    steps = payload.get("steps")
    if not isinstance(steps, list) or not steps:
        raise BenchmarkError("UI flow steps must be a non-empty array")
    measurement_start = payload.get("measurementStartStep", 0)
    if not isinstance(measurement_start, int) or not 0 <= measurement_start <= len(steps):
        raise BenchmarkError("measurementStartStep must be within the steps array")
    normalized_steps: list[dict[str, Any]] = []
    for index, raw_step in enumerate(steps):
        context = f"steps[{index}]"
        if not isinstance(raw_step, dict):
            raise BenchmarkError(f"{context} must be an object")
        action = raw_step.get("action")
        if action not in {"wait", "tap", "longPress", "sleep"}:
            raise BenchmarkError(f"{context}.action is unsupported: {action!r}")
        common_allowed = {"action", "repeat", "intervalMillis"}
        if action == "sleep":
            allowed = common_allowed | {"durationMillis"}
        elif action == "wait":
            allowed = common_allowed | {"selector", "timeoutMillis"}
        elif action == "longPress":
            allowed = common_allowed | {
                "selector",
                "timeoutMillis",
                "durationMillis",
                "reusePreviousHierarchy",
            }
        elif action == "tap":
            allowed = common_allowed | {
                "selector",
                "timeoutMillis",
                "reusePreviousHierarchy",
            }
        else:
            allowed = common_allowed | {"selector", "timeoutMillis"}
        unknown = set(raw_step) - allowed
        if unknown:
            raise BenchmarkError(f"{context} contains unknown keys: {sorted(unknown)}")
        step = dict(raw_step)
        if action != "sleep":
            step["selector"] = validate_selector(raw_step.get("selector"), context)
        repeat = step.get("repeat", 1)
        interval = step.get("intervalMillis", 0)
        timeout = step.get("timeoutMillis", 10_000)
        duration = step.get("durationMillis")
        reuse_previous = step.get("reusePreviousHierarchy", False)
        if not isinstance(repeat, int) or not 1 <= repeat <= 100:
            raise BenchmarkError(f"{context}.repeat must be in 1..100")
        if not isinstance(interval, int) or not 0 <= interval <= 10_000:
            raise BenchmarkError(f"{context}.intervalMillis must be in 0..10000")
        if action != "sleep" and (
            not isinstance(timeout, int) or not 1 <= timeout <= 60_000
        ):
            raise BenchmarkError(f"{context}.timeoutMillis must be in 1..60000")
        if action in {"longPress", "sleep"} and (
            not isinstance(duration, int) or not 1 <= duration <= 60_000
        ):
            raise BenchmarkError(f"{context}.durationMillis must be in 1..60000")
        if not isinstance(reuse_previous, bool):
            raise BenchmarkError(f"{context}.reusePreviousHierarchy must be boolean")
        step["repeat"] = repeat
        step["intervalMillis"] = interval
        if action in {"tap", "longPress"}:
            step["reusePreviousHierarchy"] = reuse_previous
        if action != "sleep":
            step["timeoutMillis"] = timeout
        normalized_steps.append(step)
    for phase_name, phase_start, phase_end in (
        ("setup", 0, measurement_start),
        ("measurement", measurement_start, len(normalized_steps)),
    ):
        has_captured_hierarchy = False
        for index in range(phase_start, phase_end):
            step = normalized_steps[index]
            if step["action"] in {"tap", "longPress"} and step.get(
                "reusePreviousHierarchy",
                False,
            ):
                if not has_captured_hierarchy:
                    raise BenchmarkError(
                        f"steps[{index}] cannot reuse a hierarchy before {phase_name} "
                        "has captured one",
                    )
            elif step["action"] in {"wait", "tap", "longPress"}:
                has_captured_hierarchy = True
    return {
        "$schema": "android_gameplay_flow.schema.json",
        "schemaVersion": 1,
        "name": payload["name"],
        "measurementStartStep": measurement_start,
        "steps": normalized_steps,
    }


def extract_ui_xml(output: str) -> str:
    start = output.find("<?xml")
    end = output.rfind("</hierarchy>")
    if start < 0 or end < start:
        raise BenchmarkError("uiautomator dump did not contain a hierarchy")
    xml = output[start : end + len("</hierarchy>")]
    try:
        ET.fromstring(xml)
    except ET.ParseError as error:
        raise BenchmarkError(f"uiautomator returned malformed hierarchy XML: {error}") from error
    return xml


def parse_bounds(value: str) -> tuple[int, int, int, int]:
    match = BOUNDS_PATTERN.fullmatch(value)
    if not match:
        raise BenchmarkError(f"invalid UIAutomator bounds: {value!r}")
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        raise BenchmarkError(f"empty UIAutomator bounds: {value!r}")
    return left, top, right, bottom


def selector_matches(
    xml: str,
    selector: dict[str, Any],
    expected_package: str,
) -> list[dict[str, Any]]:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError as error:
        raise BenchmarkError(f"invalid UIAutomator XML: {error}") from error
    matches: list[dict[str, Any]] = []
    for node in root.iter("node"):
        attributes = node.attrib
        if attributes.get("package") != expected_package:
            continue
        matched = True
        for key, expected in selector.items():
            actual = attributes.get(SELECTOR_ATTRIBUTES[key], "")
            expected_text = str(expected).lower() if isinstance(expected, bool) else str(expected)
            if actual != expected_text:
                matched = False
                break
        if matched:
            bounds = parse_bounds(attributes.get("bounds", ""))
            matches.append(
                {
                    "bounds": list(bounds),
                    "center": [(bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2],
                    "className": attributes.get("class"),
                    "resourceId": attributes.get("resource-id"),
                    "text": attributes.get("text"),
                    "contentDescription": attributes.get("content-desc"),
                },
            )
    return matches


def selector_alternatives(selector: dict[str, Any]) -> list[dict[str, Any]]:
    alternatives = selector.get("anyOf")
    return alternatives if isinstance(alternatives, list) else [selector]


def resolve_selector_in_hierarchy(
    xml: str,
    selector: dict[str, Any],
    expected_package: str,
) -> tuple[dict[str, Any], dict[str, Any]] | None:
    # Consent and installer surfaces take precedence even when UIAutomator also exposes
    # the obscured app hierarchy.  Resolving an app node first could otherwise turn its
    # safe semantic center into a tap intercepted by the foreign modal.
    blocker = known_blocking_ui(xml, expected_package)
    if blocker:
        raise BenchmarkError(blocker["message"])
    for candidate in selector_alternatives(selector):
        matches = selector_matches(xml, candidate, expected_package)
        if len(matches) == 1:
            return matches[0], candidate
        if len(matches) > 1:
            raise BenchmarkError(
                f"UI selector is ambiguous ({len(matches)} matches): {candidate}",
            )
    return None


def known_blocking_ui(xml: str, expected_package: str) -> dict[str, str] | None:
    root = ET.fromstring(xml)
    nodes = list(root.iter("node"))
    packages = {node.attrib.get("package", "") for node in nodes}
    texts = {
        text.strip()
        for node in nodes
        for text in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        if text.strip()
    }
    if "com.android.vending" in packages and {
        "App scan recommended",
        "Scan app",
    }.issubset(texts):
        return {
            "kind": "play-protect-scan-consent",
            "package": "com.android.vending",
            "message": (
                "Google Play Protect is waiting for an explicit scan/install decision. "
                "The benchmark will not upload the APK, start a scan, or choose an install "
                "override; resolve the dialog manually, then rerun."
            ),
        }
    miui_packages = {
        "com.miui.packageinstaller",
        "com.miui.securitycenter",
        "com.miui.securityadd",
    }
    privacy_markers = ("privacy policy", "privacy terms", "user agreement")
    if packages & miui_packages and any(
        marker in text.lower() for marker in privacy_markers for text in texts
    ):
        return {
            "kind": "miui-privacy-consent",
            "package": sorted(packages & miui_packages)[0],
            "message": (
                "MIUI is waiting for privacy/user-agreement consent. The benchmark will not "
                "accept terms or guess a coordinate; resolve the dialog manually, then rerun."
            ),
        }
    return None


def dump_ui(target: AdbTarget) -> str:
    failures: list[str] = []
    commands = (
        ["exec-out", "uiautomator", "dump", "/dev/tty"],
        ["exec-out", "uiautomator", "dump", "--compressed", "/dev/tty"],
    )
    for command in commands:
        try:
            result = target.run(command, timeout_seconds=20.0, check=False)
        except BenchmarkError as error:
            failures.append(str(error))
            continue
        combined = result.stdout + ("\n" + result.stderr if result.stderr else "")
        try:
            return extract_ui_xml(combined)
        except BenchmarkError as error:
            failures.append(
                f"{' '.join(command[2:])} returned {result.returncode}: {error}",
            )
    raise BenchmarkError(
        "UI hierarchy is unavailable after normal and compressed UIAutomator dumps: "
        + "; ".join(failures),
    )


def inspect_known_blocking_ui(
    target: AdbTarget,
    expected_package: str,
) -> dict[str, str] | None:
    try:
        return known_blocking_ui(dump_ui(target), expected_package)
    except BenchmarkError:
        # Installation still owns the authoritative error path. A hierarchy failure here
        # must not be mistaken for consent or trigger any alternate input mechanism.
        return None


def wait_for_selector(
    target: AdbTarget,
    selector: dict[str, Any],
    expected_package: str,
    timeout_millis: int,
) -> tuple[dict[str, Any], str, int, dict[str, Any]]:
    deadline = time.monotonic() + timeout_millis / 1000.0
    attempts = 0
    last_count = 0
    last_xml = ""
    last_dump_error: str | None = None
    while time.monotonic() < deadline:
        attempts += 1
        try:
            last_xml = dump_ui(target)
        except BenchmarkError as error:
            last_dump_error = str(error)
            if time.monotonic() < deadline:
                time.sleep(0.25)
            continue
        resolved = resolve_selector_in_hierarchy(last_xml, selector, expected_package)
        if resolved:
            match, resolved_selector = resolved
            return match, last_xml, attempts, resolved_selector
        last_count = 0
        time.sleep(0.25)
    error_suffix = f"; last hierarchy error={last_dump_error}" if last_dump_error else ""
    raise BenchmarkError(
        f"UI selector was not found after {attempts} attempts: {selector}; "
        f"last match count={last_count}{error_suffix}",
    )


def execute_flow_steps(
    target: AdbTarget,
    flow: dict[str, Any],
    package_name: str,
    directory: pathlib.Path,
    *,
    start_index: int,
    end_index: int,
    phase: str,
) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    previous_hierarchy: dict[str, Any] | None = None
    for index in range(start_index, end_index):
        step = flow["steps"][index]
        action = step["action"]
        started = time.monotonic()
        event: dict[str, Any] = {
            "stepIndex": index,
            "phase": phase,
            "action": action,
            "startedAtUtc": utc_now(),
            "repeat": step["repeat"],
        }
        if action == "sleep":
            for repetition in range(step["repeat"]):
                time.sleep(step["durationMillis"] / 1000.0)
                if repetition + 1 < step["repeat"] and step["intervalMillis"]:
                    time.sleep(step["intervalMillis"] / 1000.0)
            event["durationMillisRequested"] = step["durationMillis"]
        else:
            reuse_previous = step.get("reusePreviousHierarchy", False)
            if reuse_previous:
                if action not in {"tap", "longPress"}:
                    raise BenchmarkError(
                        f"step {index} cannot reuse hierarchy for action {action}",
                    )
                if previous_hierarchy is None:
                    raise BenchmarkError(
                        f"step {index} requested hierarchy reuse before phase {phase} "
                        "captured a hierarchy",
                    )
                resolved = resolve_selector_in_hierarchy(
                    previous_hierarchy["xml"],
                    step["selector"],
                    package_name,
                )
                if resolved is None:
                    raise BenchmarkError(
                        f"step {index} selector is absent from the immediately previous "
                        f"captured hierarchy at step {previous_hierarchy['captureStepIndex']}: "
                        f"{step['selector']}",
                    )
                match, resolved_selector = resolved
                attempts = 0
                hierarchy_provenance = {
                    "source": "reused-previous-capture",
                    "captureStepIndex": previous_hierarchy["captureStepIndex"],
                    "capturePhase": previous_hierarchy["capturePhase"],
                    "artifact": previous_hierarchy["artifact"],
                    "sha256": previous_hierarchy["sha256"],
                }
            else:
                match, xml, attempts, resolved_selector = wait_for_selector(
                    target,
                    step["selector"],
                    package_name,
                    step["timeoutMillis"],
                )
                artifact = f"ui-{index:03d}-{phase}-{action}.xml"
                write_text(directory / artifact, xml)
                previous_hierarchy = {
                    "xml": xml,
                    "captureStepIndex": index,
                    "capturePhase": phase,
                    "artifact": artifact,
                    "sha256": hashlib.sha256(xml.encode("utf-8")).hexdigest(),
                }
                hierarchy_provenance = {
                    "source": "fresh-capture",
                    "captureStepIndex": index,
                    "capturePhase": phase,
                    "artifact": artifact,
                    "sha256": previous_hierarchy["sha256"],
                }
            event.update(
                {
                    "selector": step["selector"],
                    "resolvedSelector": resolved_selector,
                    "match": match,
                    "selectorAttempts": attempts,
                    "reusePreviousHierarchy": reuse_previous,
                    "hierarchy": hierarchy_provenance,
                },
            )
            x, y = match["center"]
            for repetition in range(step["repeat"]):
                if action == "tap":
                    target.shell("input", "tap", str(x), str(y))
                elif action == "longPress":
                    target.shell(
                        "input",
                        "swipe",
                        str(x),
                        str(y),
                        str(x),
                        str(y),
                        str(step["durationMillis"]),
                    )
                    event["durationMillisRequested"] = step["durationMillis"]
                if repetition + 1 < step["repeat"] and step["intervalMillis"]:
                    time.sleep(step["intervalMillis"] / 1000.0)
        event["elapsedMillis"] = (time.monotonic() - started) * 1000.0
        events.append(event)
    return events


def parse_startup(output: str) -> dict[str, Any]:
    values: dict[str, str] = {}
    for line in output.splitlines():
        match = re.match(r"\s*([^:]+):\s*(.*?)\s*$", line)
        if match:
            values[match.group(1)] = match.group(2)
    if values.get("Status") != "ok":
        raise BenchmarkError(f"am start -W status is not ok: {values.get('Status')!r}")
    required_times = ("TotalTime", "WaitTime")
    missing = [name for name in required_times if name not in values]
    if missing:
        raise BenchmarkError(f"am start -W metrics missing: {', '.join(missing)}")
    result = {
        "status": values["Status"],
        "activity": values.get("Activity"),
        "launchState": values.get("LaunchState"),
        "thisTimeMillis": int(values["ThisTime"]) if "ThisTime" in values else None,
        "totalTimeMillis": int(values["TotalTime"]),
        "waitTimeMillis": int(values["WaitTime"]),
        "complete": values.get("Complete") == "true" if "Complete" in values else None,
    }
    if result["totalTimeMillis"] < 0 or result["waitTimeMillis"] < 0:
        raise BenchmarkError("am start -W reported a negative startup time")
    return result


def parse_framestats_rows(output: str) -> tuple[list[dict[str, int]], int]:
    blocks = re.findall(
        r"---PROFILEDATA---\s*(.*?)\s*---PROFILEDATA---",
        output,
        re.DOTALL,
    )
    parsed: list[dict[str, int]] = []
    malformed = 0
    for block in blocks:
        lines = [line.strip() for line in block.splitlines() if line.strip()]
        if not lines or not lines[0].startswith("Flags,"):
            continue
        reader = csv.DictReader(io.StringIO("\n".join(lines)))
        for row in reader:
            converted: dict[str, int] = {}
            try:
                for key, value in row.items():
                    if key is None or not key.strip() or value is None or not value.strip():
                        continue
                    converted[key.strip()] = int(value.strip())
            except ValueError:
                malformed += 1
                continue
            if converted:
                parsed.append(converted)
    if not blocks:
        raise BenchmarkError("gfxinfo output contains no PROFILEDATA block")
    return parsed, malformed


def parse_gfxinfo_summary(output: str) -> dict[str, Any]:
    start = output.find("Stats since:")
    if start < 0:
        raise BenchmarkError("gfxinfo output contains no process summary")
    end = output.find("\nPipeline=", start)
    summary = output[start : end if end >= 0 else len(output)]

    def required_integer(label: str) -> int:
        match = re.search(rf"^{re.escape(label)}:\s*(\d+)", summary, re.MULTILINE)
        if not match:
            raise BenchmarkError(f"gfxinfo summary is missing {label!r}")
        return int(match.group(1))

    def optional_integer(label: str) -> int | None:
        match = re.search(rf"^{re.escape(label)}:\s*(\d+)", summary, re.MULTILINE)
        return int(match.group(1)) if match else None

    stats_since = required_integer("Stats since")
    total_frames = required_integer("Total frames rendered")
    janky_match = re.search(
        r"^Janky frames:\s*(\d+)\s*\(([0-9.]+)%\)",
        summary,
        re.MULTILINE,
    )
    if not janky_match:
        raise BenchmarkError("gfxinfo summary is missing platform Janky frames")
    platform_janky_frames = int(janky_match.group(1))
    reported_janky_rate = float(janky_match.group(2)) / 100.0
    if total_frames <= 0:
        raise BenchmarkError("gfxinfo summary total frame count must be positive")
    if not 0 <= platform_janky_frames <= total_frames:
        raise BenchmarkError("gfxinfo summary platform janky count is outside total frames")
    exact_janky_rate = platform_janky_frames / total_frames
    if abs(exact_janky_rate - reported_janky_rate) > 0.0001:
        raise BenchmarkError(
            "gfxinfo summary janky rate disagrees with its frame counts: "
            f"reported={reported_janky_rate}, exact={exact_janky_rate}",
        )

    frame_percentiles: dict[str, int] = {}
    gpu_percentiles: dict[str, int] = {}
    for percentile_value, millis in re.findall(
        r"^(50|90|95|99)th percentile:\s*(\d+)ms$",
        summary,
        re.MULTILINE,
    ):
        frame_percentiles[f"p{percentile_value}"] = int(millis)
    for percentile_value, millis in re.findall(
        r"^(50|90|95|99)th gpu percentile:\s*(\d+)ms$",
        summary,
        re.MULTILINE,
    ):
        gpu_percentiles[f"p{percentile_value}"] = int(millis)
    required_percentiles = {"p50", "p90", "p95", "p99"}
    if set(frame_percentiles) != required_percentiles:
        raise BenchmarkError(
            "gfxinfo summary frame percentiles are incomplete: "
            f"{sorted(frame_percentiles)}",
        )
    ordered_percentiles = [frame_percentiles[key] for key in ("p50", "p90", "p95", "p99")]
    if ordered_percentiles != sorted(ordered_percentiles):
        raise BenchmarkError("gfxinfo summary frame percentiles are not monotonic")

    def histogram(prefix: str) -> dict[str, int]:
        match = re.search(rf"^{re.escape(prefix)}:\s*(.+)$", summary, re.MULTILINE)
        if not match:
            return {}
        return {
            bucket: int(count)
            for bucket, count in re.findall(r"(\d+ms)=(\d+)", match.group(1))
        }

    return {
        "measurementScope": "full-measured-flow-since-gfxinfo-reset",
        "summarySelection": "first-process-level-summary",
        "statsSinceNanos": stats_since,
        "totalFramesRendered": total_frames,
        "platformJankyFrames": platform_janky_frames,
        "platformJankyFrameRate": exact_janky_rate,
        "platformJankyFrameRateReported": reported_janky_rate,
        "frameTimePercentilesMillis": frame_percentiles,
        "frameTimeHistogram": histogram("HISTOGRAM"),
        "gpuFrameTimePercentilesMillis": gpu_percentiles or None,
        "gpuFrameTimeHistogram": histogram("GPU HISTOGRAM") or None,
        "counters": {
            "missedVsync": optional_integer("Number Missed Vsync"),
            "highInputLatency": optional_integer("Number High input latency"),
            "slowUiThread": optional_integer("Number Slow UI thread"),
            "slowBitmapUploads": optional_integer("Number Slow bitmap uploads"),
            "slowIssueDrawCommands": optional_integer("Number Slow issue draw commands"),
            "frameDeadlineMissed": optional_integer("Number Frame deadline missed"),
            "frameDeadlineMissedLegacy": optional_integer(
                "Number Frame deadline missed (legacy)",
            ),
        },
    }


def timestamp_cadence(
    timestamps_nanos: list[int],
    frame_budget_nanos: float,
    *,
    count_missed_vsyncs: bool,
) -> dict[str, Any]:
    if len(timestamps_nanos) < 2:
        raise BenchmarkError("cadence requires at least two timestamps")
    intervals_nanos = [
        current - previous
        for previous, current in zip(timestamps_nanos, timestamps_nanos[1:])
    ]
    if any(interval <= 0 for interval in intervals_nanos):
        raise BenchmarkError("cadence timestamps are not strictly increasing")
    intervals_millis = [interval / 1_000_000.0 for interval in intervals_nanos]
    frames_per_second = (len(timestamps_nanos) - 1) * 1_000_000_000.0 / (
        timestamps_nanos[-1] - timestamps_nanos[0]
    )
    if count_missed_vsyncs:
        missed = sum(
            max(0, int(math.floor(interval / frame_budget_nanos + 0.5)) - 1)
            for interval in intervals_nanos
        )
        opportunities = len(intervals_nanos) + missed
        missed_rate = missed / opportunities if opportunities else 0.0
    else:
        missed = None
        missed_rate = None
    return {
        "intervalMillis": sample_statistics(intervals_millis),
        "framesPerSecond": frames_per_second,
        "cadenceMissedVsyncCount": missed,
        "cadenceMissedVsyncRate": missed_rate,
    }


def analyze_framestats(
    output: str,
    refresh_rate_hz: float,
    minimum_frames: int,
    api_level: int,
) -> dict[str, Any]:
    if not 20.0 <= refresh_rate_hz <= 240.0:
        raise BenchmarkError(f"invalid refresh rate for framestats: {refresh_rate_hz}")
    rows, malformed = parse_framestats_rows(output)
    if malformed:
        raise BenchmarkError(
            f"gfxinfo framestats contain {malformed} malformed numeric row(s)",
        )
    budget_nanos = 1_000_000_000.0 / refresh_rate_hz
    excluded_flags = 0
    excluded_incomplete = 0
    seen_intended_vsyncs: dict[int, int] = {}
    samples: list[dict[str, Any]] = []
    raw_display_present_values: list[int | None] = []
    display_present_column_observed = any("DisplayPresentTime" in row for row in rows)
    for row in rows:
        if row.get("Flags", 0) != 0:
            excluded_flags += 1
            continue
        intended = row.get("IntendedVsync")
        completed = row.get("FrameCompleted")
        if intended is None or completed is None or intended <= 0 or completed <= intended:
            excluded_incomplete += 1
            continue
        if intended in seen_intended_vsyncs:
            if seen_intended_vsyncs[intended] == completed:
                continue
            raise BenchmarkError(
                "terminal framestats contain multiple completion rows for one IntendedVsync",
            )
        seen_intended_vsyncs[intended] = completed
        completion_latency_nanos = completed - intended
        if completion_latency_nanos <= 0 or completion_latency_nanos > 5_000_000_000:
            excluded_incomplete += 1
            continue
        deadline = row.get("FrameDeadline")
        if api_level >= 31 and (
            deadline is None
            or deadline <= intended
            or deadline - intended > 5_000_000_000
        ):
            raise BenchmarkError(
                f"API {api_level} terminal framestats are missing a valid FrameDeadline",
            )
        if api_level < 31:
            deadline = None
        sync_queued = row.get("SyncQueued")
        if sync_queued is None or not intended < sync_queued <= completed:
            raise BenchmarkError(
                "terminal framestats are missing a valid SyncQueued timestamp",
            )
        deadline_delta_nanos = completed - deadline if deadline and deadline > intended else None
        deadline_miss = deadline_delta_nanos > 0 if deadline_delta_nanos is not None else None
        raw_display_present = row.get("DisplayPresentTime")
        display_present = raw_display_present
        if display_present is not None and not (
            intended < display_present < 9_000_000_000_000_000_000
        ):
            display_present = None
        sample = {
            "intendedVsyncNanos": intended,
            "syncQueuedNanos": sync_queued,
            "frameCompletedNanos": completed,
            "displayPresentTimeNanos": display_present,
            "frameDeadlineNanos": deadline if deadline and deadline > intended else None,
            "completionLatencyMillis": completion_latency_nanos / 1_000_000.0,
            "uiSubmissionLatencyMillis": (sync_queued - intended) / 1_000_000.0,
            "singleRefreshCompletionOverrun": completion_latency_nanos > budget_nanos,
            "doubleRefreshCompletionOverrun": completion_latency_nanos > 2.0 * budget_nanos,
            "deadlineMiss": deadline_miss,
            "completionDeadlineDeltaMillis": (
                deadline_delta_nanos / 1_000_000.0
                if deadline_delta_nanos is not None
                else None
            ),
        }
        samples.append(sample)
        raw_display_present_values.append(raw_display_present)
    samples.sort(key=lambda sample: sample["intendedVsyncNanos"])
    if len(samples) < minimum_frames:
        raise BenchmarkError(
            f"gfxinfo produced {len(samples)} valid frames; at least {minimum_frames} are required",
        )
    completion_latencies = [sample["completionLatencyMillis"] for sample in samples]
    ui_submission_latencies = [
        sample["uiSubmissionLatencyMillis"] for sample in samples
    ]
    single_refresh_overruns = sum(
        bool(sample["singleRefreshCompletionOverrun"]) for sample in samples
    )
    double_refresh_overruns = sum(
        bool(sample["doubleRefreshCompletionOverrun"]) for sample in samples
    )
    intended_cadence = timestamp_cadence(
        [sample["intendedVsyncNanos"] for sample in samples],
        budget_nanos,
        count_missed_vsyncs=True,
    )
    present_timestamps = [
        sample["displayPresentTimeNanos"]
        for sample in samples
        if sample["displayPresentTimeNanos"] is not None
    ]
    if present_timestamps:
        first_unpresented = next(
            (
                index
                for index, sample in enumerate(samples)
                if sample["displayPresentTimeNanos"] is None
            ),
            len(samples),
        )
        if any(
            sample["displayPresentTimeNanos"] is not None
            for sample in samples[first_unpresented:]
        ):
            raise BenchmarkError(
                "DisplayPresentTime is missing inside the presented framestats sequence",
            )
        if len(present_timestamps) < 2:
            raise BenchmarkError(
                "DisplayPresentTime produced fewer than two presented terminal frames",
            )
    display_present_column_all_unset = (
        display_present_column_observed
        and bool(raw_display_present_values)
        and all(value == 0 for value in raw_display_present_values)
    )
    if (
        display_present_column_observed
        and not present_timestamps
        and not display_present_column_all_unset
    ):
        raise BenchmarkError(
            "DisplayPresentTime column is present but contains no valid timestamps",
        )
    if present_timestamps:
        raw_present_cadence = timestamp_cadence(
            present_timestamps,
            budget_nanos,
            count_missed_vsyncs=True,
        )
        present_cadence = {
            "available": True,
            "availabilityState": "available",
            "unavailableReason": None,
            "intervalMillis": raw_present_cadence["intervalMillis"],
            "presentedFramesPerSecond": raw_present_cadence["framesPerSecond"],
            "cadenceMissedVsyncCount": raw_present_cadence[
                "cadenceMissedVsyncCount"
            ],
            "cadenceMissedVsyncRate": raw_present_cadence["cadenceMissedVsyncRate"],
        }
    else:
        availability_state = (
            "column-present-all-unset"
            if display_present_column_all_unset
            else "column-absent"
        )
        unavailable_reason = (
            "DisplayPresentTime is zero/unset for every valid frame on this platform"
            if display_present_column_all_unset
            else "DisplayPresentTime is absent from this platform framestats format"
        )
        present_cadence = {
            "available": False,
            "availabilityState": availability_state,
            "unavailableReason": unavailable_reason,
            "intervalMillis": None,
            "presentedFramesPerSecond": None,
            "cadenceMissedVsyncCount": None,
            "cadenceMissedVsyncRate": None,
        }
    if api_level >= 31:
        deadline_values = [bool(sample["deadlineMiss"]) for sample in samples]
        deadline_overruns = [
            sample["completionDeadlineDeltaMillis"]
            for sample in samples
            if sample["completionDeadlineDeltaMillis"] is not None
            and sample["completionDeadlineDeltaMillis"] > 0.0
        ]
        terminal_jank = {
            "available": True,
            "unavailableReason": None,
            "basis": "FrameCompleted-minus-FrameDeadline",
            "deadlineSampleCount": len(deadline_values),
            "deadlineMissCount": sum(deadline_values),
            "deadlineMissRate": sum(deadline_values) / len(deadline_values),
            "completionDeadlineOverrunMillis": sample_statistics(deadline_overruns),
        }
    else:
        terminal_jank = {
            "available": False,
            "unavailableReason": "FrameDeadline is unavailable before API 31",
            "basis": None,
            "deadlineSampleCount": None,
            "deadlineMissCount": None,
            "deadlineMissRate": None,
            "completionDeadlineOverrunMillis": None,
        }
    return {
        "measurementScope": "terminal-framestats-ring-steady-state",
        "sourceRowCount": len(rows),
        "validFrameCount": len(samples),
        "malformedRowCount": malformed,
        "excludedFlaggedRowCount": excluded_flags,
        "excludedIncompleteRowCount": excluded_incomplete,
        "refreshRateHz": refresh_rate_hz,
        "frameBudgetMillis": 1000.0 / refresh_rate_hz,
        "frameCompletionLatencyMillis": sample_statistics(completion_latencies),
        "uiSubmissionLatencyMillis": sample_statistics(ui_submission_latencies),
        "singleRefreshCompletionOverrunCount": single_refresh_overruns,
        "singleRefreshCompletionOverrunRate": single_refresh_overruns / len(samples),
        "doubleRefreshCompletionOverrunCount": double_refresh_overruns,
        "doubleRefreshCompletionOverrunRate": double_refresh_overruns / len(samples),
        "intendedVsyncCadence": {
            "available": True,
            "unavailableReason": None,
            "intervalMillis": intended_cadence["intervalMillis"],
            "producedFramesPerSecond": intended_cadence["framesPerSecond"],
            "cadenceMissedVsyncCount": intended_cadence[
                "cadenceMissedVsyncCount"
            ],
            "cadenceMissedVsyncRate": intended_cadence["cadenceMissedVsyncRate"],
        },
        "displayPresentCadence": present_cadence,
        "excludedUnpresentedTailRowCount": (
            len(samples) - len(present_timestamps)
            if present_timestamps
            else None
        ),
        "terminalJank": terminal_jank,
        "rawFrames": samples,
    }


def parse_meminfo(output: str) -> dict[str, Any]:
    patterns = {
        "totalPssKibibytes": r"^\s*TOTAL PSS:\s*(\d+)",
        "totalRssKibibytes": r"\bTOTAL RSS:\s*(\d+)",
        "totalSwapPssKibibytes": r"\bTOTAL SWAP PSS:\s*(\d+)",
        "javaHeapPssKibibytes": r"^\s*Java Heap:\s*(\d+)",
        "nativeHeapPssKibibytes": r"^\s*Native Heap:\s*(\d+)",
        "graphicsPssKibibytes": r"^\s*Graphics:\s*(\d+)",
        "viewCount": r"^\s*Views:\s*(\d+)",
        "activityCount": r"\bActivities:\s*(\d+)",
    }
    result: dict[str, Any] = {}
    missing: list[str] = []
    for name, pattern in patterns.items():
        match = re.search(pattern, output, re.MULTILINE)
        if match:
            result[name] = int(match.group(1))
        elif name == "totalSwapPssKibibytes":
            result[name] = None
        else:
            missing.append(name)
    if missing:
        raise BenchmarkError(f"dumpsys meminfo metrics missing: {', '.join(missing)}")
    return result


def analyze_logcat(output: str, package_name: str, process_ids: set[int]) -> dict[str, Any]:
    fatal_lines: list[str] = []
    anr_lines: list[str] = []
    crash_lines: list[str] = []
    skipped_frames: list[int] = []
    relevant_lines: list[str] = []
    for line in output.splitlines():
        pid_match = re.match(r"^\s*\d+(?:\.\d+)?\s+(\d+)\s+", line)
        pid = int(pid_match.group(1)) if pid_match else None
        belongs_to_app = package_name in line or pid in process_ids
        if belongs_to_app:
            relevant_lines.append(line)
        if "FATAL EXCEPTION" in line and pid in process_ids:
            fatal_lines.append(line)
        if package_name in line and re.search(r"\bANR in\b|\bam_anr\b", line):
            anr_lines.append(line)
        if package_name in line and re.search(
            r"\bam_crash\b|has died|Force finishing activity|Process .* crashed",
            line,
            re.IGNORECASE,
        ):
            crash_lines.append(line)
        if belongs_to_app:
            skipped = re.search(r"Skipped\s+(\d+)\s+frames", line)
            if skipped:
                skipped_frames.append(int(skipped.group(1)))
    return {
        "rawLineCount": len(output.splitlines()),
        "appRelevantLineCount": len(relevant_lines),
        "fatalExceptionCount": len(fatal_lines),
        "anrCount": len(anr_lines),
        "crashSignalCount": len(crash_lines),
        "skippedFrameMessageCount": len(skipped_frames),
        "skippedFramesReported": sum(skipped_frames),
        "fatalLines": fatal_lines,
        "anrLines": anr_lines,
        "crashLines": crash_lines,
    }


def require_runtime_health(
    snapshot: dict[str, Any],
    *,
    maximum_thermal_status: int,
    minimum_battery_level: int,
) -> None:
    thermal_status = snapshot["thermal"]["statusCode"]
    if thermal_status > maximum_thermal_status:
        raise BenchmarkError(
            f"thermal status {thermal_status} exceeds allowed {maximum_thermal_status}",
        )
    battery_level = snapshot["battery"]["levelPercent"]
    if battery_level < minimum_battery_level:
        raise BenchmarkError(
            f"battery level {battery_level}% is below required {minimum_battery_level}%",
        )


def wake_and_require_unlocked(target: AdbTarget, directory: pathlib.Path) -> dict[str, Any]:
    target.shell("input", "keyevent", "KEYCODE_WAKEUP")
    deadline = time.monotonic() + 5.0
    policy_output = ""
    policy: dict[str, Any] = {}
    while time.monotonic() < deadline:
        policy_output = target.shell("dumpsys", "window", "policy")
        policy = parse_window_policy(policy_output)
        if policy["interactiveState"] in {None, "INTERACTIVE_STATE_AWAKE"}:
            break
        time.sleep(0.25)
    write_text(directory / "window-policy-after-wake.txt", policy_output)
    if policy["keyguardShowing"] is True:
        raise BenchmarkError("device keyguard is showing; unlock the device before benchmarking")
    if policy["interactiveState"] not in {None, "INTERACTIVE_STATE_AWAKE"}:
        raise BenchmarkError(f"device is not awake after wake request: {policy}")
    return policy


def process_ids(target: AdbTarget, package_name: str) -> set[int]:
    result = target.shell("pidof", package_name, check=False).strip()
    if not result:
        return set()
    try:
        return {int(value) for value in result.split()}
    except ValueError as error:
        raise BenchmarkError(f"pidof returned invalid output: {result!r}") from error


def capture_screenshot(target: AdbTarget, path: pathlib.Path) -> None:
    payload = target.exec_out_bytes(["screencap", "-p"], timeout_seconds=30.0)
    if not payload.startswith(b"\x89PNG\r\n\x1a\n"):
        raise BenchmarkError("screencap did not return a PNG")
    path.write_bytes(payload)


def run_fork(
    target: AdbTarget,
    *,
    fork_index: int,
    package_name: str,
    component: str,
    flow: dict[str, Any],
    directory: pathlib.Path,
    minimum_frames: int,
    startup_settle_millis: int,
    maximum_thermal_status: int,
    minimum_battery_level: int,
    api_level: int,
) -> dict[str, Any]:
    directory.mkdir(parents=True, exist_ok=False)
    started_at = utc_now()
    wake_and_require_unlocked(target, directory)
    target.shell("am", "force-stop", package_name)
    time.sleep(0.2)
    preexisting_processes = process_ids(target, package_name)
    if preexisting_processes:
        raise BenchmarkError(
            f"force-stop left application processes alive: {sorted(preexisting_processes)}",
        )
    logcat_start = target.shell("date", "+%s.%3N").strip()
    if not re.fullmatch(r"\d+\.\d{3}", logcat_start):
        raise BenchmarkError(f"device timestamp is invalid: {logcat_start!r}")
    startup_raw = target.shell(
        "am",
        "start",
        "-W",
        "-n",
        component,
        timeout_seconds=60.0,
    )
    write_text(directory / "startup.txt", startup_raw)
    startup = parse_startup(startup_raw)
    time.sleep(startup_settle_millis / 1000.0)
    app_processes = process_ids(target, package_name)
    if not app_processes:
        raise BenchmarkError("application process is absent after cold startup")

    measurement_start = flow["measurementStartStep"]
    setup_events = execute_flow_steps(
        target,
        flow,
        package_name,
        directory,
        start_index=0,
        end_index=measurement_start,
        phase="setup",
    )
    pre_snapshot = capture_runtime_snapshot(target, directory, "pre-measurement")
    require_runtime_health(
        pre_snapshot,
        maximum_thermal_status=maximum_thermal_status,
        minimum_battery_level=minimum_battery_level,
    )
    target.shell("dumpsys", "gfxinfo", package_name, "reset", timeout_seconds=60.0)
    measured_started = time.monotonic()
    measurement_events = execute_flow_steps(
        target,
        flow,
        package_name,
        directory,
        start_index=measurement_start,
        end_index=len(flow["steps"]),
        phase="measurement",
    )
    measured_elapsed_millis = (time.monotonic() - measured_started) * 1000.0
    events = setup_events + measurement_events
    write_json(directory / "ui-events.json", events)
    gfxinfo_raw = target.shell(
        "dumpsys",
        "gfxinfo",
        package_name,
        "framestats",
        timeout_seconds=60.0,
    )
    write_text(directory / "gfxinfo-framestats.txt", gfxinfo_raw)
    meminfo_raw = target.shell("dumpsys", "meminfo", package_name, timeout_seconds=60.0)
    write_text(directory / "meminfo.txt", meminfo_raw)
    post_snapshot = capture_runtime_snapshot(target, directory, "post-measurement")
    require_runtime_health(
        post_snapshot,
        maximum_thermal_status=maximum_thermal_status,
        minimum_battery_level=minimum_battery_level,
    )
    refresh_before = pre_snapshot["activeRefreshRateHz"]
    refresh_after = post_snapshot["activeRefreshRateHz"]
    if abs(refresh_before - refresh_after) > 0.25:
        raise BenchmarkError(
            "active refresh rate changed during the measured flow: "
            f"{refresh_before:.4f}Hz -> {refresh_after:.4f}Hz",
        )
    refresh_rate = statistics.fmean([refresh_before, refresh_after])
    full_flow_gfxinfo_summary = parse_gfxinfo_summary(gfxinfo_raw)
    terminal_frame_ring = analyze_framestats(
        gfxinfo_raw,
        refresh_rate,
        minimum_frames,
        api_level,
    )
    memory_metrics = parse_meminfo(meminfo_raw)
    final_xml = dump_ui(target)
    if not any(
        node.attrib.get("package") == package_name
        for node in ET.fromstring(final_xml).iter("node")
    ):
        raise BenchmarkError("final UI hierarchy does not contain the benchmark package")
    write_text(directory / "ui-final.xml", final_xml)
    capture_screenshot(target, directory / "screenshot-final.png")
    final_processes = process_ids(target, package_name)
    if not final_processes:
        raise BenchmarkError("application process exited during the measured flow")
    all_processes = app_processes | final_processes
    logcat = target.run(
        ["logcat", "-d", "-v", "epoch", "-T", logcat_start],
        timeout_seconds=60.0,
    ).stdout
    write_text(directory / "logcat.txt", logcat)
    diagnostics = analyze_logcat(logcat, package_name, all_processes)
    write_json(directory / "diagnostics.json", diagnostics)
    if diagnostics["fatalExceptionCount"] or diagnostics["anrCount"] or diagnostics["crashSignalCount"]:
        raise BenchmarkError(f"fatal runtime diagnostics detected: {diagnostics}")
    result = {
        "fork": fork_index,
        "status": "ok",
        "startedAtUtc": started_at,
        "completedAtUtc": utc_now(),
        "processColdStart": True,
        "processIds": sorted(all_processes),
        "logcatStartDeviceEpoch": logcat_start,
        "startup": startup,
        "measurementElapsedMillis": measured_elapsed_millis,
        "runtimeBefore": pre_snapshot,
        "runtimeAfter": post_snapshot,
        "gfxinfo": {
            "fullFlowSummary": full_flow_gfxinfo_summary,
            "terminalFrameRing": terminal_frame_ring,
        },
        "memory": memory_metrics,
        "diagnostics": diagnostics,
        "uiEventCount": len(events),
        "artifacts": {
            "startup": "startup.txt",
            "gfxinfoFramestats": "gfxinfo-framestats.txt",
            "meminfo": "meminfo.txt",
            "logcat": "logcat.txt",
            "uiEvents": "ui-events.json",
            "finalUiHierarchy": "ui-final.xml",
            "finalScreenshot": "screenshot-final.png",
        },
    }
    write_json(directory / "result.json", result)
    return result


def error_result(fork_index: int, error: Exception, directory: pathlib.Path) -> dict[str, Any]:
    result = {
        "fork": fork_index,
        "status": "error",
        "completedAtUtc": utc_now(),
        "error": {"type": type(error).__name__, "message": str(error)},
    }
    write_json(directory / "result.json", result)
    return result


def aggregate_device(forks: list[dict[str, Any]], expected_forks: int) -> dict[str, Any]:
    successful = [fork for fork in forks if fork.get("status") == "ok"]
    if len(successful) != expected_forks:
        raise BenchmarkError(
            f"only {len(successful)} of {expected_forks} device forks are valid",
        )
    rings = [fork["gfxinfo"]["terminalFrameRing"] for fork in successful]
    summaries = [fork["gfxinfo"]["fullFlowSummary"] for fork in successful]
    refresh_rates = [ring["refreshRateHz"] for ring in rings]
    if max(refresh_rates) - min(refresh_rates) > 0.25:
        raise BenchmarkError(
            f"refresh rate differs across forks on the same device: {refresh_rates}",
        )
    completion_latencies = [
        frame["completionLatencyMillis"]
        for ring in rings
        for frame in ring["rawFrames"]
    ]
    ui_submission_latencies = [
        value
        for ring in rings
        for value in ring["uiSubmissionLatencyMillis"]["rawSamples"]
    ]
    single_refresh_overruns = sum(
        bool(frame["singleRefreshCompletionOverrun"])
        for ring in rings
        for frame in ring["rawFrames"]
    )
    double_refresh_overruns = sum(
        bool(frame["doubleRefreshCompletionOverrun"])
        for ring in rings
        for frame in ring["rawFrames"]
    )
    terminal_jank_availability = {ring["terminalJank"]["available"] for ring in rings}
    if len(terminal_jank_availability) != 1:
        raise BenchmarkError("terminal FrameDeadline availability differs across forks")
    terminal_jank_available = terminal_jank_availability.pop()
    if terminal_jank_available:
        deadline_values = [
            bool(frame["deadlineMiss"])
            for ring in rings
            for frame in ring["rawFrames"]
        ]
        deadline_overruns = [
            value
            for ring in rings
            for value in ring["terminalJank"]["completionDeadlineOverrunMillis"][
                "rawSamples"
            ]
        ]
        terminal_jank = {
            "available": True,
            "unavailableReason": None,
            "basis": "FrameCompleted-minus-FrameDeadline",
            "deadlineSampleCount": len(deadline_values),
            "deadlineMissCount": sum(deadline_values),
            "deadlineMissRate": sum(deadline_values) / len(deadline_values),
            "completionDeadlineOverrunMillis": sample_statistics(deadline_overruns),
        }
    else:
        terminal_jank = {
            "available": False,
            "unavailableReason": "FrameDeadline is unavailable before API 31",
            "basis": None,
            "deadlineSampleCount": None,
            "deadlineMissCount": None,
            "deadlineMissRate": None,
            "completionDeadlineOverrunMillis": None,
        }
    cadence_missed = sum(
        ring["intendedVsyncCadence"]["cadenceMissedVsyncCount"] for ring in rings
    )
    cadence_intervals = sum(
        ring["intendedVsyncCadence"]["intervalMillis"]["sampleCount"] for ring in rings
    )
    present_availability = {ring["displayPresentCadence"]["available"] for ring in rings}
    if len(present_availability) != 1:
        raise BenchmarkError("DisplayPresentTime availability differs across forks")
    present_available = present_availability.pop()
    present_states = {
        ring["displayPresentCadence"]["availabilityState"] for ring in rings
    }
    if len(present_states) != 1:
        raise BenchmarkError("DisplayPresentTime availability state differs across forks")
    present_state = present_states.pop()
    present_cadence_missed = (
        sum(
            ring["displayPresentCadence"]["cadenceMissedVsyncCount"]
            for ring in rings
        )
        if present_available
        else None
    )
    present_cadence_intervals = (
        sum(
            ring["displayPresentCadence"]["intervalMillis"]["sampleCount"]
            for ring in rings
        )
        if present_available
        else None
    )
    display_present_cadence = (
        {
            "available": True,
            "availabilityState": present_state,
            "unavailableReason": None,
            "intervalMillis": sample_statistics(
                value
                for ring in rings
                for value in ring["displayPresentCadence"]["intervalMillis"]["rawSamples"]
            ),
            "presentedFramesPerSecond": sample_statistics(
                ring["displayPresentCadence"]["presentedFramesPerSecond"]
                for ring in rings
            ),
            "cadenceMissedVsyncCount": present_cadence_missed,
            "cadenceMissedVsyncRate": (
                present_cadence_missed
                / (present_cadence_intervals + present_cadence_missed)
            ),
            "excludedUnpresentedTailRowCount": sum(
                ring["excludedUnpresentedTailRowCount"] for ring in rings
            ),
        }
        if present_available
        else {
            "available": False,
            "availabilityState": present_state,
            "unavailableReason": rings[0]["displayPresentCadence"]["unavailableReason"],
            "intervalMillis": None,
            "presentedFramesPerSecond": None,
            "cadenceMissedVsyncCount": None,
            "cadenceMissedVsyncRate": None,
            "excludedUnpresentedTailRowCount": None,
        }
    )
    full_flow_total_frames = sum(summary["totalFramesRendered"] for summary in summaries)
    full_flow_janky_frames = sum(summary["platformJankyFrames"] for summary in summaries)
    return {
        "forkCount": len(successful),
        "refreshRateHz": statistics.fmean(refresh_rates),
        "frameBudgetMillis": 1000.0 / statistics.fmean(refresh_rates),
        "startupTotalTimeMillis": sample_statistics(
            fork["startup"]["totalTimeMillis"] for fork in successful
        ),
        "startupWaitTimeMillis": sample_statistics(
            fork["startup"]["waitTimeMillis"] for fork in successful
        ),
        "fullFlowGfxinfoSummary": {
            "measurementScope": "full-measured-flow-since-gfxinfo-reset",
            "totalFramesRendered": full_flow_total_frames,
            "platformJankyFrames": full_flow_janky_frames,
            "platformJankyFrameRate": full_flow_janky_frames / full_flow_total_frames,
            "frameTimePercentilesMillis": {
                percentile_name: sample_statistics(
                    summary["frameTimePercentilesMillis"][percentile_name]
                    for summary in summaries
                )
                for percentile_name in ("p50", "p90", "p95", "p99")
            },
        },
        "terminalFrameCompletionLatencyMillis": sample_statistics(completion_latencies),
        "terminalUiSubmissionLatencyMillis": sample_statistics(
            ui_submission_latencies
        ),
        "terminalRawFrameSampleCount": len(completion_latencies),
        "terminalSingleRefreshCompletionOverrunCount": single_refresh_overruns,
        "terminalSingleRefreshCompletionOverrunRate": (
            single_refresh_overruns / len(completion_latencies)
        ),
        "terminalDoubleRefreshCompletionOverrunCount": double_refresh_overruns,
        "terminalDoubleRefreshCompletionOverrunRate": (
            double_refresh_overruns / len(completion_latencies)
        ),
        "terminalJank": terminal_jank,
        "terminalIntendedVsyncCadence": {
            "available": True,
            "intervalMillis": sample_statistics(
                value
                for ring in rings
                for value in ring["intendedVsyncCadence"]["intervalMillis"]["rawSamples"]
            ),
            "producedFramesPerSecond": sample_statistics(
                ring["intendedVsyncCadence"]["producedFramesPerSecond"]
                for ring in rings
            ),
            "cadenceMissedVsyncCount": cadence_missed,
            "cadenceMissedVsyncRate": (
                cadence_missed / (cadence_intervals + cadence_missed)
                if cadence_intervals + cadence_missed
                else 0.0
            ),
        },
        "terminalDisplayPresentCadence": display_present_cadence,
        "totalPssKibibytes": sample_statistics(
            fork["memory"]["totalPssKibibytes"] for fork in successful
        ),
        "javaHeapPssKibibytes": sample_statistics(
            fork["memory"]["javaHeapPssKibibytes"] for fork in successful
        ),
        "nativeHeapPssKibibytes": sample_statistics(
            fork["memory"]["nativeHeapPssKibibytes"] for fork in successful
        ),
        "graphicsPssKibibytes": sample_statistics(
            fork["memory"]["graphicsPssKibibytes"] for fork in successful
        ),
        "measurementElapsedMillis": sample_statistics(
            fork["measurementElapsedMillis"] for fork in successful
        ),
    }


def format_number(value: Any, digits: int = 2) -> str:
    if value is None:
        return "n/a"
    if isinstance(value, bool):
        return "yes" if value else "no"
    if isinstance(value, (int, float)):
        return f"{float(value):.{digits}f}"
    return str(value)


def render_markdown(report: dict[str, Any]) -> str:
    source = report["source"]
    apk = report["apk"]
    lines = [
        "# Android physical-device performance",
        "",
        f"Overall status: **{report['status']}**.",
        "",
        (
            f"Source `{source['label']}` at `{source['revision']}` "
            f"(dirty: `{str(source['dirty']).lower()}`); APK SHA-256 "
            f"`{apk['sha256']}`."
        ),
        "",
        (
            f"Protocol: {report['protocol']['forks']} process-cold forks per device; "
            f"selector-only flow `{report['protocol']['flow']['name']}`; "
            f"minimum {report['protocol']['minimumFramesPerFork']} valid frames per fork."
        ),
        "",
        (
            "Each device row is an independent same-serial aggregate with its own active-refresh "
            "frame budget. Raw frame counts are retained only inside that device's evidence and "
            "are never compared across devices."
        ),
        "",
        "| Device | API | Hz / budget ms | Cold p50 ms | Full-flow platform janky / p50 / p95 / p99 ms | Terminal deadline miss / overrun p95 ms | Completion latency p50 / p95 / p99 ms | UI submission p50 / p95 / p99 ms | Completion >1 / >2 refresh | Produced / presented FPS | Produced / presented cadence missed-vsync | PSS p50 MiB | Status |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|",
    ]
    for device in report["devices"]:
        environment = device.get("environment") or {}
        aggregate = device.get("aggregate") or {}
        completion_stats = aggregate.get("terminalFrameCompletionLatencyMillis") or {}
        ui_submission_stats = aggregate.get("terminalUiSubmissionLatencyMillis") or {}
        full_flow = aggregate.get("fullFlowGfxinfoSummary") or {}
        terminal_jank = aggregate.get("terminalJank") or {}
        intended_cadence = aggregate.get("terminalIntendedVsyncCadence") or {}
        present_cadence = aggregate.get("terminalDisplayPresentCadence") or {}
        startup_stats = aggregate.get("startupTotalTimeMillis") or {}
        pss_stats = aggregate.get("totalPssKibibytes") or {}
        frame_cell = (
            f"{format_number(completion_stats.get('median'))} / "
            f"{format_number(completion_stats.get('p95'))} / "
            f"{format_number(completion_stats.get('p99'))}"
        )
        ui_submission_cell = (
            f"{format_number(ui_submission_stats.get('median'))} / "
            f"{format_number(ui_submission_stats.get('p95'))} / "
            f"{format_number(ui_submission_stats.get('p99'))}"
        )
        full_flow_percentiles = full_flow.get("frameTimePercentilesMillis") or {}
        full_flow_cell = " / ".join(
            [
                (
                    format_number(100.0 * full_flow["platformJankyFrameRate"]) + "%"
                    if full_flow.get("platformJankyFrameRate") is not None
                    else "n/a"
                ),
                format_number((full_flow_percentiles.get("p50") or {}).get("median")),
                format_number((full_flow_percentiles.get("p95") or {}).get("median")),
                format_number((full_flow_percentiles.get("p99") or {}).get("median")),
            ],
        )
        deadline_overrun_stats = (
            terminal_jank.get("completionDeadlineOverrunMillis") or {}
        )
        produced_fps = (intended_cadence.get("producedFramesPerSecond") or {}).get("median")
        presented_fps = (
            (present_cadence.get("presentedFramesPerSecond") or {}).get("median")
            if present_cadence.get("available")
            else None
        )
        pss_mib = (
            pss_stats.get("median") / 1024.0 if pss_stats.get("median") is not None else None
        )
        lines.append(
            "| "
            + " | ".join(
                [
                    f"`{environment.get('manufacturer', '')} {environment.get('model', device['serial'])}`",
                    str(environment.get("apiLevel", "n/a")),
                    (
                        f"{format_number(aggregate.get('refreshRateHz'))} / "
                        f"{format_number(aggregate.get('frameBudgetMillis'))}"
                    ),
                    format_number(startup_stats.get("median")),
                    full_flow_cell,
                    " / ".join(
                        [
                            (
                                format_number(
                                    100.0 * terminal_jank["deadlineMissRate"],
                                )
                                + "%"
                                if terminal_jank.get("available")
                                else "unavailable"
                            ),
                            (
                                format_number(deadline_overrun_stats.get("p95"))
                                if terminal_jank.get("available")
                                else "unavailable"
                            ),
                        ],
                    ),
                    frame_cell,
                    ui_submission_cell,
                    " / ".join(
                        [
                            (
                                format_number(
                                    100.0
                                    * aggregate[
                                        "terminalSingleRefreshCompletionOverrunRate"
                                    ],
                                )
                                + "%"
                                if aggregate.get(
                                    "terminalSingleRefreshCompletionOverrunRate"
                                )
                                is not None
                                else "n/a"
                            ),
                            (
                                format_number(
                                    100.0
                                    * aggregate[
                                        "terminalDoubleRefreshCompletionOverrunRate"
                                    ],
                                )
                                + "%"
                                if aggregate.get(
                                    "terminalDoubleRefreshCompletionOverrunRate"
                                )
                                is not None
                                else "n/a"
                            ),
                        ],
                    ),
                    f"{format_number(produced_fps)} / {format_number(presented_fps)}",
                    " / ".join(
                        [
                            (
                                format_number(
                                    100.0
                                    * intended_cadence["cadenceMissedVsyncRate"],
                                )
                                + "%"
                                if intended_cadence.get("cadenceMissedVsyncRate")
                                is not None
                                else "n/a"
                            ),
                            (
                                format_number(
                                    100.0
                                    * present_cadence["cadenceMissedVsyncRate"],
                                )
                                + "%"
                                if present_cadence.get("cadenceMissedVsyncRate")
                                is not None
                                else "unavailable"
                            ),
                        ],
                    ),
                    format_number(pss_mib),
                    device.get("status", "error"),
                ],
            )
            + " |",
        )
    lines.extend(["", "## Per-device evidence", ""])
    for device in report["devices"]:
        environment = device.get("environment") or {}
        lines.extend(
            [
                f"### {environment.get('manufacturer', '')} {environment.get('model', device['serial'])} (`{device['serial']}`)",
                "",
                (
                    f"Android {environment.get('androidRelease', 'n/a')} / API "
                    f"{environment.get('apiLevel', 'n/a')}; navigation "
                    f"`{(environment.get('navigation') or {}).get('mode', 'n/a')}`; battery "
                    f"{(environment.get('battery') or {}).get('levelPercent', 'n/a')}%; "
                    f"status `{device.get('status')}`."
                ),
                "",
            ],
        )
        if device.get("aggregate"):
            aggregate = device["aggregate"]
            display_cadence = aggregate["terminalDisplayPresentCadence"]
            lines.extend(
                [
                    (
                        f"Within this serial only: the full-flow gfxinfo summary covers "
                        f"{aggregate['fullFlowGfxinfoSummary']['totalFramesRendered']} frames; "
                        f"the terminal steady-state ring retains "
                        f"{aggregate['terminalRawFrameSampleCount']} samples across "
                        f"{aggregate['forkCount']} forks at {aggregate['refreshRateHz']:.2f} Hz."
                    ),
                    "",
                ],
            )
            if not display_cadence["available"]:
                lines.extend(
                    [
                        (
                            "Presented cadence unavailable "
                            f"(`{display_cadence['availabilityState']}`): "
                            f"{display_cadence['unavailableReason']}."
                        ),
                        "",
                    ],
                )
        errors = [fork for fork in device.get("forks", []) if fork.get("status") != "ok"]
        for fork in errors:
            lines.append(
                f"- Fork {fork['fork']} failed closed: `{fork['error']['type']}` — "
                f"{fork['error']['message']}"
            )
        if device.get("error"):
            lines.append(
                f"- Device failed closed: `{device['error']['type']}` — "
                f"{device['error']['message']}"
            )
        if errors or device.get("error"):
            lines.append("")
    lines.extend(
        [
            "## Interpretation contract",
            "",
            "- A process-cold start force-stops only this package; it does not clear app data, reset compilation, reboot the device, or flush filesystem caches.",
            "- Full-flow platform janky frames and percentiles come from the complete `dumpsys gfxinfo` summary accumulated after the reset. Terminal latency/cadence/deadline evidence comes only from the bounded final framestats ring after the flow's steady-state tail.",
            "- `FrameCompleted - IntendedVsync` is completion latency. Crossing one or two active-refresh periods is reported as a completion overrun, not as jank.",
            "- On API 31+, terminal jank is strictly `FrameCompleted > FrameDeadline`; the positive difference is the completion-deadline overrun. API 30 reports terminal deadline jank as unavailable/null rather than inferring it.",
            "- Produced cadence uses neighboring `IntendedVsync`; presented cadence uses neighboring `DisplayPresentTime` only when the platform populates it. An entirely zero/unset column is explicitly unavailable; mixed internal gaps fail closed. Each neighboring interval is rounded to active-refresh periods; periods beyond the first are cadence missed-vsync opportunities.",
            "- Installation uses `adb install -r -t --no-incremental`, preserving app data. No uninstall, data clear, refresh-rate override, navigation change, rotation change, battery override, or other global setting mutation is performed.",
            "- Compare branches only on the same serial, flow, APK variant, active refresh rate, thermal state, charging state, and fork count.",
            "",
        ],
    )
    return "\n".join(lines)


def source_identity(repository: pathlib.Path, label: str | None) -> dict[str, Any]:
    revision = git_text(repository, "rev-parse", "HEAD")
    if not FULL_SHA_PATTERN.fullmatch(revision):
        raise BenchmarkError(f"git HEAD is not a full SHA: {revision!r}")
    status = command_text(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=repository,
    ).stdout
    branch = git_text(repository, "rev-parse", "--abbrev-ref", "HEAD")
    return {
        "label": label or branch,
        "branch": branch,
        "revision": revision,
        "dirty": bool(status.strip()),
        "gitStatusSha256": hashlib.sha256(status.encode("utf-8")).hexdigest(),
        "gitStatusEntryCount": len([line for line in status.splitlines() if line]),
    }


def tool_provenance(
    adb: pathlib.Path,
    flow_path: pathlib.Path,
    repository: pathlib.Path,
    aapt2: pathlib.Path | None,
) -> dict[str, Any]:
    script_path = pathlib.Path(__file__).resolve()
    schema_sha = sha256_file(DEFAULT_SCHEMA) if DEFAULT_SCHEMA.is_file() else None
    adb_version_raw = command_text([str(adb), "version"]).stdout.strip()
    adb_version = "\n".join(
        line for line in adb_version_raw.splitlines() if not line.startswith("Installed as ")
    )
    provenance = {
        "scriptPath": "tools/performance/android_device_benchmark.py",
        "scriptSha256": sha256_file(script_path),
        "schemaPath": "tools/performance/android_device_benchmark.schema.json",
        "schemaSha256": schema_sha,
        "flowSchemaPath": "tools/performance/android_gameplay_flow.schema.json",
        "flowSchemaSha256": sha256_file(DEFAULT_FLOW_SCHEMA),
        "flowPath": logical_artifact_path(flow_path, repository, "external-flow"),
        "flowSha256": sha256_file(flow_path),
        "pythonVersion": platform.python_version(),
        "hostPlatform": platform.platform(),
        "adbBinary": {
            "basename": adb.name,
            "logicalPath": "android-sdk/platform-tools/adb",
            "sha256": sha256_file(adb),
        },
        "adbVersion": adb_version,
    }
    if aapt2 is None:
        provenance["aapt2Binary"] = None
    else:
        aapt2_version_result = command_text([str(aapt2), "version"])
        provenance["aapt2Binary"] = {
            "basename": aapt2.name,
            "logicalPath": f"android-sdk/build-tools/{aapt2.parent.name}/aapt2",
            "sha256": sha256_file(aapt2),
            "version": (
                aapt2_version_result.stdout or aapt2_version_result.stderr
            ).strip(),
        }
    return provenance


def prepare_output(path: pathlib.Path) -> pathlib.Path:
    resolved = path.expanduser().resolve()
    if resolved.exists():
        if not resolved.is_dir():
            raise BenchmarkError(f"output exists and is not a directory: {resolved}")
        if any(resolved.iterdir()):
            raise BenchmarkError(f"refusing to reuse non-empty output directory: {resolved}")
    else:
        resolved.mkdir(parents=True)
    return resolved


def run(arguments: argparse.Namespace) -> tuple[dict[str, Any], pathlib.Path]:
    repository = arguments.repository.expanduser().resolve()
    apk = arguments.apk.expanduser().resolve()
    flow_path = arguments.flow.expanduser().resolve()
    if not repository.is_dir():
        raise BenchmarkError(f"repository does not exist: {repository}")
    if not apk.is_file():
        raise BenchmarkError(f"APK does not exist: {apk}")
    if not flow_path.is_file():
        raise BenchmarkError(f"UI flow does not exist: {flow_path}")
    if not arguments.component.startswith(arguments.package + "/"):
        raise BenchmarkError("component package must match --package")
    serials = list(arguments.serial or DEFAULT_SERIALS)
    if len(serials) != len(set(serials)):
        raise BenchmarkError("duplicate --serial values are not allowed")
    for serial in serials:
        if not SERIAL_PATTERN.fullmatch(serial):
            raise BenchmarkError(f"invalid adb serial: {serial!r}")
    adb = resolve_adb(arguments.adb)
    aapt2 = resolve_aapt2(adb)
    flow = load_flow(flow_path)
    source = source_identity(repository, arguments.label)
    apk_sha = sha256_file(apk)
    apk_manifest, apk_manifest_raw = inspect_apk_manifest(apk, aapt2)
    if apk_manifest["package"] is not None and apk_manifest["package"] != arguments.package:
        raise BenchmarkError(
            f"APK manifest package {apk_manifest['package']!r} does not match "
            f"--package {arguments.package!r}",
        )
    output = prepare_output(arguments.output)
    shutil.copyfile(DEFAULT_SCHEMA, output / DEFAULT_SCHEMA.name)
    shutil.copyfile(DEFAULT_FLOW_SCHEMA, output / DEFAULT_FLOW_SCHEMA.name)
    shutil.copyfile(flow_path, output / "android-gameplay-flow.json")
    if apk_manifest_raw is not None:
        write_text(output / "apk-manifest-aapt2.txt", apk_manifest_raw)
    apk_identity = {
        "path": logical_artifact_path(apk, repository, "external-apk"),
        "sha256": apk_sha,
        "bytes": apk.stat().st_size,
        "modifiedAtUnixNanos": apk.stat().st_mtime_ns,
        "manifest": apk_manifest,
    }
    report: dict[str, Any] = {
        "$schema": "android_device_benchmark.schema.json",
        "schemaVersion": SCHEMA_VERSION,
        "suite": SUITE_NAME,
        "status": "running",
        "runId": f"android-{dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')}",
        "createdAtUtc": utc_now(),
        "source": source,
        "apk": apk_identity,
        "tool": tool_provenance(adb, flow_path, repository, aapt2),
        "protocol": {
            "serials": serials,
            "forks": arguments.forks,
            "minimumFramesPerFork": arguments.minimum_frames,
            "startupSettleMillis": arguments.startup_settle_millis,
            "maximumThermalStatus": arguments.maximum_thermal_status,
            "minimumBatteryLevelPercent": arguments.minimum_battery_level,
            "package": arguments.package,
            "component": arguments.component,
            "installMode": "verify-existing" if arguments.skip_install else "replace-preserve-data",
            "flow": {
                "name": flow["name"],
                "schemaVersion": flow["schemaVersion"],
                "measurementStartStep": flow["measurementStartStep"],
                "stepCount": len(flow["steps"]),
                "sha256": sha256_file(flow_path),
                "hierarchyReuseStepIndexes": [
                    index
                    for index, step in enumerate(flow["steps"])
                    if step.get("reusePreviousHierarchy", False)
                ],
            },
            "deviceMutationPolicy": {
                "applicationDataCleared": False,
                "packageUninstalled": False,
                "globalSettingsChanged": False,
                "packageForceStoppedForColdStart": True,
                "screenWakeKeySent": True,
            },
        },
        "devices": [],
    }
    write_json(output / "android-device-benchmark.json", report)
    states = connected_devices(adb)
    for serial in serials:
        device_directory = output / "devices" / serial
        device_directory.mkdir(parents=True, exist_ok=False)
        device: dict[str, Any] = {"serial": serial, "status": "running", "forks": []}
        report["devices"].append(device)
        try:
            state = states.get(serial)
            if state != "device":
                raise BenchmarkError(
                    f"required adb target is unavailable or unauthorized: state={state!r}",
                )
            target = AdbTarget(adb, serial, arguments.command_timeout_seconds)
            package = install_and_verify_apk(
                target,
                apk,
                apk_sha,
                arguments.package,
                skip_install=arguments.skip_install,
                directory=device_directory,
                manifest_profileable_by_shell=apk_manifest[
                    "profileableByShellDeclared"
                ],
            )
            device["installedPackage"] = package
            wake_and_require_unlocked(target, device_directory)
            environment = capture_device_environment(target, device_directory)
            device["environment"] = environment
            if environment["lowPowerModeEnabled"]:
                raise BenchmarkError("low-power mode is enabled")
            require_runtime_health(
                {
                    "battery": environment["battery"],
                    "thermal": environment["thermal"],
                },
                maximum_thermal_status=arguments.maximum_thermal_status,
                minimum_battery_level=arguments.minimum_battery_level,
            )
            write_json(device_directory / "environment.json", environment)
            for fork_index in range(1, arguments.forks + 1):
                fork_directory = device_directory / f"fork-{fork_index:03d}"
                try:
                    fork = run_fork(
                        target,
                        fork_index=fork_index,
                        package_name=arguments.package,
                        component=arguments.component,
                        flow=flow,
                        directory=fork_directory,
                        minimum_frames=arguments.minimum_frames,
                        startup_settle_millis=arguments.startup_settle_millis,
                        maximum_thermal_status=arguments.maximum_thermal_status,
                        minimum_battery_level=arguments.minimum_battery_level,
                        api_level=environment["apiLevel"],
                    )
                except Exception as error:  # Retain partial evidence and continue the matrix.
                    fork_directory.mkdir(parents=True, exist_ok=True)
                    fork = error_result(fork_index, error, fork_directory)
                device["forks"].append(fork)
                write_json(output / "android-device-benchmark.json", report)
            device["aggregate"] = aggregate_device(device["forks"], arguments.forks)
            device["status"] = "ok"
        except Exception as error:  # Retain other devices even when one target fails closed.
            device["status"] = "error"
            device["error"] = {"type": type(error).__name__, "message": str(error)}
        write_json(device_directory / "device-result.json", device)
        write_json(output / "android-device-benchmark.json", report)
    report["status"] = (
        "ok" if report["devices"] and all(item["status"] == "ok" for item in report["devices"]) else "error"
    )
    report["completedAtUtc"] = utc_now()
    write_json(output / "android-device-benchmark.json", report)
    write_text(output / "android-device-benchmark.md", render_markdown(report))
    return report, output


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Collect refresh-aware KINETICKK benchmarks from physical Android devices.",
    )
    parser.add_argument("--apk", required=True, type=pathlib.Path)
    parser.add_argument("--repository", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--label")
    parser.add_argument("--adb")
    parser.add_argument("--serial", action="append", help="Repeat to select devices; defaults to the four-device lab matrix.")
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--component", default=DEFAULT_COMPONENT)
    parser.add_argument("--flow", type=pathlib.Path, default=DEFAULT_FLOW)
    parser.add_argument("--forks", type=parse_positive_int, default=DEFAULT_FORKS)
    parser.add_argument("--minimum-frames", type=parse_positive_int, default=DEFAULT_MIN_FRAMES)
    parser.add_argument("--startup-settle-millis", type=parse_non_negative_int, default=750)
    parser.add_argument("--maximum-thermal-status", type=parse_non_negative_int, default=0)
    parser.add_argument("--minimum-battery-level", type=parse_non_negative_int, default=50)
    parser.add_argument("--command-timeout-seconds", type=float, default=30.0)
    parser.add_argument(
        "--skip-install",
        action="store_true",
        help="Do not install; fail unless the installed base APK hash exactly matches --apk.",
    )
    return parser


def main() -> int:
    parser = build_parser()
    arguments = parser.parse_args()
    if arguments.command_timeout_seconds <= 0:
        parser.error("--command-timeout-seconds must be greater than zero")
    if arguments.minimum_battery_level > 100:
        parser.error("--minimum-battery-level must be in 0..100")
    try:
        report, output = run(arguments)
    except (BenchmarkError, OSError, ValueError) as error:
        print(f"Android device benchmark failed: {error}", file=sys.stderr)
        return 1
    print(output / "android-device-benchmark.json")
    print(output / "android-device-benchmark.md")
    return 0 if report["status"] == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
