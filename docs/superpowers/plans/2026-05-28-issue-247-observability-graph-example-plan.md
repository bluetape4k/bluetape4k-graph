# 이슈 #247 관측성 그래프 예제 계획

## DoD

- [x] `examples/` 아래에 `observability-graph-examples` Gradle 모듈을 추가한다.
- [x] 동기 및 suspend 관측성 장애 서비스를 구현한다.
- [x] 번들 graph-io CSV fixture와 로더를 추가한다.
- [x] 추상 백엔드 테스트와 구체 TinkerGraph, Neo4j, Memgraph, AGE 및 FalkorDB 클래스를 추가한다.
- [x] 시나리오, 아키텍처 다이어그램, 그래프 모델, traversal 목표, 샘플 데이터 및 예상 출력을 포함한 영문 및 한국어 README 파일을 추가한다.
- [x] 루트 README 로케일 집합, 에이전트 안내 모듈 목록, Examples workflow 및 변경 기록에 모듈을 등록한다.
- [x] 컴파일, 대상 테스트, workflow YAML, 모듈 등록 및 공백을 검증한다.

## 검증 명령

```bash
./gradlew :observability-graph-examples:compileKotlin :observability-graph-examples:compileTestKotlin --no-daemon
./gradlew :observability-graph-examples:test --no-daemon
./gradlew projects --no-daemon
actionlint .github/workflows/examples.yml
git diff --check
```

## 참고

컨테이너 기반 테스트는 하나의 순차 lane에서 실행해야 한다. README 아키텍처 이미지는
`docs/images/readme-diagrams/` 아래에 PNG embed와 대응하는 SVG source를 함께 저장한다.
