# Issue #260 PostgreSQL Traversal Benchmark Plan

## Scope

Implement child issues #261 through #265 in one feature branch:

- #261 shared traversal workload contracts.
- #262 AGE/Cypher traversal benchmark implementation.
- #263 PostgreSQL recursive CTE and iterative traversal baselines.
- #264 explicit ORM baseline boundaries.
- #265 benchmark report, chart, and README documentation.

## Step Plan

1. Baseline inspection
   - Confirm `benchmark/graph-benchmark` uses `kotlinx-benchmark`.
   - Inspect existing AGE/Testcontainers setup.
   - Check PostgreSQL and Hibernate dependency-management paths.

2. Contract implementation
   - Add authorization inheritance fixture, result metrics, oracle, and engine interface.
   - Keep bounded fraud/abuser fixture and metrics for the secondary comparison.
   - Add smoke tests that prove result-set equivalence and F1 `1.0`.

3. Storage implementations
   - Add AGE/Cypher authorization traversal.
   - Add PostgreSQL recursive CTE authorization traversal.
   - Add PostgreSQL iterative authorization traversal.
   - Add native Neo4j Cypher authorization traversal for the adoption decision surface.
   - Split fraud relational baselines into recursive CTE and iterative traversal.
   - Exclude TinkerGraph from this GraphDB adoption benchmark only.
   - Add `long-chain` and `deep-wide` adoption scenarios with `large` data and 10-12 hop paths.

4. Benchmark class and Gradle wiring
   - Add `AuthzInheritanceBenchmark` with `kotlinx.benchmark` annotations.
   - Add `authzInheritanceSmoke`, `authzInheritance`, and `authzInheritanceAdoption` benchmark configurations.
   - Keep `AbuserDetectionBenchmark` and expose CTE/iterative backend parameters.
   - Keep Testcontainers-backed execution serial.

5. Documentation and evidence
   - Commit representative raw JSON under `docs/benchmark/`.
   - Generate Markdown result table plus PNG/SVG chart assets.
   - Update `benchmark/graph-benchmark/README.md` and `README.ko.md`.
   - Update the lesson file with the measured conclusion.

6. Verification and review
   - Run targeted unit/smoke tests.
   - Run `:graph-benchmark` compile.
   - Run `authzInheritanceBenchmark`.
   - Run `git diff --check`.
   - Run local review and fix P0/P1 before finalizing.

## Validation Commands

```bash
./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin \
  :graph-benchmark:test \
  --tests "io.bluetape4k.graph.benchmark.authz.AuthzInheritanceEngineSmokeTest" \
  --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionContractTest" \
  --tests "io.bluetape4k.graph.benchmark.abuser.AbuserDetectionEngineSmokeTest" \
  --no-build-cache

./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache
./gradlew :graph-benchmark:authzInheritanceAdoptionBenchmark --no-build-cache

git diff --check
```

## DoD

| Item | Evidence |
|---|---|
| Shared traversal contracts implemented | Contract source and unit/smoke tests |
| AGE/Cypher traversal implemented | Smoke test and benchmark inclusion |
| PostgreSQL CTE baseline implemented | Separate benchmark parameter and smoke test |
| PostgreSQL iterative baseline implemented | Separate benchmark parameter and smoke test |
| Native graph adoption baseline implemented | Neo4j Cypher benchmark parameter and large adoption measurement |
| ORM boundaries documented | JPA fraud baseline remains explicit |
| Result docs updated | README/docs with command, conditions, table, chart, raw evidence path |
| Local verification complete | Gradle/test/benchmark/diff-check output |
| TinkerGraph excluded from adoption benchmark | README/docs/issue/PR state that TinkerGraph remains only in separate in-memory tracks |
| Large/deep adoption surface added | `large` dataset, `long-chain`, and `deep-wide` benchmark task |

## Known Constraints

- Benchmark-owned `DataSource` setup is allowed here; production AGE managed DataSource remains outside this slice.
- TinkerGraph is excluded only from this adoption benchmark; unrelated in-memory benchmark tracks stay intact.
- AGE `large + long-chain` and Memgraph `large + long-chain` are not default adoption-task rows because the local diagnostic run produced timeout/load-failure evidence.
- Public GitHub issue, PR, and commit text must be English.
- Internal spec/plan/lesson can use Korean or English; this plan uses English for reuse.
