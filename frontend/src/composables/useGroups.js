import { ref, watch, onScopeDispose } from "vue";
import { token } from "./useAuth";
import { api } from "../services/api";

export function useGroups() {
  const groups = ref([]);
  const mine = ref([]);
  const requests = ref([]);
  const courses = ref([]);
  const selected = ref(null);
  const leaderRequests = ref([]);
  const detailLoading = ref(false);
  const loading = ref(false);
  const busy = ref(false);
  const error = ref("");
  // Ignore responses from an older refresh, closed detail, or unmounted page.
  let version = 0;
  let detailVersion = 0;
  let disposed = false;
  let controller = null;
  async function load() {
    const current = ++version;
    controller?.abort();
    controller = new AbortController();
    loading.value = true;
    error.value = "";
    try {
      const result = await Promise.all(
        [
          "/api/groups",
          "/api/groups?scope=mine",
          "/api/groups/join-requests/mine",
          "/api/courses",
        ].map((path) => api(path, { signal: controller.signal })),
      );
      if (disposed || current !== version) return false;
      [groups.value, mine.value, requests.value, courses.value] = result;
      return true;
    } catch (e) {
      if (current === version && !disposed && e.name !== "AbortError")
        error.value = e.message;
      return false;
    } finally {
      if (current === version && !disposed) loading.value = false;
    }
  }
  async function open(id) {
    const current = version;
    const detail = ++detailVersion;
    detailLoading.value = true;
    error.value = "";
    try {
      const group = await api(`/api/groups/${id}`);
      const pending = group.isLeader
        ? await api(`/api/groups/${id}/join-requests`)
        : [];
      if (disposed || current !== version || detail !== detailVersion)
        return false;
      selected.value = group;
      leaderRequests.value = pending;
      return true;
    } catch (e) {
      if (current === version && detail === detailVersion && !disposed)
        error.value = e.message;
      return false;
    } finally {
      if (detail === detailVersion && !disposed) detailLoading.value = false;
    }
  }
  function closeDetail() {
    detailLoading.value = false;
    ++detailVersion;
    selected.value = null;
    leaderRequests.value = [];
  }
  async function mutate(
    path,
    method = "POST",
    body,
    { dismissDetail = false } = {},
  ) {
    if (busy.value) return false;
    busy.value = true;
    error.value = "";
    const session = token.value;
    const id = selected.value?.groupId;
    const detail = detailVersion;
    try {
      await api(path, {
        method,
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      if (disposed || token.value !== session) return false;
      if (dismissDetail) closeDetail();
      let refreshed = await load();
      if (disposed || token.value !== session) return false;
      if (refreshed && id && !dismissDetail && detail === detailVersion) {
        refreshed = await open(id);
      }
      if (disposed || token.value !== session) return false;
      if (!refreshed) {
        // The write succeeded. Do not leave stale actions available or invite a second save.
        closeDetail();
        groups.value = [];
        mine.value = [];
        requests.value = [];
        error.value =
          "Your change was saved, but the latest groups could not be loaded. Refresh to see the current state.";
      }
      return true;
    } catch (e) {
      if (!disposed && token.value === session) error.value = e.message;
      return false;
    } finally {
      if (!disposed && token.value === session) busy.value = false;
    }
  }
  watch(
    token,
    () => {
      ++version;
      ++detailVersion;
      controller?.abort();
      groups.value = [];
      mine.value = [];
      requests.value = [];
      courses.value = [];
      closeDetail();
      busy.value = false;
      if (token.value) load();
    },
    { immediate: true },
  );
  onScopeDispose(() => {
    disposed = true;
    ++version;
    ++detailVersion;
    controller?.abort();
  });
  return {
    groups,
    mine,
    requests,
    courses,
    selected,
    leaderRequests,
    loading,
    detailLoading,
    busy,
    error,
    load,
    open,
    closeDetail,
    mutate,
  };
}
