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
    fun emptySharedFixturePreservesExplicitNullSemester() {
        val data = ScheduleDataDecoder.decode(fixture("valid", "empty-schedule.json").readBytes())
        assertNull(data.semester)
        assertTrue(data.courses.isEmpty())
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
}
