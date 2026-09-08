# #637 설계·계획 대조 검증

리더가 기준 `156097ebbc9e856e69349133b462a5a86a5ed58f`의 공개 선언과 현재 diff를 대조했다. 판정: **PASS**. 다음 단계는 여섯 관점의 pre-PR 리뷰이며 이 문서는 해당 리뷰를 대신하지 않는다.

| 요구 | 구현과 검증 | 상태 |
|---|---|---|
| AC1~3 classloader·자원·예외·취소 | CsvGraphClasspathResources.kt와 Test; 미구현 RED8, 취소 보완 후 CSV 전체84+detekt | PASS |
| AC4 8개 caller 재사용 | 8개 SampleDatasetLoader와 null-TCCL sync/suspend 테스트; 전체 예제195개 | PASS |
| AC5 공개 문서 | CSV README.md/README.ko.md, helper 한국어 KDoc, WIP/CHANGELOG/lesson | PASS |
| AC6 호환·운영 | 8개 공개 선언·기본값 동일, javap9클래스, 기존 CI path/job 포함 | PASS |

A-VER-01~07: 요구-파일/테스트 매핑, 현재 단계까지의 계획 수행, 범위·문서·위험 테스트·신선한 모듈 결과와 남은 단계 확인을 완료했다. 신규 module/dependency/settings/BOM/catalog/workflow 변경은 없다. 현재 변경은 CSV helper와 8개 caller/tests 및 관련 문서에 한정된다. helper는 import당 두 리소스를 열고 레코드별 lookup/buffering을 추가하지 않는다. 성능 수치 개선은 주장하지 않는다.

검증 명령은 `637-final-green`(CSV84개+detekt 및 8개 예제195개)이며 exit0이다. `--no-parallel --max-workers=1` 및 queue lock으로 모든 로컬 DB 테스트를 직렬화했다. `git diff --check` 통과. 새 의존성이나 별도 ABI task 없이 Kotlin 공개 선언 비교와 컴파일된 JVM descriptor 확인을 분리했다. 기존 함수 제거는 없다.

Step4-S 별도 cleanup은 N/A: 승인된 8개 중복 helper 제거 외 추가 cleanup을 수행하지 않았다. Result는 dispatcher 경계의 원래 예외 보존을 위해 필요하며 실제 회귀 테스트로 검증했다. LSP tool 미제공으로 compiler/test/detekt를 사용했다. Full Nightly dispatch와 artifact publish는 현재 변경에서 트리거되지 않는다. GNO의 .worktrees 제외 정책에 따라 새 문서 색인은 merge 후 canonical checkout에서 수행한다.

남은 항목: pre-PR 여섯 관점 리뷰, implementation commit/PR, post-PR 여섯 관점 리뷰, exact-head hosted CI, 이후 별도 일괄 merge 결정. 검증 결과를 이 단계보다 넓은 완료 주장으로 사용하지 않는다.

최신 검증: close-return 경합의 P1 수정 후 637-final-green은 279개(84+195) 및 detekt를 통과했다. 코드 스타일 경고는 두 줄의 인자 줄바꿈으로 해결했다. callback 성공 후 close 중 취소에서도 원래 취소 예외와 edge→vertex close 예외 연결을 보존한다. source/API/설계 범위는 동일하며 A-VER-01~07을 현재 diff에 재적용해 PASS로 확인했다.
