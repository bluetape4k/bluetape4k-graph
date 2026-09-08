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
