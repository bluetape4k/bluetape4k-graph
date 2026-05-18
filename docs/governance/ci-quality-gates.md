# CI Quality Gates

## Current Status

`bluetape4k-graph` uses CI quality gates that match the current repository
policy instead of the older all-in-one gate proposed in issue #18.

## Policy

- CI blocks pull requests on secret scanning, Gradle wrapper validation,
  compile-only build, Detekt static analysis, and targeted test jobs.
- Detekt reports are uploaded from each module under
  `**/build/reports/detekt/*.xml`.
- Existing findings are tracked in `config/detekt/baseline.xml`; CI should fail
  only for new findings outside that baseline.
- Kover reports are generated and uploaded as report-only trend evidence.
- Nightly keeps wider Detekt and coverage visibility for full/smoke scopes.
- Dependency security posture is handled through CodeQL, GitHub dependency
  graph alerts, Dependabot for GitHub Actions, and central library version
  governance in `bluetape4k-dependencies`.

## Exclusions

- Do not add a fixed Kover percentage gate unless a later focused issue
  reintroduces module-level thresholds.
- Do not add `ktlint` as a one-off CI gate unless the repository adopts ktlint
  as a maintained Gradle plugin and style contract.
- Do not add OWASP Dependency Check to release publishing by default. Its NVD
  dependency and false-positive profile make it a poor release-blocking gate for
  this repository without a separate vulnerability triage policy.

## Verification Contract

- Workflow YAML must pass `actionlint`.
- `./gradlew detekt` must pass locally for CI quality-gate changes.
- Regenerate the baseline only with `./gradlew detektProjectBaseline` after
  reviewing whether existing findings should instead be fixed.
- Kover report generation remains `continue-on-error: true` so coverage data is
  visible without failing builds solely on a fixed threshold.
