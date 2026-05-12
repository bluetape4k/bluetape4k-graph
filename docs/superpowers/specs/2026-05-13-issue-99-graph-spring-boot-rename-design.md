# Issue #99 graph-spring-boot Rename Design

- Issue: #99 `refactor: graph-spring-boot module naming 정리`
- Date: 2026-05-13
- Scope: Spring Boot integration module identity, package namespace, CI/Nightly, README/BOM/CHANGELOG, generated publication coordinate.
- Workflow: Type A Full Design because this freezes a public module/artifact contract before broader adoption.

## 1. Problem

`bluetape4k-graph` currently exposes the Spring Boot integration as:

```text
spring-boot4/graph-spring-boot4-starter
Gradle project: :graph-spring-boot4-starter
Package: io.bluetape4k.graph.spring.boot4
Artifact expectation in docs: graph-spring-boot4-starter
```

This name still carries two historical implementation details:

- `boot4`: the repo has already removed the Spring Boot 3 starter, so the suffix no longer distinguishes parallel artifacts.
- `starter`: other bluetape4k integration modules, especially `bluetape4k-leader`, use concise integration names such as `leader-spring-boot` and `leader-ktor`.

Keeping the old identity makes future `graph-ktor`, `graph-neptune`, and docs/examples work carry stale module names.

## 2. Goals

- Rename the module directory to `spring-boot/graph-spring-boot`.
- Rename the Gradle project to `:graph-spring-boot`.
- Rename the public package namespace to `io.bluetape4k.graph.spring.boot`.
- Update Spring AutoConfiguration imports to the new package.
- Update CI and Nightly workflow tasks from `:graph-spring-boot4-starter` to `:graph-spring-boot`.
- Update current user-facing docs and BOM README snippets to `graph-spring-boot`.
- Preserve historical docs where the old name is necessary as event history, but add current issue #99 design/plan with the new contract.

## 3. Non-Goals

- Reintroduce Spring Boot 3 support.
- Change `bluetape4k.graph.*` runtime configuration properties.
- Change backend auto-configuration behavior.
- Change GraphOperations or backend APIs.
- Fold `graph-ktor` work into this PR.

## 4. Current Evidence

| Evidence | Result |
|---|---|
| `./gradlew -q projects` | Lists only `:graph-spring-boot4-starter` for Spring Boot integration |
| `find spring-boot*` | `spring-boot4/graph-spring-boot4-starter` exists; `spring-boot3` does not |
| `leader` repo pattern | `leader-spring-boot`, `leader-ktor` use suffix-free integration module names |
| CI/Nightly | Path filter already uses logical output `graph-spring-boot`, but Gradle tasks still use `:graph-spring-boot4-starter` |
| Issue #99 | Target directory/project name is `graph-spring-boot`; package rename requires judgment |

## 5. Design Decision

Adopt a full identity rename:

```text
Old directory: spring-boot4/graph-spring-boot4-starter
New directory: spring-boot/graph-spring-boot

Old Gradle project: :graph-spring-boot4-starter
New Gradle project: :graph-spring-boot

Old package: io.bluetape4k.graph.spring.boot4
New package: io.bluetape4k.graph.spring.boot
```

The package should be renamed with the module. Leaving `boot4` in package names would keep the stale contract in public KDoc, generated Dokka, and import statements even after the artifact is renamed.

## 6. Alternatives

### A. Rename only Gradle project and directory

- Pros: smaller diff.
- Cons: public package still says `boot4`, so users import an old naming contract.
- Decision: rejected.

### B. Keep `spring-boot4/` base directory and rename only leaf module

- Pros: preserves Spring Boot 4 implementation clue.
- Cons: root README and CI still expose versioned directory layout; future users see a mixed contract.
- Decision: rejected.

### C. Full rename to `spring-boot/graph-spring-boot`

- Pros: one stable public contract; aligns with `leader-spring-boot`; avoids future churn.
- Cons: larger mechanical diff across imports and workflows.
- Decision: accepted.

## 7. Implementation Surface

- `settings.gradle.kts`: replace `includeModules("spring-boot4", false, false)` with `includeModules("spring-boot", false, false)`.
- Move files:
  - `spring-boot4/graph-spring-boot4-starter/` -> `spring-boot/graph-spring-boot/`
- Kotlin source/test package rename:
  - `io.bluetape4k.graph.spring.boot4` -> `io.bluetape4k.graph.spring.boot`
- AutoConfiguration imports:
  - update every FQCN in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Workflows:
  - `.github/workflows/ci.yml`
  - `.github/workflows/nightly.yml`
- Docs:
  - root `README.md` / `README.ko.md`
  - module README pair
  - BOM README pair
  - `CHANGELOG.md`
  - current docs/specs/plans that intentionally describe current module identity

Historical docs from earlier completed work may keep old names if they describe past state; this design/plan records the current contract.

## 8. Verification

- `./gradlew -q projects | rg 'graph-spring'` lists `:graph-spring-boot` and not `:graph-spring-boot4-starter`.
- `./gradlew :graph-spring-boot:compileKotlin :graph-spring-boot:compileTestKotlin --no-daemon`.
- `./gradlew :graph-spring-boot:test --tests '*TinkerGraph*' --no-daemon`.
- `./gradlew :graph-spring-boot:generatePomFileForBluetapeGraphPublication --no-daemon`.
- `rg '<artifactId>graph-spring-boot</artifactId>' spring-boot/graph-spring-boot/build/publications/BluetapeGraph/pom-default.xml`.
- `actionlint .github/workflows/ci.yml .github/workflows/nightly.yml`.
- `rg 'graph-spring-boot4-starter|spring-boot4/graph-spring-boot4-starter|io\.bluetape4k\.graph\.spring\.boot4'` shows only historical references or none in current docs/code.

## 9. Risks

| Risk | Mitigation |
|---|---|
| AutoConfiguration import typo breaks Spring discovery | compile + TinkerGraph ApplicationContext tests |
| CI misses renamed module | actionlint + workflow task grep |
| Publication coordinate mismatch | generated POM task + explicit generated POM `artifactId` grep |
| Historical docs over-edited | update current docs and issue #99 docs; preserve old design history when needed |

## 10. Review Gate

### Iteration 1

| Severity | Finding | Resolution |
|---|---|---|
| P1 | The initial verification only generated the POM but did not assert the generated `artifactId`. A task could pass while publishing the wrong coordinate. | Added explicit generated POM grep for `<artifactId>graph-spring-boot</artifactId>`. |
| P1 | Package declarations were renamed, but the physical Kotlin source directories could still remain under `spring/boot4`, creating IDE/source-layout drift. | Implementation must move source/test directories to `io/bluetape4k/graph/spring/boot`; verified in T3. |
| P1 | BOM Mermaid node rename can leave an edge pointing at stale `SB4`, breaking README rendering. | README/BOM verification now includes stale node id grep (`SB4`) and current README checks. |
| P1 | BOM README install snippets could keep the stale `bluetape4k-graph-*` artifact prefix even after the module POM publishes `graph-spring-boot`. | BOM README dependency snippets now use generated artifact coordinates such as `graph-neo4j` and `graph-spring-boot`; generated POM grep verifies `graph-spring-boot`. |
| P1 | Updating historical release entries in `CHANGELOG.md` can rewrite what shipped in 0.2.0 instead of documenting the rename in Unreleased. | Restored the historical 0.2.0 module name and added an English Unreleased rename entry for #99. |
| P0 | None | N/A |

P0/P1 status after iteration 1: P0 = 0, P1 = 0 for Codex self-review. External Claude advisor result is pending and must be integrated before closing the full Step 2-R/3-R gate.
