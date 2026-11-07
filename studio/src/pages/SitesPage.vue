<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Site } from '../api'

const sites = ref<Site[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try { sites.value = await api.sites() }
  catch (failure) { error.value = failure instanceof Error ? failure.message : 'Failed to load sites' }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">MANAGE</p>
        <h1>Sites</h1>
        <p>Sites registered in this RED installation.</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading" @click="load">Refresh</button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">Loading sites…</p>
    <p v-else-if="sites.length === 0">No sites are registered. Complete initial setup to create the first site.</p>
    <table v-else>
      <thead><tr><th>Name</th><th>Status</th><th>ID</th></tr></thead>
      <tbody>
        <tr v-for="site in sites" :key="site.id">
          <td><strong>{{ site.canonicalName }}</strong></td>
          <td><span class="site-status" :class="site.status.toLowerCase()">{{ site.status }}</span></td>
          <td><code>{{ site.id }}</code></td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
