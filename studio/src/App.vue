<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api'
import { currentUser } from './session'

const route = useRoute()
const router = useRouter()
const isPublic = computed(() => ['/setup', '/login', '/unavailable'].includes(route.path))
const logoutError = ref('')

async function logout() {
  logoutError.value = ''
  try {
    await api.logout()
    currentUser.value = null
    await router.replace('/login')
  } catch { logoutError.value = 'Could not sign out. Please retry.' }
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">RED<span class="brand-dot">.</span><small>TECHNOLOGY LABS</small></RouterLink>
      <nav v-if="!isPublic" aria-label="Main navigation">
        <RouterLink to="/desk">Desk</RouterLink>
        <template v-if="currentUser?.administrator">
          <RouterLink to="/">Overview</RouterLink>
          <RouterLink to="/applications">Applications</RouterLink>
          <RouterLink to="/entities">Entities</RouterLink>
          <RouterLink to="/records">Records</RouterLink>
          <RouterLink to="/sites">Sites</RouterLink>
          <RouterLink to="/users">Users</RouterLink>
        </template>
      </nav>
      <div v-if="currentUser && !isPublic" class="account">
        <span>{{ currentUser.fullName }}</span>
        <button class="text-button" type="button" @click="logout">Sign out</button>
      </div>
    </header>
    <p v-if="logoutError" class="shell-error" role="alert">{{ logoutError }}</p>
    <main :class="{ 'public-main': isPublic }"><RouterView /></main>
  </div>
</template>
