# 이슈 #111 Sample dataset loaders 설계

## 맥락

fraud detection, recommendation, knowledge graph example은 이미 backend-independent domain을 보여준다.
services. Issue #111 adds graph-io backed sample import paths so learners can load realistic fixtures instead of
building every graph imperatively in code.

## 결정

Add one small loader object per domain example module:

- `FraudDetectionSampleDatasetLoader`
- `RecommendationSampleDatasetLoader`
- `KnowledgeGraphSampleDatasetLoader`

각 loader는 `CsvGraphBulkImporter`와 `SuspendCsvGraphBulkImporter`를 통해 bundled CSV resource를 import하며,
기존 `GraphOperations` / `GraphSuspendOperations` split을 유지한다. Resource path와 domain fixture semantics가
module-specific이므로 loader는 shared module이 아니라 example module 안에 유지한다.

## Contract

- Default resources live under `sample-data/{domain}/vertices.csv` and `sample-data/{domain}/edges.csv`.
- CSV files use graph-io prefixed property columns such as `prop.accountId`.
- Import reports must complete with the expected vertex and edge counts on TinkerGraph.
- Domain services must be able to query the imported graph immediately after import.
- README.md and README.ko.md must describe the import flow.

## 검증 전략

loader가 backend-independent 경로로 graph-io를 실행하므로 loader path에는 TinkerGraph smoke test를 사용한다.
`GraphOperations` contract. Container-backed backend traversal behavior remains covered by the existing domain test
matrix, and issue #111 does not change backend-specific import behavior.
