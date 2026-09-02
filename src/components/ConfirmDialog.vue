<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description: string
    confirmLabel?: string
    tone?: 'primary' | 'danger'
  }>(),
  { confirmLabel: '确认', tone: 'primary' },
)

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const cancelButton = ref<HTMLButtonElement | null>(null)

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && props.open) emit('cancel')
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      window.addEventListener('keydown', onKeydown)
      await nextTick()
      cancelButton.value?.focus()
    } else {
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="dialog-backdrop"
      @mousedown.self="emit('cancel')"
    >
      <section
        class="confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        aria-describedby="dialog-description"
      >
        <span
          class="dialog-icon"
          :class="`is-${tone}`"
          aria-hidden="true"
        >{{ tone === 'danger' ? '!' : '↗' }}</span>
        <h2 id="dialog-title">
          {{ title }}
        </h2>
        <p id="dialog-description">
          {{ description }}
        </p>
        <div class="dialog-actions">
          <button
            ref="cancelButton"
            class="secondary-button"
            type="button"
            @click="emit('cancel')"
          >
            取消
          </button>
          <button
            :class="tone === 'danger' ? 'danger-button' : 'primary-button'"
            type="button"
            @click="emit('confirm')"
          >
            {{ confirmLabel }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
