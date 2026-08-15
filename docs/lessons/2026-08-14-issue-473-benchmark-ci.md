# #473 benchmark CI 모듈 카탈로그

## 결정

등록된 benchmark 모듈을 `benchmark/benchmark-modules.json`에 한 번만 선언하고,
CI lifecycle job과 수동 JMH workflow가 같은 JSON을 GitHub Actions matrix로
변환해 사용한다. CI matrix는 `max-parallel: 1`로 실행하며, Testcontainers
이미지 캐시와 테스트 artifact 이름에 모듈 식별자를 포함한다.

## 이유

`settings.gradle.kts`가 자동으로 모듈을 등록해도 workflow가 모듈을 직접 나열하면
새 benchmark가 compile/test와 JMH artifact 수집에서 빠질 수 있다. 공통 catalog와
계약 테스트로 등록 디렉터리와 catalog의 집합을 비교하면 drift를 로컬에서
실패시키고, 모듈별 artifact 이름과 직렬 실행으로 backend benchmark의 충돌을
피할 수 있다.

## 검증

- `actionlint .github/workflows/ci.yml .github/workflows/benchmark.yml`
- `:graph-benchmark:test` — 28개 테스트 통과
- `:graph-age-benchmark:test` — 1개 테스트 통과
- `:graph-io-benchmark:test` — 1개 테스트 통과
- `:graph-neo4j-benchmark:test` — 1개 테스트 통과
- `git diff --check`

수동 JMH 실행과 hosted CI는 PR의 정확한 head에서 후속으로 확인한다.
