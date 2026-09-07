package com.qingke.schedule.transfer

import com.qingke.schedule.domain.MAX_IMPORT_BYTES
import com.qingke.schedule.domain.SUPPORTED_SCHEMA_VERSION
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.ScheduleValidator
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class ScheduleDataException(message: String) : IllegalArgumentException(message) {
    data object FileTooLarge : ScheduleDataException("课表文件超过 5 MiB 输入上限")
    data class UnsupportedSchemaVersion(val version: Int?) :
        ScheduleDataException("不支持的课表数据版本：${version ?: "missing"}")
    data class MalformedJson(val reason: String) : ScheduleDataException(reason)
    data class InvalidData(val issues: List<com.qingke.schedule.domain.ValidationIssue>) :
        ScheduleDataException(issues.firstOrNull()?.message ?: "课表内容未通过校验")
}

/** Strict v1 import boundary shared by file import and future persistence code. */
object ScheduleDataDecoder {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = true
        coerceInputValues = false
    }

    fun decode(input: InputStream): ScheduleData = decode(readBounded(input))

    fun decode(bytes: ByteArray): ScheduleData {
        if (bytes.size > MAX_IMPORT_BYTES) throw ScheduleDataException.FileTooLarge
        val source = decodeUtf8(bytes)
        val element = try {
            json.parseToJsonElement(source)
        } catch (error: SerializationException) {
            throw ScheduleDataException.MalformedJson("JSON 格式无效：${error.message}")
        }
        val objectValue = element as? JsonObject
            ?: throw ScheduleDataException.MalformedJson("JSON 顶层必须是对象")
        val version = integerAt(objectValue["schemaVersion"], "schemaVersion")
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw ScheduleDataException.UnsupportedSchemaVersion(version)
        }
        val normalized = normalizeIntegerTokens(objectValue)
        val data = try {
            json.decodeFromJsonElement(ScheduleData.serializer(), normalized)
        } catch (error: SerializationException) {
            throw ScheduleDataException.MalformedJson("JSON 契约无效：${error.message}")
        }
        val issues = ScheduleValidator.validate(data)
        if (issues.isNotEmpty()) throw ScheduleDataException.InvalidData(issues)
        return data
    }

    /** Encodes the v1 DTO without dropping an explicitly null semester field. */
    fun encode(data: ScheduleData): ByteArray {
        val issues = ScheduleValidator.validate(data)
        if (issues.isNotEmpty()) throw ScheduleDataException.InvalidData(issues)
        return json.encodeToString(ScheduleData.serializer(), data).encodeToByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        throw ScheduleDataException.MalformedJson("JSON 编码无效：输入不是有效 UTF-8")
    }

    /**
     * Kotlin serialization's Int decoder accepts quoted numerals in some paths and does not
     * accept every JSON spelling that Foundation decodes as an Int. Check the JSON token before
     * DTO decoding so each contract integer is numeric, mathematically integral, and Int-sized.
     */
    private fun normalizeIntegerTokens(root: JsonObject): JsonObject {
        val normalized = root.toMutableMap()
        normalized.normalizeInteger("schemaVersion")
        val semester = normalized["semester"]
        if (semester != null && semester !is JsonNull) {
            (semester as? JsonObject)?.let { semesterObject ->
                normalized["semester"] = semesterObject.normalizeSemester()
            }
        }
        (normalized["courses"] as? JsonArray)?.let { courses ->
            normalized["courses"] = JsonArray(courses.mapIndexed { courseIndex, course ->
                (course as? JsonObject)?.normalizeCourse(courseIndex) ?: course
            })
        }
        return JsonObject(normalized)
    }

    private fun JsonObject.normalizeSemester(): JsonObject {
        val normalized = toMutableMap()
        normalized.normalizeInteger("totalWeeks", "semester.totalWeeks")
        (normalized["periods"] as? JsonArray)?.let { periods ->
            normalized["periods"] = JsonArray(periods.mapIndexed { index, period ->
                (period as? JsonObject)?.let { periodObject ->
                    JsonObject(periodObject.toMutableMap().also {
                        it.normalizeInteger("number", "semester.periods.$index.number")
                    })
                } ?: period
            })
        }
        return JsonObject(normalized)
    }

    private fun JsonObject.normalizeCourse(courseIndex: Int): JsonObject {
        val normalized = toMutableMap()
        (normalized["schedules"] as? JsonArray)?.let { schedules ->
            normalized["schedules"] = JsonArray(schedules.mapIndexed { scheduleIndex, schedule ->
                (schedule as? JsonObject)?.let { scheduleObject ->
                    val path = "courses.$courseIndex.schedules.$scheduleIndex"
                    JsonObject(scheduleObject.toMutableMap().also {
                        it.normalizeInteger("dayOfWeek", "$path.dayOfWeek")
                        it.normalizeInteger("startPeriod", "$path.startPeriod")
                        it.normalizeInteger("endPeriod", "$path.endPeriod")
                        it.normalizeInteger("startWeek", "$path.startWeek")
                        it.normalizeInteger("endWeek", "$path.endWeek")
                    })
                } ?: schedule
            })
        }
        return JsonObject(normalized)
    }

    private fun MutableMap<String, JsonElement>.normalizeInteger(name: String, path: String = name) {
        get(name)?.let { put(name, JsonPrimitive(requireNotNull(integerAt(it, path)))) }
    }

    private fun integerAt(element: JsonElement?, path: String): Int? {
        if (element == null) return null
        val primitive = element as? JsonPrimitive
            ?: throw ScheduleDataException.MalformedJson("JSON 契约无效：$path 必须是整数数值")
        if (primitive.isString) {
            throw ScheduleDataException.MalformedJson("JSON 契约无效：$path 必须是整数数值")
        }
        if (!JSON_NUMBER.matches(primitive.content)) {
            throw ScheduleDataException.MalformedJson("JSON 契约无效：$path 必须是合法 JSON 数字")
        }
        val decimal = try {
            BigDecimal(primitive.content)
        } catch (_: NumberFormatException) {
            throw ScheduleDataException.MalformedJson("JSON 契约无效：$path 必须是整数数值")
        }
        // Compare before materializing a BigInteger: a JSON exponent can describe an enormous
        // value while still fitting inside the input byte limit.
        if (decimal < INT_MIN || decimal > INT_MAX) {
            throw ScheduleDataException.MalformedJson("JSON 契约无效：$path 超出整数范围")
        }
        val value = try {
            decimal.toBigIntegerExact()
        } catch (_: ArithmeticException) {
            throw ScheduleDataException.MalformedJson("JSON 契约无效：$path 必须是整数数值")
        }
        return value.toInt()
    }

    private val INT_MIN = BigDecimal(Int.MIN_VALUE)
    private val INT_MAX = BigDecimal(Int.MAX_VALUE)
    private val JSON_NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")

    private fun readBounded(input: InputStream): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_IMPORT_BYTES) throw ScheduleDataException.FileTooLarge
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
