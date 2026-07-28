# 이슈 18/19 CI and Dependency Governance

## 맥락

Issues #18 and #19 still described the pre-governance setup: ktlint, fixed
Kover thresholds, OWASP dependency-check, and per-repository Gradle Dependabot
updates.

## 결정

Keep the current governance model: CI blocks on Detekt and tests, Kover stays
report-only, and Gradle/Maven library updates flow through
`bluetape4k-dependencies`. Leaf Dependabot remains scoped to GitHub Actions.

## 결과

CI now runs Detekt in the pull-request build job, governance docs describe the
current quality and dependency policies, and WIP status no longer advertises
#18/#19 as open automation work.

## 검증

- `actionlint .github/workflows/ci.yml`
- `ruby -e 'require "yaml"; YAML.load_file(".github/dependabot.yml")'`
- `./gradlew detektProjectBaseline --no-daemon`
- `./gradlew detekt --no-daemon`
- `./gradlew build -x test --no-daemon`
- `git diff --check`

## 향후 지침

Do not re-add ktlint, fixed Kover thresholds, OWASP dependency-check, or leaf
Gradle Dependabot updates just because old issue text mentions them. Open a new
focused governance issue first.
