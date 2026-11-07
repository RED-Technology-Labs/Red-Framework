<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'

const status = ref<'loading' | 'online' | 'offline'>('loading')
onMounted(async () => {
  try {
    await api.health()
    status.value = 'online'
  } catch { status.value = 'offline' }
})
</script>

<template>
  <section>
    <h1>Workspace overview</h1>
    <p>Server status: <strong>{{ status }}</strong></p>
    <p v-if="status === 'offline'">Start RED Server to access workspace data.</p>
  </section>
</template>
