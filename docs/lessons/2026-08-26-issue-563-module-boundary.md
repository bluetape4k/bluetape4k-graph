# Issue #563: module-path 경계는 resolved artifact로 검증해야 한다

## 배경

source package를 나누었다는 사실만으로 published dependency graph가 안전해지는
것은 아니다. 이전 `2.0.0-SNAPSHOT` core/API JAR은 같은 package를 포함해
`java --validate-modules` exit 1을 재현했다. upstream #1523은 Java 21 API를
`.api` subpackage로 이동하고 core owner를 유지한다.

## 교훈

- Gradle dependency 선언이나 source compile만으로 package ownership을 증명하지
  않는다. 실제 resolved JAR pair와 module-path를 검사한다.
- API package 이동은 binary/source migration이므로 graph helper owner migration
  (#542/#562)과 upstream package boundary (#563)를 별도 증거로 유지한다.
- ServiceLoader descriptor 파일명도 interface package의 일부다. API import와
  descriptor를 함께 검사해야 runtime provider 누락을 놓치지 않는다.
- upstream release 전에는 graph dependency를 임의로 바꾸지 않고, verifier와
  문서를 먼저 준비한 뒤 새 snapshot에서 downstream regression을 재실행한다.

## 검증 기록

- upstream build: API/JDK21/JDK25/core test 성공
- upstream generated core/API JAR: `java --validate-modules` exit 0
- graph verifier: upstream built JAR pair에서 package 교집합 0, legacy API 0,
  missing API owner 0, validation exit 0
