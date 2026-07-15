# bluetape4k-graph-bom

## 선택 기준

이 플랫폼은 공개된 graph 모듈의 버전을 맞춘다. 애플리케이션에서는 `bluetape4k-dependencies` 생태계 BOM으로 버전을 한 번만 정하고, 사용할 모듈은 버전 없이 선언한다. graph BOM 버전을 따로 고르면 두 플랫폼이 서로 다른 조합을 선택할 수 있으므로 권하지 않는다.

BOM은 API나 실행 코드가 없는 버전 제약 모음이다. 그래프 데이터베이스, 드라이버, importer를 자동으로 넣지 않는다.

## 의존성 설정

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j")
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson3")
}
```

다음 명령으로 실제 선택 버전을 확인한다.

```bash
./gradlew dependencyInsight --dependency bluetape4k-graph-core --configuration runtimeClasspath
```

예상 결과는 두 모듈이 생태계 BOM이 고른 같은 graph 버전으로 해석되는 것이다. 버전이 둘 이상 보이면 강제 버전을 추가하지 말고, 중복 platform과 직접 지정한 버전부터 찾는다.

## 관리 범위와 동작

core, 데이터베이스 연동, graph-io, OkIO, Ktor, Spring Boot 모듈을 제약한다. 예제와 benchmark 프로젝트는 포함하지 않는다. 정확한 목록은 release 시점의 [BOM build](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/bom/build.gradle.kts)와 [settings.gradle.kts](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/settings.gradle.kts)에 고정돼 있다.

이 모듈에는 transaction과 자원 종료 동작이 없다. 실제 동작은 선택한 모듈 문서에서 확인한다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-bom:build
```

예상 결과는 platform metadata 생성 성공이다. 실패하면 graph 데이터가 아니라 publication metadata와 누락된 project constraint를 확인한다. 서버 호환성은 이 명령으로 검증할 수 없으므로 해당 모듈 테스트를 따로 실행한다.

## 관련 문서와 하지 않는 일

[저장소 구성](../architecture/repository-map.md), [시작하기](../getting-started.md), 실제로 선택한 모듈 문서를 이어서 읽는다. 이 페이지는 독립 graph 버전을 추천하지 않으며, 데이터베이스 운영 호환성도 보장하지 않는다.
