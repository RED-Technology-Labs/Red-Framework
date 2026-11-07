<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError, type Entity, type EntityPublication, type EntityRecord, type RecordAuditEvent, type RecordValue } from '../api'

const supportedTypes = new Set(['TEXT', 'EMAIL', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME'])
const entities = ref<Entity[]>([])
const selectedId = ref('')
const publication = ref<EntityPublication | null>(null)
const publishedDefinition = ref<Entity | null>(null)
const records = ref<EntityRecord[]>([])
const values = ref<Record<string, string>>({})
const loading = ref(true)
const selectionLoading = ref(false)
const saving = ref(false)
const error = ref('')
const selectionError = ref('')
const createError = ref('')
const success = ref('')
const editingRecord = ref<EntityRecord | null>(null)
const editValues = ref<Record<string, string>>({})
const editBusy = ref(false)
const editError = ref('')
const editSuccess = ref('')
const editConflict = ref(false)
const historyRecordId = ref('')
const auditEvents = ref<RecordAuditEvent[]>([])
const auditLoading = ref(false)
const auditError = ref('')

const selectedEntity = computed(() => entities.value.find((entity) => entity.id === selectedId.value) ?? null)
const unsupported = computed(() => publishedDefinition.value?.attributes.filter((attribute) => !supportedTypes.has(attribute.typeId)) ?? [])
const hasNewerDraft = computed(() => !!selectedEntity.value && !!publication.value && selectedEntity.value.revision > publication.value.publishedRevision)
const canCreate = computed(() => !!publication.value && !!publishedDefinition.value && unsupported.value.length === 0 && !selectionLoading.value)

async function loadEntities() {
  loading.value = true
  error.value = ''
  try {
    entities.value = await api.entities()
    if (selectedId.value) {
      if (entities.value.some((entity) => entity.id === selectedId.value)) await selectEntity(selectedId.value)
      else { selectedId.value = ''; publication.value = null; publishedDefinition.value = null; records.value = [] }
    }
  }
  catch (failure) { error.value = failure instanceof Error ? failure.message : 'Failed to load Entities' }
  finally { loading.value = false }
}

async function selectEntity(id: string) {
  selectedId.value = id
  publication.value = null
  publishedDefinition.value = null
  records.value = []
  values.value = {}
  selectionError.value = ''
  createError.value = ''
  success.value = ''
  cancelEdit()
  historyRecordId.value = ''
  auditEvents.value = []
  auditError.value = ''
  if (!id) return
  selectionLoading.value = true
  try {
    const published = await api.entityPublication(id)
    if (selectedId.value !== id) return
    publication.value = published
    const entity = selectedEntity.value
    if (!entity) throw new Error('Entity definition no longer exists.')
    const definition = entity.revision === published.publishedRevision
      ? entity
      : (await api.entityRevisions(id)).find((revision) => revision.revision === published.publishedRevision)
    if (!definition) throw new Error('The published revision definition could not be loaded.')
    if (selectedId.value !== id) return
    publishedDefinition.value = definition
    const loadedRecords = await api.records(id)
    if (selectedId.value === id) records.value = loadedRecords
  } catch (failure) {
    if (selectedId.value !== id) return
    if (!(failure instanceof ApiError && failure.status === 404 && publication.value === null))
      selectionError.value = failure instanceof Error ? failure.message : 'Failed to load records'
  } finally { if (selectedId.value === id) selectionLoading.value = false }
}

function encodeValue(raw: string, typeId: string, required: boolean): RecordValue {
  if (raw === '') {
    if (required) throw new Error('Please fill every required field.')
    return null
  }
  if (typeId === 'BOOLEAN') return raw === 'true'
  if (typeId === 'INTEGER') {
    const parsed = Number(raw)
    if (!Number.isSafeInteger(parsed)) throw new Error('Integer values must be whole numbers within the supported range.')
    return parsed
  }
  if (typeId === 'DECIMAL') {
    const parsed = Number(raw)
    if (!Number.isFinite(parsed)) throw new Error('Decimal values must be finite numbers.')
    return parsed
  }
  if (typeId === 'DATETIME') {
    const instant = new Date(raw)
    if (Number.isNaN(instant.getTime())) throw new Error('Date and time is invalid.')
    return instant.toISOString()
  }
  return raw
}

async function create() {
  if (!selectedEntity.value || !publishedDefinition.value || !canCreate.value) return
  const entityId = publishedDefinition.value.id
  saving.value = true
  createError.value = ''
  success.value = ''
  try {
    const encoded = encodeValues(values.value)
    const created = await api.createRecord(entityId, encoded)
    if (selectedId.value === entityId) {
      records.value = [...records.value, created]
      values.value = {}
      success.value = 'Record saved in PostgreSQL.'
    }
  } catch (failure) { createError.value = failure instanceof Error ? failure.message : 'Failed to save record' }
  finally { saving.value = false }
}

function encodeValues(rawValues: Record<string, string>): Record<string, RecordValue> {
  if (!publishedDefinition.value) throw new Error('Published definition is unavailable.')
  const encoded: Record<string, RecordValue> = {}
  for (const attribute of publishedDefinition.value.attributes) {
    encoded[attribute.name] = encodeValue(rawValues[attribute.name] ?? '', attribute.typeId, attribute.required)
  }
  return encoded
}

function localDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const part = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}T${part(date.getHours())}:${part(date.getMinutes())}:${part(date.getSeconds())}`
}

function startEdit(record: EntityRecord) {
  if (!publishedDefinition.value || unsupported.value.length) return
  editingRecord.value = record
  editValues.value = Object.fromEntries(publishedDefinition.value.attributes.map((attribute) => {
    const value = record.values[attribute.name]
    return [attribute.name, value === null || value === undefined ? '' : attribute.typeId === 'DATETIME' && typeof value === 'string' ? localDateTime(value) : String(value)]
  }))
  editError.value = ''
  editSuccess.value = ''
  editConflict.value = false
}

function cancelEdit() {
  editingRecord.value = null
  editValues.value = {}
  editError.value = ''
  editSuccess.value = ''
  editConflict.value = false
}

async function loadAudit(recordId: string) {
  const entityId = selectedId.value
  if (!entityId) return
  historyRecordId.value = recordId
  auditEvents.value = []
  auditError.value = ''
  auditLoading.value = true
  try {
    const loaded = await api.recordAudit(entityId, recordId)
    if (selectedId.value === entityId && historyRecordId.value === recordId) {
      auditEvents.value = [...loaded].sort((left, right) => Date.parse(right.at) - Date.parse(left.at))
    }
  } catch (failure) {
    if (selectedId.value === entityId && historyRecordId.value === recordId)
      auditError.value = failure instanceof Error ? failure.message : 'Failed to load audit history'
  } finally {
    if (selectedId.value === entityId && historyRecordId.value === recordId) auditLoading.value = false
  }
}

function closeAudit() {
  historyRecordId.value = ''
  auditEvents.value = []
  auditError.value = ''
  auditLoading.value = false
}

async function refreshEditing() {
  if (!editingRecord.value || !selectedId.value) return
  const recordId = editingRecord.value.id
  const entityId = selectedId.value
  editBusy.value = true
  editError.value = ''
  try {
    const fresh = await api.records(entityId)
    if (selectedId.value !== entityId) return
    records.value = fresh
    const found = fresh.find((record) => record.id === recordId)
    if (found) startEdit(found)
    else { cancelEdit(); editError.value = 'Record no longer exists.' }
  } catch (failure) { editError.value = failure instanceof Error ? failure.message : 'Could not reload record' }
  finally { editBusy.value = false }
}

async function saveEdit() {
  if (!editingRecord.value || !selectedId.value || !canCreate.value) return
  const entityId = selectedId.value
  const original = editingRecord.value
  editBusy.value = true
  editError.value = ''
  editSuccess.value = ''
  editConflict.value = false
  try {
    const encoded = encodeValues(editValues.value)
    for (const attribute of publishedDefinition.value?.attributes ?? []) {
      const originalValue = original.values[attribute.name]
      if (attribute.typeId === 'DATETIME' && typeof originalValue === 'string' && editValues.value[attribute.name] === localDateTime(originalValue)) {
        encoded[attribute.name] = originalValue
      }
    }
    const updated = await api.updateRecord(entityId, original.id, original.version, encoded)
    if (selectedId.value !== entityId) return
    records.value = records.value.map((record) => record.id === updated.id ? updated : record)
    startEdit(updated)
    editSuccess.value = `Saved record version ${updated.version}.`
    if (historyRecordId.value === updated.id) await loadAudit(updated.id)
  } catch (failure) {
    editConflict.value = failure instanceof ApiError && failure.status === 409
    editError.value = editConflict.value
      ? 'This record changed after you opened it. Reload the latest version before saving again.'
      : failure instanceof Error ? failure.message : 'Failed to update record'
  } finally { editBusy.value = false }
}

function formatValue(value: RecordValue | undefined, typeId: string): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (typeId === 'DATETIME' && typeof value === 'string') {
    const date = new Date(value)
    if (!Number.isNaN(date.getTime())) return date.toLocaleString()
  }
  return String(value)
}

function formatAuditTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

onMounted(loadEntities)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">RED DESK · BUSINESS DATA</p>
        <h1>Records</h1>
        <p>Create, view, and edit records stored in a published Entity table.</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading || selectionLoading || saving || editBusy" @click="loadEntities">Refresh Entities</button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">Loading Entities…</p>
    <template v-else>
      <p v-if="entities.length === 0" class="empty-message">No Entity definitions exist. Create one in <RouterLink to="/entities">Entities</RouterLink>.</p>
      <label v-else class="record-selector">Entity
        <select :value="selectedId" :disabled="saving || selectionLoading || editBusy" @change="selectEntity(($event.target as HTMLSelectElement).value)">
          <option value="">Select an Entity</option>
          <option v-for="entity in entities" :key="entity.id" :value="entity.id">{{ entity.label }} ({{ entity.name }})</option>
        </select>
      </label>
      <template v-if="selectedEntity">
        <p v-if="selectionLoading" role="status">Loading publication and records…</p>
        <p v-if="selectionError" role="alert">{{ selectionError }}</p>
        <p v-if="success" role="status" class="success-message">{{ success }}</p>
        <template v-if="!selectionLoading && !selectionError">
          <p v-if="!publication" class="empty-message">This Entity has not been published. Publish its first revision in <RouterLink to="/entities">Entity definitions</RouterLink>.</p>
          <p v-else-if="!publishedDefinition" class="empty-message">The published definition could not be loaded. Records are unavailable until its revision history is accessible.</p>
          <template v-else>
            <p class="muted">Published revision {{ publication.publishedRevision }} · Table <code>{{ publication.tableName }}</code></p>
            <p v-if="hasNewerDraft" class="published-draft-note">The active draft is revision {{ selectedEntity.revision }}. This page displays existing records and creates new ones using published revision {{ publication.publishedRevision }}. Draft changes are not applied to this table.</p>
            <p v-if="unsupported.length" class="empty-message">Record creation and editing are unavailable because these attributes have unsupported form types: {{ unsupported.map((attribute) => `${attribute.name} (${attribute.typeId})`).join(', ') }}.</p>
            <form v-else class="record-form" @submit.prevent="create">
              <h2>New record · published revision {{ publication.publishedRevision }}</h2>
              <p v-if="createError" role="alert">{{ createError }}</p>
              <p v-if="!publishedDefinition.attributes.length" class="muted">This Entity has no published attributes. You can still create an empty record.</p>
              <div class="record-fields">
                <label v-for="attribute in publishedDefinition.attributes" :key="attribute.id" :for="`record-${attribute.id}`">
                  {{ attribute.label }} <span v-if="attribute.required" aria-label="required">*</span>
                  <select v-if="attribute.typeId === 'BOOLEAN'" :id="`record-${attribute.id}`" v-model="values[attribute.name]" :required="attribute.required" :disabled="saving">
                    <option value="">Unset</option><option value="true">Yes</option><option value="false">No</option>
                  </select>
                  <input v-else :id="`record-${attribute.id}`" v-model="values[attribute.name]"
                    :type="attribute.typeId === 'EMAIL' ? 'email' : attribute.typeId === 'INTEGER' || attribute.typeId === 'DECIMAL' ? 'number' : attribute.typeId === 'DATE' ? 'date' : attribute.typeId === 'DATETIME' ? 'datetime-local' : 'text'"
                    :step="attribute.typeId === 'INTEGER' ? '1' : attribute.typeId === 'DECIMAL' ? 'any' : undefined"
                    :required="attribute.required" :disabled="saving" />
                </label>
              </div>
              <button type="submit" :disabled="saving || !canCreate">{{ saving ? 'Saving…' : 'Create record' }}</button>
            </form>
            <div class="record-list">
              <h2>Saved records</h2>
              <p v-if="records.length === 0" class="muted">No records have been created for this Entity.</p>
              <div v-else class="table-scroll"><table>
                <thead><tr><th>ID</th><th v-for="attribute in publishedDefinition.attributes" :key="attribute.id">{{ attribute.label }}</th><th>Version</th><th></th></tr></thead>
                <tbody><tr v-for="record in records" :key="record.id">
                  <td><code>{{ record.id }}</code></td>
                  <td v-for="attribute in publishedDefinition.attributes" :key="attribute.id">{{ formatValue(record.values[attribute.name], attribute.typeId) }}</td>
                  <td>{{ record.version }}</td>
                  <td class="record-row-actions">
                    <button type="button" class="text-button" :disabled="!!unsupported.length || editBusy || saving" :aria-pressed="editingRecord?.id === record.id" @click="startEdit(record)">Edit</button>
                    <button type="button" class="text-button" :aria-pressed="historyRecordId === record.id" @click="loadAudit(record.id)">History</button>
                  </td>
                </tr></tbody>
              </table></div>
            </div>
            <div v-if="historyRecordId" class="audit-panel">
              <div class="page-heading">
                <div><h2>Record history</h2><p class="muted">Record <code>{{ historyRecordId }}</code>. Audit events show actions and versions without field values.</p></div>
                <button type="button" class="secondary-button" @click="closeAudit">Close</button>
              </div>
              <p v-if="auditLoading" role="status">Loading audit history…</p>
              <p v-if="auditError" role="alert">{{ auditError }} <button type="button" class="text-button" @click="loadAudit(historyRecordId)">Retry</button></p>
              <p v-else-if="!auditLoading && auditEvents.length === 0" class="muted">No audit events recorded for this record.</p>
              <div v-else-if="!auditLoading" class="table-scroll"><table>
                <thead><tr><th>When</th><th>Action</th><th>Version</th><th>Actor ID</th></tr></thead>
                <tbody><tr v-for="event in auditEvents" :key="event.id">
                  <td>{{ formatAuditTime(event.at) }}</td>
                  <td>{{ event.action }}</td>
                  <td>{{ event.recordVersion }}</td>
                  <td><code>{{ event.actorId }}</code></td>
                </tr></tbody>
              </table></div>
            </div>
            <div v-if="editingRecord && !unsupported.length" class="record-editor">
              <div class="page-heading">
                <div><h2>Edit record</h2><p class="muted">Record <code>{{ editingRecord.id }}</code> · Version {{ editingRecord.version }} · Published Entity revision {{ publication.publishedRevision }}</p></div>
                <button type="button" class="secondary-button" :disabled="editBusy" @click="refreshEditing">Reload latest</button>
              </div>
              <p v-if="editError" role="alert">{{ editError }}</p>
              <p v-if="editSuccess" role="status" class="success-message">{{ editSuccess }}</p>
              <p v-if="editConflict" class="muted">Your unsaved edits are still shown. Reloading replaces them with the latest saved record.</p>
              <form class="record-form" @submit.prevent="saveEdit">
                <div class="record-fields">
                  <label v-for="attribute in publishedDefinition.attributes" :key="attribute.id" :for="`edit-record-${attribute.id}`">
                    {{ attribute.label }} <span v-if="attribute.required" aria-label="required">*</span>
                    <select v-if="attribute.typeId === 'BOOLEAN'" :id="`edit-record-${attribute.id}`" v-model="editValues[attribute.name]" :required="attribute.required" :disabled="editBusy">
                      <option value="">Unset</option><option value="true">Yes</option><option value="false">No</option>
                    </select>
                    <input v-else :id="`edit-record-${attribute.id}`" v-model="editValues[attribute.name]"
                      :type="attribute.typeId === 'EMAIL' ? 'email' : attribute.typeId === 'INTEGER' || attribute.typeId === 'DECIMAL' ? 'number' : attribute.typeId === 'DATE' ? 'date' : attribute.typeId === 'DATETIME' ? 'datetime-local' : 'text'"
                      :step="attribute.typeId === 'INTEGER' ? '1' : attribute.typeId === 'DECIMAL' ? 'any' : attribute.typeId === 'DATETIME' ? '1' : undefined"
                      :required="attribute.required" :disabled="editBusy" />
                  </label>
                </div>
                <div class="record-actions">
                  <button type="submit" :disabled="editBusy">{{ editBusy ? 'Saving…' : 'Save changes' }}</button>
                  <button type="button" class="secondary-button" :disabled="editBusy" @click="cancelEdit">Cancel</button>
                </div>
              </form>
            </div>
          </template>
        </template>
      </template>
    </template>
  </section>
</template>
