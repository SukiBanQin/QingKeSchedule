package com.qingke.schedule.state

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.SUPPORTED_SCHEMA_VERSION
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import com.qingke.schedule.preferences.SchedulePreferences
import com.qingke.schedule.preferences.SchedulePreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LoadStatus { NOT_LOADED, LOADING, READY, FAILED }

data class ScheduleState(
    val data: ScheduleData = ScheduleData(SUPPORTED_SCHEMA_VERSION, null, emptyList(), "1970-01-01T00:00:00.000Z"),
    val preferences: SchedulePreferences = SchedulePreferences.defaults,
    val loadStatus: LoadStatus = LoadStatus.NOT_LOADED,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val needsOnboarding: Boolean get() = loadStatus == LoadStatus.READY && data.semester == null
}

class ScheduleAppState(
    private val repository: ScheduleRepository,
    private val preferencesRepository: SchedulePreferencesRepository,
) {
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = mutableState.asStateFlow()

    suspend fun load() = operationMutex.withLock {
        val previous = mutableState.value
        mutableState.value = previous.copy(loadStatus = LoadStatus.LOADING, error = null)
        try {
            val data = loadValue("读取课表失败") { repository.load() }
            val preferences = loadValue("读取设置失败") { preferencesRepository.load() }
            mutableState.value = previous.copy(
                data = data,
                preferences = preferences,
                loadStatus = LoadStatus.READY,
                isSaving = false,
                error = null,
            )
        } catch (error: CancellationException) {
            mutableState.value = previous
            throw error
        } catch (error: Throwable) {
            mutableState.value = previous.copy(loadStatus = LoadStatus.FAILED, error = message(error))
        }
    }

    suspend fun replace(data: ScheduleData) = saveSchedule { repository.replace(data) }
    suspend fun saveSemester(semester: Semester) = saveSchedule { repository.saveSemester(semester) }
    suspend fun saveCourse(course: Course) = saveSchedule { repository.saveCourse(course) }
    suspend fun deleteCourse(id: String) = saveSchedule { repository.deleteCourse(id) }
    suspend fun savePreferences(preferences: SchedulePreferences) =
        savePreferences { preferencesRepository.save(preferences) }

    suspend fun updatePreferences(transform: (SchedulePreferences) -> SchedulePreferences) =
        savePreferences { preferencesRepository.update(transform) }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private suspend fun saveSchedule(block: suspend () -> ScheduleData) = save(
        operation = block,
        publish = { previous, data -> previous.copy(data = data) },
    )

    private suspend fun savePreferences(block: suspend () -> SchedulePreferences) = save(
        operation = block,
        publish = { previous, preferences -> previous.copy(preferences = preferences) },
    )

    private suspend fun <T> save(
        operation: suspend () -> T,
        publish: (ScheduleState, T) -> ScheduleState,
    ) = operationMutex.withLock {
        check(mutableState.value.loadStatus == LoadStatus.READY) { "应用数据尚未加载" }
        val previous = mutableState.value
        mutableState.value = previous.copy(isSaving = true, error = null)
        try {
            mutableState.value = publish(previous, operation()).copy(isSaving = false, error = null)
        } catch (error: CancellationException) {
            mutableState.value = previous
            throw error
        } catch (error: Throwable) {
            mutableState.value = previous.copy(isSaving = false, error = message(error))
        }
    }

    private suspend fun <T> loadValue(prefix: String, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw IllegalStateException("$prefix：${message(error)}", error)
    }

    private fun message(error: Throwable): String = error.message ?: "课表操作失败"
}
