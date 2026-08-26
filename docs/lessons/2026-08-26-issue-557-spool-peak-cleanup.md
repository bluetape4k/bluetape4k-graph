# #557 spool record serialization peak memory·constructor cleanup lesson

## 상황

#539의 spool은 record payload를 `ByteArrayOutputStream`에 만든 뒤
`toByteArray()` 복사본을 생성하고 128 MiB 제한을 검사했다. 또한 두 temp file과
output stream을 field initializer에서 순차적으로 생성해 constructor 중간 실패가
앞서 만든 resource를 남길 수 있었다.

## 결정

max byte를 넘는 즉시 실패하는 capped payload buffer를 사용하고, 크기 확인 후
`writeTo(output)`으로 직접 기록해 전체 record의 두 번째 byte-array 복사를 없앴다.
temp file/output은 하나의 resource factory에서 열고, 어느 단계가 실패해도 이미
획득한 stream을 닫고 file을 삭제한다. cleanup failure는 최초 예외에 suppressed로
연결하며 public no-arg constructor와 기존 length-prefix format은 유지한다.

## 검증

- TDD RED에서 hardening constructor parameter 부재로 compile failure를 확인했다.
- targeted `GraphIoRecordSpoolTest` 8/8 PASS
- no-second-copy, small max oversized guard, second temp file/output fault injection,
  replay/close regression을 모두 통과했다.
- full graph-io-core test, Detekt, 금지 assertion scan, `git diff --check`와 hosted
  exact-head CI/Examples는 PR receipt에서 갱신한다.

## 남은 가드

1. 128 MiB cap은 전체 export heap/disk bound가 아니며 backend bounded cursor나
   transaction snapshot을 보장하지 않는다.
2. 다른 serializer나 record format을 추가할 때 same cap/direct-write/cleanup TCK를
   재사용한다.
3. PR exact base/head와 hosted terminal receipt를 read-back한 뒤에도 전체 train은
   마지막 일괄 merge 승인 전까지 병합하지 않는다.
