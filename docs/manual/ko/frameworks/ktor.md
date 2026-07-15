# Ktor 연동

Ktor 애플리케이션에 `GraphPlugin`을 한 번 설치하고 백엔드를 고르거나 동기·코루틴 operations를 넘긴다. 아무 백엔드도 정하지 않으면 설치 단계에서 실패한다. 만들어진 `GraphPluginState`는 application attributes에 저장된다. 소스: [`GraphPlugin.kt`](../../../../ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt), [`GraphPluginConfig.kt`](../../../../ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt).

```kotlin
fun Application.module() {
    install(GraphPlugin) { tinkerGraph() }
    routing { /* graphOperations() / graphSuspendOperations() */ }
}
```

생명주기는 요청 단위가 아니라 애플리케이션 단위다. `ApplicationStopped`가 오면 설정 과정에서 등록한 close action만 실행한다. 호출자가 넘긴 Driver와 DataSource는 호출자가 계속 소유한다. 시작, attribute 조회, 한 번만 닫히는지는 [`GraphPluginTest.kt`](../../../../ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/GraphPluginTest.kt)와 [`BackendGraphPluginRuntimeTest.kt`](../../../../ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/BackendGraphPluginRuntimeTest.kt)가 검증한다.

경로 처리 오류를 보기 전에 설치 오류부터 확인한다. 운영에서는 stop event, driver pool, 요청 취소, handler가 blocking/코루틴 모델에 맞는 API를 쓰는지 관찰한다.
