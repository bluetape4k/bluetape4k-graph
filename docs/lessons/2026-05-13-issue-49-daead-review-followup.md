# 이슈 #49 DAEAD Review Follow-up

- Date: 2026-05-13
- 범위: `graph-okio`, CI gitleaks installer

## 맥락

PR #114 was merged after adding graph-okio DAEAD chunk encryption, but the follow-up 6-tier Codex + Claude review found two P1 gaps:

- touched tests used `kotlin.test.assertFailsWith` instead of `io.bluetape4k.assertions.assertFailsWith`
- the spec/plan promised a truncated ciphertext negative test, but only wrong associated data was covered

The same review also found a missing CSV unsupported-contract test and a CI supply-chain hardening gap for the gitleaks tarball download.

## 결정

Fix the review gaps in a narrow follow-up branch:

- migrate touched exception assertions to bluetape4k assertions
- add a truncated DAEAD ciphertext failure test
- add DAEAD/gzip+DAEAD CSV rejection tests for export and import
- verify the pinned gitleaks archive against the upstream checksum file before installing it

## 결과

The P1 review gaps were closed without changing production DAEAD behavior.

## 검증

- `actionlint .github/workflows/ci.yml`
- `git diff --check`
- gitleaks checksum script smoke test: `gitleaks_8.30.1_linux_x64.tar.gz: OK`
- `./gradlew :graph-okio:compileKotlin :graph-okio:compileTestKotlin --no-daemon`
- `./gradlew :graph-okio:test --tests 'io.bluetape4k.graph.io.okio.GraphIoOkioPathsTest' --tests 'io.bluetape4k.graph.io.okio.OkioRoundTripTest' --no-daemon`
- `./gradlew :graph-okio:test --no-daemon` -> 101 passing

## 향후 가드

When a spec/plan explicitly accepts a negative-path test, grep the final test diff for that exact failure mode before merge. For touched tests, grep `kotlin.test.assertFailsWith`, `assertThrows`, `invoking`, and `shouldThrow` before review sign-off.
