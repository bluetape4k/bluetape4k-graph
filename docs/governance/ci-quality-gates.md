# CI 품질 게이트

## 현재 상태

`bluetape4k-graph`는 issue #18에서 제안했던 예전 all-in-one gate 대신 현재
저장소 정책에 맞는 CI 품질 게이트를 사용한다.

## 정책

- CI는 secret scanning, Gradle wrapper validation, compile-only build, Detekt
  static analysis, targeted test job을 기준으로 pull request를 차단한다.
- Detekt report는 각 module의 `**/build/reports/detekt/*.xml` 경로에서
  업로드한다.
- 기존 finding은 `config/detekt/baseline.xml`에 추적한다. CI는 baseline 밖의
  신규 finding에 대해서만 실패해야 한다.
- Kover report는 trend evidence 용도의 report-only artifact로 생성하고 업로드한다.
- Nightly는 full/smoke scope에 대해 더 넓은 Detekt와 coverage visibility를
  유지한다.
- dependency security posture는 CodeQL, GitHub dependency graph alert,
  GitHub Actions용 Dependabot, `bluetape4k-dependencies`의 중앙 library version
  governance로 처리한다.

## 제외 사항

- 이후 집중 이슈에서 module-level threshold를 다시 도입하기 전까지 fixed Kover
  percentage gate를 추가하지 않는다.
- 저장소가 ktlint를 유지 관리되는 Gradle plugin과 style contract로 채택하기
  전까지 ktlint를 일회성 CI gate로 추가하지 않는다.
- OWASP Dependency Check를 release publishing의 기본 차단 gate로 추가하지
  않는다. NVD dependency와 false-positive profile 때문에 별도 vulnerability
  triage policy 없이는 이 저장소의 release-blocking gate로 적합하지 않다.

## 검증 계약

- workflow YAML은 `actionlint`를 통과해야 한다.
- CI quality-gate 변경에는 로컬 `./gradlew detekt` 통과가 필요하다.
- baseline은 기존 finding을 고쳐야 하는지 먼저 검토한 뒤
  `./gradlew detektProjectBaseline`로만 재생성한다.
- Kover report generation은 `continue-on-error: true`를 유지한다. 고정 threshold
  하나만으로 build를 실패시키지 않으면서 coverage data를 볼 수 있어야 한다.
