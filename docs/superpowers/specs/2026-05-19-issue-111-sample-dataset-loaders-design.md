# Issue #111 Sample Dataset Loaders Design

## Context

The fraud detection, recommendation, and knowledge graph examples already demonstrate backend-independent domain
services. Issue #111 adds graph-io backed sample import paths so learners can load realistic fixtures instead of
building every graph imperatively in code.

## Decision

Add one small loader object per domain example module:

- `FraudDetectionSampleDatasetLoader`
- `RecommendationSampleDatasetLoader`
- `KnowledgeGraphSampleDatasetLoader`

Each loader imports bundled CSV resources through `CsvGraphBulkImporter` and `SuspendCsvGraphBulkImporter`, preserving
the existing `GraphOperations` / `GraphSuspendOperations` split. The loaders stay in the example modules rather than a
shared module because the resource paths and domain fixture semantics are module-specific.

## Contract

- Default resources live under `sample-data/{domain}/vertices.csv` and `sample-data/{domain}/edges.csv`.
- CSV files use graph-io prefixed property columns such as `prop.accountId`.
- Import reports must complete with the expected vertex and edge counts on TinkerGraph.
- Domain services must be able to query the imported graph immediately after import.
- README.md and README.ko.md must describe the import flow.

## Verification Strategy

Use TinkerGraph smoke tests for the loader path because the loaders exercise graph-io through the backend-independent
`GraphOperations` contract. Container-backed backend traversal behavior remains covered by the existing domain test
matrix, and issue #111 does not change backend-specific import behavior.
