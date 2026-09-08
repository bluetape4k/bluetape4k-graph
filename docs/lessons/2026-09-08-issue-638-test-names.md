# 테스트 graph 이름 생성 규칙 재사용

관련 이슈: #638, 상위 #641.

## 결정과 범위

UUID 문자열에서 하이픈을 제거하고 앞부분을 자르던 16개 파일의 21곳을 기존 `Base58.randomString`으로 교체했다. graph 이름 prefix와 8·10·12자 길이를 보존한다. Base58은 영문자와 숫자를 사용하므로 기존 식별자 검증에 맞으며, 이미 제공되는 core utility라 새 의존성이 필요 없다. production API는 바뀌지 않는다.

## 검증

영향받는 12개 모듈의 testClasses와 적용 가능한 detekt를 통과했다. Neo4j·Memgraph schema 테스트 및 FalkorDB 전체 테스트도 통과했다. 각 사용처의 기존 prefix·길이를 diff와 대조했다. examples에는 detekt task가 없어서 최초 명령은 task 탐색 단계에서 실패했고 실제 등록 task로 수정했다.

## 재발 방지

임시 이름은 새 난수 가공식을 추가하기 전에 기존 Base58 helper를 확인한다. 테스트 코드만 바뀌는 단순 재사용 작업에서는 같은 구현을 반복하는 새 테스트보다 기존 backend가 이름을 실제로 받는 검증을 사용한다.
