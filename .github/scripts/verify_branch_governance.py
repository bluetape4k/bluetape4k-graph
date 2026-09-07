#!/usr/bin/env python3
"""Canonical branch와 동결 legacy branch의 정렬 계약을 검증한다."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, NamedTuple, Optional, Sequence


class GovernanceError(RuntimeError):
    """Branch governance 계약 위반."""


class GovernanceReport(NamedTuple):
    canonical_branch: str
    legacy_branch: str
    legacy_head: str
    alignment_commit: str


def _git(
    repository: Path, *arguments: str, check: bool = True
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["git", *arguments],
        cwd=repository,
        check=False,
        capture_output=True,
        text=True,
    )
    if check and result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or "unknown git error"
        raise GovernanceError(f"git {' '.join(arguments)} 실패: {detail}")
    return result


def _load_policy(policy_path: Path) -> dict[str, Any]:
    try:
        policy = json.loads(policy_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise GovernanceError(f"branch policy를 읽을 수 없습니다: {error}") from error
    if not isinstance(policy, dict):
        raise GovernanceError("branch policy root는 object여야 합니다")
    if policy.get("schema_version") != 1:
        raise GovernanceError("지원하는 schema_version은 1입니다")
    return policy


def _required_string(mapping: dict[str, Any], key: str) -> str:
    value = mapping.get(key)
    if not isinstance(value, str) or not value.strip():
        raise GovernanceError(
            f"branch policy의 {key}는 비어 있지 않은 문자열이어야 합니다"
        )
    return value


def _resolve_commit(repository: Path, reference: str) -> str:
    return _git(
        repository, "rev-parse", "--verify", f"{reference}^{{commit}}"
    ).stdout.strip()


def _parents(repository: Path, commit: str) -> list[str]:
    tokens = _git(repository, "show", "-s", "--format=%P", commit).stdout.split()
    return tokens


def _tree(repository: Path, commit: str) -> str:
    return _git(repository, "show", "-s", "--format=%T", commit).stdout.strip()


def _require_ancestor(
    repository: Path, ancestor: str, descendant: str, label: str
) -> None:
    result = _git(
        repository,
        "merge-base",
        "--is-ancestor",
        ancestor,
        descendant,
        check=False,
    )
    if result.returncode != 0:
        raise GovernanceError(
            f"{label}: {ancestor}가 {descendant}의 ancestor가 아닙니다"
        )


def _verify_event(
    canonical_branch: str,
    event_name: str,
    base_ref: str,
    ref_name: str,
) -> None:
    if event_name == "pull_request" and base_ref != canonical_branch:
        raise GovernanceError(
            f"PR base는 canonical branch {canonical_branch}여야 합니다: {base_ref or '<empty>'}"
        )
    if event_name == "push" and ref_name != canonical_branch:
        raise GovernanceError(
            f"push branch는 canonical branch {canonical_branch}여야 합니다: {ref_name or '<empty>'}"
        )
    supported_events = {"pull_request", "push", "schedule", "workflow_dispatch"}
    if event_name not in supported_events:
        raise GovernanceError(f"지원하지 않는 workflow event입니다: {event_name}")


def verify_branch_governance(
    *,
    policy_path: Path,
    repository: Path,
    canonical_ref: str,
    legacy_ref: str,
    event_name: str,
    base_ref: str,
    ref_name: str,
) -> GovernanceReport:
    """정책, ref, ancestry, tree-equivalence와 workflow event를 fail-closed로 검증한다."""
    policy = _load_policy(policy_path)
    canonical_branch = _required_string(policy, "canonical_branch")
    for field in ("default_branch", "release_branch", "snapshot_branch"):
        branch = _required_string(policy, field)
        if branch != canonical_branch:
            raise GovernanceError(
                f"{field}는 canonical branch {canonical_branch}와 같아야 합니다: {branch}"
            )

    legacy_branches = policy.get("legacy_branches")
    if not isinstance(legacy_branches, dict) or len(legacy_branches) != 1:
        raise GovernanceError("legacy_branches에는 동결 branch 하나가 필요합니다")
    legacy_branch, legacy_policy = next(iter(legacy_branches.items()))
    if not isinstance(legacy_branch, str) or not isinstance(legacy_policy, dict):
        raise GovernanceError("legacy branch policy 형식이 올바르지 않습니다")
    if legacy_policy.get("mode") != "frozen-history-anchor":
        raise GovernanceError("legacy branch mode는 frozen-history-anchor여야 합니다")

    frozen_head = _required_string(legacy_policy, "frozen_head")
    alignment_commit = _required_string(legacy_policy, "alignment_commit")
    resolved_legacy = _resolve_commit(repository, legacy_ref)
    if resolved_legacy != frozen_head:
        raise GovernanceError(
            f"legacy branch {legacy_branch}의 동결 head가 변경됐습니다: "
            f"expected={frozen_head} actual={resolved_legacy}"
        )

    resolved_alignment = _resolve_commit(repository, alignment_commit)
    parents = _parents(repository, resolved_alignment)
    if len(parents) != 2:
        raise GovernanceError(
            "정렬 commit은 parent가 정확히 2개인 merge commit이어야 합니다"
        )
    if parents[1] != frozen_head:
        raise GovernanceError(
            f"정렬 commit의 두 번째 parent가 동결 head와 다릅니다: {parents[1]}"
        )
    if _tree(repository, resolved_alignment) != _tree(repository, parents[0]):
        raise GovernanceError(
            "정렬 commit tree가 canonical first-parent tree를 변경했습니다"
        )

    resolved_canonical = _resolve_commit(repository, canonical_ref)
    _require_ancestor(
        repository,
        resolved_alignment,
        resolved_canonical,
        "정렬 commit ancestry 위반",
    )
    _require_ancestor(
        repository,
        frozen_head,
        resolved_canonical,
        "legacy branch ancestry 위반",
    )
    _verify_event(canonical_branch, event_name, base_ref, ref_name)

    return GovernanceReport(
        canonical_branch=canonical_branch,
        legacy_branch=legacy_branch,
        legacy_head=frozen_head,
        alignment_commit=resolved_alignment,
    )


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--repository", type=Path, default=Path.cwd())
    parser.add_argument("--canonical-ref", required=True)
    parser.add_argument("--legacy-ref", required=True)
    parser.add_argument("--event-name", required=True)
    parser.add_argument("--base-ref", default="")
    parser.add_argument("--ref-name", default="")
    return parser


def main(arguments: Optional[Sequence[str]] = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(arguments)
    try:
        report = verify_branch_governance(
            policy_path=args.policy,
            repository=args.repository,
            canonical_ref=args.canonical_ref,
            legacy_ref=args.legacy_ref,
            event_name=args.event_name,
            base_ref=args.base_ref,
            ref_name=args.ref_name,
        )
    except GovernanceError as error:
        print(f"branch-governance: FAIL: {error}", file=sys.stderr)
        return 1

    print(
        "branch-governance: PASS "
        f"canonical={report.canonical_branch} "
        f"legacy={report.legacy_branch}@{report.legacy_head} "
        f"alignment={report.alignment_commit}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
