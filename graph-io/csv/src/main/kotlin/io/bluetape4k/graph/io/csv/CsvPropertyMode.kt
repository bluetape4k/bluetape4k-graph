package io.bluetape4k.graph.io.csv

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * CSV 속성 컬럼 표현 방식을 지정하는 sealed 인터페이스.
 *
 * | 구현체 | 컬럼 구조 예시 |
 * |---|---|
 * | [PrefixedColumns] | `id, label, prop.name, prop.age` |
 * | [RawJsonColumn] | `id, label, properties` (JSON 문자열) |
 * | [None] | `id, label` (속성 없음) |
 */
sealed interface CsvPropertyMode {

    /** 속성 컬럼을 아예 포함하지 않는다. `id, label` 컬럼만 생성된다. */
    data object None : CsvPropertyMode, Serializable {
        private const val serialVersionUID: Long = 1L
    }

    /**
     * `prefix + propertyKey` 형식의 컬럼명으로 속성을 저장한다.
     * 기본 prefix는 `"prop."`.
     */
    data class PrefixedColumns(val prefix: String = "prop.") : CsvPropertyMode, Serializable {
        init {
            prefix.requireNotBlank("prefix")
        }

        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /**
     * 모든 속성을 JSON 문자열로 직렬화하여 단일 컬럼에 저장한다.
     * 기본 컬럼명은 `"properties"`.
     */
    data class RawJsonColumn(val columnName: String = "properties") : CsvPropertyMode, Serializable {
        init {
            columnName.requireNotBlank("columnName")
        }

        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
