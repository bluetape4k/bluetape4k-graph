# Issue #563 구현 계획

1. upstream #1523의 exact head와 package ownership 결정을 receipt로 고정한다.
2. graph-core resolved JAR pair를 검사하는 표준-library verifier를 추가한다.
3. EN/KO README, CHANGELOG, WIP에 API import 재컴파일과 external artifact gate를
   기록한다.
4. 7-Tier review와 lesson에 graph source·generated owner·module-path 증거를
   연결한다.
5. upstream merge와 새 snapshot 배포 후 verifier, graph-core
   compile/test/Detekt/catalog/BOM 및 downstream regression을 재실행한다.

## 비범위

- graph-local helper 복원 또는 shading
- upstream PR merge, release, publication
- graph dependency 좌표를 임의 snapshot으로 고정

## 현재 gate

1–4는 이 PR에서 수행한다. 5는 upstream artifact가 준비될 때까지 PENDING이며,
그 전에는 전체 stacked train merge 승인을 요청하지 않는다.
