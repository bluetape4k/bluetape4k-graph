# #526 Testcontainers image family gate 교훈

## 결정

Neo4j, Memgraph, Apache AGE, FalkorDB의 image tag와 startup/workload 계약을 하나의
manifest에 두고, backend별 기존 `GraphCapability` conformance test를 대표
workload로 재사용한다. 새로운 mock health check를 만들지 않고 실제 shared launcher와
backend fixture가 검증하는 readiness 경계를 사용했다.

변경된 backend만 CI에서 선택하되 image manifest, shared launcher, Gradle 설정,
workflow, gate script 변경은 전체 family를 선택한다. Full Nightly는 항상 전체
네 family를 순차 실행하고, release는 exact tag SHA의 성공한 Full Nightly 증거를
재사용한다.

## 실패를 green으로 만들지 않기

Testcontainers 실패는 readiness timeout, image pull/rate-limit, infrastructure,
application으로 분류한다. 최대 세 번 retry할 수 있지만 첫 시도 실패가 한 번이라도
있으면 결과를 `success_after_retry`로 남기고 release gate를 차단한다. 이 규칙은
registry가 일시적으로 허용된 상황과 image 또는 product 회귀를 구분할 수 있게 한다.

실패 또는 retry 시 image digest, container inspect/logs, Docker events를 보존한다.
진단 명령 자체의 실패는 원래 product 결과를 덮지 않으며, secret 값은 artifact에
남기지 않는다.

## 다음 변경에 대한 지침

새 graph backend를 추가할 때는 manifest family 수, image/family 목록, 대표
conformance class, path selection, diagnostics, CI/Nightly dependency와 release
Nightly attestation을 한 번에 갱신한다. image tag만 변경하는 PR도 해당 family의 full gate와 hosted
artifact를 확인하기 전에는 release-ready로 취급하지 않는다.
