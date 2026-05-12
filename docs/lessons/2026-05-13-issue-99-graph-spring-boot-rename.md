# Issue #99 Graph Spring Boot Rename

- Context: Issue #99 renamed the Spring Boot integration from the historical `graph-spring-boot4-starter` identity to the stable `graph-spring-boot` contract.
- Decision: Treat the rename as a full public identity change: directory, Gradle project, package namespace, AutoConfiguration imports, workflows, module README files, BOM README snippets, and generated publication coordinates must move together.
- Outcome: The module now lives under `spring-boot/graph-spring-boot`, registers as `:graph-spring-boot`, publishes `graph-spring-boot`, and imports from `io.bluetape4k.graph.spring.boot`.
- Verification: `./gradlew -q projects`, compile/testCompile, TinkerGraph tests, generated POM artifactId grep, `actionlint`, current-surface stale identifier grep, and `git diff --check`.
- Future rule: For module rename work, do not stop at source package replacement. Verify generated Maven POM coordinates, README dependency snippets, workflow task names, Mermaid node ids, and physical Kotlin source directories. Keep historical changelog entries factual and add the rename under Unreleased.
