# Branch governance 정책

## 결정

`bluetape4k-graph`의 canonical, GitHub default, release, SNAPSHOT branch는 모두
`develop`이다. `main`은 신규 변경을 받지 않는 `frozen-history-anchor`이며,
`857cf1298734254ebf1715825f5e229ddb974653`에서 동결한다.

정책의 machine-readable source of truth는
[`config/branch-governance.json`](../../config/branch-governance.json)이다.
GitHub default branch의 실제 설정과 branch protection은 이 파일의 값과 함께
검증해야 한다.

| 용도 | branch | 쓰기 정책 |
|---|---|---|
| 최종 merge 대상 | `develop` | 허용 |
| GitHub default | `develop` | 허용 |
| stable release source | `develop` | exact-head Full Nightly를 통과한 commit만 허용 |
| SNAPSHOT source | `develop` | workflow 계약에 따른 실행만 허용 |
| legacy history | `main` | 동결, 직접 push·PR merge·강제 이동 금지 |

## 분기 이력 분류

2026-09-07 점검 기준 merge base는
`62321b9c6ad29b5cd48a8e53a332a72369ef3586`였고, `main` 전용 10 commits와
`develop` 전용 772 commits가 존재했다.

`main` 전용 변경은 다음과 같이 분류했다.

- `f2653fa4`/`4ab7b695`, `9c9a75ce`/`14ed522b`는 change/revert 쌍으로 상쇄됐다.
- `a8a1bdff`의 초기 FalkorDB 구현은 현재 `develop`의 후속 구현과 검증으로
  대체됐다.
- `7d6cf238`, `0c2e7e13`, `f2d95956`, `857cf129`의 ignore·WIP·README visual
  변경은 현재 `develop`의 최신 파일에 의해 대체됐다.
- `311feaa7`은 위 WIP 변경을 포함하는 merge commit이다.

따라서 과거 tree를 content merge로 재도입하지 않고, 이력만 보존하는
tree-preserving merge를 선택했다.

## 정렬 불변식

정렬 commit `4d9d8d962a7bd47c1dd39eaee02d9825814724b4`는 다음을 만족한다.

- first parent는 정렬 직전 canonical head
  `203b14999f73157bff06f324ecada9072265eaaa`다.
- second parent는 동결 `main` head
  `857cf1298734254ebf1715825f5e229ddb974653`다.
- merge commit tree는 first-parent tree
  `337450408bd1a5bd0ea8e2815d95a90f033d5bc8`와 같다.
- 동결 `main`과 정렬 commit은 모두 이후 `develop` head의 ancestor여야 한다.

`.github/scripts/verify_branch_governance.py`는 이 불변식과 workflow event의
대상 branch를 fail-closed로 검증한다. `pull_request`의 base와 `push`의 ref가
`develop`이 아니면 실패하며, schedule과 manual dispatch에서는 현재 이력
불변식만 검증한다. 일반 build workflow는 `develop`만 활성 branch로 사용하고,
Branch Governance workflow만 `main` push·PR을 정책 위반으로 관찰한다.

## GitHub 보호 규칙

`develop`은 PR을 통한 변경만 허용하고 `Branch Governance / Verify branch
governance`를 required check로 사용한다. 기존 CI required check와 exact-head
검증 계약은 그대로 유지한다.

`main`은 동결 기준점을 유지한다.

- push, PR merge, force push, deletion을 허용하지 않는다.
- 관리자 우회도 허용하지 않는다.
- branch rename이나 삭제로 이력을 재작성하지 않는다.

Stacked PR train에서 review와 선행 변경 공유를 위해 semantic feature branch를
임시 base로 사용할 수 있다. 다만 최종 merge 전에 모든 PR을 `develop`으로
retarget하고, 각 exact head의 required checks를 다시 통과해야 한다. feature
branch는 canonical 또는 release source가 아니다.

보호 규칙 변경은 PR merge와 별도의 외부 상태 변경이다. 변경 직전에 live rule을
다시 읽고, 적용 뒤 default branch, required checks, force-push/deletion/admin
enforcement를 재조회해 이 문서와 일치하는지 확인한다.

## Release 확인

- tag는 성공한 exact-head Full Nightly의 `develop` commit에만 생성한다.
- release workflow의 `CANONICAL_BRANCH`는 policy와 같은 `develop`을 사용한다.
- checklist에는 tag SHA, Full Nightly run, release source를 기록한다.
- `main`을 release source나 동기화 대상으로 사용하지 않는다.

## 운영 검증

```bash
python3 .github/scripts/test_verify_branch_governance.py
python3 .github/scripts/verify_branch_governance.py \
  --policy config/branch-governance.json \
  --repository . \
  --canonical-ref HEAD \
  --legacy-ref origin/main \
  --event-name workflow_dispatch
actionlint .github/workflows/branch-governance.yml .github/workflows/release.yml
```

## DoD Status

- 상태: `PENDING`
- 완료: canonical/default/release/SNAPSHOT 정책, 독점 commit 분류,
  tree-preserving history 정렬, 회귀 검증기와 workflow, release workflow 기준 정렬
- 미완료: PR exact-head CI, merge 후 GitHub branch protection 적용·read-back
