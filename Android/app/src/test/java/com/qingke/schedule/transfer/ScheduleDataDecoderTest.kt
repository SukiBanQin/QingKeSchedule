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
    fun everySharedValidFixtureRoundTripsToItsOriginalJsonTree() {
        manifestFixtures("valid").forEach { name ->
            val original = Json.parseToJsonElement(fixture("valid", name).readText())
            val encoded = Json.parseToJsonElement(
                ScheduleDataDecoder.encode(ScheduleDataDecoder.decode(fixture("valid", name).readBytes()))
                    .decodeToString(),
            )
            assertEquals("$name must preserve every field, array order, and null", original, encoded)
        }
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
        val expected = ScheduleDataDecoder.decode(source.encodeToByteArray())
        fieldSpecs.forEach { field ->
            field.validJsonNumberForms().forEach { number ->
                assertEquals(
                    "${field.name} must preserve the full DTO for $number",
                    expected,
                    ScheduleDataDecoder.decode(field.replace(source, number).encodeToByteArray()),
                )
            }
        }
    }

    @Test
    fun rejectsNonNumericNonIntegralAndOverflowAtEveryIntegerField() {
        fieldSpecs.forEach { field ->
            listOf(
                "\"${field.literal}\"",
                "true",
                "${field.literal}.5",
                "2147483648",
                *field.invalidJsonNumberForms().toTypedArray(),
            ).forEach { invalid ->
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
        val truncatedMultibyte = removeLastByteOfUtf8Character(
            completeFixtureText().encodeToByteArray(),
            "秋".encodeToByteArray(),
        )
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

    private fun removeLastByteOfUtf8Character(bytes: ByteArray, character: ByteArray): ByteArray {
        val index = indexOf(bytes, character)
        return bytes.copyInto(
            ByteArray(bytes.size - 1),
            destinationOffset = index + character.size - 1,
            startIndex = index + character.size,
        ).also { truncated ->
            bytes.copyInto(truncated, endIndex = index + character.size - 1)
        }
    }

    private data class IntegerFieldSpec(
        val name: String,
        val original: String,
        val literal: String,
    ) {
        fun replace(source: String, replacement: String): String = source.replaceFirst(
            original,
            original.replaceFirst(": $literal", ": $replacement"),
        )

        fun validJsonNumberForms(): List<String> = listOf(
            "$literal.0",
            "${literal}e0",
            "${literal}e+0",
        )

        fun invalidJsonNumberForms(): List<String> = listOf(
            "+$literal",
            "0$literal",
            "$literal.",
            ".${literal}e${literal.length}",
        )
    }

    private val fieldSpecs = listOf(
        IntegerFieldSpec("schemaVersion", "\"schemaVersion\": 1", "1"),
        IntegerFieldSpec("semester.totalWeeks", "\"totalWeeks\": 18", "18"),
        IntegerFieldSpec("semester.periods.number", "\"number\": 1, \"startTime\"", "1"),
        IntegerFieldSpec("schedule.dayOfWeek", "\"dayOfWeek\": 1", "1"),
        IntegerFieldSpec("schedule.startPeriod", "\"startPeriod\": 1", "1"),
        IntegerFieldSpec("schedule.endPeriod", "\"endPeriod\": 2", "2"),
        IntegerFieldSpec("schedule.startWeek", "\"startWeek\": 1", "1"),
        IntegerFieldSpec("schedule.endWeek", "\"endWeek\": 18", "18"),
    )
}
