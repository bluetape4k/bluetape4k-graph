# Projects 1.10.0 BOM handoff

## 맥락

`bluetape4k-projects` 1.10.0 was released and `bluetape4k-bom:1.10.0` is visible
from Maven Central.

## 결정

Update the local catalog's projects BOM version from 1.9.2 to 1.10.0 while
leaving this repository's own release line unchanged.

## 결과

Graph builds now consume the stable projects 1.10.0 BOM for shared bluetape4k
module versions.

## 검증

- Maven Central HTTP 200 for `bluetape4k-bom:1.10.0`.

