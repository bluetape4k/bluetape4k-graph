# 2026-08-13 issue #464 bounded/expiring cache

## Context

AGE, Neo4j, Memgraph 캐시 데코레이터의 공개 생성자에는 `maxSize`와
`expireAfterWrite`가 있었지만 실제 구현은 여섯 개의 무제한 `ConcurrentHashMap`을
사용해 두 설정을 무시했다. 장기 실행 프로세스에서 읽기 키가 계속 증가하고
오래된 결과가 TTL 없이 남을 수 있는 상태였다.

## Decision or Finding

- 세 wrapper의 읽기 캐시를 기존 모듈 API 의존성인 Caffeine 3.2.4의 `Cache`로 전환한다.
- 모든 read cache에 동일한 `maxSize`와 `expireAfterWrite` 정책을 적용하고, 생성자에서
  `maxSize > 0`, `expireAfterWrite > Duration.ZERO`를 즉시 검증한다.
- Caffeine의 write buffer가 eviction을 지연시킬 수 있으므로 read 결과 저장 직후
  `cleanUp()`을 호출해 작은 `maxSize` 계약도 테스트와 운영에서 결정적으로 관찰한다.
- `Optional`로 감싼 null 결과 캐싱과 쓰기 후 전체 read-cache 무효화 계약은 유지한다.

## Outcome

AGE, Neo4j, Memgraph wrapper가 bounded/expiring read cache를 사용하며, 공개 문서와
KDoc에 설정 의미와 양수 제약을 명시했다. `maxSize = 1` eviction, 짧은 TTL 재조회,
잘못된 설정 거부 회귀 테스트를 각 backend에 추가했다.

## Verification

- RED: 세 targeted 테스트에서 설정 무시, 무제한 보관, TTL 미적용, 잘못된 값 허용을 재현했다.
- GREEN: AGE, Neo4j, Memgraph `Caching*GraphOperationsTest`가 각각 25개 전부 통과했다.
- 추가 검증: AGE 180개, Neo4j 120개, Memgraph 115개 전체 모듈 테스트와 세 모듈
  detekt/Dokka, `git diff --check`가 모두 통과했다.

## Future Guidance

캐시 생성자에 노출된 정책 파라미터는 내부 자료구조와 반드시 연결하고, 각 read cache의
크기·만료·null 결과·쓰기 무효화 경계를 한 세트로 회귀 테스트한다. Caffeine 정책을
변경할 때는 eviction maintenance가 즉시 관찰되는지와 Testcontainers backend별
실행 시간을 함께 확인한다.
