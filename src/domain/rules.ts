import type {
  Course,
  CourseOccurrence,
  CourseSchedule,
  CourseStatus,
  DayOfWeek,
  ScheduleConflict,
  Semester,
} from './types'

const DAY_MS = 24 * 60 * 60 * 1000

export function parseLocalDate(value: string): Date {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return new Date(Number.NaN)
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

export function toLocalDateString(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function startOfMondayWeek(date: Date): Date {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const sundayBasedDay = result.getDay()
  const daysSinceMonday = sundayBasedDay === 0 ? 6 : sundayBasedDay - 1
  result.setDate(result.getDate() - daysSinceMonday)
  return result
}

export function getTeachingWeek(semester: Semester, date: Date): number {
  const semesterMonday = startOfMondayWeek(parseLocalDate(semester.startDate))
  const dateMonday = startOfMondayWeek(date)
  return Math.round((dateMonday.getTime() - semesterMonday.getTime()) / (7 * DAY_MS)) + 1
}

export function isTeachingWeekInSemester(semester: Semester, week: number): boolean {
  return week >= 1 && week <= semester.totalWeeks
}

export function getDateForTeachingWeek(
  semester: Semester,
  week: number,
  dayOfWeek: DayOfWeek,
): Date {
  const semesterMonday = startOfMondayWeek(parseLocalDate(semester.startDate))
  const date = new Date(semesterMonday)
  date.setDate(date.getDate() + (week - 1) * 7 + dayOfWeek - 1)
  return date
}

export function scheduleAppliesInWeek(schedule: CourseSchedule, week: number): boolean {
  if (week < schedule.startWeek || week > schedule.endWeek) return false
  if (schedule.repeat === 'odd') return week % 2 === 1
  if (schedule.repeat === 'even') return week % 2 === 0
  return true
}

export function getOccurrencesForWeek(courses: Course[], week: number): CourseOccurrence[] {
  return courses.flatMap((course) =>
    course.schedules
      .filter((schedule) => scheduleAppliesInWeek(schedule, week))
      .map((schedule) => ({ course, schedule })),
  )
}

export function getOccurrencesForDate(
  semester: Semester,
  courses: Course[],
  date: Date,
): CourseOccurrence[] {
  const week = getTeachingWeek(semester, date)
  if (!isTeachingWeekInSemester(semester, week)) return []
  const browserDay = date.getDay()
  const dayOfWeek = (browserDay === 0 ? 7 : browserDay) as DayOfWeek
  return getOccurrencesForWeek(courses, week)
    .filter(({ schedule }) => schedule.dayOfWeek === dayOfWeek)
    .sort(
      (left, right) =>
        left.schedule.startPeriod - right.schedule.startPeriod ||
        left.schedule.endPeriod - right.schedule.endPeriod ||
        left.course.name.localeCompare(right.course.name, 'zh-CN'),
    )
}

function timeToMinutes(value: string): number {
  const [hours, minutes] = value.split(':').map(Number)
  return hours * 60 + minutes
}

export function getOccurrenceStatus(
  occurrence: CourseOccurrence,
  semester: Semester,
  now: Date,
): CourseStatus {
  const startPeriod = semester.periods.find(
    (period) => period.number === occurrence.schedule.startPeriod,
  )
  const endPeriod = semester.periods.find(
    (period) => period.number === occurrence.schedule.endPeriod,
  )
  if (!startPeriod || !endPeriod) return 'upcoming'
  const currentMinutes = now.getHours() * 60 + now.getMinutes()
  const startMinutes = timeToMinutes(startPeriod.startTime)
  const endMinutes = timeToMinutes(endPeriod.endTime)
  if (currentMinutes < startMinutes) return 'upcoming'
  if (currentMinutes <= endMinutes) return 'ongoing'
  return 'finished'
}

export function periodRangesOverlap(left: CourseSchedule, right: CourseSchedule): boolean {
  return left.startPeriod <= right.endPeriod && right.startPeriod <= left.endPeriod
}

export function getOverlappingWeeks(
  left: CourseSchedule,
  right: CourseSchedule,
): number[] {
  const firstWeek = Math.max(left.startWeek, right.startWeek)
  const lastWeek = Math.min(left.endWeek, right.endWeek)
  if (firstWeek > lastWeek) return []
  const weeks: number[] = []
  for (let week = firstWeek; week <= lastWeek; week += 1) {
    if (scheduleAppliesInWeek(left, week) && scheduleAppliesInWeek(right, week)) weeks.push(week)
  }
  return weeks
}

export function schedulesConflict(left: CourseSchedule, right: CourseSchedule): boolean {
  return (
    left.dayOfWeek === right.dayOfWeek &&
    periodRangesOverlap(left, right) &&
    getOverlappingWeeks(left, right).length > 0
  )
}

export function findCourseConflicts(candidate: Course, existingCourses: Course[]): ScheduleConflict[] {
  const conflicts: ScheduleConflict[] = []
  for (const candidateSchedule of candidate.schedules) {
    for (const existingCourse of existingCourses) {
      if (existingCourse.id === candidate.id) continue
      for (const existingSchedule of existingCourse.schedules) {
        if (!schedulesConflict(candidateSchedule, existingSchedule)) continue
        conflicts.push({
          candidateCourse: candidate,
          candidateSchedule,
          existingCourse,
          existingSchedule,
          weeks: getOverlappingWeeks(candidateSchedule, existingSchedule),
        })
      }
    }
  }
  return conflicts
}

export function getOccurrenceTimeLabel(occurrence: CourseOccurrence, semester: Semester): string {
  const start = semester.periods.find((period) => period.number === occurrence.schedule.startPeriod)
  const end = semester.periods.find((period) => period.number === occurrence.schedule.endPeriod)
  if (!start || !end) return `第 ${occurrence.schedule.startPeriod}–${occurrence.schedule.endPeriod} 节`
  return `${start.startTime}–${end.endTime}`
}

export function getWeekRangeLabel(semester: Semester, week: number): string {
  const monday = getDateForTeachingWeek(semester, week, 1)
  const sunday = getDateForTeachingWeek(semester, week, 7)
  const sameMonth = monday.getMonth() === sunday.getMonth()
  const mondayText = `${monday.getMonth() + 1}月${monday.getDate()}日`
  const sundayText = sameMonth
    ? `${sunday.getDate()}日`
    : `${sunday.getMonth() + 1}月${sunday.getDate()}日`
  return `${mondayText}—${sundayText}`
}

export function createId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}
