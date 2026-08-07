# 그래프 입출력 벤치마크

`graph-io-benchmark`는 TinkerGraph와 임시 파일로 CSV, Jackson 2/3 NDJSON, GraphML, OkIO의 내보내기·가져오기·왕복을 측정합니다. [빌드 설정](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-io-benchmark/build.gradle.kts)은 main에 warmup 3회와 3초 측정 5회를 둡니다. [상태 클래스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-io-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/io/BulkGraphIoBenchmarkState.kt)가 임시 자원을 관리합니다.

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 임시 디스크 공간이 필요합니다. Docker는 필요 없습니다.

```bash
./gradlew :graph-io-benchmark:smokeBenchmark --rerun-tasks --console=plain
./gradlew :graph-io-benchmark:benchmark --rerun-tasks --console=plain
```

smoke는 `sizeName=smoke`, warmup 1회, 200ms 측정 1회로 배선만 확인합니다. 성능 수치로 쓰지 마십시오. 전체 실행의 평균 `ms/op`은 낮을수록 좋으며 크기와 실행 방식이 같은 행만 비교해야 합니다. 커밋된 숫자는 [2026-04-18 결과](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/2026-04-18-graph-io-bulk-results.md)에 실행 조건과 함께 있습니다.

전체 실행을 세 번 반복하고 구간을 비교하십시오. 파일 시스템 캐시, 압축, 디스크 경합은 warmup으로 없어지지 않습니다. 종료 단계가 임시 디렉터리를 지우며, 중단했다면 모듈 빌드 디렉터리와 운영체제 임시 공간을 확인하십시오.

가져온 정점·간선 수가 다르면 지연 시간보다 정확성 실패를 먼저 고쳐야 합니다. 이 결과는 원격 데이터베이스 처리량, 대규모 그래프 메모리 한계, 암호화 저장소, 장애 복구를 증명하지 않습니다. [graph-io 실행 방식](../graph-io/execution-model.md)과 [개요](./overview.md)를 보십시오.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph-io-benchmark 아키텍처

[![Bluetape4k Graph-io-benchmark 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-io-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-io-benchmark-architecture-01.svg)

_배포본 README: [`benchmark/graph-io-benchmark/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-io-benchmark/README.ko.md)_

<!-- release-readme-diagrams:end -->
