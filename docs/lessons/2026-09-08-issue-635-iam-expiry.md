# IAM 임시 grant 만료 시각의 단일 평가

## 배경

`IamSessionGrant`가 `expiresAt`을 저장하고도 sync/suspend `temporaryPaths()`가
그 값을 읽지 않아 만료된 break-glass 권한을 계속 허용했다. 이 예제는 IAM 제품의
정책 엔진이 아니라 graph traversal 교육용 계약이므로, 두 API가 같은 만료 규칙을
공유해야 한다.

## 결정 or Finding

서비스 생성자는 기본 `Clock.systemUTC()`를 사용하되 테스트가 `Clock.fixed(...)`를
주입할 수 있도록 한다. 한 access 평가에서 `clock.instant()`를 한 번만 읽어 같은
평가 시각을 모든 임시 grant에 전달한다. `expiresAt`은 ISO-8601 `Instant`로 파싱하고
평가 시각보다 엄격하게 뒤에 있을 때만 활성으로 판정한다. 누락되거나 잘못된 값은
fail-closed로 거부하며, explicit deny path는 기존 순서대로 먼저 판정한다.

날짜 파싱과 활성 판정은 sync/suspend가 함께 사용하는 순수 함수로 두어 backend별
구현이 규칙을 복제하지 않게 했다.

## 결과

만료 전에는 temporary grant path가 반환되고, 정확한 만료 시각과 만료 후에는
`No matching grant path`로 거부된다. malformed timestamp도 접근을 허용하지 않으며,
활성 temporary grant가 있어도 explicit deny policy가 우선한다. 기존 예제 backend
fixture는 고정 평가 시각을 사용해 샘플 grant 검증이 현재 날짜에 의존하지 않는다.

## 검증

- 고정 `Clock`으로 sync/suspend 각각 만료 전·정확한 만료 시각·만료 후를 검증한다.
- malformed `expiresAt`의 sync/suspend fail-closed 동작을 검증한다.
- 활성 temporary grant와 explicit deny path가 함께 있을 때 deny 우선순위를 검증한다.
- 구현 전 동일 시나리오의 4개 테스트가 만료 및 malformed 값을 허용하는 RED임을
  확인했다. 수정 후 전체 49개 테스트를 통과했다. 최초 GREEN의 만료 전 2개 실패는 fixture의 expiresAt/evaluatedAt 인자 순서 오류였으며 named argument로 고쳤다.

## 재발 방지

시간에 의존하는 접근 판정은 시스템 시계를 직접 호출하지 말고 명시적인 평가 시각
계약을 사용한다. 만료 경계는 `expiresAt > evaluatedAt`으로 정의하고, malformed
저장값은 안전한 기본값인 거부로 처리한다. sync/suspend API를 추가할 때는 파싱과
판정 함수를 재사용하고, 한 요청의 시각을 경로별로 다시 읽지 않는다.

## 추적

- GitHub issue: [#635](https://github.com/bluetape4k/bluetape4k-graph/issues/635)
