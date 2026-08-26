# #562 generated owner ABI migration TCK 설계

## 목표

graph-core가 graph-local `CompletableFutureNullableSupportKt` owner를 제거한
뒤에도 외부 consumer가 어떤 경계에서 재컴파일해야 하는지 실행 가능한 TCK로
고정한다. 테스트는 이전 owner를 직접 호출하도록 precompiled Java fixture를
정의하고, 공식 `bluetape4k-core` owner로 재컴파일한 fixture가 같은 nullable
future 계약을 수행하는지 비교한다.

## 기준선과 범위

- stacked 기준선: PR #587 exact head `0c859bc135c1ca68efcd36690427ccb2863773b1`
- 대상 모듈: `bluetape4k-graph-core`
- 공식 owner: `io.bluetape4k.concurrent.virtualthread.CompletableFutureSupportKt`
- 제거된 owner: `io.bluetape4k.concurrent.virtualthread.CompletableFutureNullableSupportKt`
- 새 dependency, graph-local compatibility shim, upstream API 변경은 추가하지 않는다.

## Fixture 결정

`src/test/resources/abi/virtual-future/`에 세 Java source fixture를 둔다.

1. `legacy-owner/CompletableFutureNullableSupportKt.java`는 이전 graph-core
   generated owner의 compile-only ABI shape만 제공한다. 이 source는 runtime
   classpath에 넣지 않아 실제 제거 후 linkage failure를 재현한다.
2. `legacy-consumer/LegacyVirtualFutureConsumer.java`는 위 owner의
   `Function0`/`CompletableFuture` signature를 직접 호출하는 최소 외부 consumer다.
3. `migrated-consumer/MigratedVirtualFutureConsumer.java`는 동일한 source-level
   호출을 공식 `CompletableFutureSupportKt` owner로 재컴파일한 consumer다.

테스트는 JDK `JavaCompiler`로 owner shim과 legacy consumer를 서로 다른 output
directory에 먼저 컴파일하고, runtime에는 consumer output만 `URLClassLoader`로
로드한다. 따라서 legacy fixture 호출은 `NoClassDefFoundError`와 owner binary
name을 관찰하고, migrated fixture 호출은 공식 helper의 nullable `CompletableFuture`
결과를 관찰한다. 두 classfile의 constant-pool 문자열도 각각 legacy/official
owner를 포함하는지 확인해 owner-only ABI 차이를 명시한다.

## 수용 기준 추적

| 기준 | 근거 | 상태 |
| --- | --- | --- |
| 이전 graph-core owner를 직접 참조하는 최소 fixture | `LegacyVirtualFutureConsumer.java`와 compile-only legacy owner | PASS |
| official owner 재컴파일 fixture의 source/ABI 차이 | `MigratedVirtualFutureConsumer.java`, classfile owner 검사 | PASS |
| legacy consumer 실패와 migration 경로 재현 | `NoClassDefFoundError` 및 migrated future 실행 TCK | PASS |
| official owner의 실제 artifact 확인 | `ProtectionDomain.codeSource`가 `bluetape4k-core` 경로인지 검사 | PASS |
| README/release migration 안내와 TCK 일치 | graph-core EN/KO README, CHANGELOG, WIP | PASS |
| Bluetape assertions·기존 catalog 사용 | `assertFailsWith`, `shouldContain`, build script 무변경 | PASS |

## TDD와 검증 순서

1. TDD RED: 테스트 harness를 먼저 추가하고 fixture resource가 없어 2개가
   실패하는 것을 확인한다.
2. TDD GREEN: compile-only owner, legacy/migrated consumer resource를 추가해
   세 TCK가 통과하도록 한다.
3. graph-core 전체 test, compile, Detekt, forbidden assertion scan,
   `git diff --check`를 fresh run한다.
4. 7-Tier review와 EN/KO 문서에 exact head, 결과, 남은 split-package 경계를
   기록한 뒤 #587 위에 PR을 생성한다.

구현 commit `9a027f69`에서 targeted TCK 3/3, graph-core 전체 382/382,
`compileKotlin`, Detekt, forbidden assertion scan, `git diff --check`를 통과했다.

## 비범위 및 후속

- 이전 generated owner를 graph-core에 compatibility shim으로 복원하지 않는다.
- `bluetape4k-core` API나 generated method signature 자체는 변경하지 않는다.
- `bluetape4k-core`와 `bluetape4k-virtualthread-api`의 split-package 및
  `java --validate-modules` 정리는 [#563](https://github.com/bluetape4k/bluetape4k-graph/issues/563)에서
  별도 train slice로 다룬다.
- 실제 외부 artifact repository의 여러 release 버전 matrix는 이 저장소의
  deterministic fixture 범위를 넘어선다. fixture ABI가 변하면 owner shape를
  먼저 갱신하고 migration 문서를 함께 검토한다.
