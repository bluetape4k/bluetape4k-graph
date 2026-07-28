# Manual parity 검증

`docs/manual/en`과 `docs/manual/ko`는 이미 bilingual pair로 관리되는 manual이므로
이번 단일 언어 문서 한국어 재작성의 primary scope에서 제외한다. 이 문서는 제외 판단과
parity 검증 결과를 남기는 증거 파일이다.

## 범위 판단

- `docs/manual/en`: English manual source.
- `docs/manual/ko`: Korean manual source.
- 두 directory는 동일 basename pair를 유지해야 한다.
- primary rewrite 대상이 아니므로 manual 본문은 이 PR train에서 변경하지 않는다.

## 검증 결과

- EN manual Markdown file: 52개
- KO manual Markdown file: 52개
- basename parity diff: 0

## 향후 가드

manual을 수정할 때는 한쪽 locale만 고치지 않는다. 새 manual page를 추가하거나 삭제할 때는
`docs/manual/en`과 `docs/manual/ko`의 relative path set을 함께 갱신하고, basename parity
diff가 0인지 확인한다.
