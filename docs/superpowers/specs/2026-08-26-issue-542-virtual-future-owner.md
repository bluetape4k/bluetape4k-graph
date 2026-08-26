# #542 graph-core Virtual Thread helper owner 설계

## 목표

`graph-core`가 `bluetape4k-core`가 이미 제공하는
`io.bluetape4k.concurrent.virtualthread.virtualFutureOfNullable`을 다시
소유하지 않도록 graph-local source를 제거한다. Kotlin source import와
`virtualFutureOfNullable` 호출 semantics는 유지하고, generated JVM owner 변경과
후속 consumer migration 경계를 문서화한다.

## 기준선과 원인

`graph/graph-core/build.gradle.kts`는 이미 `api(bt4k.bluetape4k.core)`와
`implementation(bt4k.bluetape4k.virtualthread.api)`를 사용한다. 현재 source의
`CompletableFutureNullableSupport.kt`는 공식 `CompletableFutureSupport.kt`와 같은
package에 동일 helper를 정의해 `CompletableFutureNullableSupportKt`라는 graph-local
generated owner를 추가한다.

## 결정

- `graph/graph-core/src/main/kotlin/io/bluetape4k/concurrent/virtualthread/CompletableFutureNullableSupport.kt`를 삭제한다.
- 기존 adapter의 import 경로는 유지해 공식 `bluetape4k-core` symbol로 resolve한다.
- 새 dependency나 shading을 추가하지 않는다.
- graph-core local owner 제거와 upstream `bluetape4k-core`/`bluetape4k-virtualthread-api`
  split-package 정리는 분리한다. 후자는 [#563](https://github.com/bluetape4k/bluetape4k-graph/issues/563)의
  범위다.

## ABI·마이그레이션 계약

Kotlin source consumer는 import 변경 없이 재컴파일할 수 있다. 그러나 생성된
`CompletableFutureNullableSupportKt`를 직접 호출한 Java 또는 precompiled Kotlin
consumer는 공식 `CompletableFutureSupportKt` owner를 기준으로 재컴파일해야 한다.
이번 issue는 local duplicate 제거와 source-level utility 실행만 검증하며, 외부
consumer의 code source·precompiled ABI는 [#562](https://github.com/bluetape4k/bluetape4k-graph/issues/562)에서
검증한다.

## 검증 기준

1. RED 상태에서 graph-local generated owner가 여전히 로드됨을 관찰한다.
2. source 삭제 후 clean output에서 `CompletableFutureNullableSupportKt`가 사라지고
   공식 `CompletableFutureSupportKt`가 로드되는지 검증한다.
3. 공식 `virtualFutureOfNullable { null }`가 nullable result를 반환하는지
   `io.bluetape4k.assertions`로 검증한다.
4. graph-core 전체 test, compile, Detekt, forbidden assertion scan,
   `git diff --check`를 fresh rerun한다.

## 비범위

- upstream virtualthread-api의 package/module ownership 정리
- 외부 generated owner를 위한 compatibility shim 복원
- untrusted Java serialization 또는 backend-specific cancellation
