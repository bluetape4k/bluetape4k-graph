# Issue #260 Abuser Detection Benchmark Plan

## Scope

Implement child issues #261 through #265 in one feature branch:

- #261 shared PostgreSQL abuser detection workload contract.
- #262 AGE plus Exposed benchmark implementation.
- #263 Exposed JDBC relational baseline.
- #264 JPA/Hibernate PostgreSQL baseline.
- #265 benchmark report and README documentation.

## Step Plan

1. Baseline inspection
   - Confirm `benchmark/graph-benchmark` uses `kotlinx-benchmark`.
   - Inspect existing AGE/Testcontainers benchmark setup.
   - Check dependency-management path for JPA/Hibernate.

2. Contract implementation
   - Add fixture generation, domain value objects, signal kinds, result metrics, and engine interface under `benchmark/graph-benchmark`.
   - Add pure unit tests for deterministic fixture and metric math.

3. Storage implementations
   - Add AGE + Exposed engine using `GraphOperations`.
   - Add Exposed JDBC engine using PostgreSQL tables and SQL queries.
   - Add JPA/Hibernate engine using programmatic persistence setup.
   - Add smoke tests that load/detect the same fixture per engine.

4. Benchmark class and Gradle wiring
   - Add `AbuserDetectionBenchmark` with `kotlinx.benchmark` annotations.
   - Add `abuserDetectionSmoke` and `abuserDetection` benchmark configurations.
   - Keep Testcontainers-backed execution serial.

5. Documentation and evidence
   - Add or update benchmark docs and README sections.
   - Commit raw result JSON only when a representative run completes locally.
   - Add `docs/lessons/2026-05-28-issue-260-abuser-detection-benchmark.md`.

6. Verification and review
   - Run targeted unit tests.
   - Run compile for `:graph-benchmark`.
   - Run smoke benchmark or a bounded fallback if local containers fail.
   - Run `git diff --check`.
   - Run local 7-tier review and fix P0/P1 before PR.

## Validation Commands

Preferred commands:

```bash
./gradlew :graph-benchmark:compileKotlin :graph-benchmark:test --tests "*Abuser*"
./gradlew :graph-benchmark:abuserDetectionSmokeBenchmark
git diff --check
```

If Gradle emits a different benchmark task name, verify with:

```bash
./gradlew :graph-benchmark:tasks --group benchmark
```

## DoD

| Item | Evidence |
|---|---|
| Shared workload contract implemented | Contract source and unit tests |
| AGE engine implemented | Smoke detection test and benchmark inclusion |
| Exposed engine implemented | Smoke detection test and benchmark inclusion |
| JPA/Hibernate engine implemented | Smoke detection test and benchmark inclusion |
| Docs updated | README/docs with command, conditions, table, raw evidence path |
| Local verification complete | Gradle/test/diff-check output |
| Review gate closed | Local 7-tier review P0=0 P1=0 |

## Known Constraints

- Benchmark-owned `DataSource` setup is allowed here; production AGE managed DataSource remains outside this slice.
- Public GitHub issue, PR, and commit text must be English.
- Internal spec/plan/lesson can use Korean or English; this plan uses English for reuse.
