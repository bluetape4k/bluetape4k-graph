# Issue #96 graph-ktor Lessons

## Lessons & Learns

- Lesson: Ktor route handler에서 `Application` extension을 직접 호출하는 API sketch는 컴파일되지 않는다.
  - Evidence: Claude spec/plan advisor가 route context mismatch를 P1로 지적했고, `ApplicationCall.graphOperations()` / `ApplicationCall.graphSuspendOperations()`를 추가했다.
  - Future guard: Ktor plugin spec에는 application setup accessor와 route accessor를 분리해 적는다.

- Lesson: `GraphOperations`와 `GraphSuspendOperations`는 메서드명이 겹치므로 하나의 class가 둘을 동시에 delegation으로 구현하는 test double은 Kotlin signature conflict를 만든다.
  - Evidence: `DualGraphOperations` test double compile 실패 후 제거.
  - Future guard: sync/suspend facade pair의 shared delegate lifecycle은 helper 내부에서 명시적으로 관리하고, external caller pair는 KDoc contract로 책임을 분리한다.

- Lesson: 신규 publishable module을 추가하면 BOM README와 root README뿐 아니라 `settings.gradle.kts` auto-include 결과를 `./gradlew projects`로 확인해야 한다.
  - Evidence: `:graph-ktor`와 `:ktor-graph-examples`가 `./gradlew projects`에 각각 publishable/example module로 등록됨을 확인했다.
  - Future guard: module 추가 plan에 `projects`, targeted `build`, README/BOM sync를 함께 둔다.
