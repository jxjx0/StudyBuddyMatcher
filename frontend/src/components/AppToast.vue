<script setup>
import { Check, CircleAlert } from 'lucide-vue-next'
import { useAppState } from '../composables/useAppState'

// Shown on both the login page and inside the app, so a message survives the move between them.
const { toastMessage, toastType } = useAppState()
</script>

<template>
<div v-if="toastMessage" class="toast" :class="{ 'toast-error': toastType === 'error' }"
  :role="toastType === 'error' ? 'alert' : 'status'">
  <CircleAlert v-if="toastType === 'error'" class="toast-icon" :size="16"/><Check v-else class="toast-check" :size="16"/>
  {{ toastMessage }}
</div>
</template>
