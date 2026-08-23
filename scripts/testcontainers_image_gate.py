#!/usr/bin/env python3
"""Manifest validation and deterministic family selection for graph image gates."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Set


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "scripts/testcontainers_image_gate_manifest.json"
EXPECTED_FAMILY_COUNT = 4
REQUIRED_FIELDS = {
    "id",
    "image",
    "tag",
    "testTask",
    "testPattern",
    "testSource",
    "pathPrefixes",
    "readiness",
    "workload",
    "diagnostics",
    "releaseRequired",
}
KNOWN_TEST_TASKS = {
    ":bluetape4k-graph-neo4j:test",
    ":bluetape4k-graph-memgraph:test",
    ":bluetape4k-graph-age:test",
    ":bluetape4k-graph-falkordb:test",
}


def load_manifest(path: Path = MANIFEST) -> List[Dict[str, Any]]:
    payload = _load_payload(path)
    entries = payload.get("families")
    if not isinstance(entries, list):
        raise ValueError("graph image gate manifest families must be a list: %s" % path)
    if not all(isinstance(entry, dict) for entry in entries):
        raise ValueError("graph image gate manifest families must contain objects: %s" % path)
    return [dict(entry) for entry in entries]


def _load_payload(path: Path) -> Dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or payload.get("version") != 1:
        raise ValueError("unsupported graph image gate manifest format: %s" % path)
    return payload


def load_shared_paths(path: Path = MANIFEST) -> List[str]:
    payload = _load_payload(path)
    shared_paths = payload.get("sharedPaths")
    if not isinstance(shared_paths, list) or not all(isinstance(path, str) for path in shared_paths):
        raise ValueError("graph image gate manifest sharedPaths must be a string list: %s" % path)
    return list(shared_paths)


def _lines(path: Path) -> List[str]:
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def _image_map(root: Path) -> Dict[str, str]:
    images: Dict[str, str] = {}
    for reference in _lines(root / ".github/testcontainers-images.txt"):
        image, tag = reference.rsplit(":", 1)
        images[image] = tag
    return images


def _family_map(root: Path) -> Dict[str, str]:
    families: Dict[str, str] = {}
    for line in _lines(root / ".github/testcontainers-image-families.txt"):
        family, image = line.split("=", 1)
        families[family.strip()] = image.strip()
    return families


def _matches(path: str, prefix: str) -> bool:
    normalized_path = path.replace("\\", "/").lstrip("./")
    normalized_prefix = prefix.replace("\\", "/").lstrip("./")
    return normalized_path == normalized_prefix or normalized_path.startswith(normalized_prefix)


def validate_manifest(entries: Iterable[Dict[str, Any]], root: Path = ROOT) -> List[str]:
    entries = list(entries)
    errors: List[str] = []
    if len(entries) != EXPECTED_FAMILY_COUNT:
        errors.append("family count %d != %d" % (len(entries), EXPECTED_FAMILY_COUNT))

    names: Set[str] = set()
    try:
        images = _image_map(root)
    except (OSError, ValueError) as error:
        images = {}
        errors.append("invalid .github/testcontainers-images.txt: %s" % error)
    try:
        families = _family_map(root)
    except (OSError, ValueError) as error:
        families = {}
        errors.append("invalid .github/testcontainers-image-families.txt: %s" % error)
    for index, entry in enumerate(entries):
        prefix = "families[%d]" % index
        missing = REQUIRED_FIELDS - set(entry)
        if missing:
            errors.append("%s missing fields: %s" % (prefix, ", ".join(sorted(missing))))
            continue

        family_id = entry["id"]
        if not isinstance(family_id, str) or not family_id:
            errors.append("%s id must be a non-empty string" % prefix)
            continue
        if family_id in names:
            errors.append("duplicate family id: %s" % family_id)
        names.add(family_id)

        image = entry["image"]
        tag = entry["tag"]
        if image not in images:
            errors.append("image drift for %s: unknown image %s" % (family_id, image))
        elif images[image] != tag:
            errors.append("image drift for %s: %s != %s" % (family_id, tag, images[image]))
        if families.get(family_id) != image:
            errors.append("family map drift for %s: %s != %s" % (family_id, image, families.get(family_id)))
        if entry.get("testTask") not in KNOWN_TEST_TASKS:
            errors.append("unknown test task for %s: %s" % (family_id, entry.get("testTask")))
        if not isinstance(entry.get("pathPrefixes"), list) or not entry["pathPrefixes"]:
            errors.append("pathPrefixes is empty for %s" % family_id)
        if not isinstance(entry.get("diagnostics"), list) or not entry["diagnostics"]:
            errors.append("diagnostics is empty for %s" % family_id)
        for field in ("readiness", "workload", "testPattern"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                errors.append("%s is empty for %s" % (field, family_id))
        if entry.get("releaseRequired") is not True:
            errors.append("releaseRequired must be true for %s" % family_id)

        source = root / str(entry["testSource"])
        if not source.is_file():
            errors.append("missing test source for %s: %s" % (family_id, entry["testSource"]))
        else:
            class_name = str(entry["testPattern"]).rsplit(".", 1)[-1]
            if ("class %s" % class_name) not in source.read_text(encoding="utf-8"):
                errors.append("test pattern drift for %s: %s" % (family_id, entry["testPattern"]))

    expected = set(families)
    errors.extend("manifest missing family: %s" % name for name in sorted(expected - names))
    errors.extend("manifest has unknown family: %s" % name for name in sorted(names - expected))
    return errors


def select_entries(
    entries: List[Dict[str, Any]],
    changed_paths: Set[str],
    scope: str = "changed",
    shared_paths: Optional[Iterable[str]] = None,
) -> List[Dict[str, Any]]:
    """Select changed families, or all families for full/nightly/release gates."""

    if scope not in {"changed", "full"}:
        raise ValueError("unsupported image gate scope: %s" % scope)
    if scope == "full":
        return list(entries)

    normalized = {path.replace("\\", "/").lstrip("./") for path in changed_paths}
    if not normalized:
        return []
    shared_prefixes = tuple(shared_paths) if shared_paths is not None else tuple(load_shared_paths())
    if any(_matches(path, prefix) for path in normalized for prefix in shared_prefixes):
        return list(entries)

    selected: List[Dict[str, Any]] = []
    for entry in entries:
        prefixes = [str(prefix) for prefix in entry.get("pathPrefixes", [])]
        if any(_matches(path, prefix) for path in normalized for prefix in prefixes):
            selected.append(entry)
    return selected
