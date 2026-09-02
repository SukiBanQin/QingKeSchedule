<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  getDateForTeachingWeek,
  getOccurrencesForWeek,
  periodRangesOverlap,
  toLocalDateString,
} from '../domain/rules'
import { DAY_NAMES, type Course, type CourseOccurrence, type Semester } from '../domain/types'

const props = defineProps<{
  semester: Semester
  courses: Course[]
  week: number
  today: Date
}>()

const router = useRouter()
const occurrences = computed(() =>
  getOccurrencesForWeek(props.courses, props.week).sort(
    (left, right) =>
      left.schedule.dayOfWeek - right.schedule.dayOfWeek ||
      left.schedule.startPeriod - right.schedule.startPeriod ||
      left.course.name.localeCompare(right.course.name, 'zh-CN'),
  ),
)

const periodIndex = (number: number) => props.semester.periods.findIndex((period) => period.number === number)

const cardStyle = (occurrence: CourseOccurrence) => {
  const overlapping = occurrences.value.filter(
    (item) =>
      item.schedule.dayOfWeek === occurrence.schedule.dayOfWeek &&
      periodRangesOverlap(item.schedule, occurrence.schedule),
  )
  const lane = overlapping.findIndex((item) => item.schedule.id === occurrence.schedule.id)
  const lanes = Math.max(overlapping.length, 1)
  return {
    gridColumn: occurrence.schedule.dayOfWeek + 1,
    gridRow: `${periodIndex(occurrence.schedule.startPeriod) + 2} / ${periodIndex(occurrence.schedule.endPeriod) + 3}`,
    width: `calc(${100 / lanes}% - 6px)`,
    marginLeft: `calc(${lane * (100 / lanes)}% + 3px)`,
    backgroundColor: occurrence.course.color,
  }
}

const isConflicting = (occurrence: CourseOccurrence) =>
  occurrences.value.some(
    (item) =>
      item.schedule.id !== occurrence.schedule.id &&
      item.schedule.dayOfWeek === occurrence.schedule.dayOfWeek &&
      periodRangesOverlap(item.schedule, occurrence.schedule),
  )

const dayDate = (index: number) => getDateForTeachingWeek(props.semester, props.week, (index + 1) as 1 | 2 | 3 | 4 | 5 | 6 | 7)
const isToday = (index: number) => toLocalDateString(dayDate(index)) === toLocalDateString(props.today)
const editCourse = (courseId: string) => router.push(`/courses/${courseId}/edit`)
</script>

<template>
  <div
    class="timetable-scroll"
    tabindex="0"
    aria-label="周课表，可横向滚动"
  >
    <div
      class="timetable-grid"
      :style="{ gridTemplateRows: `60px repeat(${semester.periods.length}, 78px)` }"
    >
      <div class="grid-corner">
        <span>时间</span>
        <span>星期</span>
      </div>
      <div
        v-for="(dayName, index) in DAY_NAMES"
        :key="dayName"
        class="day-heading"
        :class="{ 'is-today': isToday(index) }"
        :style="{ gridColumn: index + 2, gridRow: 1 }"
      >
        <span>{{ dayName }}</span>
        <b>{{ dayDate(index).getMonth() + 1 }}/{{ dayDate(index).getDate() }}</b>
        <em v-if="isToday(index)">今天</em>
      </div>

      <template
        v-for="(period, periodPosition) in semester.periods"
        :key="period.number"
      >
        <div
          class="period-heading"
          :style="{ gridColumn: 1, gridRow: periodPosition + 2 }"
        >
          <b>{{ period.number }}</b>
          <span>{{ period.startTime }}<br>{{ period.endTime }}</span>
        </div>
        <div
          v-for="day in 7"
          :key="`${period.number}-${day}`"
          class="grid-cell"
          :class="{ 'is-today': isToday(day - 1) }"
          :style="{ gridColumn: day + 1, gridRow: periodPosition + 2 }"
        />
      </template>

      <button
        v-for="occurrence in occurrences"
        :key="occurrence.schedule.id"
        class="course-card"
        :class="{ 'is-conflicting': isConflicting(occurrence) }"
        :style="cardStyle(occurrence)"
        type="button"
        :aria-label="`编辑 ${occurrence.course.name}，${occurrence.schedule.classroom || '未填写教室'}`"
        @click="editCourse(occurrence.course.id)"
      >
        <strong>{{ occurrence.course.name }}</strong>
        <span>{{ occurrence.schedule.classroom || '教室待定' }}</span>
        <small>第 {{ occurrence.schedule.startPeriod }}–{{ occurrence.schedule.endPeriod }} 节</small>
        <em v-if="isConflicting(occurrence)">冲突</em>
      </button>
    </div>
  </div>
</template>
