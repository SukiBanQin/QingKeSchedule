<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import CourseForm from '../components/CourseForm.vue'
import { useSchedule } from '../composables/useSchedule'
import { createId, findCourseConflicts } from '../domain/rules'
import { COURSE_COLORS, type Course } from '../domain/types'
import { validateCourse, type ValidationIssue } from '../domain/validation'
import { cloneData } from '../utils/clone'

const route = useRoute()
const router = useRouter()
const store = useSchedule()
const semester = computed(() => store.semester.value)
const editingId = computed(() => (typeof route.params.courseId === 'string' ? route.params.courseId : null))
const existingCourse = computed(() => store.courses.value.find((course) => course.id === editingId.value))
const isEditing = computed(() => Boolean(editingId.value))

const firstPeriod = semester.value?.periods[0]?.number ?? 1
const initialCourse: Course = existingCourse.value
  ? cloneData(existingCourse.value)
  : {
      id: createId(),
      name: '',
      teacher: '',
      color: COURSE_COLORS[0],
      schedules: [
        {
          id: createId(),
          dayOfWeek: 1,
          startPeriod: firstPeriod,
          endPeriod: firstPeriod,
          startWeek: 1,
          endWeek: semester.value?.totalWeeks ?? 18,
          repeat: 'every',
          classroom: '',
        },
      ],
    }

const issues = ref<ValidationIssue[]>([])
const candidate = ref<Course | null>(null)
const conflictDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const conflicts = computed(() =>
  candidate.value ? findCourseConflicts(candidate.value, store.courses.value) : [],
)
const conflictDescription = computed(() => {
  const names = [...new Set(conflicts.value.map((conflict) => conflict.existingCourse.name))]
  return `与 ${names.join('、')} 的时间重叠。冲突只会被标记，不会阻止保存。`
})

const persist = async (course: Course) => {
  if (!store.saveCourse(course)) return
  conflictDialogOpen.value = false
  await router.push('/')
}

const submit = async (course: Course) => {
  if (!semester.value) return
  issues.value = validateCourse(course, semester.value)
  if (issues.value.length) return
  candidate.value = course
  if (findCourseConflicts(course, store.courses.value).length) {
    conflictDialogOpen.value = true
    return
  }
  await persist(course)
}

const confirmConflict = async () => {
  if (candidate.value) await persist(candidate.value)
}

const deleteCourse = async () => {
  if (!editingId.value) return
  deleteDialogOpen.value = false
  if (store.deleteCourse(editingId.value)) await router.push('/')
}
</script>

<template>
  <section
    v-if="semester && (!isEditing || existingCourse)"
    class="editor-page page-wrap"
  >
    <header class="page-heading editor-heading">
      <div>
        <RouterLink
          class="back-link"
          to="/"
        >
          ← 返回周课表
        </RouterLink>
        <span class="eyebrow">{{ isEditing ? '编辑课程' : '新建课程' }}</span>
        <h1>{{ isEditing ? existingCourse?.name : '添加一门课程' }}</h1>
        <p>{{ isEditing ? '修改会应用到这门课程的全部上课安排。' : '把一门课程的所有上课安排一次填完整。' }}</p>
      </div>
      <button
        v-if="isEditing"
        class="secondary-button danger-outline"
        type="button"
        @click="deleteDialogOpen = true"
      >
        删除课程
      </button>
    </header>

    <CourseForm
      :course="initialCourse"
      :semester="semester"
      :issues="issues"
      :submit-label="isEditing ? '保存修改' : '保存课程'"
      @submit="submit"
    />

    <ConfirmDialog
      :open="conflictDialogOpen"
      title="检测到时间冲突"
      :description="conflictDescription"
      confirm-label="仍然保存"
      @cancel="conflictDialogOpen = false"
      @confirm="confirmConflict"
    />
    <ConfirmDialog
      :open="deleteDialogOpen"
      title="删除这门课程？"
      description="课程及其所有上课安排都会被删除，这项操作无法撤销。"
      confirm-label="确认删除"
      tone="danger"
      @cancel="deleteDialogOpen = false"
      @confirm="deleteCourse"
    />
  </section>

  <section
    v-else-if="isEditing"
    class="centered-state page-wrap"
  >
    <span
      class="empty-symbol"
      aria-hidden="true"
    >?</span>
    <h1>没有找到这门课程</h1>
    <p>它可能已被删除，或链接已经失效。</p>
    <RouterLink
      class="primary-button"
      to="/"
    >
      返回周课表
    </RouterLink>
  </section>
</template>
