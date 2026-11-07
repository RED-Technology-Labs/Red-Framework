<script setup lang="ts">
import { computed } from 'vue'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import type { EntityPresentation, FormTab } from '../api'

const props = defineProps<{ modelValue: EntityPresentation; attributes: { name: string; label: string }[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: EntityPresentation] }>()
const validAttributes = computed(() => props.attributes.filter((attribute) => attribute.name.trim()))
const usedFields = computed(() => new Set(props.modelValue.formTabs.flatMap((tab) => tab.fields)))

function change(value: Partial<EntityPresentation>) { emit('update:modelValue', { ...props.modelValue, ...value }) }
function changeTab(index: number, patch: Partial<FormTab>) {
  change({ formTabs: props.modelValue.formTabs.map((tab, position) => position === index ? { ...tab, ...patch } : tab) })
}
function addTab() {
  let number = 1
  while (props.modelValue.formTabs.some((tab) => tab.name === `tab_${number}`)) number++
  change({ formTabs: [...props.modelValue.formTabs, { name: `tab_${number}`, label: `Tab ${number}`, fields: [] }] })
}
function removeTab(index: number) { change({ formTabs: props.modelValue.formTabs.filter((_, position) => position !== index) }) }
function toggleField(index: number, field: string, checked: boolean) {
  const current = props.modelValue.formTabs[index].fields
  changeTab(index, { fields: checked ? [...current, field] : current.filter((name) => name !== field) })
}
function toggleListField(field: string, checked: boolean) {
  const current = props.modelValue.listAttributes
  change({ listAttributes: checked ? [...current, field] : current.filter((name) => name !== field) })
}
</script>

<template>
  <div class="presentation-builder">
    <h3>Form and list layout</h3>
    <Tabs value="form">
      <TabList><Tab value="form">Form tabs</Tab><Tab value="list">List view</Tab><Tab value="naming">Naming</Tab></TabList>
      <TabPanels>
        <TabPanel value="form">
          <p>Place each field in a tab. Unplaced fields appear in General.</p>
          <Button type="button" label="Add tab" @click="addTab" />
          <div v-for="(tab, index) in modelValue.formTabs" :key="index" class="builder-tab">
            <div class="builder-tab-heading">
              <label>Tab key <InputText :model-value="tab.name" @update:model-value="changeTab(index, { name: String($event) })" /></label>
              <label>Label <InputText :model-value="tab.label" @update:model-value="changeTab(index, { label: String($event) })" /></label>
              <Button type="button" label="Remove" severity="danger" text @click="removeTab(index)" />
            </div>
            <div class="builder-choices">
              <label v-for="field in validAttributes" :key="field.name" class="checkbox-label">
                <input type="checkbox" :checked="tab.fields.includes(field.name)" :disabled="usedFields.has(field.name) && !tab.fields.includes(field.name)"
                  @change="toggleField(index, field.name, ($event.target as HTMLInputElement).checked)" />{{ field.label || field.name }}
              </label>
            </div>
          </div>
        </TabPanel>
        <TabPanel value="list">
          <label>Record title field
            <select :value="modelValue.titleAttribute ?? ''" @change="change({ titleAttribute: ($event.target as HTMLSelectElement).value || null })">
              <option value="">Use generated name or record ID</option>
              <option v-for="field in validAttributes" :key="field.name" :value="field.name">{{ field.label || field.name }}</option>
            </select>
          </label>
          <p>Columns shown in Desk (up to 12).</p>
          <div class="builder-choices"><label v-for="field in validAttributes" :key="field.name" class="checkbox-label">
            <input type="checkbox" :checked="modelValue.listAttributes.includes(field.name)"
              :disabled="modelValue.listAttributes.length >= 12 && !modelValue.listAttributes.includes(field.name)"
              @change="toggleListField(field.name, ($event.target as HTMLInputElement).checked)" />{{ field.label || field.name }}
          </label></div>
        </TabPanel>
        <TabPanel value="naming">
          <p>Generate a unique name when a record is saved. Existing records keep their names.</p>
          <label class="checkbox-label"><input type="checkbox" :checked="modelValue.namingSeries !== null"
            @change="change({ namingSeries: ($event.target as HTMLInputElement).checked ? { prefix: 'REC-', digits: 5 } : null })" />Use naming series</label>
          <div v-if="modelValue.namingSeries" class="builder-tab-heading">
            <label>Prefix <InputText :model-value="modelValue.namingSeries.prefix" placeholder="ORD-" @update:model-value="change({ namingSeries: { ...modelValue.namingSeries!, prefix: String($event).toUpperCase() } })" /></label>
            <label>Digits <InputNumber :model-value="modelValue.namingSeries.digits" :min="2" :max="12" @update:model-value="change({ namingSeries: { ...modelValue.namingSeries!, digits: Number($event) } })" /></label>
          </div>
          <p v-if="modelValue.namingSeries">Example: {{ modelValue.namingSeries.prefix }}{{ '1'.padStart(modelValue.namingSeries.digits, '0') }}</p>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>
