import type { Course, CourseSchedule, Semester } from '../src/domain/types'

export const semesterFixture: Semester = {
  id: 'semester-1',
  name: '测试学期',
  startDate: '2026-09-02',
  totalWeeks: 18,
  periods: [
    { number: 1, startTime: '08:00', endTime: '08:45' },
    { number: 2, startTime: '08:55', endTime: '09:40' },
    { number: 3, startTime: '10:00', endTime: '10:45' },
  ],
}

export function scheduleFixture(overrides: Partial<CourseSchedule> = {}): CourseSchedule {
  return {
    id: 'schedule-1',
    dayOfWeek: 1,
    startPeriod: 1,
    endPeriod: 2,
    startWeek: 1,
    endWeek: 18,
    repeat: 'every',
    classroom: 'A101',
    ...overrides,
  }
}

export function courseFixture(overrides: Partial<Course> = {}): Course {
  return {
    id: 'course-1',
    name: '数据结构',
    teacher: '陈老师',
    color: '#287B74',
    schedules: [scheduleFixture()],
    ...overrides,
  }
}
