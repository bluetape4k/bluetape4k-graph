# 이슈 #290 Central snapshot retry

## 맥락
Downstream CI and Nightly runs can fail when GitHub runners receive transient
HTTP 403 responses from Central Portal snapshot metadata.

## 결정
Wrap the top-level Gradle build and Nightly detekt gates in bounded three-attempt
retry loops without changing the Gradle command semantics. Also disable
configuration cache for the Neo4j CI test/coverage job after it reproduced the
same Central metadata 403 path.

## 검증
- `git diff --check`
- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml`

## 다음 작업 시 유의점
If a bluetape4k SNAPSHOT dependency fails with Central metadata 403, check
upstream publish status first, then prefer a bounded workflow retry over
dependency or catalog churn.
