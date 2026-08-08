# 이슈 #111 샘플 데이터셋 로더 계획

## 작업 단계

1. 세 도메인 예제 모듈에 graph-io CSV 의존성을 추가한다.
2. 각 모듈에 sync 및 suspend import 진입점을 제공하는 공개 샘플 데이터셋 로더를 하나씩 추가한다.
3. 사기 탐지, 추천 및 지식 그래프 데이터셋용 번들 CSV fixture를 추가한다.
4. import 결과와 도메인 서비스 쿼리를 검증하는 TinkerGraph smoke 테스트를 추가한다.
5. import 흐름과 검증 범위를 영문 및 한국어 README에 반영한다.
6. PR 전에 대상 컴파일, 테스트, Detekt, Codex 검토 및 Claude 검토를 실행한다.

## 완료 조건

- 세 샘플 로더가 각자의 기본 CSV fixture를 성공적으로 import한다.
- 테스트를 통해 import한 그래프가 각 도메인 서비스와 함께 동작함을 검증한다.
- 공개 문서와 KDoc가 현재 API 이름을 사용한다.
- PR 검토 산출물에 Codex와 Claude 판정이 모두 포함된다.
