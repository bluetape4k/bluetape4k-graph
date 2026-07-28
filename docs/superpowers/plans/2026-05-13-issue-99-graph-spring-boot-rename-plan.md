# Issue #99 graph-spring-boot Rename Plan

- Spec: `docs/superpowers/specs/2026-05-13-issue-99-graph-spring-boot-rename-design.md`
- Branch: `refactor/issue-99-graph-spring-boot`
- 목표: freeze the Spring Boot integration identity as `graph-spring-boot` before broader adoption.

## Task List

| Task | 범위 | 검증 |
|---|---|---|
| T1 | Move `spring-boot4/graph-spring-boot4-starter` to `spring-boot/graph-spring-boot` | `find spring-boot -maxdepth 2 -type d` |
| T2 | Update `settings.gradle.kts` include base from `spring-boot4` to `spring-boot` | `./gradlew -q projects` |
| T3 | Rename package from `io.bluetape4k.graph.spring.boot4` to `io.bluetape4k.graph.spring.boot` in source, tests, source directories, and AutoConfiguration imports | `rg 'spring\\.boot4|graph\\.spring\\.boot4' spring-boot`; `find spring-boot/graph-spring-boot/src -path '*boot4*'` returns no files |
| T4 | Update CI and Nightly tasks/path filters to `:graph-spring-boot` and `spring-boot/**` | `actionlint .github/workflows/ci.yml .github/workflows/nightly.yml` |
| T5 | Update current README/BOM/CHANGELOG/module docs to `graph-spring-boot` | `rg 'graph-spring-boot4-starter|spring-boot4/graph-spring-boot4-starter|SB4|io\\.bluetape4k:graph-' README*.md bom CHANGELOG.md spring-boot AGENTS.md CLAUDE.md` |
| T6 | Update current issue #96 references that explicitly say issue #99 will rename the module | targeted `rg` over `docs/superpowers/specs/2026-05-12-issue-96-graph-ktor-design.md` |
| T7 | Compile/test renamed module | `./gradlew :graph-spring-boot:compileKotlin :graph-spring-boot:compileTestKotlin --no-daemon`; `./gradlew :graph-spring-boot:test --tests '*TinkerGraph*' --no-daemon` |
| T8 | Verify publication coordinate | `./gradlew :graph-spring-boot:generatePomFileForBluetapeGraphPublication --no-daemon`; `rg '<artifactId>graph-spring-boot</artifactId>' spring-boot/graph-spring-boot/build/publications/BluetapeGraph/pom-default.xml` |
| T9 | Final grep/diff checks and lesson capture | `git diff --check`; `rg` old identifiers |

## Notes

- Package rename is intentional. It prevents the old `boot4` suffix from remaining in public imports after the artifact is renamed.
- Historical design/plans from April may keep old names if the sentence describes old implementation history. Current user-facing docs and code must use the new name.
- Workflow YAML edits require `actionlint`.

## Review Gate

### Iteration 1

| Severity | Finding | Resolution |
|---|---|---|
| P1 | POM generation alone does not prove the published artifact coordinate changed. | T8 now verifies the generated POM contains `<artifactId>graph-spring-boot</artifactId>`. |
| P1 | Package declaration rename without source directory rename leaves `boot4` in the physical source path. | T3 now includes physical source directory verification. |
| P1 | BOM Mermaid node rename can leave `SB4` edges behind. | T5 now greps for `SB4` and current docs dependency coordinate mistakes. |
| P1 | BOM README dependency snippets can drift from generated Maven artifact coordinates. | Snippets now use `graph-neo4j` and `graph-spring-boot`; T8 verifies the generated Spring Boot POM artifactId. |
| P1 | Changelog changes can accidentally rewrite historical release facts. | The rename is recorded in Unreleased; the 0.2.0 entry keeps the original shipped module name. |
| P0 | None | N/A |

P0/P1 status after iteration 1: P0 = 0, P1 = 0 for Codex self-review. External Claude advisor result is pending and must be integrated before closing the full Step 2-R/3-R gate.
