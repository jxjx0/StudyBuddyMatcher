import { ref, watch, onScopeDispose } from "vue";
import { token } from "./useAuth";
import { api } from "../services/api";

// Keep the last successful list only for this login session, even when the page unmounts.
let cachedOverview = null;
watch(
  token,
  () => {
    cachedOverview = null;
  },
  { flush: "sync" },
);

export function useGroups() {
  const groups = ref([]);
  const mine = ref([]);
  const requests = ref([]);
  const courses = ref([]);
  const selected = ref(null);
  const leaderRequests = ref([]);
  const detailLoading = ref(false);
  const loading = ref(false);
  const loaded = ref(false);
  const busy = ref(false);
  const error = ref("");
  // Ignore responses from an older refresh, closed detail, or unmounted page.
  let overviewVersion = 0;
  let detailsVersion = 0;
  let isDisposed = false;
  let overviewController = null;

  function showOverview(overview, courseList) {
    groups.value = overview.groups;
    mine.value = overview.mine;
    requests.value = overview.requests;
    courses.value = courseList;
    loaded.value = true;
  }

  function clearOverview() {
    groups.value = [];
    mine.value = [];
    requests.value = [];
    courses.value = [];
    loaded.value = false;
  }

  function isCurrentOverview(requestVersion) {
    return !isDisposed && requestVersion === overviewVersion;
  }

  function isCurrentDetail(overviewAtStart, requestVersion) {
    return (
      isCurrentOverview(overviewAtStart) && requestVersion === detailsVersion
    );
  }

  function isCurrentSession(sessionToken) {
    return !isDisposed && token.value === sessionToken;
  }

  async function load() {
    const startingOverviewVersion = ++overviewVersion;
    overviewController?.abort();
    overviewController = new AbortController();
    loading.value = true;
    error.value = "";
    try {
      const options = { signal: overviewController.signal };
      const [overview, courseList] = await Promise.all([
        api("/api/groups/overview", options),
        api("/api/courses", options),
      ]);
      if (!isCurrentOverview(startingOverviewVersion)) return false;
      showOverview(overview, courseList);
      cachedOverview = { sessionToken: token.value, overview, courseList };
      return true;
    } catch (problem) {
      if (
        isCurrentOverview(startingOverviewVersion) &&
        problem.name !== "AbortError"
      ) {
        cachedOverview = null;
        clearOverview();
        error.value = problem.message;
      }
      return false;
    } finally {
      if (isCurrentOverview(startingOverviewVersion)) loading.value = false;
    }
  }

  async function open(groupId) {
    const startingOverviewVersion = overviewVersion;
    const startingDetailsVersion = ++detailsVersion;
    detailLoading.value = true;
    error.value = "";
    try {
      const group = await api(`/api/groups/${groupId}`);
      let groupRequests = [];
      if (group.isLeader) {
        groupRequests = await api(`/api/groups/${groupId}/join-requests`);
      }
      if (!isCurrentDetail(startingOverviewVersion, startingDetailsVersion)) {
        return false;
      }
      selected.value = group;
      leaderRequests.value = groupRequests;
      return true;
    } catch (problem) {
      if (isCurrentDetail(startingOverviewVersion, startingDetailsVersion)) {
        error.value = problem.message;
      }
      return false;
    } finally {
      if (startingDetailsVersion === detailsVersion && !isDisposed) {
        detailLoading.value = false;
      }
    }
  }

  function closeDetail() {
    detailLoading.value = false;
    ++detailsVersion;
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
    cachedOverview = null;
    const sessionToken = token.value;
    const groupId = selected.value?.groupId;
    const startingDetailsVersion = detailsVersion;
    try {
      const requestOptions = { method };
      if (body) requestOptions.body = JSON.stringify(body);
      const result = await api(path, requestOptions);
      if (token.value === sessionToken) cachedOverview = null;
      if (!isCurrentSession(sessionToken)) return false;
      const joinRequestPath = path.match(/^\/api\/groups\/([^/]+)\/join-requests$/);
      if (
        method === "POST" &&
        joinRequestPath &&
        result?.status === "PENDING" &&
        result.groupJoinRequestId &&
        result.groupId === joinRequestPath[1]
      ) {
        const updateRequestState = (group) =>
          group.groupId === result.groupId
            ? { ...group, myRequestStatus: "PENDING", canRequest: false }
            : group;
        groups.value = groups.value.map(updateRequestState);
        mine.value = mine.value.map(updateRequestState);
        if (selected.value?.groupId === result.groupId) {
          selected.value = updateRequestState(selected.value);
        }
        if (
          !requests.value.some(
            (request) =>
              request.groupJoinRequestId === result.groupJoinRequestId,
          )
        ) {
          const updatedGroup = groups.value.find(
            (group) => group.groupId === result.groupId,
          ) || selected.value;
          requests.value = [
            {
              groupJoinRequestId: result.groupJoinRequestId,
              studentId: result.studentId,
              groupId: result.groupId,
              groupName: updatedGroup?.groupName || "",
              status: result.status,
              createdAt: result.createdAt,
              respondedAt: result.respondedAt,
            },
            ...requests.value,
          ];
        }
        return true;
      }
      if (dismissDetail) closeDetail();
      let refreshSucceeded = await load();
      if (!isCurrentSession(sessionToken)) return false;
      const shouldRefreshDetail =
        groupId && !dismissDetail && startingDetailsVersion === detailsVersion;
      if (refreshSucceeded && shouldRefreshDetail) {
        refreshSucceeded = await open(groupId);
      }
      if (!isCurrentSession(sessionToken)) return false;
      if (!refreshSucceeded) {
        // The write succeeded. Do not leave stale actions available or invite a second save.
        closeDetail();
        cachedOverview = null;
        clearOverview();
        error.value =
          "Your change was saved, but the latest groups could not be loaded. Refresh to see the current state.";
      }
      return true;
    } catch (problem) {
      if (isCurrentSession(sessionToken)) error.value = problem.message;
      return false;
    } finally {
      if (isCurrentSession(sessionToken)) busy.value = false;
    }
  }

  watch(
    token,
    () => {
      ++overviewVersion;
      ++detailsVersion;
      overviewController?.abort();
      clearOverview();
      closeDetail();
      busy.value = false;
      loading.value = false;
      error.value = "";
      if (token.value) {
        if (cachedOverview?.sessionToken === token.value) {
          showOverview(cachedOverview.overview, cachedOverview.courseList);
        }
        load();
      }
    },
    { immediate: true, flush: "sync" },
  );

  onScopeDispose(() => {
    isDisposed = true;
    ++overviewVersion;
    ++detailsVersion;
    overviewController?.abort();
  });

  return {
    groups,
    mine,
    requests,
    courses,
    selected,
    leaderRequests,
    loading,
    loaded,
    detailLoading,
    busy,
    error,
    load,
    open,
    closeDetail,
    mutate,
  };
}
