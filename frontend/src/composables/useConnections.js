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

function connectionStatus(studentId) {
  if (
    activeConnections.value.some(
      (connection) =>
        connection.studentAId === studentId ||
        connection.studentBId === studentId,
    )
  )
    return "Connected";
  if (
    incomingRequests.value.some(
      (request) => request.senderId === studentId && request.status === "PENDING",
    )
  )
    return "Accept request";
  if (
    outgoingRequests.value.some(
      (request) => request.recipientId === studentId && request.status === "PENDING",
    )
  )
    return "Request Sent";
  return "Connect";
}

async function loadConnectionsAndRequests({ silent = false } = {}) {
  const currentStudentId = user.value?.userId;
  if (!currentStudentId) return false;

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

    let deletedPartnersCount = 0;

    const [loadedIncoming, loadedOutgoing, loadedConnections] =
      await Promise.all([
        Promise.all(
          incoming
            .filter((req) => req && req.requestId)
            .map(async (request) => {
              try {
                const sender = await fetchStudentOrNull(request.senderId);
                if (sender === null) {
                  // The student who sent this request has deleted their account.
                  // Auto-decline/clean up the orphaned match request so it doesn't linger.
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
        ),
        Promise.all(
          outgoing
            .filter((req) => req && req.requestId)
            .map(async (request) => {
              try {
                const recipient = await fetchStudentOrNull(request.recipientId);
                if (recipient === null) {
                  return null;
                }
                return { ...request, recipientName: recipient.name };
              } catch {
                return { ...request, recipientName: "Former Student" };
              }
            }),
        ),
        Promise.all(
          connections.map(async (connection) => {
            const partnerId =
              connection.studentAId === currentStudentId
                ? connection.studentBId
                : connection.studentAId;
            try {
              // Fetch the partner profile and their contact details in parallel —
              // neither depends on the other, so there is no reason to wait for
              // one before starting the other.
              const [partner, contact] = await Promise.all([
                fetchStudentOrNull(partnerId),
                api(`/api/connections/${connection.connectionId}/contact`).catch(() => ({})),
              ]);
              if (partner === null) {
                // Account confirmed deleted (404) — clean up the orphaned record
                // and exclude it from the list so a notification can be shown.
                deletedPartnersCount++;
                void api(
                  `/api/connections/${connection.connectionId}?requesterId=${encodeURIComponent(currentStudentId)}`,
                  { method: "DELETE" },
                ).catch(() => {});
                return null;
              }
              return {
                ...connection,
                partnerId,
                partnerName: partner.name,
                partnerEmail: contact.email || "",
                partnerContactNumber: contact.contactNumber || "",
              };
            } catch {
              // Transient failure (service restarting, network hiccup, etc.) —
              // never delete a real connection record for a temporary error.
              // Show a placeholder so the connection stays visible and safe.
              return {
                ...connection,
                partnerId,
                partnerName: "Study Buddy",
                partnerEmail: "",
                partnerContactNumber: "",
              };
            }
          }),
        ),
      ]);

    if (user.value?.userId !== currentStudentId) return false;

    incomingRequests.value = loadedIncoming.filter(Boolean);
    outgoingRequests.value = loadedOutgoing.filter(Boolean);
    activeConnections.value = loadedConnections.filter(Boolean);
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

    if (deletedPartnersCount > 0 && !silent) {
      toast(
        deletedPartnersCount === 1
          ? "A study buddy connection was removed because the student's account was deleted."
          : `${deletedPartnersCount} study buddy connections were removed because their accounts were deleted.`,
        "info",
      );
    }

    return true;
  } catch (problem) {
    error.value = `Could not load requests and connections: ${problem.message}`;
    return false;
  } finally {
    if (!silent) loading.value = false;
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

async function connectToStudent(studentId, message = "") {
  const incoming = incomingRequests.value.find(
    (request) => request.senderId === studentId && request.status === "PENDING",
  );
  if (incoming) {
    return acceptMatchRequest(incoming.requestId);
  }
  if (connectionStatus(studentId) !== "Connect") return false;
  return sendMatchRequest(studentId, message);
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
    await loadConnectionsAndRequests();
    if (error.value) {
      toast("Connection ended, but the connections list could not be refreshed.", "error");
      return;
    }
    toast("Connection ended");
  } catch (problem) {
    toast(`Could not end connection: ${problem.message}`, "error");
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
    connectToStudent,
    connectionStatus,
    cancelMatchRequest,
    cancelMatchRequestFor,
    acceptMatchRequest,
    declineMatchRequest,
    endStudyConnection,
  };
}
