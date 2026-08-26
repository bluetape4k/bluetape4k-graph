# #557 spool record serialization peak memory·constructor cleanup 설계

## 문제

#539의 `GraphIoRecordSpool.writeRecord`는 payload를 `ByteArrayOutputStream`에
직렬화한 뒤 `toByteArray()`로 전체 복사본을 만들고 크기를 검사한다. 제한을
넘지 않는 정상 레코드도 두 개의 큰 byte array가 동시에 생길 수 있다. 또한 두
임시 파일과 두 output stream이 field initializer에서 순차적으로 생성되어
constructor 중간 실패 시 먼저 획득한 resource가 orphan될 수 있다.

## 결정

1. payload는 max byte 수를 넘는 순간 실패하는 capped buffer에 인코딩한다.
2. 크기 검증 후 payload buffer를 `writeTo(output)`으로 직접 기록해
   `toByteArray()` 전체 복사본을 만들지 않는다.
3. 임시 파일·output stream 획득은 하나의 resource factory에서 수행한다. 어느
   단계가 실패해도 이미 획득한 stream을 닫고 파일을 삭제하며 cleanup failure는
   최초 예외에 suppressed로 연결한다.
4. 테스트 전용 internal constructor로 max size, file factory, output factory,
   payload factory를 주입한다. public no-arg API와 published ABI는 유지한다.

## 범위

- `graph-io-core`의 `GraphIoRecordSpool` production implementation
- oversized payload, no-second-copy, second file/output initialization failure,
  normal replay/close regression
- CSV·GraphML README EN/KO의 128 MiB 및 fail-clean lifecycle 설명

## 비범위

- spool record format의 length prefix/field encoding 변경
- backend cursor/bounded capability와 실제 transaction snapshot
- CSV·GraphML exporter API 및 caller-owned output ownership 계약 변경

## 계약

- public `GraphIoRecordSpool()` 생성자는 그대로 동작한다.
- payload가 configured max를 넘으면 partial record를 spool에 쓰지 않고
  `IllegalArgumentException`을 발생시킨다.
- 정상 replay는 기존 record/property-key 순서와 동일하다.
- constructor 실패는 primary error identity를 유지하고 이미 만든 resource를
  정리한다.
- #539의 source·sink·cancellation primary exception 및 suppressed cleanup
  계약은 그대로 유지한다.

## 검증 기준

- TDD RED에서 기존 constructor에 없는 hardening seam을 관찰한다.
- GraphIoRecordSpool targeted tests와 `graph-io-core` 전체 test가 통과한다.
- Detekt, 금지 assertion scan, `git diff --check`가 통과한다.
- hosted exact-head CI/Examples와 PR metadata를 read-back한다.
