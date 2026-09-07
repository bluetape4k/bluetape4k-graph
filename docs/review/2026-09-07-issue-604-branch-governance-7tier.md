# #604 branch governance 7-Tier review

## 범위와 기준

- Issue: [#604](https://github.com/bluetape4k/bluetape4k-graph/issues/604)
- Branch: `chore/issue-604-branch-governance`
- Review base: `feat/issue-618-common-resource-registry`
  `203b14999f73157bff06f324ecada9072265eaaa`
- 구현 review head: `140cc524b8e03b6542f955b2da15ea946c968c18`
- History alignment:
  `4d9d8d962a7bd47c1dd39eaee02d9825814724b4`
- Scope: canonical/default/release/SNAPSHOT branch 정책, legacy `main` 이력
  정렬, Git object 회귀 검증, workflow routing, EN/KO README와 운영 문서

Native `code-reviewer` lane이 exact implementation head를 독립 검토했다. 최초
review에서 `main` drift 감지 지연 P1과 symbolic alignment ref 허용 P2를 찾았고,
수정 뒤 최신 head를 다시 검토해 코드상 P0/P1/P2/P3=0 `COMMENT` 판정을 내렸다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API | PASS | Python 3.9 로컬 실행에서 18개 unit test와 실제 verifier가 통과했다. Kotlin/public API 변경은 없다. JSON schema version과 필수 문자열을 fail-closed로 확인한다. |
| T2 기능·호환성 | PASS | `develop`을 canonical/default/release/SNAPSHOT source로 고정하고 일반 build workflow에서 `main`을 제거한다. Stacked PR 임시 base 지원은 유지한다. |
| T3 실패·동시성·수명주기 | PASS | ref 해석, Git command, parent 수, tree-equivalence, ancestry가 실패하면 non-zero로 종료한다. 전용 workflow는 `develop`과 `main` push/PR, weekly schedule, manual dispatch를 감시하며 concurrency를 bounded하게 취소한다. |
| T4 보안·오류 노출 | PASS | `frozen_head`와 `alignment_commit`을 40자리 lowercase full SHA로 제한해 symbolic ref 이동을 거부한다. Token·credential·file content를 출력하지 않고 commit SHA와 구조적 오류만 보고한다. |
| T5 성능·boundedness | PASS | 검증은 고정된 수의 local Git subprocess와 8개 임시 저장소 test로 끝난다. Network fan-out, retry loop, daemon이나 unbounded queue가 없다. |
| T6 ecosystem·패턴 | PASS | 기존 workflow와 Python stdlib를 사용하며 신규 dependency를 추가하지 않았다. Release의 Nightly 조회, SNAPSHOT, Dependabot과 README의 기준 branch를 `develop`으로 맞췄다. |
| T7 테스트·문서·CI | PASS/PENDING | Governance 8/8, routing 10/10, Ruff, 변경 workflow 5개 `actionlint`, 실제 Git history 검증, diff check가 통과했다. Hosted exact-head CI와 GitHub branch protection 적용·read-back은 PR/merge gate다. |

## Finding과 처리

| 심각도 | Finding | 처리 |
|---|---|---|
| P1 | Governance workflow가 `develop`만 관찰하면 `main` push·PR 위반 검출이 schedule까지 늦어진다. | 일반 CI·Examples·Testcontainers는 `develop` 전용으로 유지하고 Branch Governance만 `main` push·PR을 관찰해 `_verify_event`에서 실패시킨다. |
| P2 | `alignment_commit`이 symbolic ref여도 `rev-parse` 후 통과해 immutable 정책을 우회할 수 있다. | 동결 head와 정렬 commit 모두 40자리 lowercase full SHA로 제한하고 symbolic `develop` 거부 회귀를 추가했다. |
| P1 외부 gate | Live `develop`/`main` protection은 required checks가 없고 admin enforcement가 비활성이라 정책 문서와 아직 다르다. | 코드 finding과 분리한다. 최종 merge 승인 뒤 `develop` required checks/admin enforcement와 `main` lock/admin enforcement를 적용하고 API read-back이 통과하기 전에는 merge하지 않는다. |

최신 독립 판정은 코드상 P0=0, P1=0, P2=0, P3=0이다. GitHub protection은
외부 상태 P1 merge gate로 `PENDING`이며 PR 생성 자체를 막지는 않는다.

## 검증 증거

- RED: verifier 구현 전 7개 test가 missing module로 실패했다.
- GREEN: branch governance 8/8, CI routing policy 10/10 통과.
- `ruff check`와 `ruff format --check`: PASS. Python 3.9 호환을 위해 3.10 전용
  union syntax 관련 rule은 적용하지 않았다.
- 실제 verifier: frozen
  `main@857cf1298734254ebf1715825f5e229ddb974653`, alignment
  `4d9d8d962a7bd47c1dd39eaee02d9825814724b4` PASS.
- Alignment parents: first
  `203b14999f73157bff06f324ecada9072265eaaa`, second
  `857cf1298734254ebf1715825f5e229ddb974653`.
- Alignment tree와 first-parent tree:
  `337450408bd1a5bd0ea8e2815d95a90f033d5bc8`로 동일.
- `origin/main`과 alignment commit 모두 구현 head의 ancestor.
- 변경 workflow 5개 `actionlint`: PASS.
- `git diff --check`: PASS.
- 독립 reviewer의 LSP 도구는 unavailable이어서 `PENDING`; Python compile/unit
  execution으로 대체했으며 미실행 provenance를 PASS로 표기하지 않는다.

## DoD Status

- [x] canonical/default/release/SNAPSHOT branch를 `develop`으로 고정했다.
- [x] `main` 독점 commit을 분류하고 tree-preserving merge로 이력을 보존했다.
- [x] frozen SHA, merge parents, tree-equivalence와 ancestry를 회귀로 고정했다.
- [x] 일반 build와 전용 policy 감시 workflow의 `main` 역할을 분리했다.
- [x] Release, SNAPSHOT, Dependabot, README, WIP, CHANGELOG, checklist와 과거 계획의
  branch 설명을 정렬했다.
- [x] 독립 exact-head review의 코드 P0/P1/P2/P3가 모두 0이다.
- [ ] PR exact-head hosted CI와 live review/thread read-back은 PR 생성 후 수행한다.
- [ ] GitHub branch protection 적용·read-back은 최종 batch merge 승인 뒤 수행한다.

현재 판정: **PASS/PENDING**. 코드와 local evidence는 PR 생성 가능하며, 외부
protection과 hosted exact-head gate 전에는 merge-ready가 아니다.
