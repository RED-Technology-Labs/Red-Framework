<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type DeskEntity, type EntityRecord, type EntityAttribute, type RecordAuditEvent, type RecordValue } from '../api'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'

const entities = ref<DeskEntity[]>([])
const selectedId = ref('')
const rows = ref<EntityRecord[]>([])
const search = ref('')
const sortDescending = ref(false)
const page = ref(1)
const pageSize = 20
const editing = ref<EntityRecord | null>(null)
const form = ref<Record<string, string>>({})
const busy = ref(false)
const error = ref('')
const message = ref('')
const audit = ref<RecordAuditEvent[]>([])
const auditRecordId = ref('')
const selected = computed(() => entities.value.find((entry) => entry.definition.id === selectedId.value))
const fields = computed(() => selected.value?.definition.attributes ?? [])
const listFields = computed(() => {
  const names = selected.value?.definition.listAttributes ?? []
  return names.length ? fields.value.filter((field) => names.includes(field.name)) : fields.value
})
const formTabs = computed(() => {
  const configured = selected.value?.definition.formTabs ?? []
  const assigned = new Set(configured.flatMap((tab) => tab.fields))
  const remaining = fields.value.filter((field) => !assigned.has(field.name)).map((field) => field.name)
  const fallbackName = configured.some((tab) => tab.name === 'general') ? 'other_fields' : 'general'
  return [...configured, ...(remaining.length || !configured.length ? [{ name: fallbackName, label: 'General', fields: remaining }] : [])]
})
function tabFields(names: string[]) { return fields.value.filter((field) => names.includes(field.name)) }
function recordTitle(row: EntityRecord) {
  const titleField = selected.value?.definition.titleAttribute
  return titleField ? display(row.values[titleField]) : row.name ?? row.id
}
const filtered = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  const result = rows.value.filter((record) => !query ||
    Object.values(record.values).some((value) => String(value ?? '').toLocaleLowerCase().includes(query)))
  return sortDescending.value ? [...result].reverse() : result
})
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const visibleRows = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

async function loadEntities() {
  error.value = ''
  try {
    entities.value = await api.deskEntities()
    if (selectedId.value && !entities.value.some((entry) => entry.definition.id === selectedId.value)) selectedId.value = ''
    if (!selectedId.value && entities.value.length) await selectEntity(entities.value[0].definition.id)
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not load Desk' }
}

async function selectEntity(id: string) {
  selectedId.value = id
  page.value = 1
  search.value = ''
  editing.value = null
  audit.value = []
  auditRecordId.value = ''
  error.value = ''
  rows.value = []
  try { rows.value = await api.records(id) }
  catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not load records' }
}

function startNew() {
  editing.value = null
  form.value = {}
  message.value = ''
}

function startEdit(row: EntityRecord) {
  editing.value = row
  form.value = Object.fromEntries(fields.value.map((field) => {
    const value = row.values[field.name]
    const text = value == null ? '' : field.typeId === 'DATETIME' && typeof value === 'string'
      ? value.slice(0, 19) : String(value)
    return [field.name, text]
  }))
  message.value = ''
}

function encode(field: EntityAttribute, raw: string): RecordValue {
  if (!raw) {
    if (field.required) throw new Error(`${field.label} is required`)
    return null
  }
  if (field.typeId === 'BOOLEAN') return raw === 'true'
  if (field.typeId === 'INTEGER') {
    const number = Number(raw)
    if (!Number.isInteger(number) || number < -2147483648 || number > 2147483647) throw new Error(`${field.label} must be a 32-bit integer`)
    return number
  }
  if (field.typeId === 'DECIMAL') {
    const number = Number(raw)
    if (!Number.isFinite(number)) throw new Error(`${field.label} must be a number`)
    return number
  }
  if (field.typeId === 'DATETIME') {
    const value = new Date(raw)
    if (Number.isNaN(value.getTime())) throw new Error(`${field.label} has an invalid date and time`)
    return value.toISOString()
  }
  return raw
}

async function save() {
  if (!selected.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const values = Object.fromEntries(fields.value.map((field) => [field.name, encode(field, form.value[field.name] ?? '')]))
    if (editing.value) {
      if (!selected.value.canUpdate) throw new Error('Update permission required')
      const saved = await api.updateRecord(selectedId.value, editing.value.id, editing.value.version, values)
      rows.value = rows.value.map((row) => row.id === saved.id ? saved : row)
      editing.value = saved
      message.value = 'Record updated.'
    } else {
      if (!selected.value.canCreate) throw new Error('Create permission required')
      const saved = await api.createRecord(selectedId.value, values)
      rows.value = [...rows.value, saved]
      form.value = {}
      message.value = 'Record created.'
    }
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not save record' }
  finally { busy.value = false }
}

function display(value: RecordValue | undefined) {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

async function showAudit(row: EntityRecord) {
  auditRecordId.value = row.id
  audit.value = []
  error.value = ''
  try { audit.value = await api.recordAudit(selectedId.value, row.id) }
  catch (failure) { error.value = failure instanceof Error ? failure.message : 'Could not load activity' }
}

onMounted(loadEntities)
</script>

<template>
  <section class="desk-shell">
    <div class="page-heading"><div><p class="eyebrow">WORKSPACE</p><h1>RED Desk</h1><p>Published records you can access.</p></div>
      <button class="secondary-button" type="button" @click="loadEntities">Refresh</button></div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="message" role="status" class="success-message">{{ message }}</p>
    <div class="desk-layout">
      <aside class="desk-sidebar" aria-label="Entities">
        <h2>Entities</h2>
        <p v-if="!entities.length">No published Entities are available to your account.</p>
        <button v-for="entry in entities" :key="entry.definition.id" type="button" class="desk-entity"
          :aria-current="selectedId === entry.definition.id ? 'page' : undefined" @click="selectEntity(entry.definition.id)">{{ entry.definition.label }}</button>
      </aside>
      <div v-if="selected" class="desk-content">
        <h2>{{ selected.definition.label }}</h2>
        <div class="desk-toolbar">
          <label>Search records <input v-model="search" type="search" @input="page = 1" /></label>
          <button class="secondary-button" type="button" @click="sortDescending = !sortDescending; page = 1">{{ sortDescending ? 'Newest first' : 'Oldest first' }}</button>
          <button v-if="selected.canCreate" type="button" @click="startNew">New record</button>
        </div>
        <DataTable :value="visibleRows" striped-rows scrollable class="desk-table">
          <Column header="Record"><template #body="slot">{{ recordTitle(slot.data as EntityRecord) }}</template></Column>
          <Column v-for="field in listFields" :key="field.id" :header="field.label">
            <template #body="slot">{{ display((slot.data as EntityRecord).values[field.name]) }}</template>
          </Column>
          <Column header="Actions"><template #body="slot">
            <Button v-if="selected.canUpdate" label="Edit" text size="small" @click="startEdit(slot.data as EntityRecord)" />
            <Button label="Activity" text size="small" @click="showAudit(slot.data as EntityRecord)" />
          </template></Column>
        </DataTable>
        <p v-if="!filtered.length" class="empty-message">No records found.</p>
        <div v-if="pages > 1" class="desk-pagination">
          <button class="secondary-button" type="button" :disabled="page <= 1" @click="page--">Previous</button>
          <span>Page {{ page }} of {{ pages }}</span>
          <button class="secondary-button" type="button" :disabled="page >= pages" @click="page++">Next</button>
        </div>
        <form v-if="selected.canCreate || (editing && selected.canUpdate)" class="desk-form" @submit.prevent="save">
          <h3>{{ editing ? 'Edit record' : 'New record' }}</h3>
          <Tabs :key="selectedId" :value="formTabs[0]?.name ?? 'general'">
            <TabList><Tab v-for="tab in formTabs" :key="tab.name" :value="tab.name">{{ tab.label }}</Tab></TabList>
            <TabPanels><TabPanel v-for="tab in formTabs" :key="tab.name" :value="tab.name"><div class="record-fields"><label v-for="field in tabFields(tab.fields)" :key="field.id">{{ field.label }}
            <select v-if="field.typeId === 'BOOLEAN'" v-model="form[field.name]">
              <option value="">Unset</option><option value="true">Yes</option><option value="false">No</option>
            </select>
            <input v-else v-model="form[field.name]"
              :type="field.typeId === 'DATE' ? 'date' : field.typeId === 'DATETIME' ? 'datetime-local' : field.typeId === 'EMAIL' ? 'email' : field.typeId === 'INTEGER' || field.typeId === 'DECIMAL' ? 'number' : 'text'"
              :step="field.typeId === 'DECIMAL' ? 'any' : undefined" />
            </label></div></TabPanel></TabPanels>
          </Tabs>
          <button type="submit" :disabled="busy">{{ busy ? 'Saving…' : 'Save record' }}</button>
        </form>
        <div v-if="auditRecordId" class="audit-panel">
          <h3>Record activity</h3>
          <p v-if="!audit.length">No activity entries found.</p>
          <ul v-else><li v-for="event in audit" :key="event.id">{{ event.action }} · version {{ event.recordVersion }} · {{ new Date(event.at).toLocaleString() }}</li></ul>
        </div>
      </div>
    </div>
  </section>
</template>
