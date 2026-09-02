import { COURSE_COLORS } from './types'
import type { Course, Period, Semester } from './types'
import { parseLocalDate } from './rules'

export interface ValidationIssue {
  path: string
  message: string
}

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/

function isValidPeriod(period: Period): boolean {
  return (
    Number.isInteger(period.number) &&
    period.number > 0 &&
    TIME_PATTERN.test(period.startTime) &&
    TIME_PATTERN.test(period.endTime) &&
    period.startTime < period.endTime
  )
}

export function validateSemester(semester: Semester): ValidationIssue[] {
  const issues: ValidationIssue[] = []
  if (!semester.name.trim()) issues.push({ path: 'name', message: '请填写学期名称' })
  if (Number.isNaN(parseLocalDate(semester.startDate).getTime())) {
    issues.push({ path: 'startDate', message: '请选择有效的开始日期' })
  }
  if (!Number.isInteger(semester.totalWeeks) || semester.totalWeeks < 1 || semester.totalWeeks > 52) {
    issues.push({ path: 'totalWeeks', message: '总周数需要在 1 到 52 之间' })
  }
  if (semester.periods.length < 1 || semester.periods.length > 20) {
    issues.push({ path: 'periods', message: '请设置 1 到 20 个节次' })
  }
  const numbers = new Set<number>()
  semester.periods.forEach((period, index) => {
    if (!isValidPeriod(period)) {
      issues.push({ path: `periods.${index}`, message: `第 ${index + 1} 行的时间无效` })
    }
    if (numbers.has(period.number)) {
      issues.push({ path: `periods.${index}`, message: '节次编号不能重复' })
    }
    numbers.add(period.number)
    if (index > 0 && semester.periods[index - 1].endTime > period.startTime) {
      issues.push({ path: `periods.${index}`, message: '相邻节次的时间不能重叠' })
    }
  })
  return issues
}

export function validateCourse(course: Course, semester: Semester): ValidationIssue[] {
  const issues: ValidationIssue[] = []
  const validPeriodNumbers = new Set(semester.periods.map((period) => period.number))
  if (!course.name.trim()) issues.push({ path: 'name', message: '请填写课程名称' })
  if (!(COURSE_COLORS as readonly string[]).includes(course.color)) {
    issues.push({ path: 'color', message: '请选择一个可用的课程颜色' })
  }
  if (course.schedules.length < 1) {
    issues.push({ path: 'schedules', message: '至少需要一个上课安排' })
  }
  course.schedules.forEach((schedule, index) => {
    if (schedule.dayOfWeek < 1 || schedule.dayOfWeek > 7) {
      issues.push({ path: `schedules.${index}.dayOfWeek`, message: '请选择星期' })
    }
    if (
      !validPeriodNumbers.has(schedule.startPeriod) ||
      !validPeriodNumbers.has(schedule.endPeriod) ||
      schedule.startPeriod > schedule.endPeriod
    ) {
      issues.push({ path: `schedules.${index}.periods`, message: '请选择有效的起止节次' })
    }
    if (
      !Number.isInteger(schedule.startWeek) ||
      !Number.isInteger(schedule.endWeek) ||
      schedule.startWeek < 1 ||
      schedule.endWeek > semester.totalWeeks ||
      schedule.startWeek > schedule.endWeek
    ) {
      issues.push({ path: `schedules.${index}.weeks`, message: '请选择有效的起止周' })
    }
  })
  return issues
}
