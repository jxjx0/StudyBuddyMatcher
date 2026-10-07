import router from '../router'
import { token, clearSession, authError } from '../composables/useAuth'

export async function groupApi(path, options = {}) {
  const session = token.value
  const response = await fetch(path, { ...options, headers: {
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(session ? { Authorization: `Bearer ${session}` } : {}),
  } })
  if (response.status === 401 && token.value === session) {
    clearSession()
    authError.value = 'Your session has expired. Please log in again.'
    router.push('/login')
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || `Request failed (${response.status}). Please try again.`)
  }
  return response.status === 204 ? null : response.json()
}
