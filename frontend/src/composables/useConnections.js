import { ref, watch } from "vue";
import { user, token } from "./useAuth";
import { api } from "../services/api";
import { toast } from "./useNotifications";

const studentProfileCache = new Map();

/**
 * Fetch a student's public profile with in-memory caching.
 *
 * Returns null when the account has been definitively deleted (HTTP 404).
 * Re-throws for any other error (e.g. service temporarily down, 500) so the
 * caller can show a placeholder instead of removing the connection.
 */
async function fetchStudentOrNull(studentId) {
  if (studentProfileCache.has(studentId)) {
    return studentProfileCache.get(studentId);
  }
  const session = token.value;
  const headers = session ? { Authorization: `Bearer ${session}` } : {};
  const response = await fetch(`/api/students/${encodeURIComponent(studentId)}`, { headers });
  if (response.status === 404) return null; // account definitively deleted
  if (!response.ok) throw new Error(`Request failed (${response.status})`);
  const data = await response.json();
  studentProfileCache.set(studentId, data);
  return data;
}

const incomingRequests = ref([]);
const outgoingRequests = ref([]);
const activeConnections = ref([]);
const loading = ref(false);
const cancellingRequestIds = ref(new Set());
const error = ref("");
const unreadConnectionUpdates = ref(0);
const hasLoadedOnce = ref(false);
let cachedUserId = null;
let lastAcknowledgedPendingCount = 0;
let connectionLoadVersion = 0;

function resetConnectionsCache() {
  incomingRequests.value = [];
  outgoingRequests.value = [];
  activeConnections.value = [];
  loading.value = false;
  error.value = "";
  unreadConnectionUpdates.value = 0;
  hasLoadedOnce.value = false;
  cachedUserId = null;
  lastAcknowledgedPendingCount = 0;
  studentProfileCache.clear();
}

// Automatically reset cached connections whenever the logged-in user changes or signs out
watch(
  () => user.value?.userId,
  (newId, oldId) => {
    if (newId !== oldId) {
      resetConnectionsCache();
    }
  },
);

function markConnectionUpdatesRead() {
  lastAcknowledgedPendingCount = Math.max(
    incomingRequests.value.length,
    unreadConnectionUpdates.value,
    lastAcknowledgedPendingCount,
  );
  unreadConnectionUpdates.value = 0;
}

async function checkForConnectionUpdates() {
  const studentId = user.value?.userId;
  if (!studentId) return false;
  try {
    const res = await api(`/api/match-requests/pending/count`);
    if (user.value?.userId !== studentId) return false;
    const serverCount = res.count ?? 0;
    // Only show unread badge if new pending requests have arrived beyond what was acknowledged
    if (serverCount > lastAcknowledgedPendingCount) {
      unreadConnectionUpdates.value = serverCount - lastAcknowledgedPendingCount;
    } else {
      unreadConnectionUpdates.value = 0;
      if (serverCount < lastAcknowledgedPendingCount) {
        lastAcknowledgedPendingCount = serverCount;
      }
    }
    return true;
  } catch (problem) {
    console.error("Could not check for connection updates.", problem);
    return false;
  }
}

function updateRequestList(list, loadedItems) {
  const updates = new Map(loadedItems.map((item) => [item.requestId, item]));
  return list
    .map((item) => updates.get(item.requestId) ?? item)
    .filter((item) => updates.has(item.requestId) && updates.get(item.requestId));
}

async function enrichConnectionsAndRequests({
  incoming,
  outgoing,
  connections,
  currentStudentId,
  loadVersion,
  silent,
}) {
  const isCurrentLoad = () =>
    user.value?.userId === currentStudentId &&
    loadVersion === connectionLoadVersion;

  const loadedIncoming = await Promise.all(
    incoming.map(async (request) => {
      try {
        const sender = await fetchStudentOrNull(request.senderId);
        if (!isCurrentLoad()) return null;
        if (sender === null) {
          void api(
            `/api/match-requests/${request.requestId}/decline?respondentId=${encodeURIComponent(currentStudentId)}`,
            { method: "POST" },
          ).catch(() => {});
          return null;
        }
        return { ...request, senderName: sender.name };
      } catch {
        return { ...request, senderName: "Former Student" };
      }
    }),
  );
  if (!isCurrentLoad()) return;
  incomingRequests.value = updateRequestList(
    incomingRequests.value,
    loadedIncoming.filter(Boolean),
  );

  const loadedOutgoing = await Promise.all(
    outgoing.map(async (request) => {
      try {
        const recipient = await fetchStudentOrNull(request.recipientId);
        if (!isCurrentLoad()) return null;
        if (recipient === null) return null;
        return { ...request, recipientName: recipient.name };
      } catch {
        return { ...request, recipientName: "Former Student" };
      }
    }),
  );
  if (!isCurrentLoad()) return;
  outgoingRequests.value = updateRequestList(
    outgoingRequests.value,
    loadedOutgoing.filter(Boolean),
  );

  let deletedPartnersCount = 0;
  const loadedConnections = await Promise.all(
    connections.map(async (connection) => {
      const partnerId =
        connection.studentAId === currentStudentId
          ? connection.studentBId
          : connection.studentAId;
      try {
        const partner = await fetchStudentOrNull(partnerId);
        if (!isCurrentLoad()) return null;
        if (partner === null) {
          deletedPartnersCount++;
          void api(
            `/api/connections/${connection.connectionId}?requesterId=${encodeURIComponent(currentStudentId)}`,
            { method: "DELETE" },
          ).catch(() => {});
          return null;
        }
        const enriched = {
          ...connection,
          partnerId,
          partnerName: partner.name,
        };
        if (isCurrentLoad()) {
          activeConnections.value = activeConnections.value.map((item) =>
            item.connectionId === connection.connectionId ? enriched : item,
          );
        }
        try {
          const contact = await api(
            `/api/connections/${connection.connectionId}/contact`,
          );
          return {
            ...enriched,
            partnerEmail: contact.email || "",
            partnerContactNumber: contact.contactNumber || "",
          };
        } catch {
          return enriched;
        }
      } catch {
        return {
          ...connection,
          partnerId,
          partnerName: "Study Buddy",
          partnerEmail: "",
          partnerContactNumber: "",
        };
      }
    }),
  );
  if (!isCurrentLoad()) return;
  const connectionUpdates = new Map(
    loadedConnections
      .filter(Boolean)
      .map((connection) => [connection.connectionId, connection]),
  );
  activeConnections.value = activeConnections.value
    .map((connection) => connectionUpdates.get(connection.connectionId) ?? connection)
    .filter((connection) => connectionUpdates.has(connection.connectionId));

  if (deletedPartnersCount > 0 && !silent) {
    toast(
      deletedPartnersCount === 1
        ? "A study buddy connection was removed because the student's account was deleted."
        : `${deletedPartnersCount} study buddy connections were removed because their accounts were deleted.`,
      "info",
    );
  }
}

async function loadConnectionsAndRequests({ silent = false } = {}) {
  const currentStudentId = user.value?.userId;
  if (!currentStudentId) return false;
  const loadVersion = ++connectionLoadVersion;

  // If the user changed, reset before loading
  if (cachedUserId && cachedUserId !== currentStudentId) {
    resetConnectionsCache();
  }

  // If we've already loaded once for this user, keep the existing data visible and refresh in background
  if (!silent && !hasLoadedOnce.value) loading.value = true;
  error.value = "";
  try {
    const studentId = encodeURIComponent(currentStudentId);
    const [incoming, outgoing, connections] = await Promise.all([
      api(`/api/match-requests/pending?studentId=${studentId}`),
      api(`/api/match-requests/outgoing-pending?studentId=${studentId}`),
      api(`/api/connections?studentId=${studentId}`),
    ]);

    if (user.value?.userId !== currentStudentId) return false;
    if (loadVersion !== connectionLoadVersion) return true;

    const visibleIncoming = incoming.filter((req) => req && req.requestId);
    const visibleOutgoing = outgoing.filter((req) => req && req.requestId);
    const previousConnections = new Map(
      activeConnections.value.map((connection) => [
        connection.connectionId,
        connection,
      ]),
    );
    const visibleConnections = connections.map((connection) => {
      const partnerId =
        connection.studentAId === currentStudentId
          ? connection.studentBId
          : connection.studentAId;
      const previous = previousConnections.get(connection.connectionId);
      return {
        ...previous,
        ...connection,
        partnerId,
        partnerName: previous?.partnerName || "Study Buddy",
        partnerEmail: previous?.partnerEmail || "",
        partnerContactNumber: previous?.partnerContactNumber || "",
      };
    });
    incomingRequests.value = visibleIncoming.map((request) => ({
      ...request,
      senderName: "Former Student",
    }));
    outgoingRequests.value = visibleOutgoing.map((request) => ({
      ...request,
      recipientName: "Former Student",
    }));
    activeConnections.value = visibleConnections;
    const incomingCount = incomingRequests.value.length;
    if (incomingCount > lastAcknowledgedPendingCount) {
      unreadConnectionUpdates.value = incomingCount - lastAcknowledgedPendingCount;
    } else {
      unreadConnectionUpdates.value = 0;
      if (incomingCount < lastAcknowledgedPendingCount) {
        lastAcknowledgedPendingCount = incomingCount;
      }
    }
    hasLoadedOnce.value = true;
    cachedUserId = currentStudentId;
    void enrichConnectionsAndRequests({
      incoming: visibleIncoming,
      outgoing: visibleOutgoing,
      connections,
      currentStudentId,
      loadVersion,
      silent,
    });

    return true;
  } catch (problem) {
    if (user.value?.userId !== currentStudentId) return false;
    if (loadVersion !== connectionLoadVersion) return true;
    error.value = `Could not load requests and connections: ${problem.message}`;
    return false;
  } finally {
    if (loadVersion === connectionLoadVersion) loading.value = false;
  }
}

async function sendMatchRequest(recipientId, message = "") {
  try {
    const params = new URLSearchParams({
      senderId: user.value.userId,
      recipientId,
      message,
    });
    const result = await api(`/api/match-requests?${params}`, { method: "POST" });
    await loadConnectionsAndRequests();
    if (error.value) {
      toast("Request sent, but the connections list could not be refreshed.", "error");
      return true;
    }
    toast(
      result.status === "ACCEPTED"
        ? "Connection request accepted!"
        : "Match request sent!",
    );
    return true;
  } catch (problem) {
    toast(`Could not send match request: ${problem.message}`, "error");
    return false;
  }
}

async function cancelMatchRequest(requestId) {
  if (cancellingRequestIds.value.has(requestId)) return false;
  cancellingRequestIds.value = new Set(cancellingRequestIds.value).add(requestId);
  try {
    await api(`/api/match-requests/${requestId}`, { method: "DELETE" });
    outgoingRequests.value = outgoingRequests.value.filter(
      (request) => request.requestId !== requestId,
    );
    await loadConnectionsAndRequests({ silent: true });
    if (error.value) {
      toast("Request cancelled, but the connections list could not be refreshed.", "error");
      return true;
    }
    toast("Connection request cancelled.");
    return true;
  } catch (problem) {
    toast(`Could not cancel connection request: ${problem.message}`, "error");
    return false;
  } finally {
    const pending = new Set(cancellingRequestIds.value);
    pending.delete(requestId);
    cancellingRequestIds.value = pending;
  }
}

function cancelMatchRequestFor(studentId) {
  const request = outgoingRequests.value.find(
    (item) => item.recipientId === studentId && item.status === "PENDING",
  );
  if (!request) return Promise.resolve(false);
  return cancelMatchRequest(request.requestId);
}

async function respondToMatchRequest(requestId, action) {
  try {
    const respondentId = encodeURIComponent(user.value.userId);
    await api(
      `/api/match-requests/${requestId}/${action}?respondentId=${respondentId}`,
      { method: "POST" },
    );
    await loadConnectionsAndRequests();
    if (error.value) {
      toast(
        action === "accept"
          ? "Request accepted, but the connections list could not be refreshed."
          : "Request declined, but the connections list could not be refreshed.",
        "error",
      );
      return false;
    }
    toast(action === "accept" ? "Match request accepted!" : "Match request declined");
    return true;
  } catch (problem) {
    toast(`Could not ${action} request: ${problem.message}`, "error");
    return false;
  }
}

function acceptMatchRequest(requestId) {
  return respondToMatchRequest(requestId, "accept");
}

function declineMatchRequest(requestId) {
  return respondToMatchRequest(requestId, "decline");
}

async function endStudyConnection(connectionId) {
  try {
    const requesterId = encodeURIComponent(user.value.userId);
    await api(`/api/connections/${connectionId}?requesterId=${requesterId}`, {
      method: "DELETE",
    });
    activeConnections.value = activeConnections.value.filter(
      (connection) => connection.connectionId !== connectionId,
    );
    if (!(await loadConnectionsAndRequests({ silent: true }))) {
      toast("Connection ended, but the connections list could not be refreshed.", "error");
      return false;
    }
    toast("Connection ended");
    return true;
  } catch (problem) {
    toast(`Could not end connection: ${problem.message}`, "error");
    return false;
  }
}

export function useConnections() {
  return {
    incomingRequests,
    outgoingRequests,
    activeConnections,
    loading,
    cancellingRequestIds,
    error,
    unreadConnectionUpdates,
    hasLoadedOnce,
    resetConnectionsCache,
    loadConnectionsAndRequests,
    checkForConnectionUpdates,
    markConnectionUpdatesRead,
    sendMatchRequest,
    cancelMatchRequest,
    cancelMatchRequestFor,
    acceptMatchRequest,
    declineMatchRequest,
    endStudyConnection,
  };
}
