# #526 Testcontainers image family gate 운영 계약

이 문서는 `1.0.0` milestone의 #526이 추가한 graph backend image 검증 gate를
운영하는 방법을 정의한다. 현재 image tag는 `.github/testcontainers-images.txt`가
권위 있는 목록이며, family 이름과 image 이름의 대응은
`.github/testcontainers-image-families.txt`가 소유한다. 이 이슈는 image version을
업그레이드하지 않고, 변경된 family가 실제로 기동하고 대표 workload를 수행하는지
검증하는 데만 사용한다.

## Family와 workload

`scripts/testcontainers_image_gate_manifest.json`은 다음 네 family의 실행 계약을
고정한다.

| family | Gradle test | startup readiness | 대표 workload |
|---|---|---|---|
| `neo4j` | `:bluetape4k-graph-neo4j:test` | `Neo4jServer.Launcher.neo4j.boltUrl` 연결 | `Neo4jGraphCapabilityConformanceTest`의 batch/chunk, merge, transaction |
| `memgraph` | `:bluetape4k-graph-memgraph:test` | `MemgraphServer.Launcher.memgraph.boltUrl` 연결 | `MemgraphGraphCapabilityConformanceTest`의 batch/chunk, merge, transaction |
| `age` | `:bluetape4k-graph-age:test` | PostgreSQL JDBC와 AGE extension 준비 | `AgeGraphCapabilityConformanceTest`의 batch/chunk, merge, transaction |
| `falkordb` | `:bluetape4k-graph-falkordb:test` | `FalkorDBServer.Launcher.falkordb` host/port 준비 | `FalkorDBGraphCapabilityConformanceTest`의 batch/chunk, merge |

검증 runner는 family를 병렬화하지 않는다. Docker resource와 backend 로그를
family별로 분리하고, 한 family의 실패를 다음 family의 성공으로 덮지 않기 위해
순차 실행한다.

## 실행

변경 범위만 검증하려면 repository root에서 다음을 실행한다.

```bash
python3 scripts/run_testcontainers_image_gate.py \
  --scope changed \
  --changed-path graph/graph-neo4j/src/testFixtures/kotlin/io/bluetape4k/graph/neo4j/Neo4jServer.kt \
  --report-dir build/reports/testcontainers-image-gate
```

Nightly와 release는 네 family를 모두 실행한다.

```bash
python3 scripts/run_testcontainers_image_gate.py \
  --scope full \
  --report-dir build/reports/testcontainers-image-gate \
  --max-attempts 3 \
  --timeout-minutes 30
```

CI의 changed gate는 exact base/head diff에서 경로를 수집한다. image manifest,
shared launcher, Gradle 설정, gate script, CI/Nightly/release workflow가 바뀌면
네 family를 모두 선택한다. backend의 test 또는 testFixtures 경로만 바뀌면 해당
family만 선택하며, unrelated 문서 변경은 `skipped`로 끝난다.

## 실패 분류와 retry

각 family는 최대 세 번 시도하지만 retry는 원인을 숨기지 않는다.

- `readiness_timeout`: container startup 또는 wait strategy가 제한 시간 안에
  readiness를 충족하지 못한 경우
- `pull_rate_limit`: registry의 `429` 또는 `toomanyrequests`가 관찰된 경우
- `image_pull_failure`: image/tag를 찾지 못하거나 pull 권한이 없는 경우
- `infrastructure_failure`: Docker daemon, disk, transport 등 실행 환경 문제
- `application_failure`: image가 기동했지만 대표 conformance workload가 실패한 경우

첫 시도 성공만 `success`이며 release gate를 열 수 있다. 첫 시도 실패 후 retry로
성공하면 `success_after_retry`로 기록하고 release gate는 `false`로 유지한다.
Rate limit도 예외가 아니므로 retry 성공만으로 green으로 바꾸지 않는다.

## 증거 artifact

`--report-dir`에는 family별 JSON, `summary.json`, `summary.md`가 생성된다.
실패하거나 retry가 발생한 family에는 다음 Docker 증거가 포함된다.

- image reference와 `RepoDigests`/image ID
- `docker inspect` 결과와 대상 container logs
- 최근 Docker events와 matching container 목록
- 각 시도의 명령, return code, elapsed time, 분류된 원인, 제한된 stdout/stderr

로그에는 password, token, bearer credential 같은 값이 redaction된 뒤 저장된다.
출력은 bounded되므로 원본 전체 로그가 필요하면 hosted artifact의 backend별
workflow 로그를 함께 확인한다.

## Gate 판정과 대응

`summary.json`의 `status`가 `success`이고 `coverage`가 선택 family 수와 같아야
해당 gate가 통과한다. release workflow는 `coverage=4/4` 및 `release_gate=true`를
명시적으로 검사한 후에만 Maven Central publication job을 시작한다.

실패 시에는 먼저 `first_failure`와 `diagnostics.image_digest`를 확인하고,
readiness 문제와 registry rate limit을 application failure로 재분류하지 않는다.
image digest가 바뀌었다면 tag 변경 없이 registry가 tag를 재지정했는지 조사한다.
원인 수정 후에는 같은 exact head에서 full gate를 다시 실행하고, 이전 retry 성공을
새로운 첫 시도 성공 증거로 재사용하지 않는다.
