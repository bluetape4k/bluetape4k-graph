# Issue #563: Virtual Thread module boundary 검증 명세

## 목적

`bluetape4k-core`와 `bluetape4k-virtualthread-api`가 published JAR에서
`io.bluetape4k.concurrent.virtualthread`를 함께 소유하지 않는지 graph
consumer 관점에서 검증한다. 실제 ownership 수정은 upstream
[#1523](https://github.com/bluetape4k/bluetape4k-projects/pull/1523)에서
수행하고, 이 저장소는 resolved artifact 검증과 migration 문서를 소유한다.

## 기대 경계

- core: `io.bluetape4k.concurrent.virtualthread`
- virtualthread-api: `io.bluetape4k.concurrent.virtualthread.api`
- JDK provider ServiceLoader contract: `.api.VirtualThreadRuntime` 및
  `.api.StructuredTaskScopeProvider`
- graph-core source는 core helper import를 유지하고 API package를 재정의하거나
  shading하지 않는다.

## 검증 계약

`scripts/verify_virtualthread_module_boundary.py`는 두 resolved JAR을 입력으로
받아 다음을 모두 fail-closed로 확인한다.

1. 두 JAR의 class package 교집합이 없다.
2. API JAR에 legacy package의 API class가 없다.
3. API owner 타입이 `.api` package에 존재한다.
4. `java --validate-modules --module-path <core>:<api>`가 exit 0이다.

upstream 지원 PR이 merge되고 새 snapshot이 배포되기 전에는 현재 graph
dependency가 이전 artifact를 가리키므로 이 verifier의 downstream 실행은
PENDING이다. 그 전환 뒤 graph-core compile/test/Detekt와 함께 실행한다.
