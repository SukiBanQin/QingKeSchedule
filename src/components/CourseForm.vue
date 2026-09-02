<script setup lang="ts">
import { reactive, watch } from 'vue'
import { createId } from '../domain/rules'
import {
  COURSE_COLORS,
  DAY_NAMES,
  REPEAT_LABELS,
  type Course,
  type DayOfWeek,
  type Semester,
} from '../domain/types'
import type { ValidationIssue } from '../domain/validation'
import { cloneData } from '../utils/clone'

const props = defineProps<{
  course: Course
  semester: Semester
  issues: ValidationIssue[]
  submitLabel: string
}>()

const emit = defineEmits<{
  submit: [course: Course]
}>()

const cloneCourse = (course: Course): Course => cloneData(course)
const form = reactive<Course>(cloneCourse(props.course))

watch(
  () => props.course,
  (course) => Object.assign(form, cloneCourse(course)),
  { deep: true },
)

const addSchedule = () => {
  const firstPeriod = props.semester.periods[0]?.number ?? 1
  form.schedules.push({
    id: createId(),
    dayOfWeek: 1,
    startPeriod: firstPeriod,
    endPeriod: firstPeriod,
    startWeek: 1,
    endWeek: props.semester.totalWeeks,
    repeat: 'every',
    classroom: '',
  })
}

const removeSchedule = (index: number) => {
  if (form.schedules.length > 1) form.schedules.splice(index, 1)
}

const hasIssue = (pathPrefix: string) => props.issues.some((issue) => issue.path.startsWith(pathPrefix))

const submit = () => {
  emit('submit', {
    ...cloneData(form),
    name: form.name.trim(),
    teacher: form.teacher.trim(),
    schedules: form.schedules.map((schedule) => ({
      ...schedule,
      classroom: schedule.classroom.trim(),
    })),
  })
}
</script>

<template>
  <form
    class="course-form"
    novalidate
    @submit.prevent="submit"
  >
    <section class="surface-card form-section">
      <div class="section-title-row">
        <div>
          <span class="section-kicker">课程信息</span>
          <h2>这门课叫什么？</h2>
        </div>
        <span
          class="section-number"
          aria-hidden="true"
        >01</span>
      </div>

      <div class="form-grid course-basics">
        <label
          class="field span-2"
          :class="{ 'has-error': hasIssue('name') }"
        >
          <span>课程名称 <b aria-hidden="true">*</b></span>
          <input
            v-model="form.name"
            name="courseName"
            autocomplete="off"
            placeholder="例如：数据结构"
          >
        </label>
        <label class="field">
          <span>任课教师</span>
          <input
            v-model="form.teacher"
            name="teacher"
            autocomplete="off"
            placeholder="例如：陈老师"
          >
        </label>
        <fieldset class="color-field">
          <legend>课程颜色</legend>
          <div class="color-options">
            <label
              v-for="color in COURSE_COLORS"
              :key="color"
              class="color-option"
              :style="{ backgroundColor: color }"
            >
              <input
                v-model="form.color"
                type="radio"
                name="color"
                :value="color"
              >
              <span class="sr-only">选择颜色 {{ color }}</span>
              <span
                v-if="form.color === color"
                aria-hidden="true"
              >✓</span>
            </label>
          </div>
        </fieldset>
      </div>
    </section>

    <section class="surface-card form-section schedules-section">
      <div class="section-title-row">
        <div>
          <span class="section-kicker">上课安排</span>
          <h2>什么时候、在哪里上课？</h2>
          <p>同一门课程可以添加多个上课安排。</p>
        </div>
        <span
          class="section-number"
          aria-hidden="true"
        >02</span>
      </div>

      <div class="schedule-list">
        <fieldset
          v-for="(schedule, index) in form.schedules"
          :key="schedule.id"
          class="schedule-block"
        >
          <legend>安排 {{ index + 1 }}</legend>
          <button
            v-if="form.schedules.length > 1"
            class="text-button schedule-remove"
            type="button"
            :aria-label="`删除安排 ${index + 1}`"
            @click="removeSchedule(index)"
          >
            删除
          </button>

          <div class="schedule-grid">
            <label class="field">
              <span>星期</span>
              <select
                v-model.number="schedule.dayOfWeek"
                :name="`day-${index}`"
              >
                <option
                  v-for="(name, dayIndex) in DAY_NAMES"
                  :key="name"
                  :value="(dayIndex + 1) as DayOfWeek"
                >{{ name }}</option>
              </select>
            </label>
            <label class="field">
              <span>教室</span>
              <input
                v-model="schedule.classroom"
                :name="`classroom-${index}`"
                autocomplete="off"
                placeholder="例如：博学楼 A201"
              >
            </label>
            <label
              class="field"
              :class="{ 'has-error': hasIssue(`schedules.${index}.periods`) }"
            >
              <span>开始节次</span>
              <select
                v-model.number="schedule.startPeriod"
                :name="`startPeriod-${index}`"
              >
                <option
                  v-for="period in semester.periods"
                  :key="period.number"
                  :value="period.number"
                >
                  第 {{ period.number }} 节 · {{ period.startTime }}
                </option>
              </select>
            </label>
            <label
              class="field"
              :class="{ 'has-error': hasIssue(`schedules.${index}.periods`) }"
            >
              <span>结束节次</span>
              <select
                v-model.number="schedule.endPeriod"
                :name="`endPeriod-${index}`"
              >
                <option
                  v-for="period in semester.periods"
                  :key="period.number"
                  :value="period.number"
                >
                  第 {{ period.number }} 节 · {{ period.endTime }}
                </option>
              </select>
            </label>
            <label
              class="field"
              :class="{ 'has-error': hasIssue(`schedules.${index}.weeks`) }"
            >
              <span>开始周</span>
              <div class="input-suffix"><input
                v-model.number="schedule.startWeek"
                :name="`startWeek-${index}`"
                type="number"
                min="1"
                :max="semester.totalWeeks"
              ><span>周</span></div>
            </label>
            <label
              class="field"
              :class="{ 'has-error': hasIssue(`schedules.${index}.weeks`) }"
            >
              <span>结束周</span>
              <div class="input-suffix"><input
                v-model.number="schedule.endWeek"
                :name="`endWeek-${index}`"
                type="number"
                min="1"
                :max="semester.totalWeeks"
              ><span>周</span></div>
            </label>
            <label class="field span-2">
              <span>重复规则</span>
              <select
                v-model="schedule.repeat"
                :name="`repeat-${index}`"
              >
                <option
                  v-for="(label, value) in REPEAT_LABELS"
                  :key="value"
                  :value="value"
                >{{ label }}</option>
              </select>
            </label>
          </div>
        </fieldset>
      </div>

      <button
        class="dashed-button"
        type="button"
        @click="addSchedule"
      >
        <span aria-hidden="true">＋</span> 添加另一个上课安排
      </button>
    </section>

    <div
      v-if="issues.length"
      class="validation-summary"
      role="alert"
    >
      <strong>还有几处需要调整</strong>
      <ul>
        <li
          v-for="issue in issues"
          :key="`${issue.path}-${issue.message}`"
        >
          {{ issue.message }}
        </li>
      </ul>
    </div>

    <div class="editor-actions">
      <RouterLink
        class="secondary-button"
        to="/"
      >
        取消
      </RouterLink>
      <button
        class="primary-button"
        type="submit"
      >
        {{ submitLabel }}
      </button>
    </div>
  </form>
</template>
