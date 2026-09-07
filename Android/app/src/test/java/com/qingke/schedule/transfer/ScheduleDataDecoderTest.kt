package com.qingke.schedule.transfer

import com.qingke.schedule.domain.MAX_IMPORT_BYTES
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ScheduleDataDecoderTest {
    private val fixtureRoot = File(requireNotNull(System.getProperty("sharedFixturesDirectory")))

    @Test
    fun decodesEverySharedValidFixture() {
        manifestFixtures("valid").forEach { name ->
            val data = ScheduleDataDecoder.decode(fixture("valid", name).readBytes())
            assertEquals(1, data.schemaVersion)
        }
    }

    @Test
    fun completeSharedFixturePreservesEveryContractFieldAndRoundTrips() {
        val data = ScheduleDataDecoder.decode(fixture("valid", "complete-schedule.json").readBytes())
        val semester = requireNotNull(data.semester)
        assertEquals("semester-2026-fall", semester.id)
        assertEquals("2026 秋季学期", semester.name)
        assertEquals("2026-09-02", semester.startDate)
        assertEquals(18, semester.totalWeeks)
        assertEquals(listOf(1, 2, 3, 4), semester.periods.map { it.number })
        assertEquals("08:00", semester.periods.first().startTime)
        assertEquals("11:40", semester.periods.last().endTime)
        assertEquals(6, data.courses.size)
        val every = data.courses.first()
        assertEquals(listOf("course-every", "数据结构", "陈老师", "#287B74"),
            listOf(every.id, every.name, every.teacher, every.color))
        assertEquals(listOf("schedule-every", "A101"), listOf(every.schedules.first().id, every.schedules.first().classroom))
        assertEquals(1, every.schedules.first().dayOfWeek)
        assertEquals(1, every.schedules.first().startPeriod)
        assertEquals(2, every.schedules.first().endPeriod)
        assertEquals(1, every.schedules.first().startWeek)
        assertEquals(18, every.schedules.first().endWeek)
        assertEquals("EVERY", every.schedules.first().repeatRule.name)
        assertEquals("2026-09-02T12:00:00.000Z", data.updatedAt)
        assertEquals(data, ScheduleDataDecoder.decode(ScheduleDataDecoder.encode(data)))
    }

    @Test
    fun emptySharedFixturePreservesExplicitNullSemester() {
        val data = ScheduleDataDecoder.decode(fixture("valid", "empty-schedule.json").readBytes())
        assertNull(data.semester)
        assertTrue(data.courses.isEmpty())
        val encoded = Json.parseToJsonElement(ScheduleDataDecoder.encode(data).decodeToString()).jsonObject
        assertTrue("semester must remain explicitly encoded as null", "semester" in encoded)
        assertTrue(encoded.getValue("semester").toString() == "null")
    }

    @Test
    fun rejectsEverySharedInvalidFixture() {
        manifestFixtures("invalid").forEach { name ->
            val exception = expectException<ScheduleDataException> {
                ScheduleDataDecoder.decode(fixture("invalid", name).readBytes())
            }
            assertTrue("$name should expose a domain or decoding error", exception.message!!.isNotBlank())
        }
    }

    @Test
    fun rejectsUnknownVersionAndUnknownFields() {
        expectException<ScheduleDataException.UnsupportedSchemaVersion> {
            ScheduleDataDecoder.decode("""{"schemaVersion":2,"semester":null,"courses":[],"updatedAt":"1970-01-01T00:00:00Z"}""".encodeToByteArray())
        }
        expectException<ScheduleDataException.MalformedJson> {
            ScheduleDataDecoder.decode("""{"schemaVersion":1,"semester":null,"courses":[],"updatedAt":"1970-01-01T00:00:00Z","extra":true}""".encodeToByteArray())
        }
    }

    @Test
    fun acceptsFoundationCompatibleIntegralNumberSpellingsAtTopLevelAndNestedFields() {
        val source = completeFixtureText()
        fieldSpecs.forEach { field ->
            val decimal = field.replace(source, field.decimal)
            assertEquals("${field.name} decimal spelling", 1, ScheduleDataDecoder.decode(decimal.encodeToByteArray()).schemaVersion)
            val exponent = field.replace(source, field.exponent)
            assertEquals("${field.name} exponent spelling", 1, ScheduleDataDecoder.decode(exponent.encodeToByteArray()).schemaVersion)
        }
    }

    @Test
    fun rejectsNonNumericNonIntegralAndOverflowAtEveryIntegerField() {
        fieldSpecs.forEach { field ->
            listOf(field.quoted, field.boolean, field.nonIntegral, field.overflow).forEach { invalid ->
                expectException<ScheduleDataException.MalformedJson> {
                    ScheduleDataDecoder.decode(field.replace(completeFixtureText(), invalid).encodeToByteArray())
                }
            }
        }
    }

    @Test
    fun rejectsRequiredFieldsAndRepresentativeWrongJsonTypes() {
        listOf(
            """{"semester":null,"courses":[],"updatedAt":"1970-01-01T00:00:00Z"}""",
            """{"schemaVersion":1,"courses":[],"updatedAt":"1970-01-01T00:00:00Z"}""",
            """{"schemaVersion":1,"semester":null,"updatedAt":"1970-01-01T00:00:00Z"}""",
            """{"schemaVersion":1,"semester":null,"courses":[]}""",
        ).forEach { missingField ->
            expectException<ScheduleDataException> {
                ScheduleDataDecoder.decode(missingField.encodeToByteArray())
            }
        }
        val empty = fixture("valid", "empty-schedule.json").readText()
        listOf(
            empty.replace("\"semester\": null", "\"semester\": []"),
            empty.replace("\"courses\": []", "\"courses\": {}"),
            empty.replace("\"updatedAt\": \"1970-01-01T00:00:00.000Z\"", "\"updatedAt\": false"),
        ).forEach { invalid ->
            expectException<ScheduleDataException> { ScheduleDataDecoder.decode(invalid.encodeToByteArray()) }
        }
    }

    @Test
    fun manifestListsExactlyTheFixturesExercisedByTheContractTests() {
        listOf("valid", "invalid").forEach { kind ->
            assertEquals(fixtureNames(kind), manifestFixtures(kind))
        }
    }

    @Test
    fun rejectsInputsBeyondFiveMiBWhetherBytesOrStream() {
        val tooLarge = ByteArray(MAX_IMPORT_BYTES + 1)
        expectException<ScheduleDataException.FileTooLarge> { ScheduleDataDecoder.decode(tooLarge) }
        expectException<ScheduleDataException.FileTooLarge> {
            ScheduleDataDecoder.decode(ByteArrayInputStream(tooLarge))
        }
    }

    @Test
    fun acceptsInputAtExactlyFiveMiBForBytesAndStream() {
        val valid = fixture("valid", "empty-schedule.json").readBytes()
        val exactLimit = valid + ByteArray(MAX_IMPORT_BYTES - valid.size) { ' '.code.toByte() }
        assertNull(ScheduleDataDecoder.decode(exactLimit).semester)
        assertNull(ScheduleDataDecoder.decode(ByteArrayInputStream(exactLimit)).semester)
    }

    @Test
    fun rejectsMalformedUtf8ForByteAndStreamEntrypointsWithoutReplacingText() {
        val invalidByte = completeFixtureText().encodeToByteArray().also { bytes ->
            bytes[indexOf(bytes, "semester-2026-fall".encodeToByteArray())] = 0xFF.toByte()
        }
        val truncatedMultibyte = "{\"schemaVersion\":1,\"semester\":null,\"courses\":[],\"updatedAt\":\"中".encodeToByteArray()
        listOf(invalidByte, truncatedMultibyte).forEach { bytes ->
            expectException<ScheduleDataException.MalformedJson> { ScheduleDataDecoder.decode(bytes) }
            expectException<ScheduleDataException.MalformedJson> { ScheduleDataDecoder.decode(ByteArrayInputStream(bytes)) }
        }
        assertEquals("数据结构", ScheduleDataDecoder.decode(completeFixtureText().encodeToByteArray()).courses.first().name)
    }

    private inline fun <reified T : Throwable> expectException(block: () -> Unit): T = try {
        block()
        fail("Expected ${T::class.java.name}")
        error("unreachable")
    } catch (error: Throwable) {
        if (error is T) error else throw error
    }

    private fun fixtureNames(kind: String): List<String> = fixtureRoot.resolve(kind)
        .listFiles()
        .orEmpty()
        .map { it.name }
        .sorted()

    private fun manifestFixtures(kind: String): List<String> = Json.parseToJsonElement(
        fixtureRoot.resolve("manifest.json").readText(),
    ).jsonObject.getValue(kind).jsonArray.map { it.jsonPrimitive.content }.sorted()

    private fun fixture(kind: String, name: String): File = fixtureRoot.resolve("$kind/$name")

    private fun completeFixtureText(): String = fixture("valid", "complete-schedule.json").readText()

    private fun indexOf(bytes: ByteArray, needle: ByteArray): Int {
        val index = bytes.indices.firstOrNull { start ->
            start + needle.size <= bytes.size && needle.indices.all { offset -> bytes[start + offset] == needle[offset] }
        }
        return requireNotNull(index) { "Expected fixture text was not found" }
    }

    private data class IntegerFieldSpec(
        val name: String,
        val original: String,
        val decimal: String,
        val exponent: String,
        val quoted: String,
        val boolean: String,
        val nonIntegral: String,
        val overflow: String,
    ) {
        fun replace(source: String, replacement: String): String = source.replaceFirst(original, replacement)
    }

    private val fieldSpecs = listOf(
        IntegerFieldSpec("schemaVersion", "\"schemaVersion\": 1", "\"schemaVersion\": 1.0", "\"schemaVersion\": 1e0", "\"schemaVersion\": \"1\"", "\"schemaVersion\": true", "\"schemaVersion\": 1.5", "\"schemaVersion\": 2147483648"),
        IntegerFieldSpec("semester.totalWeeks", "\"totalWeeks\": 18", "\"totalWeeks\": 18.0", "\"totalWeeks\": 1.8e1", "\"totalWeeks\": \"18\"", "\"totalWeeks\": true", "\"totalWeeks\": 18.5", "\"totalWeeks\": 2147483648"),
        IntegerFieldSpec("semester.periods.number", "\"number\": 1, \"startTime\"", "\"number\": 1.0, \"startTime\"", "\"number\": 1e0, \"startTime\"", "\"number\": \"1\", \"startTime\"", "\"number\": true, \"startTime\"", "\"number\": 1.5, \"startTime\"", "\"number\": 2147483648, \"startTime\""),
        IntegerFieldSpec("schedule.dayOfWeek", "\"dayOfWeek\": 1", "\"dayOfWeek\": 1.0", "\"dayOfWeek\": 1e0", "\"dayOfWeek\": \"1\"", "\"dayOfWeek\": true", "\"dayOfWeek\": 1.5", "\"dayOfWeek\": 2147483648"),
        IntegerFieldSpec("schedule.startPeriod", "\"startPeriod\": 1", "\"startPeriod\": 1.0", "\"startPeriod\": 1e0", "\"startPeriod\": \"1\"", "\"startPeriod\": true", "\"startPeriod\": 1.5", "\"startPeriod\": 2147483648"),
        IntegerFieldSpec("schedule.endPeriod", "\"endPeriod\": 2", "\"endPeriod\": 2.0", "\"endPeriod\": 2e0", "\"endPeriod\": \"2\"", "\"endPeriod\": true", "\"endPeriod\": 2.5", "\"endPeriod\": 2147483648"),
        IntegerFieldSpec("schedule.startWeek", "\"startWeek\": 1", "\"startWeek\": 1.0", "\"startWeek\": 1e0", "\"startWeek\": \"1\"", "\"startWeek\": true", "\"startWeek\": 1.5", "\"startWeek\": 2147483648"),
        IntegerFieldSpec("schedule.endWeek", "\"endWeek\": 18", "\"endWeek\": 18.0", "\"endWeek\": 1.8e1", "\"endWeek\": \"18\"", "\"endWeek\": true", "\"endWeek\": 18.5", "\"endWeek\": 2147483648"),
    )
}
