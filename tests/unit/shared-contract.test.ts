import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import Ajv2020, { type ErrorObject } from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import { describe, expect, it } from 'vitest'
import {
  getOccurrenceStatus,
  getOccurrencesForDate,
  getOverlappingWeeks,
  getTeachingWeek,
  scheduleAppliesInWeek,
  schedulesConflict,
} from '../../src/domain/rules'
import type { Course, CourseSchedule, StoredScheduleData } from '../../src/domain/types'
import { validateCourse, validateSemester } from '../../src/domain/validation'
import { parseStoredData } from '../../src/storage/localStorageRepository'

interface FixtureManifest {
  valid: string[]
  invalid: string[]
}

const repositoryRoot = process.cwd()
const fixtureRoot = resolve(repositoryRoot, 'shared/fixtures')
const schema = JSON.parse(
  readFileSync(resolve(repositoryRoot, 'shared/schedule-data.schema.json'), 'utf8'),
) as object
const manifest = readJson<FixtureManifest>(resolve(fixtureRoot, 'manifest.json'))

const ajv = new Ajv2020({ allErrors: true, strict: true })
addFormats(ajv)
const validatesSchema = ajv.compile(schema)

function readJson<T>(path: string): T {
  return JSON.parse(readFileSync(path, 'utf8')) as T
}

function readFixture(kind: 'valid' | 'invalid', fileName: string): unknown {
  return readJson(resolve(fixtureRoot, kind, fileName))
}

function formatSchemaErrors(errors: ErrorObject[] | null | undefined): string {
  return errors?.map((error) => `${error.instancePath || '/'} ${error.message}`).join('; ') ?? ''
}

function businessIssues(data: StoredScheduleData): string[] {
  if (data.semester === null) {
    return data.courses.length === 0 ? [] : ['没有学期时不能包含课程']
  }
  return [
    ...validateSemester(data.semester).map((issue) => issue.message),
    ...data.courses.flatMap((course) =>
      validateCourse(course, data.semester!).map((issue) => issue.message),
    ),
  ]
}

function fixtureIsAccepted(value: unknown): boolean {
  const schemaAccepted = validatesSchema(value)
  if (!schemaAccepted) return false

  try {
    const parsed = parseStoredData(JSON.stringify(value))
    return businessIssues(parsed).length === 0
  } catch {
    return false
  }
}

function findCourse(data: StoredScheduleData, id: string): Course {
  const course = data.courses.find((candidate) => candidate.id === id)
  if (!course) throw new Error(`Missing fixture course: ${id}`)
  return course
}

function findSchedule(data: StoredScheduleData, id: string): CourseSchedule {
  const schedule = data.courses.flatMap((course) => course.schedules)
    .find((candidate) => candidate.id === id)
  if (!schedule) throw new Error(`Missing fixture schedule: ${id}`)
  return schedule
}

describe('共享 schemaVersion 1 契约', () => {
  it.each(manifest.valid)('接受有效 fixture：%s', (fileName) => {
    const value = readFixture('valid', fileName)
    expect(validatesSchema(value), formatSchemaErrors(validatesSchema.errors)).toBe(true)
    expect(fixtureIsAccepted(value)).toBe(true)
  })

  it.each(manifest.invalid)('拒绝无效 fixture：%s', (fileName) => {
    expect(fixtureIsAccepted(readFixture('invalid', fileName))).toBe(false)
  })

  it('使用完整 fixture 验证跨端关键规则结果', () => {
    const data = parseStoredData(
      JSON.stringify(readFixture('valid', 'complete-schedule.json')),
    )
    if (!data.semester) throw new Error('Complete fixture must contain a semester')

    const every = findSchedule(data, 'schedule-every')
    const odd = findSchedule(data, 'schedule-odd')
    const even = findSchedule(data, 'schedule-even')
    expect(scheduleAppliesInWeek(every, 2)).toBe(true)
    expect(scheduleAppliesInWeek(odd, 3)).toBe(true)
    expect(scheduleAppliesInWeek(odd, 2)).toBe(false)
    expect(scheduleAppliesInWeek(even, 2)).toBe(true)
    expect(schedulesConflict(odd, even)).toBe(false)
    expect(getOverlappingWeeks(every, odd)).toEqual([1, 3, 5])

    expect(getTeachingWeek(data.semester, new Date(2026, 7, 31))).toBe(1)
    expect(getTeachingWeek(data.semester, new Date(2026, 8, 7))).toBe(2)
    expect(getTeachingWeek(data.semester, new Date(2026, 7, 30))).toBe(0)

    const weekOneMonday = getOccurrencesForDate(
      data.semester,
      data.courses,
      new Date(2026, 7, 31, 8, 0),
    )
    expect(weekOneMonday.map(({ schedule }) => schedule.id)).toEqual([
      'schedule-every',
      'schedule-odd',
      'schedule-alpha',
      'schedule-beta',
    ])

    const occurrence = {
      course: findCourse(data, 'course-every'),
      schedule: every,
    }
    expect(getOccurrenceStatus(occurrence, data.semester, new Date(2026, 7, 31, 7, 59)))
      .toBe('upcoming')
    expect(getOccurrenceStatus(occurrence, data.semester, new Date(2026, 7, 31, 8, 0)))
      .toBe('ongoing')
    expect(getOccurrenceStatus(occurrence, data.semester, new Date(2026, 7, 31, 9, 40)))
      .toBe('ongoing')
    expect(getOccurrenceStatus(occurrence, data.semester, new Date(2026, 7, 31, 9, 41)))
      .toBe('finished')
  })
})
