<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Application } from '../api'

const applications = ref<Application[]>([])
const canonicalName = ref('')
const version = ref('1.0.0')
const loading = ref(true)
const saving = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try { applications.value = await api.applications() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : 'Failed to load applications' }
  finally { loading.value = false }
}

async function create() {
  saving.value = true
  error.value = ''
  try {
    await api.createApplication(canonicalName.value, version.value)
    canonicalName.value = ''
    await load()
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Failed to create application' }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section>
    <h1>Applications</h1>
    <form @submit.prevent="create">
      <label>Canonical name <input v-model="canonicalName" required pattern="[a-z][a-z0-9-]{1,61}[a-z0-9]" placeholder="my-application" /></label>
      <label>Version <input v-model="version" required pattern="[0-9]+\.[0-9]+\.[0-9]+" /></label>
      <button :disabled="saving">{{ saving ? 'Creating…' : 'Create application' }}</button>
    </form>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading">Loading…</p>
    <p v-else-if="applications.length === 0">No applications registered yet.</p>
    <table v-else><thead><tr><th>Name</th><th>Version</th><th>ID</th></tr></thead><tbody>
      <tr v-for="application in applications" :key="application.id"><td>{{ application.canonicalName }}</td><td>{{ application.version }}</td><td><code>{{ application.id }}</code></td></tr>
    </tbody></table>
  </section>
</template>
