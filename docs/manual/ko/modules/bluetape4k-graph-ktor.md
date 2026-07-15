# bluetape4k-graph-ktor

## 선택 기준

`GraphPlugin`은 애플리케이션 범위의 동기·suspend operations를 Ktor attribute에 보관한다. 관리형 graph 하나를 선택하거나 이미 만든 operations를 주입한다. request마다 설치하거나 graph를 여러 개 동시에 선택하지 않는다. 구현은 [GraphPlugin.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt)다.

## 의존성과 실행

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

예상 결과는 설치 뒤 route에서 애플리케이션 범위 facade를 조회하는 것이다. 빈 설정은 시작 단계에서 실패한다.

## 수명과 종료 책임

관리형 DSL은 operations와 필요한 연결 자원을 만들고 `ApplicationStopped`에 종료 동작을 등록한다. 이미 만든 operations를 넣을 때는 기본값이 정확히 `closeOnStop = false`다.

```kotlin
install(GraphPlugin) {
    operations(syncOps, suspendOps) // closeOnStop = false
}
```

기본 설정에서는 호출자나 DI container가 두 객체를 닫는다. true로 지정할 때만 plugin에 종료 책임을 넘긴다. 같은 객체는 한 번만 닫는다. 주입한 Driver는 관리형 DSL이 만든 경우가 아니면 별도로 호출자가 소유한다. 계약은 [GraphPluginConfig.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt)에 있다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest' --tests '*BackendGraphPluginRuntimeTest'
```

예상 결과는 설치·접근, 빈 설정 실패, 기본 미종료, 관리형/명시적 종료가 각각 검증되는 것이다. route 오류보다 plugin 설치와 graph 생성 오류를 먼저 본다. stop event, pool 상태, request latency, 한 번만 닫혔는지 기록한다.

## 관련 문서와 하지 않는 일

[Ktor 연동](../frameworks/ktor.md), [짝을 이루는 API](../architecture/paired-apis.md), [운영](../guides/operations.md)을 참고한다. plugin은 request transaction을 만들거나 호출자 자원을 기본으로 닫지 않는다.
