# Issue #111 Sample Dataset Loaders

## Context

Issue #111 adds graph-io backed sample data loaders for the fraud detection, recommendation, and knowledge graph
example modules.

## Decision

Keep the loaders module-local and resource-backed. TinkerGraph smoke tests are sufficient for the import path because
the loaders call graph-io through the common `GraphOperations` contract, while existing backend matrix tests continue to
cover traversal behavior.

## Outcome

Added sync and suspend CSV loader entry points, bundled domain fixtures, README import examples, and TinkerGraph smoke
tests for each module.

## Verification

- `./gradlew :fraud-detection-examples:compileKotlin :fraud-detection-examples:compileTestKotlin :fraud-detection-examples:test --tests "io.bluetape4k.graph.examples.fraud.FraudDetectionSampleDatasetLoaderTest" :recommendation-examples:compileKotlin :recommendation-examples:compileTestKotlin :recommendation-examples:test --tests "io.bluetape4k.graph.examples.recommendation.RecommendationSampleDatasetLoaderTest" :knowledge-graph-examples:compileKotlin :knowledge-graph-examples:compileTestKotlin :knowledge-graph-examples:test --tests "io.bluetape4k.graph.examples.knowledge.KnowledgeGraphSampleDatasetLoaderTest" --console=plain --no-daemon` passed with 12 sample loader tests.
- `./gradlew :fraud-detection-examples:test :knowledge-graph-examples:test :recommendation-examples:test --tests '*SampleDatasetLoaderTest'` passed during Codex review, including the existing fraud and knowledge backend matrices plus the new loader tests.
- Example modules do not expose Detekt tasks; repository-wide `detektMain detektTest` still fails on pre-existing non-example issues.
- Codex review found no actionable bugs after the class loader fallback fix.
- Claude review was run and the P1 findings were fixed: KDoc contract sections, verification evidence, and stream cleanup on failure paths.

## Future Guard

When example modules add sample data, keep fixtures small, cover the graph-io import report counts, and assert at least
one domain service query against the imported graph.
