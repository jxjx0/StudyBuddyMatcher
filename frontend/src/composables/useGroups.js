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
  let detailController = null;

  function rememberOverview() {
    if (!loaded.value) return;
    cachedOverview = {
      sessionToken: token.value,
      overview: {
        groups: groups.value,
        mine: mine.value,
        requests: requests.value,
      },
      courseList: courses.value,
    };
  }

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

  function isCurrentDetail(sessionToken, requestVersion) {
    return isCurrentSession(sessionToken) && requestVersion === detailsVersion;
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
    const sessionToken = token.value;
    const startingDetailsVersion = ++detailsVersion;
    detailController?.abort();
    detailController = new AbortController();
    // The overview already contains the description, availability and members.
    // Show them now; actions wait for the server to verify the latest permissions.
    selected.value =
      groups.value.find((group) => group.groupId === groupId) ||
      mine.value.find((group) => group.groupId === groupId) ||
      null;
    leaderRequests.value = [];
    detailLoading.value = true;
    error.value = "";
    try {
      const details = await api(`/api/groups/${groupId}/details`, {
        signal: detailController.signal,
      });
      if (!isCurrentDetail(sessionToken, startingDetailsVersion)) {
        return false;
      }
      selected.value = details.group;
      leaderRequests.value = details.requests;
      return true;
    } catch (problem) {
      if (
        isCurrentDetail(sessionToken, startingDetailsVersion) &&
        problem.name !== "AbortError"
      ) {
        // A cached preview must not leave stale actions available after verification fails.
        selected.value = null;
        leaderRequests.value = [];
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
    detailController?.abort();
    detailLoading.value = false;
    ++detailsVersion;
    selected.value = null;
    leaderRequests.value = [];
  }

  function stopPendingReads() {
    // Reads begun before the write must not overwrite the confirmed result afterwards.
    ++overviewVersion;
    overviewController?.abort();
    loading.value = false;
    ++detailsVersion;
    detailController?.abort();
    detailLoading.value = false;
  }

  function updateGroupRequestState(groupId, requestStatus) {
    function updateGroup(group) {
      if (group.groupId !== groupId) return group;

      const canRequest =
        requestStatus === "CANCELLED" &&
        group.status === "ACTIVE" &&
        group.leaderAvailable &&
        group.memberCount < group.maxGroupSize &&
        !group.isMember;

      return { ...group, myRequestStatus: requestStatus, canRequest };
    }

    groups.value = groups.value.map(updateGroup);
    mine.value = mine.value.map(updateGroup);
    if (selected.value?.groupId === groupId) {
      selected.value = updateGroup(selected.value);
    }
  }

  function addPendingRequest(savedRequest) {
    const alreadyListed = requests.value.some(
      (request) =>
        request.groupJoinRequestId === savedRequest.groupJoinRequestId,
    );
    if (alreadyListed) return;

    const group =
      groups.value.find((group) => group.groupId === savedRequest.groupId) ||
      selected.value;
    const newRequest = {
      groupJoinRequestId: savedRequest.groupJoinRequestId,
      studentId: savedRequest.studentId,
      groupId: savedRequest.groupId,
      groupName: group?.groupName || "",
      status: savedRequest.status,
      createdAt: savedRequest.createdAt,
      respondedAt: savedRequest.respondedAt,
    };
    requests.value = [newRequest, ...requests.value];
  }

  function markRequestCancelled(savedRequest) {
    requests.value = requests.value.map((request) => {
      if (request.groupJoinRequestId !== savedRequest.groupJoinRequestId) {
        return request;
      }
      return {
        ...request,
        status: savedRequest.status,
        respondedAt: savedRequest.respondedAt,
      };
    });
  }

  function applySavedJoinRequest(path, method, savedRequest) {
    const requestPath = path.match(/^\/api\/groups\/([^/]+)\/join-requests$/);
    const requestWasSent =
      method === "POST" && savedRequest?.status === "PENDING";
    const requestWasCancelled =
      method === "DELETE" && savedRequest?.status === "CANCELLED";

    if (
      !requestPath ||
      (!requestWasSent && !requestWasCancelled) ||
      !savedRequest?.groupJoinRequestId ||
      savedRequest.groupId !== requestPath[1]
    ) {
      return false;
    }

    updateGroupRequestState(savedRequest.groupId, savedRequest.status);
    if (requestWasSent) {
      addPendingRequest(savedRequest);
    } else {
      markRequestCancelled(savedRequest);
    }
    rememberOverview();
    return true;
  }

  async function refreshAfterMutation({
    groupId,
    dismissDetail,
    startingDetailsVersion,
    sessionToken,
  }) {
    if (dismissDetail) closeDetail();
    const shouldRefreshDetail =
      groupId && !dismissDetail && startingDetailsVersion === detailsVersion;
    const refreshed = await Promise.all([
      load(),
      shouldRefreshDetail ? open(groupId) : Promise.resolve(true),
    ]);
    if (!isCurrentSession(sessionToken)) return false;

    const refreshSucceeded = refreshed.every(Boolean);
    if (!refreshSucceeded) {
      // The write succeeded. Do not leave stale actions available or invite a second save.
      closeDetail();
      cachedOverview = null;
      clearOverview();
      error.value =
        "Your change was saved, but the latest groups could not be loaded. Refresh to see the current state.";
    }
    return true;
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
    stopPendingReads();
    const startingDetailsVersion = detailsVersion;
    try {
      const requestOptions = { method };
      if (body) requestOptions.body = JSON.stringify(body);
      const result = await api(path, requestOptions);
      if (token.value === sessionToken) cachedOverview = null;
      if (!isCurrentSession(sessionToken)) return false;
      if (applySavedJoinRequest(path, method, result)) return true;
      return await refreshAfterMutation({
        groupId,
        dismissDetail,
        startingDetailsVersion,
        sessionToken,
      });
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
    detailController?.abort();
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
