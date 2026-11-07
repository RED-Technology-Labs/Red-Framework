import { shallowRef } from 'vue'
import { api, type User } from './api'

export const currentUser = shallowRef<User | null>(null)

export async function refreshSession(): Promise<boolean> {
  try {
    currentUser.value = await api.me()
    return true
  } catch {
    currentUser.value = null
    return false
  }
}
