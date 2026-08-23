#!/usr/bin/env python3
"""Test the graph Testcontainers image-family gate contract."""

from __future__ import annotations

import unittest
from pathlib import Path

from scripts.testcontainers_image_gate import (
    EXPECTED_FAMILY_COUNT,
    load_manifest,
    select_entries,
    validate_manifest,
)


class TestTestcontainersImageGate(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.root = Path(__file__).resolve().parents[1]
        cls.manifest_path = cls.root / "scripts/testcontainers_image_gate_manifest.json"
        cls.entries = load_manifest(cls.manifest_path)

    def test_ci_changed_gate_and_status_coverage_are_wired(self) -> None:
        workflow = (self.root / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("testcontainers-image-gate:", workflow)
        self.assertIn("--scope changed", workflow)
        self.assertIn("--changed-path-file", workflow)
        self.assertIn("path: build/reports/testcontainers-image-gate/", workflow)
        status_start = workflow.index("ci-status:")
        self.assertIn("- testcontainers-image-gate", workflow[status_start:])

    def test_nightly_full_gate_and_status_coverage_are_wired(self) -> None:
        workflow = (self.root / ".github/workflows/nightly-tests.yml").read_text(encoding="utf-8")
        self.assertIn("testcontainers-image-gate:", workflow)
        self.assertIn("--scope full", workflow)
        status_start = workflow.index("nightly-status:")
        self.assertIn("- testcontainers-image-gate", workflow[status_start:])

    def test_release_publish_depends_on_exact_full_gate(self) -> None:
        workflow = (self.root / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("testcontainers-image-gate:", workflow)
        self.assertIn("--scope full", workflow)
        self.assertIn("test \"$(jq -r '.coverage' \"$summary\")\" = \"4/4\"", workflow)
        self.assertIn("needs: [resolve-version, testcontainers-image-gate]", workflow)

    def test_manifest_covers_all_graph_families(self) -> None:
        self.assertEqual(EXPECTED_FAMILY_COUNT, len(self.entries))
        self.assertEqual([], validate_manifest(self.entries, self.root))

    def test_changed_fixture_selects_only_the_affected_family(self) -> None:
        changed = select_entries(
            self.entries,
            {"graph/graph-neo4j/src/testFixtures/kotlin/io/bluetape4k/graph/neo4j/Neo4jServer.kt"},
        )
        self.assertEqual(["neo4j"], [entry["id"] for entry in changed])

    def test_backend_test_source_selects_only_the_affected_family(self) -> None:
        changed = select_entries(
            self.entries,
            {"graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphCapabilityConformanceTest.kt"},
        )
        self.assertEqual(["falkordb"], [entry["id"] for entry in changed])

    def test_manifest_or_shared_launcher_change_selects_all_families(self) -> None:
        for path in (
            ".github/testcontainers-images.txt",
            ".github/testcontainers-image-families.txt",
            "settings.gradle.kts",
            "gradle/libs.versions.toml",
        ):
            with self.subTest(path=path):
                selected = select_entries(self.entries, {path})
                self.assertEqual(EXPECTED_FAMILY_COUNT, len(selected))

    def test_empty_changed_scope_is_skipped_and_full_scope_is_complete(self) -> None:
        self.assertEqual([], select_entries(self.entries, set()))
        self.assertEqual(self.entries, select_entries(self.entries, set(), scope="full"))

    def test_invalid_manifest_reports_image_and_test_drift(self) -> None:
        invalid = [dict(self.entries[0], image="wrong/image", testPattern="missing.Test")]
        errors = validate_manifest(invalid, self.root)
        message = " ".join(errors)
        self.assertIn("image drift", message)
        self.assertIn("test pattern drift", message)


if __name__ == "__main__":
    unittest.main(verbosity=2)
