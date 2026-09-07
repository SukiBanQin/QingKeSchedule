package com.qingke.schedule.transfer

import com.qingke.schedule.domain.MAX_IMPORT_BYTES
import com.qingke.schedule.domain.SUPPORTED_SCHEMA_VERSION
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.ScheduleValidator
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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
        val element = try {
            json.parseToJsonElement(bytes.decodeToString())
        } catch (error: SerializationException) {
            throw ScheduleDataException.MalformedJson("JSON 格式无效：${error.message}")
        }
        val objectValue = element as? JsonObject
            ?: throw ScheduleDataException.MalformedJson("JSON 顶层必须是对象")
        val version = (objectValue["schemaVersion"] as? JsonPrimitive)?.intOrNull
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw ScheduleDataException.UnsupportedSchemaVersion(version)
        }
        val data = try {
            json.decodeFromJsonElement(ScheduleData.serializer(), element)
        } catch (error: SerializationException) {
            throw ScheduleDataException.MalformedJson("JSON 契约无效：${error.message}")
        }
        val issues = ScheduleValidator.validate(data)
        if (issues.isNotEmpty()) throw ScheduleDataException.InvalidData(issues)
        return data
    }

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
