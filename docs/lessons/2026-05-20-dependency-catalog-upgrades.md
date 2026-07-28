# Dependency Catalog 업그레이드

## 맥락

`bluetape4k-dependencies` folded the Apache Fory Dependabot PRs into the
central dependency upgrade batch.

## 결정

Materialize the central Fory Kotlin catalog version in this repository.

## 결과

`gradle/libs.versions.toml` now carries Fory Kotlin `0.17.0`.

## 검증

- `./gradlew build -x test --no-daemon`
