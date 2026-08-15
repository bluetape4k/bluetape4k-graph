# 2026-08-14 issue #500 Caffeine maintenance and deterministic cache proof

## Context

AGE와 Neo4j의 Caffeine decorator는 여섯 read cache에 `maxSize`와
`expireAfterWrite`를 적용하지만, 이전 TTL 회귀 테스트는 `Thread.sleep`에
의존했고 destructive write 뒤에는 대표 read 하나만 재호출했다. `maxSize`는
wrapper 전체 heap 크기가 아니라 cache별 entry 정책이라는 의미도 문서에 더
분명히 적어야 했다.

## Decision or Finding

- 두 decorator constructor에 Caffeine `Ticker`를 추가하고 기본값은
  `Ticker.systemTicker()`로 유지한다. 테스트는 local fake ticker를 주입하여
  wall-clock 대기 없이 TTL 경계를 이동한다.
- `deleteVertex` 뒤에 `findVertexById`, `findVerticesByLabel`, `neighbors`,
  `shortestPath`, `allPaths`, `findEdgesByLabel` 여섯 read path를 모두 다시
  호출하여 delegate 재호출을 검증한다.
- miss-heavy Caffeine microbenchmark에서 explicit `cleanUp()`을 제거하면
  throughput은 좋아지지만 `maxSize = 1`의 즉시 eviction 관찰 계약이 깨졌다.
  따라서 AGE/Neo4j에서는 `put` 직후 `cleanUp()`을 유지한다. 이 선택은
  latency 최적화가 아니라 작은 per-cache capacity 계약의 결정성에 우선순위를
  둔 것이다.

## Outcome

`maxSize`는 각 cache에 독립 적용되며 wrapper 전체 entry 수나 heap 바이트 상한이
아님을 두 README와 KDoc에 명시했다. `ticker`는 public constructor contract에
추가되었지만 기본 호출 형태와 system-clock 운영 동작은 유지된다.

## Verification

- Microbenchmark: Caffeine 3.2.4 direct bounded cache, 1,000,000 puts per
  sample, `maxSize = 10,000`, four warmup samples and five measured samples,
  GraalVM JDK 25.0.4 on macOS arm64, one JVM process.
- `cleanUp = false`: 122.1 ns/op, 8,189,268 ops/s.
- `cleanUp = true`: 385.5 ns/op, 2,594,083 ops/s (약 3.16배 latency, 약 68%
  throughput 감소).
- Removing `cleanUp()` made the existing `maxSize = 1` test fail because
  Caffeine eviction maintenance is eventual. Keeping it preserves the
  immediate capacity observation used by the wrapper contract.

## Future Guidance

If hot-path maintenance becomes unacceptable, do not remove `cleanUp()` without
choosing a replacement capacity-observation contract first. A scheduler or
batched maintenance policy must add its own deterministic test and document that
`maxSize` is an eventual policy rather than an immediate lookup guarantee.
