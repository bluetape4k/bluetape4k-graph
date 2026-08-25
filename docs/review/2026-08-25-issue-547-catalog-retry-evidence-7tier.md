# #547 catalog ownership·retry-only CI evidence 7-Tier review

## 범위와 기준

- Issue: [#547](https://github.com/bluetape4k/bluetape4k-graph/issues/547)
- Branch: `fix/issue-547-catalog-retry-evidence`
- Base: PR #567 (`fix/issue-546-example-teardown`) exact head
  `75e45556f22994bf46b8aaab297747845669e0e4`
- Scope: `gradle/libs.versions.toml`의 미사용 local `bluetape4k` alias,
  `examples.yml` build retry와 `ci.yml` core-test retry evidence, helper
  workflow routing 및 필수 회귀 job
- Out of scope: 다른 기존 Gradle retry loop의 일괄 마이그레이션. 후속 이슈에서
  같은 helper/evidence 계약으로 정렬해야 한다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·구성 | PASS | immutable `bt4k` catalog를 보존한 상태에서 local alias를 제거했고 `./gradlew help --no-daemon --no-configuration-cache --console=plain`이 성공했다. |
| T2 동작 계약 | PASS | 공통 helper가 first-attempt `success`, retry 후 `success_after_retry`, bounded `failed`를 분리하고 attempt 수와 retry 수를 output/summary에 기록한다. |
| T3 실패·예외 | PASS | 각 시도 stdout/stderr와 첫 실패 log를 보존하며, 최대 시도 후 마지막 non-zero exit code를 반환한다. command 또는 `tee`가 실패하거나 evidence 파일·output을 쓸 수 없으면 helper가 fail-closed exit code `74`를 반환한다. retry로 성공해도 첫 실패 evidence를 삭제하지 않는다. |
| T4 보안·노출 | PASS/WATCH | 새 로그에는 실행 command와 Gradle output이 남으므로 호출 step은 secret을 인자로 전달하지 않아야 한다. 이번 두 command에는 credential 인자가 없으며, helper가 값을 새로 출력하거나 dependency를 추가하지 않는다. |
| T5 운영·관찰성 | PASS | `GITHUB_STEP_SUMMARY`, `GITHUB_OUTPUT`, always-upload artifact를 통해 retry-only green을 정상 green과 구분한다. artifact는 `${RUNNER_TEMP}/bluetape4k-retry/<name>/`에 격리하고, evidence가 없으면 upload action도 실패한다. helper와 회귀 테스트 경로는 두 workflow에 라우팅되고 `ci.yml`의 필수 `retry-helper` job으로 실행된다. |
| T6 ecosystem·패턴 | PASS | `settings.gradle.kts`가 소유한 immutable `bt4k` catalog와 leaf catalog ownership을 문서화하고, Gradle 기존 명령·workflow contract를 재사용했다. 새 runtime dependency는 없다. |
| T7 문서·인계 | PASS/WATCH | governance 문서, `CHANGELOG.md`, `WIP.md`, lesson과 이 review를 갱신한다. hosted exact-head CI와 최종 train merge는 마지막 승인 단계로 남긴다. |

## 검증 증거

- `actionlint .github/workflows/examples.yml .github/workflows/ci.yml` — PASS.
- `bash -n .github/scripts/run-gradle-retry.sh` — PASS.
- `python3 .github/scripts/test_run_gradle_retry.py` — 5 tests, PASS.
- helper/test path routing과 `retry-helper` 필수 job 정적 확인 — PASS.
- helper 수동 fake 검증 — first-attempt success, retry 후 success, bounded
  failure, first-failure log 보존, evidence root/`tee` write failure fail-closed를
  모두 확인했다.
- `./gradlew help --no-daemon --no-configuration-cache --console=plain` —
  BUILD SUCCESSFUL.
- `libs.versions.*`의 제거 alias accessor 검색 — 잔존 0건.
- `git diff --check` — PASS.

## DoD Status

- [x] local `bluetape4k` alias ownership을 제거·문서화했다.
- [x] 두 지정 retry step이 첫 실패 log, attempt log, retry count와
  `success_after_retry`를 노출한다.
- [x] helper의 성공·retry·최종실패와 evidence write failure 회귀 테스트를
  추가하고 통과했다.
- [x] workflow lint와 Gradle catalog resolution을 통과했다.
- [x] Korean governance/WIP/CHANGELOG와 7-Tier review/lesson을 기록한다.
- [ ] hosted exact-head CI/review와 최종 train merge — 최종 승인 단계에서
  수행한다.

최종 판정: **PASS/WATCH**. 구현과 로컬 검증은 완료됐으며, PR #568 exact
head `254ddcdaaa5f4eab8e496b0bdcb38c9dc5b7699a`의 hosted 검증을 대기한다.
`success_after_retry` green은 첫 실패 원인과 artifact 확인 전까지 merge-ready가
아니며, 병합은 전체 train의 마지막 사용자 승인 전까지 보류한다.
