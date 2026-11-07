<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { currentUser } from '../session'

const router = useRouter()
const email = ref('')
const password = ref('')
const visible = ref(false)
const busy = ref(false)
const error = ref('')

async function login() {
  busy.value = true
  error.value = ''
  try {
    const user = await api.login(email.value.trim(), password.value)
    currentUser.value = user
    password.value = ''
    await router.replace(user.administrator ? '/' : '/desk')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : 'Sign in failed'
  } finally { busy.value = false }
}
</script>

<template>
  <section class="auth-card">
    <p class="eyebrow">WELCOME BACK</p>
    <h1>Sign in to RED</h1>
    <p>Use your RED account to open the workspace.</p>
    <form class="stack-form" @submit.prevent="login">
      <label>Email address<input v-model="email" type="email" autocomplete="username" required /></label>
      <label>Password
        <span class="input-with-button">
          <input v-model="password" :type="visible ? 'text' : 'password'" autocomplete="current-password" required />
          <button class="text-button" type="button" :aria-label="visible ? 'Hide password' : 'Show password'" @click="visible = !visible">{{ visible ? 'Hide' : 'Show' }}</button>
        </span>
      </label>
      <p v-if="error" role="alert">{{ error }}</p>
      <button :disabled="busy" type="submit">{{ busy ? 'Signing in…' : 'Sign in' }}</button>
    </form>
  </section>
</template>
