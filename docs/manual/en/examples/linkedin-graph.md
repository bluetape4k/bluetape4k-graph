# Professional social graph

## Problem and backend

This example connects people, employers, skills, and endorsements to exercise several social-graph lookup shapes. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Person/Company/Skill
- Edges: KNOWS/WORKS_AT/FOLLOWS/HAS_SKILL/ENDORSES
- Key properties: name, title, company, skills, strength, role, level

## Prerequisites and release boundary

Use JDK 21, commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :linkedin-graph-examples:test --tests "io.bluetape4k.graph.examples.linkedin.TinkerGraphLinkedInGraphTest"
```

The tests assert direct connections, a multi-hop connection path, second-degree connections, employer lookup, and follower lookup. A failure identifies which social relation or lookup direction no longer matches the fixture, so inspect that assertion before widening traversal depth.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/schema/LinkedInSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/service/LinkedInGraphService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AbstractLinkedInGraphTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/TinkerGraphLinkedInGraphTest.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/build.gradle.kts)

Continue [from recommendation](./recommendation.md), then read [iam-access-graph](./iam-access-graph.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### linkedin graph examples architecture

[![linkedin graph examples architecture](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-architecture-01.svg)

_Release README: [`examples/linkedin-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/README.md)_

### linkedin graph examples data flow

[![linkedin graph examples data flow](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-data-flow-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-data-flow-03.svg)

_Release README: [`examples/linkedin-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/README.md)_

### linkedin graph examples ERD

[![linkedin graph examples ERD](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-erd-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-erd-02.svg)

_Release README: [`examples/linkedin-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/README.md)_

<!-- release-readme-diagrams:end -->
