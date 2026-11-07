<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, ApiError, type Application, type Entity, type EntityPublication, type EntityPresentation, type NewEntity } from '../api'
import PresentationBuilder from '../components/PresentationBuilder.vue'

type DraftAttribute = NewEntity['attributes'][number] & { key: number }
type EditAttribute = DraftAttribute & { id?: string }

const applications = ref<Application[]>([])
const entities = ref<Entity[]>([])
const typeIds = ref<string[]>([])
const applicationId = ref('')
const name = ref('')
const label = ref('')
const attributes = ref<DraftAttribute[]>([])
const emptyPresentation = (): EntityPresentation => ({ formTabs: [], namingSeries: null, titleAttribute: null, listAttributes: [] })
function validPresentation(value: EntityPresentation, fields: { name: string }[]): EntityPresentation {
  const names = new Set(fields.map((field) => field.name.trim()))
  return {
    ...value,
    formTabs: value.formTabs.map((tab) => ({ ...tab, fields: tab.fields.filter((field) => names.has(field)) })),
    titleAttribute: value.titleAttribute && names.has(value.titleAttribute) ? value.titleAttribute : null,
    listAttributes: value.listAttributes.filter((field) => names.has(field)),
  }
}
const presentation = ref<EntityPresentation>(emptyPresentation())
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const editing = ref<Entity | null>(null)
const editLabel = ref('')
const editAttributes = ref<EditAttribute[]>([])
const editPresentation = ref<EntityPresentation>(emptyPresentation())
const editBusy = ref(false)
const editError = ref('')
const editSuccess = ref('')
const conflict = ref(false)
const history = ref<Entity[]>([])
const historyLoading = ref(false)
const historyError = ref('')
const publication = ref<EntityPublication | null>(null)
const publicationLoading = ref(false)
const publicationError = ref('')
const publicationLoaded = ref(false)
const publishConfirmationOpen = ref(false)
const publishAcknowledged = ref(false)
const publishBusy = ref(false)
const publishError = ref('')
const publishSuccess = ref('')
let nextKey = 0

const applicationsById = computed(() => new Map(applications.value.map((app) => [app.id, app.canonicalName])))
const formReady = computed(() => !loading.value && applications.value.length > 0 && typeIds.value.length > 0)
const publicationNeeded = computed(() => !!editing.value && (!publication.value || editing.value.revision > publication.value.publishedRevision))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [loadedApplications, loadedEntities, loadedTypes] = await Promise.all([
      api.applications(), api.entities(), api.attributeTypes(),
    ])
    applications.value = loadedApplications
    entities.value = loadedEntities
    typeIds.value = loadedTypes
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Failed to load entity definitions' }
  finally { loading.value = false }
}

function addAttribute() {
  if (typeIds.value.length === 0) return
  attributes.value.push({ key: nextKey++, name: '', label: '', typeId: typeIds.value[0], required: false })
}

function removeAttribute(key: number) {
  attributes.value = attributes.value.filter((attribute) => attribute.key !== key)
}

function copyForEdit(entity: Entity) {
  editing.value = entity
  editLabel.value = entity.label
  editAttributes.value = entity.attributes.map((attribute) => ({ ...attribute, key: nextKey++ }))
  editPresentation.value = {
    formTabs: entity.formTabs.map((tab) => ({ ...tab, fields: [...tab.fields] })),
    namingSeries: entity.namingSeries ? { ...entity.namingSeries } : null,
    titleAttribute: entity.titleAttribute,
    listAttributes: [...entity.listAttributes],
  }
  editError.value = ''
  editSuccess.value = ''
  conflict.value = false
  publishConfirmationOpen.value = false
  publishAcknowledged.value = false
  publishError.value = ''
  publishSuccess.value = ''
}

async function loadHistory(id: string) {
  historyLoading.value = true
  historyError.value = ''
  try {
    const loaded = await api.entityRevisions(id)
    if (editing.value?.id === id) history.value = loaded
  } catch (failure) {
    if (editing.value?.id === id) historyError.value = failure instanceof Error ? failure.message : 'Failed to load revision history'
  } finally { if (editing.value?.id === id) historyLoading.value = false }
}

async function loadPublication(id: string) {
  publicationLoading.value = true
  publicationLoaded.value = false
  publicationError.value = ''
  publication.value = null
  try {
    const loaded = await api.entityPublication(id)
    if (editing.value?.id === id) publication.value = loaded
  }
  catch (failure) {
    if (editing.value?.id === id && !(failure instanceof ApiError && failure.status === 404))
      publicationError.value = failure instanceof Error ? failure.message : 'Failed to load publication status'
  } finally {
    if (editing.value?.id === id) { publicationLoading.value = false; publicationLoaded.value = true }
  }
}

async function selectEntity(entity: Entity) {
  copyForEdit(entity)
  history.value = []
  publication.value = null
  await Promise.all([loadHistory(entity.id), loadPublication(entity.id)])
}

function addEditAttribute() {
  if (!typeIds.value.length) return
  editAttributes.value.push({ key: nextKey++, name: '', label: '', typeId: typeIds.value[0], required: false })
}

function removeEditAttribute(key: number) {
  editAttributes.value = editAttributes.value.filter((attribute) => attribute.key !== key)
}

async function refreshEditing() {
  if (!editing.value) return
  const id = editing.value.id
  editBusy.value = true
  editError.value = ''
  try {
    const loaded = await api.entities()
    entities.value = loaded
    const current = loaded.find((entity) => entity.id === id)
    if (!current) { editing.value = null; editError.value = 'Entity definition no longer exists.'; return }
    copyForEdit(current)
    await Promise.all([loadHistory(id), loadPublication(id)])
  } catch (failure) { editError.value = failure instanceof Error ? failure.message : 'Could not load the latest revision' }
  finally { editBusy.value = false }
}

async function saveEdit() {
  if (!editing.value) return
  editError.value = ''
  editSuccess.value = ''
  conflict.value = false
  const names = editAttributes.value.map((attribute) => attribute.name.trim())
  if (new Set(names).size !== names.length) { editError.value = 'Attribute names must be unique.'; return }
  editBusy.value = true
  const id = editing.value.id
  try {
    const updated = await api.updateEntity(id, {
      ...validPresentation(editPresentation.value, editAttributes.value),
      expectedRevision: editing.value.revision,
      label: editLabel.value.trim(),
      attributes: editAttributes.value.map((attribute) => ({
        ...(attribute.id ? { id: attribute.id } : {}),
        name: attribute.name.trim(),
        label: attribute.label.trim(),
        typeId: attribute.typeId,
        required: attribute.required,
      })),
    })
    entities.value = entities.value.map((entity) => entity.id === id ? updated : entity)
    copyForEdit(updated)
    editSuccess.value = `Saved revision ${updated.revision}. The definition is still a draft.`
    await loadHistory(id)
  } catch (failure) {
    conflict.value = failure instanceof ApiError && failure.status === 409
    editError.value = conflict.value
      ? 'Another update was saved first. Reload the latest revision before editing again.'
      : failure instanceof Error ? failure.message : 'Failed to save definition'
  } finally { editBusy.value = false }
}

async function publish() {
  if (!editing.value || !publishAcknowledged.value || !publicationNeeded.value || !publicationLoaded.value || publicationError.value) return
  const id = editing.value.id
  publishBusy.value = true
  publishError.value = ''
  publishSuccess.value = ''
  try {
    const published = await api.publishEntity(id)
    if (editing.value?.id !== id) return
    publication.value = published
    publishSuccess.value = `Published revision ${published.publishedRevision} to ${published.tableName}.`
    publishConfirmationOpen.value = false
    publishAcknowledged.value = false
    try {
      const refreshed = await api.entityPublication(id)
      if (editing.value?.id === id) publication.value = refreshed
    } catch {
      if (editing.value?.id === id) publicationError.value = 'Publication succeeded, but its status could not be refreshed. Reload this Entity to verify it.'
    }
  } catch (failure) {
    publishError.value = failure instanceof ApiError && failure.status === 409
      ? 'This revision cannot be published safely. Only label changes and new optional attributes are supported; renaming, removing, retyping, or adding required attributes is not supported.'
      : failure instanceof Error ? failure.message : 'Publication failed'
  } finally { publishBusy.value = false }
}

async function create() {
  error.value = ''
  success.value = ''
  const names = attributes.value.map((attribute) => attribute.name.trim())
  if (new Set(names).size !== names.length) { error.value = 'Attribute names must be unique.'; return }
  saving.value = true
  try {
    const created = await api.createEntity({
      ...validPresentation(presentation.value, attributes.value),
      applicationId: applicationId.value,
      name: name.value.trim(),
      label: label.value.trim(),
      attributes: attributes.value.map(({ name, label, typeId, required }) => ({
        name: name.trim(), label: label.trim(), typeId, required,
      })),
    })
    name.value = ''
    label.value = ''
    attributes.value = []
    presentation.value = emptyPresentation()
    entities.value = [...entities.value, created].sort((left, right) => left.name.localeCompare(right.name))
    success.value = `Saved draft definition ${created.name}. It has not been published.`
  } catch (failure) { error.value = failure instanceof Error ? failure.message : 'Failed to save entity definition' }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">BUILD · METADATA</p>
        <h1>Entity definitions</h1>
        <p>Save draft definitions, then publish supported revisions to PostgreSQL. Published Entities can store records in the Records page.</p>
      </div>
      <button type="button" class="secondary-button" :disabled="loading" @click="load">Refresh</button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="success" role="status" class="success-message">{{ success }}</p>
    <p v-if="loading" role="status">Loading definitions…</p>
    <template v-else>
      <div v-if="!applications.length" class="empty-message">Create an <RouterLink to="/applications">application</RouterLink> before defining an Entity.</div>
      <form v-else class="entity-form" @submit.prevent="create">
        <h2>New draft definition</h2>
        <div class="entity-fields">
          <label>Application
            <select v-model="applicationId" required :disabled="!formReady">
              <option value="" disabled>Select application</option>
              <option v-for="application in applications" :key="application.id" :value="application.id">{{ application.canonicalName }}</option>
            </select>
          </label>
          <label>Canonical name<input v-model="name" required pattern="[a-z][a-z0-9_]{1,61}[a-z0-9]" placeholder="sales_order" :disabled="!formReady" /></label>
          <label>Display label<input v-model="label" required maxlength="120" placeholder="Sales Order" :disabled="!formReady" /></label>
        </div>
        <div class="attribute-heading">
          <h3>Attributes</h3>
          <button type="button" class="secondary-button" :disabled="!formReady" @click="addAttribute">Add attribute</button>
        </div>
        <p v-if="!typeIds.length" class="muted">Supported attribute types could not be loaded.</p>
        <p v-else-if="!attributes.length" class="muted">No attributes added yet.</p>
        <div v-for="(attribute, index) in attributes" :key="attribute.key" class="attribute-row">
          <label :for="`attribute-name-${attribute.key}`">Name
            <input :id="`attribute-name-${attribute.key}`" v-model="attribute.name" required pattern="[a-z][a-z0-9_]{1,61}[a-z0-9]" placeholder="customer_name" />
          </label>
          <label :for="`attribute-label-${attribute.key}`">Label
            <input :id="`attribute-label-${attribute.key}`" v-model="attribute.label" required maxlength="120" placeholder="Customer Name" />
          </label>
          <label :for="`attribute-type-${attribute.key}`">Type
            <select :id="`attribute-type-${attribute.key}`" v-model="attribute.typeId" required>
              <option v-for="typeId in typeIds" :key="typeId" :value="typeId">{{ typeId }}</option>
            </select>
          </label>
          <label class="checkbox-label"><input v-model="attribute.required" type="checkbox" /> Required</label>
          <button type="button" class="text-button remove-attribute" :aria-label="`Remove attribute ${index + 1}`" @click="removeAttribute(attribute.key)">Remove</button>
        </div>
        <PresentationBuilder v-model="presentation" :attributes="attributes" />
        <button type="submit" :disabled="!formReady || saving">{{ saving ? 'Saving…' : 'Save draft definition' }}</button>
      </form>
      <div class="entity-list">
        <h2>Saved definitions</h2>
        <p v-if="entities.length === 0" class="muted">No Entity definitions saved yet.</p>
        <table v-else>
          <thead><tr><th>Entity</th><th>Application</th><th>Revision</th><th>Attributes</th><th></th></tr></thead>
          <tbody>
            <tr v-for="entity in entities" :key="entity.id">
              <td><strong>{{ entity.label }}</strong><div class="muted">{{ entity.name }}</div></td>
              <td>{{ applicationsById.get(entity.applicationId) ?? entity.applicationId }}</td>
              <td>{{ entity.revision }}</td>
              <td>{{ entity.attributes.length }}</td>
              <td><button type="button" class="text-button" :aria-pressed="editing?.id === entity.id" :disabled="publishBusy || editBusy" @click="selectEntity(entity)">Edit</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="editing" class="entity-editor">
        <div class="page-heading">
          <div>
            <p class="eyebrow">DRAFT DEFINITION · REVISION {{ editing.revision }}</p>
            <h2>Edit {{ editing.name }}</h2>
            <p class="muted">Changes create a new immutable revision. They do not publish a business table.</p>
          </div>
          <button type="button" class="secondary-button" :disabled="editBusy || publishBusy" @click="refreshEditing">Reload latest</button>
        </div>
        <p v-if="editError" role="alert">{{ editError }}</p>
        <p v-if="editSuccess" role="status" class="success-message">{{ editSuccess }}</p>
        <p v-if="conflict" class="muted">Your unsaved edits are still shown below. Reloading replaces them with the latest saved revision.</p>
        <form class="entity-form edit-form" @submit.prevent="saveEdit">
          <label>Display label<input v-model="editLabel" required maxlength="120" :disabled="editBusy" /></label>
          <div class="attribute-heading">
            <h3>Attributes</h3>
            <button type="button" class="secondary-button" :disabled="editBusy || !typeIds.length" @click="addEditAttribute">Add attribute</button>
          </div>
          <p v-if="!editAttributes.length" class="muted">No attributes in this definition.</p>
          <div v-for="(attribute, index) in editAttributes" :key="attribute.key" class="attribute-row">
            <label :for="`edit-name-${attribute.key}`">Name
              <input :id="`edit-name-${attribute.key}`" v-model="attribute.name" required pattern="[a-z][a-z0-9_]{1,61}[a-z0-9]" :disabled="editBusy" />
            </label>
            <label :for="`edit-label-${attribute.key}`">Label
              <input :id="`edit-label-${attribute.key}`" v-model="attribute.label" required maxlength="120" :disabled="editBusy" />
            </label>
            <label :for="`edit-type-${attribute.key}`">Type
              <select :id="`edit-type-${attribute.key}`" v-model="attribute.typeId" required :disabled="editBusy">
                <option v-for="typeId in typeIds" :key="typeId" :value="typeId">{{ typeId }}</option>
              </select>
            </label>
            <label class="checkbox-label"><input v-model="attribute.required" type="checkbox" :disabled="editBusy" /> Required</label>
            <button type="button" class="text-button remove-attribute" :disabled="editBusy" :aria-label="`Remove attribute ${index + 1}`" @click="removeEditAttribute(attribute.key)">Remove</button>
          </div>
          <PresentationBuilder v-model="editPresentation" :attributes="editAttributes" />
          <button type="submit" :disabled="editBusy || !typeIds.length">{{ editBusy ? 'Saving…' : 'Save new revision' }}</button>
        </form>
        <div class="publication-panel">
          <div class="page-heading">
            <div>
              <h3>Publication</h3>
              <p v-if="publicationLoading" role="status">Checking publication status…</p>
              <p v-else-if="publicationError" role="alert">{{ publicationError }}</p>
              <p v-else-if="publication">Revision {{ publication.publishedRevision }} is published as <code>{{ publication.tableName }}</code>.</p>
              <p v-else>Unpublished. No business table has been created for this definition.</p>
            </div>
            <button v-if="publicationNeeded && publicationLoaded && !publicationError && !publishConfirmationOpen" type="button" :disabled="publishBusy || editBusy" @click="publishConfirmationOpen = true">Publish revision {{ editing.revision }}</button>
          </div>
          <p v-if="publishSuccess" role="status" class="success-message">{{ publishSuccess }}</p>
          <p v-if="publishError" role="alert">{{ publishError }}</p>
          <div v-if="publishConfirmationOpen && publicationNeeded" class="publish-confirmation">
            <h4>Confirm publication</h4>
            <p v-if="!publication">Publishing the first revision creates a real, empty PostgreSQL business table. This operation changes the database schema.</p>
            <p v-else>Publishing this revision can change the existing PostgreSQL table. Label changes and new optional attributes are supported. Renaming, removing, retyping, or adding required attributes will be refused.</p>
            <label class="checkbox-label"><input v-model="publishAcknowledged" type="checkbox" :disabled="publishBusy" /> I understand this will attempt a real database schema change.</label>
            <div class="publish-actions">
              <button type="button" :disabled="!publishAcknowledged || publishBusy || editBusy" @click="publish">{{ publishBusy ? 'Publishing…' : 'Confirm publish' }}</button>
              <button type="button" class="secondary-button" :disabled="publishBusy" @click="publishConfirmationOpen = false; publishAcknowledged = false">Cancel</button>
            </div>
          </div>
        </div>
        <div class="revision-history">
          <h3>Revision history</h3>
          <p v-if="historyError" role="alert">{{ historyError }}</p>
          <p v-if="historyLoading" role="status">Loading history…</p>
          <p v-else-if="history.length === 0" class="muted">No revisions available.</p>
          <table v-else>
            <thead><tr><th>Revision</th><th>Label</th><th>Attributes</th></tr></thead>
            <tbody><tr v-for="revision in history" :key="revision.revision">
              <td>{{ revision.revision }}</td><td>{{ revision.label }}</td><td>{{ revision.attributes.length }}</td>
            </tr></tbody>
          </table>
        </div>
      </div>
    </template>
  </section>
</template>
