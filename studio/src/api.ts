export type Application = { id: string; canonicalName: string; version: string }
export type Site = { id: string; canonicalName: string; status: 'ACTIVE' | 'SUSPENDED' }
export type EntityAttribute = { id: string; name: string; label: string; typeId: string; required: boolean }
export type FormTab = { name: string; label: string; fields: string[] }
export type NamingSeries = { prefix: string; digits: number }
export type EntityPresentation = { formTabs: FormTab[]; namingSeries: NamingSeries | null; titleAttribute: string | null; listAttributes: string[] }
export type Entity = { id: string; applicationId: string; name: string; label: string; revision: number; attributes: EntityAttribute[] } & EntityPresentation
export type NewEntity = EntityPresentation & {
  applicationId: string
  name: string
  label: string
  attributes: { name: string; label: string; typeId: string; required: boolean }[]
}
export type EntityUpdate = EntityPresentation & {
  expectedRevision: number
  label: string
  attributes: { id?: string; name: string; label: string; typeId: string; required: boolean }[]
}
export type EntityPublication = { entityId: string; publishedRevision: number; tableName: string }
export type RecordValue = string | number | boolean | null
export type EntityRecord = { id: string; name: string | null; version: number; values: Record<string, RecordValue> }
export type RecordAuditEvent = { id: string; entityId: string; recordId: string; actorId: string; action: string; recordVersion: number; at: string }
export type User = { id: string; fullName: string; email: string; administrator: boolean }
export type ManagedUser = Omit<User, 'administrator'> & { active: boolean }
export type EntityAction = 'READ' | 'CREATE' | 'UPDATE'
export type EntityGrant = { userId: string; entityId: string; action: EntityAction }
export type DeskEntity = { definition: Entity; canCreate: boolean; canUpdate: boolean }
export type SetupStatus = { initialized: boolean }

export class ApiError extends Error {
  constructor(readonly status: number, message: string) { super(message) }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method?.toUpperCase() ?? 'GET'
  const response = await fetch(`/api/v1${path}`, {
    ...init,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(method !== 'GET' ? { 'Content-Type': 'application/json', 'X-RED-CSRF': '1' } : {}),
      ...init?.headers,
    },
  })
  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const error = body && typeof body === 'object' && 'error' in body ? String(body.error) : `HTTP ${response.status}`
    throw new ApiError(response.status, error)
  }
  return body as T
}

export const api = {
  health: () => request<{ status: string }>('/health'),
  setupStatus: () => request<SetupStatus>('/setup/status'),
  initialize: (data: { fullName: string; email: string; password: string; confirmPassword: string; siteName: string }) =>
    request<unknown>('/setup/initialize', { method: 'POST', body: JSON.stringify(data) }),
  login: (email: string, password: string) =>
    request<User>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  me: () => request<User>('/auth/me'),
  logout: () => request<unknown>('/auth/logout', { method: 'POST', body: '{}' }),
  applications: () => request<Application[]>('/applications'),
  sites: () => request<Site[]>('/sites'),
  users: () => request<ManagedUser[]>('/users'),
  createUser: (fullName: string, email: string, password: string) =>
    request<ManagedUser>('/users', { method: 'POST', body: JSON.stringify({ fullName, email, password }) }),
  userPermissions: (userId: string) => request<EntityGrant[]>(`/users/${encodeURIComponent(userId)}/permissions`),
  grantEntityPermission: (userId: string, entityId: string, action: EntityAction) =>
    request<EntityGrant>(`/users/${encodeURIComponent(userId)}/permissions/${encodeURIComponent(entityId)}/${action}`, { method: 'PUT', body: '{}' }),
  revokeEntityPermission: (userId: string, entityId: string, action: EntityAction) =>
    request<{ revoked: boolean }>(`/users/${encodeURIComponent(userId)}/permissions/${encodeURIComponent(entityId)}/${action}`, { method: 'DELETE' }),
  deskEntities: () => request<DeskEntity[]>('/desk/entities'),
  entities: () => request<Entity[]>('/entities'),
  attributeTypes: () => request<string[]>('/attribute-types'),
  createEntity: (data: NewEntity) => request<Entity>('/entities', { method: 'POST', body: JSON.stringify(data) }),
  updateEntity: (id: string, data: EntityUpdate) => request<Entity>(`/entities/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(data) }),
  entityRevisions: (id: string) => request<Entity[]>(`/entities/${encodeURIComponent(id)}/revisions`),
  entityPublication: (id: string) => request<EntityPublication>(`/entities/${encodeURIComponent(id)}/publication`),
  publishEntity: (id: string) => request<EntityPublication>(`/entities/${encodeURIComponent(id)}/publish`, { method: 'POST', body: '{}' }),
  records: (id: string) => request<EntityRecord[]>(`/entities/${encodeURIComponent(id)}/records`),
  createRecord: (id: string, values: Record<string, RecordValue>) =>
    request<EntityRecord>(`/entities/${encodeURIComponent(id)}/records`, { method: 'POST', body: JSON.stringify({ values }) }),
  updateRecord: (entityId: string, recordId: string, expectedVersion: number, values: Record<string, RecordValue>) =>
    request<EntityRecord>(`/entities/${encodeURIComponent(entityId)}/records/${encodeURIComponent(recordId)}`, {
      method: 'PUT', body: JSON.stringify({ expectedVersion, values }),
    }),
  recordAudit: (entityId: string, recordId: string) =>
    request<RecordAuditEvent[]>(`/entities/${encodeURIComponent(entityId)}/records/${encodeURIComponent(recordId)}/audit`),
  createApplication: (canonicalName: string, version: string) =>
    request<Application>('/applications', { method: 'POST', body: JSON.stringify({ canonicalName, version }) }),
}
