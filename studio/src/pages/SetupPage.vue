<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const step = ref<'welcome' | 'administrator'>('welcome')
const language = ref<'en' | 'ar'>('en')
const theme = ref<'light' | 'dark'>('light')
const databaseStatus = ref<'checking' | 'connected' | 'unavailable'>('checking')
const fullName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const siteName = ref('')
const busy = ref(false)
const error = ref('')

onMounted(async () => {
  try { await api.health(); databaseStatus.value = 'connected' }
  catch { databaseStatus.value = 'unavailable' }
})

async function initialize() {
  error.value = ''
  if (password.value !== confirmPassword.value) { error.value = 'Passwords do not match'; return }
  busy.value = true
  try {
    await api.initialize({ fullName: fullName.value.trim(), email: email.value.trim(), password: password.value, confirmPassword: confirmPassword.value, siteName: siteName.value.trim() })
    password.value = ''
    confirmPassword.value = ''
    await router.replace('/login')
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Setup failed' }
  finally { busy.value = false }
}
</script>

<template>
  <section class="setup-card" :dir="language === 'ar' ? 'rtl' : 'ltr'" :data-theme="theme">
    <div class="setup-banner"><span class="setup-mark">R<span>.</span></span><small>RED TECHNOLOGY LABS</small></div>
    <template v-if="step === 'welcome'">
      <p class="eyebrow">RED FRAMEWORK · FIRST RUN</p>
      <h1>{{ language === 'ar' ? 'مرحبًا بك في RED' : 'Welcome to RED' }}</h1>
      <p>{{ language === 'ar' ? 'ابدأ بإنشاء حساب المسؤول وموقعك الأول.' : 'Create your administrator account and first site to initialize this installation.' }}</p>
      <div class="setup-preferences">
        <label>{{ language === 'ar' ? 'اللغة' : 'Language' }}
          <select v-model="language"><option value="en">English</option><option value="ar">العربية</option></select>
        </label>
        <label>{{ language === 'ar' ? 'المظهر' : 'Appearance' }}
          <select v-model="theme"><option value="light">Light</option><option value="dark">Dark</option></select>
        </label>
      </div>
      <p class="muted">Language and appearance selections preview this setup screen only. Site-wide preferences are not configured yet.</p>
      <div class="database-status" :class="databaseStatus">
        <span class="status-dot" />
        {{ databaseStatus === 'checking' ? 'Checking PostgreSQL…' : databaseStatus === 'connected' ? 'PostgreSQL connected' : 'PostgreSQL unavailable — check DATABASE_URL on the server' }}
      </div>
      <p class="muted">Database configuration is supplied to RED Server through <code>DATABASE_URL</code>. This setup flow does not change database credentials.</p>
      <button type="button" :disabled="databaseStatus !== 'connected'" @click="step = 'administrator'">{{ language === 'ar' ? 'ابدأ الإعداد' : 'Start setup' }}</button>
    </template>
    <template v-else>
      <button class="text-button back-link" type="button" @click="step = 'welcome'">← Back</button>
      <p class="eyebrow">STEP 1 · INITIALIZE</p>
      <h1>Administrator and first site</h1>
      <p>This account receives administrator access to the installation.</p>
      <form class="stack-form" @submit.prevent="initialize">
        <label>Full name<input v-model="fullName" autocomplete="name" required minlength="2" maxlength="120" /></label>
        <label>Email address<input v-model="email" type="email" autocomplete="username" required /></label>
        <label>Password<input v-model="password" type="password" autocomplete="new-password" required minlength="12" /></label>
        <label>Confirm password<input v-model="confirmPassword" type="password" autocomplete="new-password" required minlength="12" /></label>
        <label>First site name<input v-model="siteName" required pattern="[a-z][a-z0-9-]{1,61}[a-z0-9]" placeholder="my-site" /></label>
        <p class="muted">Use at least 12 characters with upper and lower case letters, a number, and a symbol.</p>
        <p v-if="error" role="alert">{{ error }}</p>
        <button :disabled="busy" type="submit">{{ busy ? 'Initializing…' : 'Initialize RED' }}</button>
      </form>
    </template>
  </section>
</template>
