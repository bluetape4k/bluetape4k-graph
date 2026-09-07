#!/usr/bin/env python3
"""branch governance 검증기의 회귀 테스트."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("verify_branch_governance.py")


def load_verifier():
    spec = importlib.util.spec_from_file_location("verify_branch_governance", SCRIPT_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"검증기 module을 불러올 수 없습니다: {SCRIPT_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def git(repository: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repository,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


class BranchGovernanceVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.verifier = load_verifier()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repository = Path(self.temp_dir.name)
        git(self.repository, "init", "--initial-branch=develop")
        git(self.repository, "config", "user.name", "Branch Policy Test")
        git(self.repository, "config", "user.email", "branch-policy@example.com")

        self._commit("root.txt", "root\n", "root")
        root = git(self.repository, "rev-parse", "HEAD")

        git(self.repository, "switch", "-c", "main", root)
        self._commit("legacy.txt", "legacy\n", "legacy")
        self.legacy_head = git(self.repository, "rev-parse", "HEAD")

        git(self.repository, "switch", "develop")
        self._commit("develop.txt", "develop\n", "develop")
        git(self.repository, "merge", "--no-ff", "-s", "ours", "main", "-m", "align")
        self.alignment_commit = git(self.repository, "rev-parse", "HEAD")
        self.policy_path = self.repository / "branch-governance.json"
        self._write_policy()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _commit(self, relative_path: str, content: str, message: str) -> None:
        path = self.repository / relative_path
        path.write_text(content, encoding="utf-8")
        git(self.repository, "add", relative_path)
        git(self.repository, "commit", "-m", message)

    def _write_policy(self, **overrides: object) -> None:
        policy: dict[str, object] = {
            "schema_version": 1,
            "canonical_branch": "develop",
            "default_branch": "develop",
            "release_branch": "develop",
            "snapshot_branch": "develop",
            "legacy_branches": {
                "main": {
                    "mode": "frozen-history-anchor",
                    "frozen_head": self.legacy_head,
                    "alignment_commit": self.alignment_commit,
                }
            },
        }
        policy.update(overrides)
        self.policy_path.write_text(
            json.dumps(policy, indent=2) + "\n",
            encoding="utf-8",
        )

    def verify(self, **overrides: str):
        arguments = {
            "policy_path": self.policy_path,
            "repository": self.repository,
            "canonical_ref": "develop",
            "legacy_ref": "main",
            "event_name": "pull_request",
            "base_ref": "develop",
            "ref_name": "604/merge",
        }
        arguments.update(overrides)
        return self.verifier.verify_branch_governance(**arguments)

    def test_accepts_tree_preserving_alignment_on_canonical_branch(self) -> None:
        report = self.verify()

        self.assertEqual("develop", report.canonical_branch)
        self.assertEqual(self.legacy_head, report.legacy_head)
        self.assertEqual(self.alignment_commit, report.alignment_commit)

    def test_rejects_moved_legacy_branch(self) -> None:
        git(self.repository, "switch", "main")
        self._commit("unexpected.txt", "moved\n", "move legacy")

        with self.assertRaisesRegex(self.verifier.GovernanceError, "동결 head"):
            self.verify()

    def test_rejects_alignment_that_changed_the_canonical_tree(self) -> None:
        git(self.repository, "switch", "develop")
        git(self.repository, "reset", "--hard", "HEAD^")
        git(self.repository, "merge", "--no-ff", "main", "-m", "content merge")
        content_merge = git(self.repository, "rev-parse", "HEAD")
        self._write_policy()
        policy = json.loads(self.policy_path.read_text(encoding="utf-8"))
        policy["legacy_branches"]["main"]["alignment_commit"] = content_merge
        self.policy_path.write_text(json.dumps(policy), encoding="utf-8")

        with self.assertRaisesRegex(self.verifier.GovernanceError, "tree"):
            self.verify()

    def test_rejects_canonical_ref_without_alignment_commit(self) -> None:
        canonical_before_alignment = git(self.repository, "rev-parse", "develop^")

        with self.assertRaisesRegex(self.verifier.GovernanceError, "정렬 commit"):
            self.verify(canonical_ref=canonical_before_alignment)

    def test_rejects_pull_request_to_legacy_branch(self) -> None:
        with self.assertRaisesRegex(self.verifier.GovernanceError, "PR base"):
            self.verify(base_ref="main")

    def test_rejects_push_to_legacy_branch(self) -> None:
        with self.assertRaisesRegex(self.verifier.GovernanceError, "push branch"):
            self.verify(event_name="push", base_ref="", ref_name="main")

    def test_rejects_inconsistent_policy_branches(self) -> None:
        self._write_policy(release_branch="main")

        with self.assertRaisesRegex(self.verifier.GovernanceError, "canonical branch"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
