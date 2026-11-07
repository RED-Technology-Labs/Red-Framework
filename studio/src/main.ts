import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import PrimeVue from 'primevue/config'
import Aura from '@primeuix/themes/aura'
import { api } from './api'
import { currentUser, refreshSession } from './session'
import App from './App.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/setup', component: () => import('./pages/SetupPage.vue') },
    { path: '/login', component: () => import('./pages/LoginPage.vue') },
    { path: '/unavailable', component: () => import('./pages/UnavailablePage.vue') },
    { path: '/', component: () => import('./pages/OverviewPage.vue') },
    { path: '/applications', component: () => import('./pages/ApplicationsPage.vue') },
    { path: '/sites', component: () => import('./pages/SitesPage.vue') },
    { path: '/users', component: () => import('./pages/UsersPage.vue') },
    { path: '/entities', component: () => import('./pages/EntitiesPage.vue') },
    { path: '/records', component: () => import('./pages/RecordsPage.vue') },
    { path: '/desk', component: () => import('./pages/DeskPage.vue') },
  ],
})

router.beforeEach(async (to) => {
  if (to.path === '/unavailable') return true
  let initialized: boolean
  try { initialized = (await api.setupStatus()).initialized }
  catch { return '/unavailable' }
  if (!initialized) return to.path === '/setup' ? true : '/setup'
  if (to.path === '/setup') return '/login'
  const authenticated = await refreshSession()
  if (to.path === '/login') return authenticated ? '/desk' : true
  if (!authenticated) return '/login'
  const adminRoutes = ['/', '/applications', '/entities', '/records', '/sites', '/users']
  if (adminRoutes.includes(to.path) && !currentUser.value?.administrator) return '/desk'
  return true
})

createApp(App).use(PrimeVue, { theme: { preset: Aura, options: { darkModeSelector: 'none' } } }).use(router).mount('#app')
