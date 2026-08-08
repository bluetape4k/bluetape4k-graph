# graph-okio 이름 변경 계획

- **이슈**: #76
- **설계**: docs/superpowers/specs/2026-05-10-graph-okio-rename-design.md
- **브랜치**: refactor/graph-okio-rename

## 작업 단계

1. `settings.gradle.kts`에서 `graph-io/okio`가 `:graph-okio`로만 등록되도록 수정한다.
2. 프로젝트 의존성 참조를 `:graph-io-okio`에서 `:graph-okio`로 변경한다.
3. 현재 README, BOM, WIP, 코드 주석 및 workflow의 `graph-io-okio` 참조를 `graph-okio`로 변경한다.
4. 프로젝트 등록과 영향을 받는 빌드를 검증한다.
5. 6-Tier 검토를 실행하고 발견 사항을 한 번에 처리한다.
6. 커밋하고 푸시한 뒤 #76을 닫는 PR을 연다.

## 검증

- `./gradlew projects --no-configuration-cache`
- `./gradlew :graph-okio:test --no-configuration-cache`
- `./gradlew :graph-io-benchmark:compileKotlin --no-configuration-cache`
- `./gradlew :graph-okio:koverXmlReport --no-configuration-cache`
- `./gradlew :graph-okio:generatePomFileForBluetapeGraphPublication --no-configuration-cache`
- `./gradlew :bluetape4k-graph-bom:generatePomFileForBluetapeGraphPublication --no-configuration-cache`
- `git diff --check`

2026-04의 설계/testlog 파일은 원래 모듈을 생성한 맥락을 기록하므로 `graph-io-okio`를 계속 언급할 수 있다. 현재 사용자 대상 문서, CI, BOM 문서 및 생성된 POM 메타데이터는 `graph-okio`를 사용해야 한다.
