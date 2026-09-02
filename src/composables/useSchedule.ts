import { computed, reactive, readonly, type ComputedRef, type DeepReadonly } from 'vue'
import type { Course, Semester, StoredScheduleData } from '../domain/types'
import type { ScheduleRepository } from '../repositories/ScheduleRepository'
import { createEmptyData, LocalStorageScheduleRepository } from '../storage/localStorageRepository'

export interface ScheduleStore {
  state: DeepReadonly<{
    data: StoredScheduleData
    loaded: boolean
    error: string | null
    recoveryRaw: string | null
  }>
  semester: ComputedRef<Semester | null>
  courses: ComputedRef<Course[]>
  initialize(): void
  saveSemester(semester: Semester): boolean
  saveCourse(course: Course): boolean
  deleteCourse(courseId: string): boolean
  clearAll(): boolean
  dismissError(): void
}

export function createScheduleStore(repository: ScheduleRepository): ScheduleStore {
  const state = reactive({
    data: createEmptyData(),
    loaded: false,
    error: null as string | null,
    recoveryRaw: null as string | null,
  })

  const persist = (nextData: StoredScheduleData): boolean => {
    try {
      repository.save(nextData)
      state.data = nextData
      state.error = null
      return true
    } catch (error) {
      const detail = error instanceof Error ? error.message : '浏览器拒绝了写入操作'
      state.error = `无法保存课表：${detail}。请检查浏览器存储空间或隐私设置。`
      return false
    }
  }

  return {
    state: readonly(state),
    semester: computed<Semester | null>(() => state.data.semester as Semester | null),
    courses: computed<Course[]>(() => state.data.courses as Course[]),
    initialize() {
      if (state.loaded) return
      try {
        const result = repository.load()
        state.data = result.data
        state.recoveryRaw = result.recoveryRaw
        state.error = result.warning
      } catch (error) {
        const detail = error instanceof Error ? error.message : '浏览器拒绝了读取操作'
        state.error = `无法读取本地课表：${detail}`
      } finally {
        state.loaded = true
      }
    },
    saveSemester(semester) {
      return persist({
        ...state.data,
        semester,
        updatedAt: new Date().toISOString(),
      })
    },
    saveCourse(course) {
      const existingIndex = state.data.courses.findIndex((item) => item.id === course.id)
      const courses = [...state.data.courses]
      if (existingIndex >= 0) courses.splice(existingIndex, 1, course)
      else courses.push(course)
      return persist({ ...state.data, courses, updatedAt: new Date().toISOString() })
    },
    deleteCourse(courseId) {
      return persist({
        ...state.data,
        courses: state.data.courses.filter((course) => course.id !== courseId),
        updatedAt: new Date().toISOString(),
      })
    },
    clearAll() {
      try {
        repository.clear()
        state.data = createEmptyData()
        state.error = null
        state.recoveryRaw = null
        return true
      } catch (error) {
        const detail = error instanceof Error ? error.message : '浏览器拒绝了清除操作'
        state.error = `无法清除本地数据：${detail}`
        return false
      }
    },
    dismissError() {
      state.error = null
    },
  }
}

const browserStorage = typeof window === 'undefined' ? undefined : window.localStorage
const fallbackStorage: Storage = {
  length: 0,
  clear: () => undefined,
  getItem: () => null,
  key: () => null,
  removeItem: () => undefined,
  setItem: () => undefined,
}

export const scheduleStore = createScheduleStore(
  new LocalStorageScheduleRepository(browserStorage ?? fallbackStorage),
)

export function useSchedule(): ScheduleStore {
  scheduleStore.initialize()
  return scheduleStore
}
