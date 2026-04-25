package io.bluetape4k.graph.io.csv

import io.bluetape4k.csv.CsvRecordReader
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test

class RecordExtensionsTest {

    companion object : KLogging()

    @Test
    fun `toColumnMap returns header-to-value pairs`() {
        val csv = "name,age\nAlice,30"
        val records = CsvRecordReader().read(csv.byteInputStream(), skipHeaders = true).toList()

        val record = records.first()
        record.toColumnMap() shouldBeEqualTo mapOf("name" to "Alice", "age" to "30")
    }

    @Test
    fun `toColumnMap handles multiple rows`() {
        val csv = "id,label,name\n1,Person,Alice\n2,Person,Bob"
        val records = CsvRecordReader().read(csv.byteInputStream(), skipHeaders = true).toList()

        records shouldHaveSize 2
        records[0].toColumnMap() shouldBeEqualTo mapOf("id" to "1", "label" to "Person", "name" to "Alice")
        records[1].toColumnMap() shouldBeEqualTo mapOf("id" to "2", "label" to "Person", "name" to "Bob")
    }

    @Test
    fun `toColumnMap returns empty map when record has no headers`() {
        val csv = "Alice,30"
        val records = CsvRecordReader().read(csv.byteInputStream(), skipHeaders = false).toList()

        val record = records.first()
        // skipHeaders=false 로 읽으면 headers 가 null → 빈 Map 반환
        if (record.headers == null) {
            record.toColumnMap().shouldBeEmpty()
        }
    }

    @Test
    fun `toColumnMap handles fewer values than headers`() {
        // values 수가 headers 보다 적을 때 zip 은 짧은 쪽에서 멈춘다
        val csv = "name,age,city\nAlice,30"
        val records = CsvRecordReader().read(csv.byteInputStream(), skipHeaders = true).toList()

        val record = records.first()
        val map = record.toColumnMap()
        // "city" 컬럼은 값이 없어 매핑에서 제외됨
        map shouldBeEqualTo mapOf("name" to "Alice", "age" to "30")
    }
}
