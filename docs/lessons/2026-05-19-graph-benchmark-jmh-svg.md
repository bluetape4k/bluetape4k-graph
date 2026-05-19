# 2026-05-19 — graph-benchmark JMH 벤치마크 추가와 SVG 시각화

> **Issue**: #14 graph-benchmark: 백엔드별 JMH 벤치마크 (정점 삽입 / 최단경로 / 이웃 탐색)
> **PR**: feat/issue-14-graph-benchmark
> **Scope**: `benchmark/graph-benchmark/`, `benchmark/scripts/`, `docs/`, `.github/workflows/`

## 결정 요약

- `BatchInsertBenchmark`, `ShortestPathBenchmark`, `NeighborsBenchmark` 3개 JMH 클래스 추가.
- kotlinx-benchmark `reportFormat = "json"`로 JMH 호환 JSON 출력 활성화.
- Python 표준 라이브러리만 사용한 SVG 바 차트 렌더러(`benchmark/scripts/render_benchmark_svg.py`) 작성.
- `docs/graphdb-tradeoffs.md`에 실측 수치 표 + SVG 임베딩.
- `benchmark.yml` `workflow_dispatch` CI workflow 추가 — TinkerGraph 기준선만 자동 측정.
- Neo4j / Memgraph / AGE 백엔드 벤치마크는 후속 과제로 분리 (Testcontainers 의존성 필요).

## 외부 리뷰에서 잡은 P1/P2

### P1 (HIGH) — Silent no-op workflow input

`benchmark.yml`에 `include_pattern` 입력을 노출했지만, `build.gradle.kts`가 `includeBenchmarks` 프로퍼티를 읽지 않았다. UI에는 정규식 필터처럼 보이지만 실제로는 전체가 실행되는 **silent no-op**.

- **수정**: 입력 자체를 제거. 향후 진짜 필터링이 필요하면 별도 PR에서 `(project.findProperty("includeBenchmarks") as String?)?.let { include(it) }`로 build script에 연결.
- **교훈**: workflow input은 실제 build/script에 연결되었는지 grep으로 확인할 것. 노출만 하고 미연결 상태는 거짓 약속이다.

### P2 — ShortestPathBenchmark random pair의 측정 왜곡

체인 그래프는 `0→1→…→999`의 **directed** 토폴로지인데, 무작위 (from, to) 쌍을 생성하면 약 50%가 backward pair가 되어 즉시 종료(no-path)된다. 측정값이 실제 BFS 비용을 반영하지 못함.

- **수정**: `i = rng.nextInt(size - 1)`, `j = rng.nextInt(i + 1, size)`로 forward pair만 생성. KDoc에 토폴로지 제약 명시.
- **교훈**: 합성 그래프 벤치마크에서 edge direction과 random pair 분포를 반드시 같이 검토할 것. 결과가 docs에 임베딩되는 경우 신뢰성 직결.

## Codex vs Claude 리뷰 비교

| 항목 | Codex `review --uncommitted` | Claude `code-reviewer` |
|------|-------------------------------|------------------------|
| 발견 건수 | P3 1건 (lock 파일) | HIGH 1, MEDIUM 5 |
| 비주얼 분석 | 변경 diff에 거의 집중하지 않고 untracked 운영 파일 1건만 지적 | 패치 전체를 정밀 분석, 측정 왜곡 / silent no-op 등 본질적 이슈 식별 |
| 비용 | ~3분 / MCP tool 다수 호출 | ~2분 / 1 agent |

**교훈**: `codex review --uncommitted`는 staged 변경 외에 untracked 파일도 본다. 합법적인 운영 lock 파일이 worktree에 있으면 거짓 양성을 만든다. 다음에는 prompt에 "리뷰 대상은 staged source만, untracked 런타임 파일 제외" 를 명시할 것.

## 후속 과제

- **P2 잔존**: `BatchInsertBenchmark`가 데이터셋 생성(`vertexRows()` + `createVertices()`)을 측정 영역 안에서 수행 → 별도 issue로 분리. `@Setup(Level.Invocation)` 또는 `Mode.SingleShotTime` 전환 필요.
  - 단, 기존 `VertexOperationsBenchmark.createVertices10kLoop/Batch`도 동일 패턴이므로 함께 리팩토링.
- **P2 잔존**: `render_benchmark_svg.py`가 CWD에 의존(`docs/benchmark-results/`) → `--out-dir` 플래그 추가.
- **P2 잔존**: SVG 스크립트가 missing/null 필드를 silent coerce → assert/warn + 단위 테스트 추가.
- **P2 잔존**: 신규 3개 벤치마크가 `GraphBenchmarkState`를 상속하지 않음 → 자체 setup 사용 이유 명시 또는 `LargeGraphBenchmarkState` 도입.
- **백엔드 확장**: Neo4j / Memgraph / AGE 벤치마크 (Testcontainers + `@Param("backend")`) — 별도 PR.

## 측정 환경

- macOS Darwin 25.4.0 / Apple Silicon
- JDK 21 (preview)
- kotlinx-benchmark + JMH (정확한 버전은 `gradle/libs.versions.toml` 참고)
- 측정 설정: warmup 3회 × 2초, measurement 5회 × 3초, fork 1

## 관련 파일

- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/BatchInsertBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/ShortestPathBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/NeighborsBenchmark.kt`
- `benchmark/scripts/render_benchmark_svg.py`
- `.github/workflows/benchmark.yml`
- `docs/graphdb-tradeoffs.md`
