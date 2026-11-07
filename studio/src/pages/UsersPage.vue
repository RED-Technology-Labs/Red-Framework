<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Entity, type EntityAction, type EntityGrant, type ManagedUser } from '../api'

const users = ref<ManagedUser[]>([])
const entities = ref<Entity[]>([])
const selectedUserId = ref('')
const grants = ref<EntityGrant[]>([])
const permissionsBusy = ref(false)
const actions: EntityAction[] = ['READ', 'CREATE', 'UPDATE']
const fullName = ref('')
const email = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
const success = ref('')

async function load() {
  try {
    const [loadedUsers, loadedEntities] = await Promise.all([api.users(), api.entities()])
    users.value = loadedUsers
    entities.value = loadedEntities
  }
  catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not load users' }
}

async function selectUser(userId: string) {
  selectedUserId.value = userId
  grants.value = []
  error.value = ''
  try { grants.value = await api.userPermissions(userId) }
  catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not load permissions' }
}

function hasGrant(entityId: string, action: EntityAction) {
  return grants.value.some((grant) => grant.entityId === entityId && grant.action === action)
}

async function toggleGrant(entityId: string, action: EntityAction) {
  if (!selectedUserId.value || permissionsBusy.value) return
  permissionsBusy.value = true
  error.value = ''
  try {
    if (hasGrant(entityId, action)) await api.revokeEntityPermission(selectedUserId.value, entityId, action)
    else await api.grantEntityPermission(selectedUserId.value, entityId, action)
    grants.value = await api.userPermissions(selectedUserId.value)
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not update permissions' }
  finally { permissionsBusy.value = false }
}

async function create() {
  busy.value = true
  error.value = ''
  success.value = ''
  try {
    const user = await api.createUser(fullName.value, email.value, password.value)
    users.value = [...users.value, user].sort((a, b) => a.email.localeCompare(b.email))
    fullName.value = ''
    email.value = ''
    password.value = ''
    success.value = 'User created. Assignments are not yet available; this account has no access to application data.'
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not create user' }
  finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div><p class="eyebrow">MANAGE</p><h1>Users</h1><p>Create accounts with no default application access.</p></div>
      <button type="button" class="secondary-button" @click="load">Refresh</button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="success" role="status">{{ success }}</p>
    <form class="form-card" @submit.prevent="create">
      <h2>Create user</h2>
      <label>Full name <input v-model.trim="fullName" required minlength="2" maxlength="120" autocomplete="name" /></label>
      <label>Email <input v-model.trim="email" required type="email" autocomplete="email" /></label>
      <label>Temporary password <input v-model="password" required type="password" minlength="12" autocomplete="new-password" /></label>
      <button type="submit" :disabled="busy">{{ busy ? 'Creating…' : 'Create user' }}</button>
    </form>
    <table v-if="users.length">
      <thead><tr><th>Name</th><th>Email</th><th>Status</th></tr></thead>
      <tbody><tr v-for="user in users" :key="user.id"><td>{{ user.fullName }}</td><td>{{ user.email }}</td><td>{{ user.active ? 'Active' : 'Inactive' }}</td></tr></tbody>
    </table>
    <div class="entity-list">
      <h2>Entity permissions</h2>
      <p>Users need Read access to see an Entity in Desk. Create and Update are granted separately.</p>
      <label>User
        <select :value="selectedUserId" @change="selectUser(($event.target as HTMLSelectElement).value)">
          <option value="">Select user</option>
          <option v-for="user in users" :key="user.id" :value="user.id">{{ user.fullName }} — {{ user.email }}</option>
        </select>
      </label>
      <div v-if="selectedUserId" class="table-scroll">
        <table>
          <thead><tr><th>Entity</th><th v-for="action in actions" :key="action">{{ action }}</th></tr></thead>
          <tbody><tr v-for="entity in entities" :key="entity.id">
            <td>{{ entity.label }}</td>
            <td v-for="action in actions" :key="action">
              <input type="checkbox" :checked="hasGrant(entity.id, action)" :disabled="permissionsBusy" :aria-label="`${action} ${entity.label}`" @change="toggleGrant(entity.id, action)" />
            </td>
          </tr></tbody>
        </table>
      </div>
    </div>
  </section>
</template>
