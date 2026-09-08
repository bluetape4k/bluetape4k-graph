# Spring 자동 설정은 bean 이름과 타입을 구분한다

관련 이슈: #632, 상위 #641.

## 원인

`@DependsOn("dataSource")`는 사용자 DataSource가 다른 이름이면 초기화를 실패시켰다. Exposed Database를 타입만으로 주입하면 다른 Database bean과 충돌했다.

## 결정

불필요한 이름 의존을 제거하고 `@Qualifier("ageExposedDatabase")`를 두 operations bean에 적용했다.

## 검증과 시행착오

회귀 테스트는 두 원인으로 RED였다. 전체 모듈 test 61개 중 60개 성공, 환경 opt-in FalkorDB 통합 테스트 비실행 1개 및 detekt 성공. 초기 별도 실행의 Docker socket 오류는 건강한 Colima를 확인한 후 테스트 subprocess에 실제 socket 환경만 전달하여 해결했다.

## 재발 방지

자동 설정 테스트에는 사용자 지정 bean 이름과 같은 타입의 별도 bean을 함께 넣는다. 컨테이너 fixture 실패를 제품 회귀 RED와 구별한다.

## PR CI에서 발견한 별도 경로 계약

Spring 테스트와 XML 생성은 성공했지만 download-artifact@v8의 단일 artifact 다운로드가 path 바로 아래에 풀려 Coverage Report가 실패했다. [공식 구현](https://github.com/actions/download-artifact/blob/3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c/src/download-artifact.ts#L185-L197)을 확인하고 CI의 7개 artifact를 명시적인 name/path로 내려받도록 수정했다. expected-artifact 검증은 유지한다.

Python coverage 검증 19개, CI routing 10개 및 actionlint가 통과했다. 단일 artifact의 flatten 구조는 실패하고 이름 하위 폴더 구조는 성공하는 fixture를 유지한다. Python 3.14의 symlink loop 진단 문구 차이는 실패 상태와 coverage validation error를 함께 검증한다. Nightly는 기본 full 범위에서 7개 artifact를 사용하므로 이번 단일 job CI 수정 대상에 포함하지 않았다. 최종 hosted 결과는 PR #643의 새 HEAD에서 확인한다.
