# #562 generated owner ABI migration TCK 구현 계획

## 순서

1. **기준선 확인**
   - #587 PR의 live head `0c859bc135c1ca68efcd36690427ccb2863773b1`을 확인하고
     이 commit 위에 worktree와 branch를 만든다.
   - graph-core dependency가 `bluetape4k-core`를 이미 `api`로 사용하며 catalog와
     BOM을 변경하지 않는지 확인한다.
2. **TDD RED**
   - legacy owner 직접 호출, migrated owner 호출, code source, owner bytecode
     검사를 먼저 추가한다.
   - 예외는 `io.bluetape4k.assertions.assertFailsWith`로만 검증한다.
3. **Fixture 구현**
   - compile-only legacy owner와 legacy/migrated Java consumer를 test resource로
     고정한다.
   - JDK `JavaCompiler`와 격리된 `URLClassLoader`로 compile/runtime classpath를
     분리하고, legacy failure와 migrated success를 재현한다.
4. **문서·7-Tier review**
   - graph-core EN/KO README, CHANGELOG, WIP에 재컴파일 안내와 TCK 결과를
     기록한다.
   - 7-Tier에서 예상된 P2 ABI migration과 #563 split-package 후속을 분리한다.
5. **검증·receipt**
   - targeted TCK, graph-core full test, compile, Detekt, 금지 assertion scan,
     `git diff --check`를 실행한다.
   - PR body/issue receipt에 exact base/head와 hosted CI·Examples run을 기록하고
     전체 train 최종 승인 전까지 merge/close를 보류한다.

구현 단계의 local evidence는 targeted 3/3, graph-core full 382/382,
`compileKotlin`·Detekt·forbidden assertion scan·`git diff --check` 통과다.

## 롤백

fixture harness가 JDK/Gradle worker 환경에 의존해 불안정하면 resource source와
TCK를 함께 제거하고, source-level migration 문서만 남긴다. legacy owner를
production compatibility class로 되돌리는 방식은 split-package와 ABI 혼동을
되살리므로 선택하지 않는다.
