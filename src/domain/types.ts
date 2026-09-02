export type RepeatRule = 'every' | 'odd' | 'even'

export type DayOfWeek = 1 | 2 | 3 | 4 | 5 | 6 | 7

export interface Period {
  number: number
  startTime: string
  endTime: string
}

export interface Semester {
  id: string
  name: string
  startDate: string
  totalWeeks: number
  periods: Period[]
}

export interface CourseSchedule {
  id: string
  dayOfWeek: DayOfWeek
  startPeriod: number
  endPeriod: number
  startWeek: number
  endWeek: number
  repeat: RepeatRule
  classroom: string
}

export interface Course {
  id: string
  name: string
  teacher: string
  color: string
  schedules: CourseSchedule[]
}

export interface StoredScheduleData {
  schemaVersion: number
  semester: Semester | null
  courses: Course[]
  updatedAt: string
}

export interface CourseOccurrence {
  course: Course
  schedule: CourseSchedule
}

export interface ScheduleConflict {
  candidateCourse: Course
  candidateSchedule: CourseSchedule
  existingCourse: Course
  existingSchedule: CourseSchedule
  weeks: number[]
}

export type CourseStatus = 'finished' | 'ongoing' | 'upcoming'

export const COURSE_COLORS = [
  '#287B74',
  '#D96952',
  '#536FAF',
  '#9A6AAF',
  '#B87928',
  '#46835A',
] as const

export const DAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] as const

export const REPEAT_LABELS: Record<RepeatRule, string> = {
  every: '每周',
  odd: '单周',
  even: '双周',
}
