# PR #97 graph-core capability docs 레슨

## 맥락

- PR: #97 `docs: graph-core capability 문서 정합성 확보`
- Issue: #75
- Merge commit: `22faa1f7a9628ea9b579158918f2abf916a4c168`
- 범위: root README 한/영, backend README 한/영, `GraphMergeOperations`, `GraphSchemaManager`, schema metadata model KDoc, CHANGELOG.
- 검증 used in PR: `./gradlew :graph-core:compileKotlin --no-daemon`, `git diff --check`, README capability mention grep.

## 결정 또는 발견

- 레슨: capability documentation은 root README만 고치면 끝나지 않는다.
  - 증거: `SchemaManager`, `Merge / Upsert`, `Transaction DSL` capability는 root README, `graph-core` README, backend README, public KDoc에 모두 노출되어야 사용자가 backend별 지원 범위와 unsupported behavior를 같은 방식으로 이해할 수 있다.
  - 향후 가드: cross-backend capability를 문서화할 때는 root README, `graph-core` README, backend README 한/영, public KDoc, CHANGELOG를 하나의 sync set으로 취급한다.

- 레슨: "지원하지 않음"도 documented capability다.
  - 증거: AGE schema DDL, FalkorDB transaction처럼 backend 특성 때문에 제한되는 API는 silent omission보다 explicit unsupported contract가 더 안전하다.
  - 향후 가드: backend별 capability table에는 supported path뿐 아니라 explicit unsupported path와 이유를 함께 기록한다.

- 레슨: capability docs는 compile verification과 grep verification을 함께 써야 한다.
  - 증거: KDoc code example은 compile 대상이고, README sync는 compile로 잡히지 않는다. PR에서는 `:graph-core:compileKotlin`과 README grep count를 함께 확인했다.
  - 향후 가드: docs-only처럼 보여도 public API KDoc이 바뀌면 affected module compile을 실행하고, bilingual README sync는 grep 또는 structured checklist로 확인한다.

## 결과

- Root README 한/영에 `SchemaManager`, `Merge / Upsert`, `Transaction DSL` overview가 추가됐다.
- Backend README 한/영에 backend-specific schema/merge/transaction semantics가 동기화됐다.
- `GraphMergeOperations`, `GraphSchemaManager`, schema metadata model KDoc에 usage example이 추가됐다.
- CHANGELOG에 graph-core capability docs sync가 기록됐다.

## 검증

- PR #97 CI는 모두 pass 후 merge됐다.
- Merge 후 `develop`은 `3a883f2`까지 fast-forward sync됐다.
- Local sync 후 open PR이 없는 상태를 확인했다.

## 향후 지침

- New graph-core capability가 추가되면 다음 문서 set을 한 번에 갱신한다:
  - `README.md`
  - `README.ko.md`
  - `graph/graph-core/README.md`
  - `graph/graph-core/README.ko.md`
  - affected backend `README.md`
  - affected backend `README.ko.md`
  - public API KDoc
  - `CHANGELOG.md`
- Bilingual docs에서는 technical terms를 영문으로 유지하고 설명 문장만 한국어로 작성한다.
- Backend support matrix를 작성할 때는 "not supported"를 누락이 아니라 명시적 contract로 다룬다.
