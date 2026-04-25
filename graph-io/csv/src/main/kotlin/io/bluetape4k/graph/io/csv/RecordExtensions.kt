package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.Record

/**
 * CSV 레코드의 헤더명과 값을 매핑한 Map을 반환한다.
 * 헤더가 없으면 빈 Map 반환.
 */
fun Record.toColumnMap(): Map<String, String?> =
    headers?.zip(values)?.toMap() ?: emptyMap()
