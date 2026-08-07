# bluetape4k-graph-ktor


실행 방식: **릴리스 테스트 픽스처 연계형**이다. `testApplication`이 Ktor 애플리케이션과 HTTP 테스트 클라이언트를 제공한다. 아래 테스트가 플러그인 설치, 상태 접근, 애플리케이션 종료, 정확한 `closeOnStop` 분기를 검증한다.

## 실행 전 준비

`GraphPlugin`은 애플리케이션 범위의 동기·suspend operations를 Ktor attribute에 보관한다. 관리형 그래프 하나를 선택하거나 이미 만든 operations를 주입한다. request마다 설치하거나 그래프를 여러 개 동시에 선택하지 않는다. 구현은 [GraphPlugin.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-ktor")
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop")
}
```

```kotlin
fun Application.module() {
    install(GraphPlugin) { tinkerGraph() }
    routing {
        get("/vertices") {
            call.respondText(call.graphSuspendOperations().countVertices("Person").toString())
        }
    }
}
```

## 기대 결과

예상 결과는 설치 뒤 route에서 애플리케이션 범위 진입점를 조회하는 것이다. 빈 설정은 시작 단계에서 실패한다.

## 수명과 종료 책임

관리형 DSL은 operations와 필요한 연결 자원을 만들고 `ApplicationStopped`에 종료 동작을 등록한다. 이미 만든 operations를 넣을 때는 기본값이 정확히 `closeOnStop = false`다.

```kotlin
install(GraphPlugin) {
    operations(syncOps, suspendOps) // closeOnStop = false
}
```

기본 설정에서는 호출자나 DI 컨테이너가 두 객체를 닫는다. `true`로 지정할 때만 플러그인에 종료 책임을 넘긴다. 같은 객체는 한 번만 닫는다. 주입한 Driver는 관리형 DSL이 만든 경우가 아니면 별도로 호출자가 소유한다. 계약은 [GraphPluginConfig.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt)에 있다.

## 운영 점검

- 선택한 그래프 설정을 기록한다.
- 요청 지연과 Driver/DataSource pool 상태를 확인한다.
- `ApplicationStopped`와 한 번만 닫힌 근거를 확인한다.
- 호출자 소유 operations에는 `closeOnStop=false`를 유지한다.

## 실패와 복구

증상: 시작할 때 그래프를 선택하지 않았다는 오류가 나거나 라우트에서 플러그인이 없다고 한다. 라우팅보다 설치를 먼저 고친다. 종료 뒤 자원이 남으면 관리형·주입형 소유권을 확인하고 한 번만 닫히는 테스트를 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest' --tests '*BackendGraphPluginRuntimeTest'
```

예상 결과는 설치·접근, 빈 설정 실패, 기본 미종료, 관리형·명시적 종료가 각각 검증되는 것이다. 라우트 오류보다 플러그인 설치와 그래프 생성 오류를 먼저 본다. 종료 이벤트, 풀 상태, 요청 지연 시간, 한 번만 닫혔는지 기록한다.

## 완전한 release 예제

고정된 [GraphPluginTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/GraphPluginTest.kt)가 fixture 값을 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest'
```

예상 결과는 fixture가 시작되고 검증이 통과하며 소유 자원이 문서에 적은 순서로 닫히는 것이다.

## 하지 않는 일과 관련 문서

[Ktor 연동](../frameworks/ktor.md), [짝을 이루는 API](../architecture/paired-apis.md), [운영](../guides/operations.md)을 참고한다. 플러그인은 요청 트랜잭션을 만들거나 호출자 자원을 기본으로 닫지 않는다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph ktor 아키텍처

[![Bluetape4k Graph ktor 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/ktor-graph-ktor-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/ktor-graph-ktor-architecture-01.svg)

_배포본 README: [`ktor/graph-ktor/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/README.ko.md)_

<!-- release-readme-diagrams:end -->
