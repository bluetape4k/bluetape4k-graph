# #311 graph-io progress listener와 Micrometer bridge

## 결정

- 기존 sync·suspend·Virtual Thread public overload를 유지하고 listener를
  마지막 required parameter로 추가해 source/ABI 호환성을 보존했다.
- 공통 interface의 기본 listener overload는 레거시 구현체에 operation/format
  메타데이터가 없으므로 listener를 검증한 뒤 기존 함수로만 위임한다. 내장
  CSV/Jackson/GraphML/Okio 구현체만 전체 lifecycle event를 보장한다.
- lifecycle은 호출별 reporter 하나가 소유하며 `STARTED`부터 terminal까지
  순서를 직렬화한다. 포맷별 report가 제공하는 aggregate snapshot으로
  `PHASE_COMPLETED`와 `PROGRESS`를 만들고, record마다 callback을 만들지 않는다.
- 경로 기반 처리에서는 terminal 시점의 확인 가능한 파일 크기를 bytes로
  기록하고, stream/압축/암호화처럼 논리 바이트를 증명할 수 없는 경로는
  `null`을 유지한다. 실패 결과의 `bytesProcessed`는 부분 파일 오인을 막기
  위해 비운다.
- Micrometer bridge는 operation·format·status·kind·phase enum tag만 사용한다.
  dataset path, record ID, run ID, 예외 정보는 metric tag나 warning에 넣지 않는다.
- Spring Boot는 `bluetape4k.graph.io.metrics.enabled=true`와 bridge/
  `MeterRegistry` classpath가 모두 있을 때만 명시적 bean 이름으로 등록한다.
  concrete bridge는 일반 listener 자동 주입 후보가 아니다.

## 검증

- core reporter/serialization/composite 테스트와 Virtual Thread
  `cancel(false)`/`cancel(true)` 테스트 통과.
- CSV·Jackson2·Jackson3·GraphML round-trip listener ordering 및 Okio
  Virtual Thread listener ordering 테스트 통과.
- `:bluetape4k-graph-io-micrometer:test` 2개 테스트 통과.
- `GraphIoMicrometerAutoConfigurationTest` 5개 ApplicationContextRunner 테스트
  통과: 기본 disabled, registry 부재 back-off, optional classpath back-off,
  positive bean registration.
- `.github/workflows/nightly-tests.yml` 변경 후 `scope=full`을
  `develop` 기준으로 dispatch했다. [Nightly run 31451878357](https://github.com/bluetape4k/bluetape4k-graph/actions/runs/31451878357)는
  wrapper validation, compile/detekt, graph backends, Spring Boot,
  Testcontainers, Kover coverage aggregation을 모두 성공했다. feature branch는
  아직 publish하지 않았으므로 해당 run의 head SHA는 baseline
  `5ec93ef4b98e1654480bf831b13defd5aae057b7`이다.
- ABI 호환 smoke를 `origin/develop` 기준 SHA
  `5ec93ef4b98e1654480bf831b13defd5aae057b7`에서 별도 precompiled 3-인자
  fixture로 만들고 새 core에 링크했다. `-jvm-default=enable` 조건에서
  결과는 `ABI_LINK_OK`였으며, 같은 소스셋 재컴파일이 아닌 별도 artifact
  링크를 사용했다.

## 후속 주의

phase stopwatch가 포맷 내부에 별도로 노출되지 않는 경로에서는 aggregate
report elapsed를 bounded phase duration fallback으로 사용한다. 정밀한 phase별
stopwatch가 필요하면 각 포맷 parser/batch writer에 reporter context를 직접
전달하는 별도 이슈로 분리한다.
