<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useSchedule } from './composables/useSchedule'

const store = useSchedule()
const ready = computed(() => store.state.loaded)
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink
        class="brand"
        to="/"
        aria-label="轻课表首页"
      >
        <span
          class="brand-mark"
          aria-hidden="true"
        >轻</span>
        <span>轻课表</span>
      </RouterLink>
      <nav
        class="main-nav"
        aria-label="主导航"
      >
        <RouterLink to="/">
          周课表
        </RouterLink>
        <RouterLink to="/today">
          今日课程
        </RouterLink>
        <RouterLink to="/settings">
          学期设置
        </RouterLink>
      </nav>
      <RouterLink
        v-if="store.semester.value"
        class="header-add-button"
        to="/courses/new"
      >
        ＋ 添加课程
      </RouterLink>
    </header>
    <div
      v-if="store.state.error"
      class="error-banner"
      role="alert"
    >
      <span>{{ store.state.error }}</span>
      <button
        type="button"
        aria-label="关闭错误提示"
        @click="store.dismissError"
      >
        ×
      </button>
    </div>
    <main v-if="ready">
      <RouterView />
    </main>
    <nav
      v-if="store.semester.value"
      class="mobile-nav"
      aria-label="移动端导航"
    >
      <RouterLink to="/">
        <span aria-hidden="true">▦</span>周课表
      </RouterLink>
      <RouterLink to="/today">
        <span aria-hidden="true">◉</span>今日
      </RouterLink>
      <RouterLink
        to="/courses/new"
        class="mobile-add"
      >
        <span aria-hidden="true">＋</span>添加
      </RouterLink>
      <RouterLink to="/settings">
        <span aria-hidden="true">⚙</span>设置
      </RouterLink>
    </nav>
  </div>
</template>
