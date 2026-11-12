<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
  message?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <div v-if="open" class="overlay" @click.self="emit('cancel')">
    <div class="dialog">
      <h3>{{ title }}</h3>
      <p v-if="message">{{ message }}</p>
      <div class="actions">
        <button class="btn" :disabled="loading" @click="emit('cancel')">{{ cancelText || '取消' }}</button>
        <button
          class="btn"
          :class="danger ? 'btn-danger' : 'btn-primary'"
          :disabled="loading"
          @click="emit('confirm')"
        >
          {{ confirmText || '确定' }}
        </button>
      </div>
    </div>
  </div>
</template>
