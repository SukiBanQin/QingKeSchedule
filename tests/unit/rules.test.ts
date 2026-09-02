import { describe, expect, it } from 'vitest'
import {
  findCourseConflicts,
  getOccurrenceStatus,
  getOccurrencesForDate,
  getTeachingWeek,
  scheduleAppliesInWeek,
  schedulesConflict,
} from '../../src/domain/rules'
import { courseFixture, scheduleFixture, semesterFixture } from '../fixtures'

describe('教学周计算', () => {
  it('把开始日期所在的周一到周日都算作第 1 周', () => {
    expect(getTeachingWeek(semesterFixture, new Date(2026, 7, 31))).toBe(1)
    expect(getTeachingWeek(semesterFixture, new Date(2026, 8, 6))).toBe(1)
    expect(getTeachingWeek(semesterFixture, new Date(2026, 8, 7))).toBe(2)
  })

  it('保留学期前后周数，供界面判断学期外状态', () => {
    expect(getTeachingWeek(semesterFixture, new Date(2026, 7, 30))).toBe(0)
    expect(getTeachingWeek(semesterFixture, new Date(2027, 0, 4))).toBe(19)
  })
})

describe('重复规则与课程筛选', () => {
  it('正确处理每周、单周和双周', () => {
    expect(scheduleAppliesInWeek(scheduleFixture({ repeat: 'every' }), 2)).toBe(true)
    expect(scheduleAppliesInWeek(scheduleFixture({ repeat: 'odd' }), 3)).toBe(true)
    expect(scheduleAppliesInWeek(scheduleFixture({ repeat: 'odd' }), 2)).toBe(false)
    expect(scheduleAppliesInWeek(scheduleFixture({ repeat: 'even' }), 2)).toBe(true)
    expect(scheduleAppliesInWeek(scheduleFixture({ repeat: 'even' }), 3)).toBe(false)
  })

  it('筛选一门课程的多个安排并按开始节次排序', () => {
    const course = courseFixture({
      schedules: [
        scheduleFixture({ id: 'late', dayOfWeek: 3, startPeriod: 3, endPeriod: 3 }),
        scheduleFixture({ id: 'early', dayOfWeek: 3, startPeriod: 1, endPeriod: 1 }),
      ],
    })
    const occurrences = getOccurrencesForDate(
      semesterFixture,
      [course],
      new Date(2026, 8, 2),
    )
    expect(occurrences.map(({ schedule }) => schedule.id)).toEqual(['early', 'late'])
  })
})

describe('冲突检测', () => {
  it('单双周互不冲突', () => {
    const odd = scheduleFixture({ repeat: 'odd' })
    const even = scheduleFixture({ id: 'schedule-2', repeat: 'even' })
    expect(schedulesConflict(odd, even)).toBe(false)
  })

  it('找出星期、节次、周次和重复规则同时重叠的课程', () => {
    const existing = courseFixture()
    const candidate = courseFixture({
      id: 'course-2',
      name: '操作系统',
      schedules: [scheduleFixture({ id: 'schedule-2', startPeriod: 2, endPeriod: 3, repeat: 'odd' })],
    })
    const conflicts = findCourseConflicts(candidate, [existing])
    expect(conflicts).toHaveLength(1)
    expect(conflicts[0].existingCourse.name).toBe('数据结构')
    expect(conflicts[0].weeks.slice(0, 3)).toEqual([1, 3, 5])
  })
})

describe('今日课程状态', () => {
  const occurrence = { course: courseFixture(), schedule: scheduleFixture() }

  it.each([
    [new Date(2026, 8, 1, 7, 59), 'upcoming'],
    [new Date(2026, 8, 1, 8, 0), 'ongoing'],
    [new Date(2026, 8, 1, 9, 40), 'ongoing'],
    [new Date(2026, 8, 1, 9, 41), 'finished'],
  ] as const)('在边界时间 %s 返回 %s', (now, expected) => {
    expect(getOccurrenceStatus(occurrence, semesterFixture, now)).toBe(expected)
  })
})
