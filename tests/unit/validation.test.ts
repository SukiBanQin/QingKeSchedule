import { describe, expect, it } from 'vitest'
import { validateCourse, validateSemester } from '../../src/domain/validation'
import { courseFixture, semesterFixture } from '../fixtures'

describe('业务校验', () => {
  it('接受有效学期和课程', () => {
    expect(validateSemester(semesterFixture)).toEqual([])
    expect(validateCourse(courseFixture(), semesterFixture)).toEqual([])
  })

  it('拒绝时间重叠的节次', () => {
    const semester = structuredClone(semesterFixture)
    semester.periods[1].startTime = '08:30'
    expect(validateSemester(semester).some((issue) => issue.path === 'periods.1')).toBe(true)
  })

  it('拒绝越过学期和倒置节次的安排', () => {
    const course = courseFixture()
    course.schedules[0].startPeriod = 3
    course.schedules[0].endPeriod = 1
    course.schedules[0].endWeek = 19
    const issues = validateCourse(course, semesterFixture)
    expect(issues.map((issue) => issue.path)).toContain('schedules.0.periods')
    expect(issues.map((issue) => issue.path)).toContain('schedules.0.weeks')
  })
})
