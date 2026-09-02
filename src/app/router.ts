import { createRouter, createWebHistory } from 'vue-router'
import { scheduleStore } from '../composables/useSchedule'
import CourseEditorView from '../views/CourseEditorView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import SettingsView from '../views/SettingsView.vue'
import TodayView from '../views/TodayView.vue'
import WeekView from '../views/WeekView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'week', component: WeekView },
    { path: '/today', name: 'today', component: TodayView },
    { path: '/courses/new', name: 'course-new', component: CourseEditorView },
    { path: '/courses/:courseId/edit', name: 'course-edit', component: CourseEditorView },
    { path: '/settings', name: 'settings', component: SettingsView },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
  ],
})

router.beforeEach((to) => {
  scheduleStore.initialize()
  if (!scheduleStore.semester.value && to.name !== 'settings') {
    return { name: 'settings', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
