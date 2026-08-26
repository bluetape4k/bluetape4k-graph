#!/usr/bin/env python3
"""Verify the published core/API package boundary and module-path contract."""

from __future__ import annotations

import argparse
import subprocess
import sys
import zipfile
from pathlib import Path
from typing import Iterable, Set


CORE_PACKAGE = "io.bluetape4k.concurrent.virtualthread"
API_PACKAGE = "io.bluetape4k.concurrent.virtualthread.api"
API_TYPES = {
    "StructuredScopes",
    "StructuredSubtask",
    "StructuredTaskScopeAll",
    "StructuredTaskScopeAny",
    "StructuredTaskScopeProvider",
    "StructuredTaskScopeSupervised",
    "StructuredTaskScopes",
    "TaskContext",
    "VirtualThreadRuntime",
    "VirtualThreads",
}


def _class_packages(entries: Iterable[str]) -> Set[str]:
    packages: Set[str] = set()
    for entry in entries:
        if not entry.endswith(".class"):
            continue
        package = entry.rsplit("/", 1)[0].replace("/", ".")
        packages.add(package)
    return packages


def _classes(jar: Path) -> Set[str]:
    with zipfile.ZipFile(jar) as archive:
        return {entry for entry in archive.namelist() if entry.endswith(".class")}


def _api_type_present(api_classes: Iterable[str], type_name: str) -> bool:
    prefix = f"{API_PACKAGE.replace('.', '/')}/{type_name}"
    return any(Path(entry).name.startswith(type_name) and entry.startswith(prefix) for entry in api_classes)


def _validate_modules(core: Path, api: Path, java_command: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [java_command, "--validate-modules", "--module-path", f"{core}:{api}"],
        check=False,
        capture_output=True,
        text=True,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("core_jar", type=Path)
    parser.add_argument("api_jar", type=Path)
    parser.add_argument("--java", default="java", help="java executable used for module validation")
    args = parser.parse_args()

    core = args.core_jar.resolve()
    api = args.api_jar.resolve()
    missing = [str(path) for path in (core, api) if not path.is_file()]
    if missing:
        parser.error("missing JAR: " + ", ".join(missing))

    core_classes = _classes(core)
    api_classes = _classes(api)
    core_packages = _class_packages(core_classes)
    api_packages = _class_packages(api_classes)
    shared_packages = sorted(core_packages & api_packages)
    legacy_api_classes = sorted(
        entry
        for entry in api_classes
        if entry.startswith(f"{CORE_PACKAGE.replace('.', '/')}/")
        and not entry.startswith(f"{API_PACKAGE.replace('.', '/')}/")
    )
    missing_api_types = sorted(
        type_name for type_name in API_TYPES if not _api_type_present(api_classes, type_name)
    )

    print(f"core={core}")
    print(f"api={api}")
    print(f"core_packages={len(core_packages)} api_packages={len(api_packages)}")
    print(f"shared_packages={shared_packages}")
    print(f"legacy_api_classes={legacy_api_classes}")
    print(f"missing_api_types={missing_api_types}")

    validation = _validate_modules(core, api, args.java)
    print(f"validate_modules_rc={validation.returncode}")
    if validation.stdout.strip():
        print(validation.stdout.strip())
    if validation.stderr.strip():
        print(validation.stderr.strip(), file=sys.stderr)

    if shared_packages or legacy_api_classes or missing_api_types:
        return 1
    return validation.returncode


if __name__ == "__main__":
    raise SystemExit(main())
