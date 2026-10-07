import { ref, watch, onScopeDispose } from 'vue'
import { token } from './useAuth'
import { groupApi } from '../services/groupApi'

export function useGroups() {
  const groups = ref([]), mine = ref([]), requests = ref([]), courses = ref([])
  const selected = ref(null), leaderRequests = ref([])
  const loading = ref(false), busy = ref(false), error = ref('')
  let version = 0, disposed = false, controller = null, detailVersion = 0
  async function load() {
    const current = ++version
    controller?.abort(); controller = new AbortController()
    loading.value = true; error.value = ''
    try {
      const result = await Promise.all(['/api/groups', '/api/groups?scope=mine', '/api/groups/join-requests/mine', '/api/courses']
        .map(path => groupApi(path, { signal: controller.signal })))
      if (disposed || current !== version) return
      ;[groups.value, mine.value, requests.value, courses.value] = result
    } catch (e) { if (current === version && !disposed && e.name !== 'AbortError') error.value = e.message }
    finally { if (current === version && !disposed) loading.value = false }
  }
  async function open(id) {
    const current = version, detail = ++detailVersion
    error.value = ''
    try {
      const group = await groupApi(`/api/groups/${id}`)
      const pending = group.isLeader ? await groupApi(`/api/groups/${id}/join-requests`) : []
      if (disposed || current !== version || detail !== detailVersion) return
      selected.value = group; leaderRequests.value = pending
    } catch (e) { if (current === version && detail === detailVersion && !disposed) error.value = e.message }
  }
  function closeDetail() { ++detailVersion; selected.value = null; leaderRequests.value = [] }
  async function mutate(path, method = 'POST', body) {
    if (busy.value) return false
    busy.value = true; error.value = ''
    const session = token.value, id = selected.value?.groupId
    try {
      await groupApi(path, { method, ...(body ? { body: JSON.stringify(body) } : {}) })
      if (disposed || token.value !== session) return false
      await load()
      if (id && token.value === session && !disposed) await open(id)
      return true
    } catch (e) { if (!disposed && token.value === session) error.value = e.message; return false }
    finally { if (!disposed && token.value === session) busy.value = false }
  }
  watch(token, () => {
    ++version; ++detailVersion; controller?.abort()
    groups.value = []; mine.value = []; requests.value = []; courses.value = []; closeDetail(); busy.value = false
    if (token.value) load()
  }, { immediate: true })
  onScopeDispose(() => { disposed = true; ++version; ++detailVersion; controller?.abort() })
  return { groups, mine, requests, courses, selected, leaderRequests, loading, busy, error, load, open, closeDetail, mutate }
}
