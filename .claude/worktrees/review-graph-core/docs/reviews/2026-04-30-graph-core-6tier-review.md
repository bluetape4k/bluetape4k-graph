# graph-core 6-Tier 코드 리뷰

- **날짜**: 2026-04-30
- **대상 모듈**: `graph/graph-core`
- **브랜치**: `review/graph-core-6tier`
- **리뷰 방법**: bluetape4k-design Step 6-R (6-Tier 병렬 리뷰)

---

## 리뷰 결과 요약

| Tier | 도구 | CRITICAL | HIGH | MEDIUM | LOW |
|------|------|----------|------|--------|-----|
| 1 | autoresearch:security | 0 | 0 | 0 | 1(info) |
| 2 | Ops/SRE | 0 | 4 | 1 | 2 |
| 3 | code-review-graph | 2 | 4 | 0 | 1 |
| 4 | code-reviewer (Kotlin/patterns) | 0 | 5 | 2 | 0 |
| 5 | pr-review-toolkit | 1 | 3 | 2 | 0 |
| 6 | 성능/안정성 | 0 | 2 | 0 | 0 |
| **합계** | | **3** | **18** | **5** | **4** |

> 중복 발견사항 제거 후: **CRITICAL 3, HIGH 15, MEDIUM 5, LOW 3**

---

## CRITICAL (즉시 수정 필요)

### C1. `PageRankCalculatorTest.kt:23` — Tautological Assertion (항상 통과하는 단언)
**Tier 5 | 신뢰도: 100%**

```kotlin
// 잘못된 코드 — abs()는 항상 ≥ 0이므로 -0.001보다 항상 크다 → 테스트가 의미 없음
abs(scores.getValue(id("a")) - 1.0) shouldBeGreaterThan -0.001

// 올바른 코드
abs(scores.getValue(id("a")) - 1.0) shouldBeLessThan 0.001
```

PageRank 계산이 완전히 잘못되어도 이 테스트는 통과한다. 즉시 수정 필요.

---

### C2. `GraphVirtualThreadOperations.kt` — 동기 `GraphSession` 직접 상속
**Tier 3 | 신뢰도: 95%**

```kotlin
// 현재 (문제)
interface GraphVirtualThreadOperations:
    GraphSession,              // ← 블로킹 동기 API가 VT facade에 노출됨
    GraphVirtualThreadSession,
    GraphVirtualThreadVertexRepository,
    ...

// 권고
interface GraphVirtualThreadOperations:
    GraphVirtualThreadSession,
    GraphVirtualThreadVertexRepository,
    GraphVirtualThreadEdgeRepository,
    GraphVirtualThreadTraversalRepository,
    GraphVirtualThreadAlgorithmRepository
```

VT API 사용자가 블로킹 동기 메서드를 실수로 호출할 수 있다.

---

### C3. `CLAUDE.md` — `GraphOperations` 구성 설명 오류
**Tier 3 | 신뢰도: 90%**

```markdown
# 현재 (틀림)
GraphOperations = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphTraversalRepository

# 올바른 내용
GraphOperations = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphGenericRepository
                  (GraphGenericRepository = GraphTraversalRepository + GraphAlgorithmRepository)
```

`GraphAlgorithmRepository` 전체가 누락되어 있어, 이 문서를 참고하여 backend를 구현하면 pageRank, bfs, dfs, connectedComponents, detectCycles 등이 빠진 불완전한 구현이 만들어진다.

---

## HIGH (병합 전 수정 권고)

### H1. `CycleDetector.kt:82` — 재귀 DFS StackOverflowError 위험
**Tier 2 | 신뢰도: 90%**

`CycleDetector.dfs`가 재귀 호출. `maxDepth`가 크거나 밀집 그래프에서 JVM 스택 오버플로우 가능.
형제 클래스 `BfsDfsRunner`는 이미 반복형(iterative) 구현 사용.

**권고**: 재귀 DFS를 명시적 스택 반복 루프로 전환하거나, `require(maxDepth <= N)` 가드 추가.

---

### H2. `AStarRunner.kt:74-77`, `DijkstraRunner.kt:71-74` — maxVisited 소진 시 DEBUG 로그만
**Tier 2 | 신뢰도: 88%**

```kotlin
// 현재 — debug로 기록됨. 운영 환경에서 보이지 않음
log.debug { "maxVisited=${options.maxVisited} reached; aborting" }
return null  // "경로 없음"과 구분 불가

// 권고 — warn으로 변경
log.warn { "A* maxVisited=${options.maxVisited} reached from $fromId → $toId" }
```

---

### H3. `PathReconstructor.kt:33,39` — fetchVertex 실패 시 WARN 없이 null 반환
**Tier 2 | 신뢰도: 85%**

경로 탐색 성공 후 경로 재구성 중 `fetchVertex`가 null을 반환하면 로그 없이 null 반환. "경로 없음"과 구분 불가.

```kotlin
// 권고
val vertex = vertexLookup(currentId) ?: run {
    log.warn { "PathReconstructor: vertex $currentId not found mid-reconstruction" }
    return null
}
```

---

### H4. `PageRankCalculator.kt:32-38` — 파라미터 검증 없음
**Tier 2 | 신뢰도: 82%**

```kotlin
// 권고: 함수 진입 시 검증 추가
require(iterations > 0) { "iterations must be > 0, was $iterations" }
require(dampingFactor in 0.0..1.0) { "dampingFactor must be in (0,1), was $dampingFactor" }
require(tolerance > 0.0) { "tolerance must be > 0.0, was $tolerance" }
```

`iterations <= 0`이면 반복 없이 초기 균등 분포를 결과로 반환. 음수 dampingFactor는 음수 PageRank 생성.

---

### H5. `DijkstraRunner.kt:85`, `AStarRunner.kt:88,91` — fetchVertex 루프 내 N번 호출
**Tier 5/6 | 신뢰도: 90%**

```kotlin
// 현재 (문제) — for 루프 내 매 edge마다 동일한 currentId로 fetchVertex 호출
for (edge in edges.sortedBy { it.id.value }) {
    ...
    val currentVertex = fetchVertex(currentId) ?: continue  // N번 backend 쿼리
    cameFrom[neighborId] = currentVertex to edge
}

// 권고 — 루프 밖으로 호이스팅
val currentVertex = fetchVertex(currentId) ?: continue@outer
for (edge in edges.sortedBy { it.id.value }) {
    ...
    cameFrom[neighborId] = currentVertex to edge
}
```

out-degree N인 vertex에서 N번 backend 쿼리 발생. Neo4j/AGE/FalkorDB 백엔드에서 심각한 성능 저하.

---

### H6. `ShortestPathFallback.kt:87` — `Direction.BOTH` self-loop 엣지 중복
**Tier 5 | 신뢰도: 88%**

```kotlin
// 현재 (문제)
Direction.BOTH -> (ops.findEdgesByStartId(id, edgeLabel) + ops.findEdgesByEndId(id, edgeLabel))
    .sortedBy { it.id.value }

// 권고
Direction.BOTH -> (ops.findEdgesByStartId(id, edgeLabel) + ops.findEdgesByEndId(id, edgeLabel))
    .distinctBy { it.id }
    .sortedBy { it.id.value }
```

self-loop 엣지(`startId == endId`)가 두 번 포함되어 phantom duplicate 엣지로 알고리즘에 전달됨.

---

### H7. `ShortestPathFallbackTest.kt` 누락
**Tier 5 | 신뢰도: 82%**

`ShortestPathFallback`에 대한 직접 테스트 파일 없음. `DijkstraRunnerTest`/`AStarRunnerTest`는 lambda를 직접 주입하므로 `ShortestPathFallback`의 `fetchIncident` 코드 경로(Direction.BOTH 포함)를 전혀 실행하지 않음.

**권고**: `ShortestPathFallbackTest.kt` 추가, mock `GraphOperations`로 OUTGOING/INCOMING/BOTH + self-loop 케이스 커버.

---

### H8. `PageRankCalculator.kt:52-53` — dangling node set 매 iteration 재계산
**Tier 6 | 신뢰도: 85%**

```kotlin
// 현재 (문제) — 매 iteration마다 O(V) filter + List 할당
repeat(iterations) {
    val danglingMass = vertices.filter { outAdjacency[it].isNullOrEmpty() }
        .sumOf { ranks.getOrDefault(it, 0.0) }
}

// 권고 — 루프 밖에서 1회 계산
val danglingNodes = vertices.filter { outAdjacency[it].isNullOrEmpty() }
repeat(iterations) {
    val danglingMass = danglingNodes.sumOf { ranks.getOrDefault(it, 0.0) }
}
```

---

### H9. `CompletableFutureNullableSupport.kt` — 잘못된 패키지 위치
**Tier 4 | 신뢰도: 90%**

`graph-core` 모듈에 있으나 `package io.bluetape4k.concurrent.virtualthread` 선언. 이 네임스페이스는 `bluetape4k-virtualthread-jdk25` 라이브러리 소속.

**권고**: `bluetape4k-virtualthread-jdk25`에 `virtualFutureOfNullable` PR 제출 후 이 파일 삭제. 불가능하면 패키지를 `io.bluetape4k.graph.vt`로 변경.

---

### H10. `VirtualThreadAlgorithmAdapter.kt:22-24` — KDoc과 구현 불일치
**Tier 4 | 신뢰도: 95%**

KDoc에 "여러 작업 병렬 실행 시 `StructuredTaskScopes.all { }` 사용"이라고 명시되어 있으나, 실제 구현에는 `StructuredTaskScope` 사용 코드 없음. 모든 override가 단일 `virtualFutureOf { }` 호출.

**권고**: KDoc에서 `StructuredTaskScopes.all` 언급 삭제하거나, 실제 병렬 실행 구현 추가.

---

### H11. `AStarRunner.kt:101-113`, `DijkstraRunner.kt:95-107` — `neighbourId` 함수 완전 중복
**Tier 4 | 신뢰도: 97%**

`private fun neighbourId(currentId, edge, direction)` 함수가 두 파일에 완전히 동일하게 존재.

**권고**: `algo/` 패키지 내 `GraphAlgoUtils.kt` 파일에 `internal` 함수로 추출.

---

### H12. `VertexLabel.kt:42-118`, `EdgeLabel.kt:47-126` — DSL 메서드 9개 완전 중복
**Tier 3/4 | 신뢰도: 97%**

`string`, `integer`, `long`, `boolean`, `stringList`, `json`, `localDate`, `localDateTime`, `enum` — 동일 코드 중복.

**권고**:
```kotlin
abstract class PropertyHolder {
    private val _properties = mutableListOf<PropertyDef<*>>()
    val properties: List<PropertyDef<*>> get() = _properties.toList()
    fun string(name: String) = PropertyDef<String>(name).also { _properties.add(it) }
    // ... 나머지 DSL 메서드
}
abstract class VertexLabel(val label: String) : PropertyHolder()
abstract class EdgeLabel(val label: String, val from: VertexLabel, val to: VertexLabel) : PropertyHolder()
```

---

### H13. `GraphSuspendTraversalRepository` 등 — `fun` vs `suspend fun` 혼용
**Tier 3 | 신뢰도: 85%**

`suspend` 인터페이스 내에서 `Flow<T>` 반환 메서드는 `fun`으로, `suspend` 반환 메서드는 `suspend fun`으로 선언. 네이밍 컨벤션 없이 혼용되어 구현자가 혼란.

**권고**: Flow-returning 메서드에 KDoc 추가 — "이 함수는 cold Flow 팩토리; 어느 컨텍스트에서나 호출 가능, collect는 코루틴 내에서."

---

### H14. `vt/` 패키지 확장 함수 네이밍 불일치
**Tier 4 | 신뢰도: 85%**

| 함수 | 현재 |
|------|------|
| `VirtualThreadVertexAdapter.kt:66` | `asVirtualThreadVertexRepository()` |
| `VirtualThreadEdgeAdapter.kt:57` | `asVirtualThreadEdge()` |
| `VirtualThreadTraversalAdapter.kt:60` | `asVirtualThreadTraversal()` |
| `VirtualThreadSessionAdapter.kt:35` | `asVirtualThreadSession()` |
| `VirtualThreadOperationsExt.kt:17` | `asVirtualThread()` |

3가지 네이밍 패턴 혼재. **권고**: 하나의 컨벤션으로 통일.

---

## MEDIUM (고려 사항)

### M1. `PageRankCalculator.kt:71` — 수렴 실패 시 로깅 없음
반복 cap에 걸려 종료 시 수렴 여부를 알 수 없음. `log.warn { "PageRank did not converge after $iterations iterations" }` 추가 권고.

### M2. `GraphPath.of(vararg vertices)` — `length == 0` 의미론적 오해
엣지 없는 경로 팩토리라 `length == 0`. 3-hop 경로 모킹 시 혼란. `vertexChainOf()`로 이름 변경 권고.

### M3. `GraphCycleTest.kt:88-93` — `toCycle()` 비순환 path silent failure
비순환 path로 `toCycle()` 호출해도 예외 없음. 테스트가 이 silent failure를 검증하는 게 아니라 그냥 허용. 테스트 이름 또는 동작 명확화 필요.

### M4. `GraphTraversalOptionsTest.kt` — `PathOptions` 핵심 필드 기본값 미테스트
`weightProperty`, `missingWeightPolicy`, `maxVisited` 기본값 회귀 테스트 없음.

### M5. `GraphElementId` 기본 생성자 검증 없음
`GraphElementId("")` 생성 가능. `init { require(value.isNotBlank()) }` 추가 권고.

---

## LOW (정보성)

### L1. `WeightExtractor.kt` — `IllegalArgumentException` 대신 `GraphException` 계층 사용 권고
도메인 실패를 `GraphException` 서브클래스로 throw하면 경계에서 catch 용이.

### L2. `VirtualThreadOperationsAdapter.kt:43-45` — `close()` no-op KDoc 누락
`ops.asVirtualThread().use { }` 패턴으로 사용 시 delegate가 닫히지 않음. KDoc에 명시 필요.

### L3. `GraphProperties.toCypherProps` — property key 인터폴레이션 (정보성)
신뢰된 입력만 사용하도록 KDoc에 이미 명시됨. Backend 콜사이트에서 user-controlled key가 들어오지 않는지 확인 필요.

---

## Anti-Skip Verification

| Task | Status |
|------|--------|
| Tier 1 autoresearch:security | ✅ completed |
| Tier 2 Ops/SRE | ✅ completed |
| Tier 3 code-review-graph | ✅ completed |
| Tier 4 code-reviewer | ✅ completed |
| Tier 5 pr-review-toolkit | ✅ completed |
| Tier 6 성능/안정성 | ✅ completed |
| **Final: CRITICAL 3, HIGH 14, MEDIUM 5** | **수정 작업 필요** |

---

## 우선순위별 수정 로드맵

### 즉시 수정 (CRITICAL)
1. `PageRankCalculatorTest.kt:23` — `shouldBeGreaterThan -0.001` → `shouldBeLessThan 0.001`
2. `GraphVirtualThreadOperations.kt` — `GraphSession` 상속 제거
3. `CLAUDE.md` — GraphOperations 구성 설명 수정

### 병합 전 수정 (HIGH — 알고리즘 정확성)
4. `DijkstraRunner.kt:85`, `AStarRunner.kt:88` — `fetchVertex` 루프 밖 호이스팅 + WARN 로그 추가
5. `ShortestPathFallback.kt:87` — `.distinctBy { it.id }` 추가
6. `PageRankCalculator.kt` — 파라미터 검증 + dangling nodes 사전 계산
7. `CycleDetector.kt` — 재귀 DFS → iterative 전환

### 코드 품질 개선 (HIGH — 구조/패턴)
8. `ShortestPathFallbackTest.kt` 신규 작성
9. `CompletableFutureNullableSupport.kt` 패키지 이동
10. `VertexLabel/EdgeLabel` DSL 중복 → `PropertyHolder` 추출
11. `AStarRunner/DijkstraRunner` `neighbourId` 함수 → `GraphAlgoUtils.kt` 추출
12. `VirtualThreadAlgorithmAdapter` KDoc 수정
13. VT 확장 함수 네이밍 통일
