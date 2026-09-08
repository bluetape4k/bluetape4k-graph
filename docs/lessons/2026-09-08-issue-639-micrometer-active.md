# MeterRegistry의 meter identity와 상태 identity를 일치시킨다

관련 이슈: #639, 상위 #641.

## 원인

Micrometer는 같은 이름과 tag의 gauge를 재사용하지만 각 listener는 별도 active counter를 생성해 두 번째 listener의 작업 수가 표시되지 않았다.

## 결정

registry별 약한 key map에서 상태 holder를 공유한다. holder는 registry를 역참조하지 않으며 AtomicFU 상태는 private property에 캡슐화한다. 고정 operation/format tag를 유지한다.

## 검증과 시행착오

두 listener의 활성 수가 1이어야 하지만 0인 행동 RED를 확인했다. 수정 후 모듈 4개 테스트와 detekt 성공. AtomicFU 값을 배열에 직접 보관한 최초 시도는 compiler plugin에서 실패하여 private atomic field holder로 수정했다.

## 재발 방지

등록 중복을 허용하는 관측 API는 meter뿐 아니라 backing state도 공유하는지 검증한다. AtomicFU 값을 컬렉션에 직접 노출하지 말고 상태를 소유한 객체의 동작으로 접근한다.
