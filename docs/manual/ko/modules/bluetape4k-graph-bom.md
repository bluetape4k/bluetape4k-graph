# bluetape4k-graph-bom


실행 방식: **소비자 빌드에서 바로 확인**한다. `<ecosystem-version>`에는 승인된 `bluetape4k-dependencies` 버전을 넣고 `dependencyInsight`는 실제 애플리케이션에서 실행한다.

## 실행 전 준비

이 플랫폼은 공개된 그래프 모듈의 버전을 맞춘다. 애플리케이션에서는 `bluetape4k-dependencies` 생태계 BOM으로 버전을 한 번만 정하고, 사용할 모듈은 버전 없이 선언한다. 그래프 BOM 버전을 따로 고르면 두 플랫폼이 서로 다른 조합을 선택할 수 있으므로 권하지 않는다.

BOM은 API나 실행 코드가 없는 버전 제약 모음이다. 그래프 데이터베이스, 드라이버, importer를 자동으로 넣지 않는다.

## 실행

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

## 기대 결과

예상 결과는 두 모듈이 생태계 BOM이 고른 같은 그래프 버전으로 해석되는 것이다. 버전이 둘 이상 보이면 강제 버전을 추가하지 말고, 중복 platform과 직접 지정한 버전부터 찾는다.

## 관리 범위와 동작

core, 데이터베이스 연동, graph-io, OkIO, Ktor, Spring Boot 모듈을 제약한다. 예제와 benchmark 프로젝트는 포함하지 않는다. 정확한 목록은 release 시점의 [BOM build](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/bom/build.gradle.kts)와 [settings.gradle.kts](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/settings.gradle.kts)에 고정돼 있다.

이 모듈에는 트랜잭션과 자원 종료 동작이 없다. 실제 동작은 선택한 모듈 문서에서 확인한다.

## 운영 점검

- 생태계 BOM을 하나만 버전 기준으로 둔다.
- dependency lock이나 build scan에 실제 선택 버전을 남긴다.
- 생태계 BOM을 바꿀 때 그래프 모듈의 버전 이탈을 확인한다.
- 실행 모듈의 테스트는 별도로 실행한다.

## 실패와 복구

증상: `dependencyInsight`에 그래프 버전이 둘 이상 보인다. 중복 platform 또는 직접 지정한 버전을 제거하고 dependency를 새로 받은 뒤 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-bom:build
```

예상 결과는 platform metadata 생성 성공이다. 실패하면 그래프 데이터가 아니라 publication metadata와 누락된 project constraint를 확인한다. 서버 호환성은 이 명령으로 검증할 수 없으므로 해당 모듈 테스트를 따로 실행한다.

## 완전한 release 예제

고정된 [BOM build](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/bom/build.gradle.kts)가 실제로 실행되는 release 근거다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-bom:build
```

예상 결과는 위 자원 소유권과 기능 경계를 검증하면서 release 테스트 또는 build가 끝나는 것이다.

## 하지 않는 일과 관련 문서

[저장소 구성](../architecture/repository-map.md), [시작하기](../getting-started.md), 실제로 선택한 모듈 문서를 이어서 읽는다. 이 페이지는 독립 그래프 버전을 추천하지 않으며, 데이터베이스 운영 호환성도 보장하지 않는다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### bom 아키텍처

[![bom 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bom-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bom-architecture-01.svg)

_배포본 README: [`bom/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/bom/README.ko.md)_

<!-- release-readme-diagrams:end -->
