import type { Course, CourseSchedule, Period, Semester, StoredScheduleData } from '../domain/types'
import type { RepositoryLoadResult, ScheduleRepository } from '../repositories/ScheduleRepository'

export const STORAGE_KEY = 'course-schedule:data'
export const SCHEMA_VERSION = 1

export function createEmptyData(): StoredScheduleData {
  return {
    schemaVersion: SCHEMA_VERSION,
    semester: null,
    courses: [],
    updatedAt: new Date(0).toISOString(),
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isPeriod(value: unknown): value is Period {
  return (
    isRecord(value) &&
    typeof value.number === 'number' &&
    typeof value.startTime === 'string' &&
    typeof value.endTime === 'string'
  )
}

function isSemester(value: unknown): value is Semester {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.name === 'string' &&
    typeof value.startDate === 'string' &&
    typeof value.totalWeeks === 'number' &&
    Array.isArray(value.periods) &&
    value.periods.every(isPeriod)
  )
}

function isSchedule(value: unknown): value is CourseSchedule {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.dayOfWeek === 'number' &&
    typeof value.startPeriod === 'number' &&
    typeof value.endPeriod === 'number' &&
    typeof value.startWeek === 'number' &&
    typeof value.endWeek === 'number' &&
    (value.repeat === 'every' || value.repeat === 'odd' || value.repeat === 'even') &&
    typeof value.classroom === 'string'
  )
}

function isCourse(value: unknown): value is Course {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.name === 'string' &&
    typeof value.teacher === 'string' &&
    typeof value.color === 'string' &&
    Array.isArray(value.schedules) &&
    value.schedules.every(isSchedule)
  )
}

function isStoredData(value: unknown): value is StoredScheduleData {
  return (
    isRecord(value) &&
    value.schemaVersion === SCHEMA_VERSION &&
    (value.semester === null || isSemester(value.semester)) &&
    Array.isArray(value.courses) &&
    value.courses.every(isCourse) &&
    typeof value.updatedAt === 'string'
  )
}

export function parseStoredData(raw: string): StoredScheduleData {
  const parsed: unknown = JSON.parse(raw)
  if (!isRecord(parsed)) throw new Error('保存的数据格式不正确')
  if (parsed.schemaVersion !== SCHEMA_VERSION) throw new Error('保存的数据版本暂不受支持')
  if (!isStoredData(parsed)) throw new Error('保存的数据缺少必要字段')
  return parsed
}

export class LocalStorageScheduleRepository implements ScheduleRepository {
  constructor(private readonly storage: Storage) {}

  load(): RepositoryLoadResult {
    const raw = this.storage.getItem(STORAGE_KEY)
    if (raw === null) return { data: createEmptyData(), recoveryRaw: null, warning: null }
    try {
      return { data: parseStoredData(raw), recoveryRaw: null, warning: null }
    } catch (error) {
      const detail = error instanceof Error ? error.message : '未知错误'
      return {
        data: createEmptyData(),
        recoveryRaw: raw,
        warning: `本地课表读取失败：${detail}。原数据尚未被覆盖。`,
      }
    }
  }

  save(data: StoredScheduleData): void {
    this.storage.setItem(STORAGE_KEY, JSON.stringify(data))
  }

  clear(): void {
    this.storage.removeItem(STORAGE_KEY)
  }
}
