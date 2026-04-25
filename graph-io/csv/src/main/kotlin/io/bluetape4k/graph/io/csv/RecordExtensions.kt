package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.Record

/**
 * CSV 레코드의 헤더명과 값을 매핑한 `Map<String, String?>`을 반환한다.
 *
 * [Record.headers]가 `null`이면 빈 Map을 반환한다.
 * 헤더 수와 값의 수가 다를 경우 짧은 쪽 기준으로 매핑된다.
 *
 * @return 헤더명 → 값 쌍의 Map. 헤더가 없으면 빈 Map.
 *
 * ```kotlin
 * // CSV 예시: "name,age\nAlice,30"
 * val record = CsvRecordReader().read(inputStream, skipHeaders = true).first()
 * val map = record.toColumnMap()
 * // map == {"name" -> "Alice", "age" -> "30"}
 * ```
 */
fun Record.toColumnMap(): Map<String, String?> =
    headers?.zip(values)?.toMap() ?: emptyMap()
