# #562 generated owner ABI migration TCK lesson

## Context

graph-core의 Kotlin import 경로는 `virtualFutureOfNullable` 그대로였지만,
graph-local top-level file을 제거하면서 JVM generated owner가
`CompletableFutureNullableSupportKt`에서 `CompletableFutureSupportKt`로 바뀌었다.
따라서 source compile 성공만으로는 이전 Java/precompiled Kotlin consumer의
실행 가능성을 설명할 수 없다.

## Decision

legacy owner의 compile-only ABI shape와 이를 직접 호출하는 최소 Java fixture를
test resource로 보존했다. 테스트는 legacy owner shim을 consumer compile 때만
classpath에 넣고 runtime에는 넣지 않아 `NoClassDefFoundError`를 재현한다. 동일한
fixture를 official owner로 재컴파일한 경로는 별도 output/classloader에서 실행해
nullable future 결과와 code source를 확인한다. classfile constant pool owner도
검사해 “source import는 동일하지만 generated owner는 다르다”는 차이를 명시한다.

이 방식은 production compatibility shim이나 새 dependency를 추가하지 않고,
실제 migration 지침인 “official owner로 consumer 재컴파일”을 테스트와 문서에
같은 형태로 표현한다.

## Outcome

- official `CompletableFutureSupportKt`가 `bluetape4k-core` artifact에서 로드됨을
  `ProtectionDomain.codeSource`로 확인한다.
- legacy precompiled fixture의 직접 호출은 제거된 owner binary name을 포함한
  `NoClassDefFoundError`로 실패한다.
- migrated fixture는 official owner를 호출해 `CompletableFuture`의 nullable
  `null` 결과를 완료한다.
- `io.bluetape4k.assertions.assertFailsWith`와 `shouldContain`을 사용하며
  JUnit `assertThrows`/Kotlin assertion을 도입하지 않는다.

## Misses and surprises

- JUnit 5 test method가 expression body로 `T`를 반환하면 Gradle이 void test로
  발견하지 않을 수 있다. side-effect assertion test는 block body로 `Unit`을
  반환해야 세 TCK가 모두 실행된다.
- legacy owner shim은 실제 runtime artifact가 아니므로 fixture source가 공식
  generated signature와 달라지지 않도록 `Function0`/`CompletableFuture` ABI를
  고정하고, owner 문자열을 classfile에서 다시 확인한다.
- `ProtectionDomain.codeSource`는 단순 `Class.forName`보다 artifact ownership을
  강하게 증명하지만, 로컬 JRE-only 환경은 프로젝트 toolchain 계약 밖이다.

## Verification

```text
TDD RED: fixture resource not found 2 failures
TDD GREEN: VirtualFutureOwnerAbiMigrationTckTest 3/3
graph-core full test: 382/382
compileKotlin: BUILD SUCCESSFUL
Detekt: BUILD SUCCESSFUL
forbidden assertion scan: 0
git diff --check: PASS
```

## Future guard

top-level helper file을 이동·병합할 때는 source import, generated JVM owner,
precompiled consumer fixture, `ProtectionDomain.codeSource`, EN/KO migration
문서를 한 번에 검토한다. 이전 owner를 되살리는 compatibility class는
split-package와 duplicate ownership을 재도입하므로 별도 ABI 결정과 release
정책 없이는 추가하지 않는다.
