# Adapter close와 원본 resource 소유권을 분리한다

관련 이슈: #634, 상위 #641.

## 원인

BufferedSource.inputStream과 BufferedSink.outputStream을 그대로 반환하면 adapter close가 caller-owned 원본도 닫았다.

## 결정

FilterInputStream의 close는 원본을 닫지 않고, FilterOutputStream close는 flush만 수행한다. bulk write는 원본 stream으로 전달하여 byte 단위 호출 비용을 피했다. 기존 closing adapter 계약은 유지한다.

## 검증과 시행착오

실제 buffered Source/Sink의 adapter 종료 후 재사용 테스트가 RED 후 GREEN이었다. 전체 OkIO 117개와 detekt 성공. FakeFileSystem의 열려 있는 파일 동시 읽기 제약은 별도 fixture 오류였고 출력 테스트를 실제 buffered ByteArrayOutputStream으로 수정했다.

## 재발 방지

Buffer 자체의 no-op close만으로 소유권을 검증하지 않는다. 실제 close 상태가 바뀌는 buffered resource를 사용하고 flush와 close를 따로 확인한다.
