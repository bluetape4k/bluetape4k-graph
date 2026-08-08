# 이슈 #193 그래프 벤치마크 프로그램 계획

## 작업 단계

1. `benchmark/graph-benchmark` 의존성이 그래프 DB 백엔드와 graph-io 모듈을 포함하도록 확장한다.
2. `GraphOperations`를 사용하는 그래프 DB 비교 벤치마크를 추가한다.
3. CSV, Jackson2, Jackson3, GraphML 및 OkIO 경로를 사용하는 graph-io 비교 벤치마크를 추가한다.
4. 전후 비교를 위한 JMH 보고서 정규화를 추가한다.
5. 로컬 self-improve 설정과 sealed-file validator를 추가한다.
6. 컴파일 및 파서 동작을 검증한다.
7. lesson을 기록하고 Epic #193 및 하위 이슈 #188-#192에 작업을 연결한다.

## 검증

- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin --no-build-cache`
- `python3 benchmark/graph-benchmark/scripts/normalize_jmh_report.py benchmark/graph-benchmark/src/test/resources/jmh/sample-main.json --markdown /tmp/graph-benchmark-sample.md`
- `scripts/validate-graph-benchmark-sealed.sh HEAD`
