package com.qingke.schedule.transfer

import com.qingke.schedule.domain.MAX_IMPORT_BYTES
import com.qingke.schedule.domain.ScheduleData
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * P4／A10 contract boundary: the shared manifest fixtures drive the same preview entry the system file picker
 * feeds, and the encoded version-1 tree must stay exactly what the unmodified iOS importer accepts.
 */
class ScheduleTransferContractTest {
    private val fixtureRoot = File(requireNotNull(System.getProperty("sharedFixturesDirectory")))

    @Test
    fun everySharedValidFixturePreviewsWithItsSemesterCourseCountAndUpdatedAt() {
        manifestFixtures("valid").forEach { name ->
            val preview = ScheduleImportPreview.of(ScheduleDataDecoder.decode(fixture("valid", name).readBytes()))
            assertEquals(fixtureJson(name), encodedTree(preview.data))
            assertEquals(preview.data.courses.size, preview.courseCount)
            assertEquals(preview.data.updatedAt, preview.updatedAt)
            assertTrue(preview.summary.contains("学期："))
            assertTrue(preview.summary.contains("课程：${preview.courseCount} 门"))
            assertTrue(preview.summary.contains("更新时间：${preview.updatedAt}"))
        }
    }

    @Test
    fun theRealWebExportPreviewsAsTheCompleteFixtureWithItsExactUpdatedAt() {
        val web = ScheduleDataDecoder.decode(fixture("valid", "web-export.json").readBytes())
        val complete = ScheduleDataDecoder.decode(fixture("valid", "complete-schedule.json").readBytes())
        assertEquals(complete, web)

        val preview = ScheduleImportPreview.of(web)
        assertEquals("2026 秋季学期", preview.semesterName)
        assertEquals(6, preview.courseCount)
        assertEquals("2026-09-02T12:00:00.000Z", preview.updatedAt)
        assertTrue(preview.summary.contains("2026-09-02T12:00:00.000Z"))
    }

    @Test
    fun anEmptySemesterShowsTheSharedPlaceholderInsteadOfBlankText() {
        val preview = ScheduleImportPreview.of(ScheduleDataDecoder.decode(fixture("valid", "empty-schedule.json").readBytes()))
        assertEquals("未设置学期", preview.semesterName)
        assertEquals(0, preview.courseCount)
        assertEquals("1970-01-01T00:00:00.000Z", preview.updatedAt)
        assertTrue(preview.summary.contains("学期：未设置学期"))
    }

    @Test
    fun everySharedInvalidFixtureIsRejectedByTheImportBoundary() {
        manifestFixtures("invalid").forEach { name ->
            expectFailure<ScheduleDataException>(fixture("invalid", name).readBytes()) { error ->
                assertTrue("$name must explain the failure", error.message.orEmpty().isNotBlank())
            }
        }
    }

    @Test
    fun anUnknownVersionOutranksUnknownFieldsAndAMissingVersionStaysAVersionError() {
        val unknownVersion = mutatedComplete { root ->
            root["schemaVersion"] = JsonPrimitive(2)
            root["unexpected"] = JsonPrimitive(true)
        }
        expectFailure<ScheduleDataException.UnsupportedSchemaVersion>(unknownVersion) { error ->
            assertEquals(2, error.version)
        }

        // A non-numeric schemaVersion stays the pre-existing Android numeric contract error; a missing one
        // reports the unknown version, exactly like the iOS recogniser.
        val escapedVersion = completeText().replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")
        expectFailure<ScheduleDataException.MalformedJson>(escapedVersion.encodeToByteArray()) { }

        val missingVersion = mutatedComplete { root -> root.remove("schemaVersion") }
        expectFailure<ScheduleDataException.UnsupportedSchemaVersion>(missingVersion) { error ->
            assertEquals(null, error.version)
        }
    }

    @Test
    fun unknownFieldsAreRejectedAtEveryLevelAndFailBeforeAnyPreviewExists() {
        val mutations = mapOf<String, (MutableMap<String, JsonElement>) -> Unit>(
            "top-level" to { root -> root["unexpected"] = JsonPrimitive(true) },
            "semester" to { root ->
                root["semester"] = JsonObject(semesterOf(root).also { it["unexpected"] = JsonPrimitive(true) })
            },
            "period" to { root ->
                root["semester"] = JsonObject(semesterOf(root).also { semester ->
                    semester["periods"] = JsonArray(semester.getValue("periods").jsonArray.map { element ->
                        JsonObject(element.jsonObject.toMutableMap().also { it["unexpected"] = JsonPrimitive(true) })
                    })
                })
            },
            "course" to { root ->
                root["courses"] = JsonArray(root.getValue("courses").jsonArray.map { element ->
                    JsonObject(element.jsonObject.toMutableMap().also { it["unexpected"] = JsonPrimitive(true) })
                })
            },
            "courseSchedule" to { root ->
                root["courses"] = JsonArray(root.getValue("courses").jsonArray.map { element ->
                    val course = element.jsonObject.toMutableMap()
                    course["schedules"] = JsonArray(course.getValue("schedules").jsonArray.map { schedule ->
                        JsonObject(schedule.jsonObject.toMutableMap().also { it["unexpected"] = JsonPrimitive(true) })
                    })
                    JsonObject(course)
                })
            },
        )
        mutations.forEach { (label, mutate) ->
            expectFailure<ScheduleDataException.MalformedJson>(mutatedComplete(mutate)) { error ->
                assertTrue("$label must stay a contract error", error.message.orEmpty().isNotBlank())
            }
        }
    }

    @Test
    fun aBrokenUtf8SequenceIsRejectedInsteadOfBeingReplaced() {
        val bytes = completeText().encodeToByteArray().also { raw ->
            raw[indexOf(raw, "semester-2026-fall".encodeToByteArray())] = 0xFF.toByte()
        }
        expectFailure<ScheduleDataException.MalformedJson>(bytes) { }
        try {
            ScheduleDataDecoder.decode(ByteArrayInputStream(bytes))
            fail("Expected ScheduleDataException.MalformedJson")
        } catch (error: ScheduleDataException.MalformedJson) {
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }

    @Test
    fun aNullSemesterOnlyAcceptsAnEmptyCourseList() {
        val empty = ScheduleDataDecoder.decode(fixture("valid", "empty-schedule.json").readBytes())
        assertEquals(null, empty.semester)
        assertTrue(empty.courses.isEmpty())

        val withCourse = mutatedComplete { root ->
            root["semester"] = JsonNull
            root["courses"] = JsonArray(root.getValue("courses").jsonArray.take(1))
        }
        expectFailure<ScheduleDataException.InvalidData>(withCourse) { error ->
            assertTrue(error.issues.any { it.message.contains("没有学期时不能包含课程") })
        }
    }

    @Test
    fun duplicateIdsAndReversedPeriodNumbersSurviveDecodeAndEncodeInOrder() {
        val contents = duplicateAndReversedJson()
        val data = ScheduleDataDecoder.decode(contents.encodeToByteArray())
        assertEquals(listOf("duplicate", "duplicate"), data.courses.map { it.id })
        assertEquals(listOf("shared", "shared"), data.courses.map { it.schedules.single().id })
        assertEquals(listOf(2, 1), data.semester?.periods?.map { it.number })
        assertEquals("2026-09-02T12:00:00.000Z", data.updatedAt)
        assertEquals(Json.parseToJsonElement(contents), encodedTree(data))
    }

    @Test
    fun theEncodedVersionOneTreeIsExactlyWhatTheIosImporterAccepts() {
        val encodedTrees = manifestFixtures("valid").map { name ->
            ScheduleDataDecoder.encode(ScheduleDataDecoder.decode(fixture("valid", name).readBytes())).decodeToString()
        } + duplicateAndReversedJson()

        encodedTrees.forEach { encoded ->
            val tree = Json.parseToJsonElement(encoded)
            assertTrue("the encoded tree must stay iOS-acceptable", iosAcceptsVersionOne(tree))
            val withUnknownField = JsonObject(tree.jsonObject.toMutableMap().also { it["unexpected"] = JsonPrimitive(true) })
            assertFalse("the recogniser must reject an unknown field", iosAcceptsVersionOne(withUnknownField))
        }
    }

    @Test
    fun exportCarriesOnlyTheSharedProtocolAndKeepsTheOriginalUpdatedAt() {
        val data = ScheduleDataDecoder.decode(fixture("valid", "complete-schedule.json").readBytes())
        val encoded = ScheduleDataDecoder.encode(data).decodeToString()
        val tree = Json.parseToJsonElement(encoded).jsonObject

        assertEquals(setOf("schemaVersion", "semester", "courses", "updatedAt"), tree.keys)
        assertEquals("2026-09-02T12:00:00.000Z", tree.getValue("updatedAt").jsonPrimitive.content)
        listOf(
            "remindersEnabled", "reminderLeadMinutes", "appearanceMode",
            "academicCalendar", "weekendsAreNonTeachingDays", "lunchBreak",
        ).forEach { local ->
            assertFalse("local preference $local must never enter the backup", local in tree.keys)
        }
        assertTrue(encoded.contains("\"schemaVersion\":1"))
        assertEquals(data, ScheduleDataDecoder.decode(encoded.encodeToByteArray()))
    }

    @Test
    fun anEmptySemesterBackupStaysEncodableAndTheTransferCopyExplainsTheBlockedExport() {
        val empty = ScheduleDataDecoder.decode(fixture("valid", "empty-schedule.json").readBytes())
        val tree = Json.parseToJsonElement(ScheduleDataDecoder.encode(empty).decodeToString()).jsonObject
        assertEquals("null", tree.getValue("semester").toString())
        assertEquals(0, tree.getValue("courses").jsonArray.size)
        assertEquals("请先设置学期，再导出课表备份", ScheduleDataTransfer.IMPORT_BLOCKED_NO_SEMESTER)
        assertEquals("设置学期后可导出备份", ScheduleDataTransfer.NO_SEMESTER_EXPORT_HINT)
    }

    @Test
    fun theSuggestedFileNameAndTheImportCopyFollowTheIosBaseline() {
        assertEquals("qingke-schedule-2026-09-03.json", ScheduleDataTransfer.exportFileName(LocalDate.of(2026, 9, 3)))
        assertEquals("qingke-schedule-2026-01-05.json", ScheduleDataTransfer.exportFileName(LocalDate.of(2026, 1, 5)))
        assertEquals("application/json", ScheduleDataTransfer.JSON_MIME_TYPE)
        assertEquals("已导入 6 门课程", ScheduleDataTransfer.importSuccessMessage(6))
        assertEquals("已导入 0 门课程", ScheduleDataTransfer.importSuccessMessage(0))
        assertEquals("已导出备份文件", ScheduleDataTransfer.EXPORT_SUCCESS_MESSAGE)
    }

    @Test
    fun thePreviewMessageExplainsTheDestructiveScope() {
        val preview = ScheduleImportPreview.of(ScheduleDataDecoder.decode(fixture("valid", "complete-schedule.json").readBytes()))
        val message = ScheduleDataTransfer.previewMessage(preview)
        assertTrue(message.contains("学期：2026 秋季学期"))
        assertTrue(message.contains("课程：6 门"))
        assertTrue(message.contains("更新时间：2026-09-02T12:00:00.000Z"))
        assertTrue(message.contains("将整体替换当前课表"))
    }

    @Test
    fun fiveMiBIsAcceptedWhileOneByteMoreIsRejectedOnTheStreamEntryPoint() {
        val text = fixture("valid", "empty-schedule.json").readText()
        val exact = (text + " ".repeat(MAX_IMPORT_BYTES - text.length)).encodeToByteArray()
        assertEquals(MAX_IMPORT_BYTES, exact.size)
        assertEquals(null, ScheduleDataDecoder.decode(ByteArrayInputStream(exact)).semester)

        val tooLarge = exact + ' '.code.toByte()
        try {
            ScheduleDataDecoder.decode(ByteArrayInputStream(tooLarge))
            fail("Expected ScheduleDataException.FileTooLarge")
        } catch (error: ScheduleDataException.FileTooLarge) {
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }

    private inline fun <reified T : ScheduleDataException> expectFailure(contents: ByteArray, assert: (T) -> Unit) {
        try {
            ScheduleDataDecoder.decode(contents)
            fail("Expected ${T::class.java.name}")
        } catch (error: Throwable) {
            if (error is T) assert(error) else throw error
        }
    }

    private fun encodedTree(data: ScheduleData): JsonElement =
        Json.parseToJsonElement(ScheduleDataDecoder.encode(data).decodeToString())

    private fun fixtureJson(name: String): JsonElement = Json.parseToJsonElement(fixture("valid", name).readText())

    private fun fixture(kind: String, name: String): File = fixtureRoot.resolve("$kind/$name")

    private fun manifestFixtures(kind: String): List<String> = Json.parseToJsonElement(
        fixtureRoot.resolve("manifest.json").readText(),
    ).jsonObject.getValue(kind).jsonArray.map { it.jsonPrimitive.content }.sorted()

    private fun completeText(): String = fixture("valid", "complete-schedule.json").readText()

    private fun semesterOf(root: MutableMap<String, JsonElement>): MutableMap<String, JsonElement> =
        root.getValue("semester").jsonObject.toMutableMap()

    private fun mutatedComplete(mutate: (MutableMap<String, JsonElement>) -> Unit): ByteArray {
        val root = Json.parseToJsonElement(completeText()).jsonObject.toMutableMap()
        mutate(root)
        return JsonObject(root).toString().encodeToByteArray()
    }

    private fun duplicateAndReversedJson(): String = listOf(
        "{",
        "  \"schemaVersion\": 1,",
        "  \"semester\": {",
        "    \"id\": \"semester-duplicate\",",
        "    \"name\": \"重复与反序学期\",",
        "    \"startDate\": \"2026-09-02\",",
        "    \"totalWeeks\": 18,",
        "    \"periods\": [",
        "      { \"number\": 2, \"startTime\": \"08:00\", \"endTime\": \"08:45\" },",
        "      { \"number\": 1, \"startTime\": \"08:55\", \"endTime\": \"09:40\" }",
        "    ]",
        "  },",
        "  \"courses\": [",
        "    {",
        "      \"id\": \"duplicate\",",
        "      \"name\": \"第一门\",",
        "      \"teacher\": \"\",",
        "      \"color\": \"#287B74\",",
        "      \"schedules\": [",
        "        { \"id\": \"shared\", \"dayOfWeek\": 1, \"startPeriod\": 2, \"endPeriod\": 2, \"startWeek\": 1, \"endWeek\": 18, \"repeat\": \"odd\", \"classroom\": \"B202\" }",
        "      ]",
        "    },",
        "    {",
        "      \"id\": \"duplicate\",",
        "      \"name\": \"第二门\",",
        "      \"teacher\": \"教师\",",
        "      \"color\": \"#E65A4F\",",
        "      \"schedules\": [",
        "        { \"id\": \"shared\", \"dayOfWeek\": 7, \"startPeriod\": 1, \"endPeriod\": 1, \"startWeek\": 2, \"endWeek\": 4, \"repeat\": \"even\", \"classroom\": \"\" }",
        "      ]",
        "    }",
        "  ],",
        "  \"updatedAt\": \"2026-09-02T12:00:00.000Z\"",
        "}",
    ).joinToString("\n")

    /** Mirrors the unmodified iOS `hasOnlyVersion1Fields` recogniser so the exported tree stays interoperable. */
    private fun iosAcceptsVersionOne(element: JsonElement): Boolean {
        val root = element as? JsonObject ?: return false
        if (!root.keys.all { it in setOf("schemaVersion", "semester", "courses", "updatedAt") }) return false
        val semester = root["semester"]
        if (semester != null && semester !is JsonNull) {
            val semesterObject = semester as? JsonObject ?: return false
            if (!semesterObject.keys.all { it in setOf("id", "name", "startDate", "totalWeeks", "periods") }) return false
            val periods = semesterObject["periods"] as? JsonArray ?: return false
            periods.forEach { period ->
                val periodObject = period as? JsonObject ?: return false
                if (!periodObject.keys.all { it in setOf("number", "startTime", "endTime") }) return false
            }
        }
        val courses = root["courses"] as? JsonArray ?: return false
        courses.forEach { course ->
            val courseObject = course as? JsonObject ?: return false
            if (!courseObject.keys.all { it in setOf("id", "name", "teacher", "color", "schedules") }) return false
            val schedules = courseObject["schedules"] as? JsonArray ?: return false
            schedules.forEach { schedule ->
                val scheduleObject = schedule as? JsonObject ?: return false
                val allowed = setOf(
                    "id", "dayOfWeek", "startPeriod", "endPeriod",
                    "startWeek", "endWeek", "repeat", "classroom",
                )
                if (!scheduleObject.keys.all { it in allowed }) return false
            }
        }
        return true
    }

    private fun indexOf(bytes: ByteArray, needle: ByteArray): Int =
        requireNotNull(bytes.indices.firstOrNull { start ->
            start + needle.size <= bytes.size && needle.indices.all { offset -> bytes[start + offset] == needle[offset] }
        }) { "Expected fixture text was not found" }
}
