package com.qingke.schedule.state

import com.qingke.schedule.domain.Course
import com.qingke.schedule.domain.SUPPORTED_SCHEMA_VERSION
import com.qingke.schedule.domain.ScheduleData
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.persistence.ScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LoadStatus { NOT_LOADED, LOADING, READY, FAILED }

data class ScheduleState(
    val data: ScheduleData = ScheduleData(SUPPORTED_SCHEMA_VERSION, null, emptyList(), "1970-01-01T00:00:00.000Z"),
    val loadStatus: LoadStatus = LoadStatus.NOT_LOADED,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val needsOnboarding: Boolean get() = loadStatus == LoadStatus.READY && data.semester == null
}

class ScheduleAppState(private val repository: ScheduleRepository) {
    private val writeMutex = Mutex()
    private val mutableState = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = mutableState.asStateFlow()

    suspend fun load() {
        val previous = mutableState.value
        mutableState.value = mutableState.value.copy(loadStatus = LoadStatus.LOADING, error = null)
        try {
            mutableState.value = mutableState.value.copy(data = repository.load(), loadStatus = LoadStatus.READY, error = null)
        } catch (error: CancellationException) {
            mutableState.value = previous
            throw error
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(loadStatus = LoadStatus.FAILED, error = message(error))
        }
    }

    suspend fun replace(data: ScheduleData) = save { repository.replace(data) }
    suspend fun saveSemester(semester: Semester) = save { repository.saveSemester(semester) }
    suspend fun saveCourse(course: Course) = save { repository.saveCourse(course) }
    suspend fun deleteCourse(id: String) = save { repository.deleteCourse(id) }
    fun clearError() { mutableState.value = mutableState.value.copy(error = null) }

    private suspend fun save(block: suspend () -> ScheduleData) = writeMutex.withLock {
        check(mutableState.value.loadStatus == LoadStatus.READY) { "课表尚未加载" }
        val previous = mutableState.value
        mutableState.value = mutableState.value.copy(isSaving = true, error = null)
        try {
            mutableState.value = mutableState.value.copy(data = block(), isSaving = false, error = null)
        } catch (error: CancellationException) {
            mutableState.value = previous
            throw error
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(isSaving = false, error = message(error))
        }
    }

    private fun message(error: Throwable): String = error.message ?: "课表操作失败"
}
