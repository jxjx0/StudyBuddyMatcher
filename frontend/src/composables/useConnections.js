import { ref } from "vue";
import { user } from "./useAuth";
import { api } from "../services/api";
import { toast } from "./useNotifications";

const incomingRequests = ref([]);
const outgoingRequests = ref([]);
const activeConnections = ref([]);
const loading = ref(false);
const error = ref("");
const unreadConnectionUpdates = ref(0);
const notificationStateByStudent = new Map();

function updateNotificationState(studentId, incoming) {
  const incomingIds = new Set(incoming.map((request) => request.requestId));
  let state = notificationStateByStudent.get(studentId);

  if (!state) {
    state = {
      incomingIds,
      unreadIds: new Set(),
    };
    notificationStateByStudent.set(studentId, state);
  } else {
    for (const requestId of incomingIds) {
      if (!state.incomingIds.has(requestId))
        state.unreadIds.add(`request:${requestId}`);
    }
    for (const requestId of state.incomingIds) {
      if (!incomingIds.has(requestId))
        state.unreadIds.delete(`request:${requestId}`);
    }
    state.incomingIds = incomingIds;
  }

  if (user.value?.userId === studentId)
    unreadConnectionUpdates.value = state.unreadIds.size;
}

function markConnectionUpdatesRead() {
  const studentId = user.value?.userId;
  if (!studentId) return;
  const state = notificationStateByStudent.get(studentId);
  if (state) state.unreadIds.clear();
  unreadConnectionUpdates.value = 0;
}

async function checkForConnectionUpdates() {
  const studentId = user.value?.userId;
  if (!studentId) return false;
  try {
    const encodedId = encodeURIComponent(studentId);
    const incoming = await api(
      `/api/match-requests/pending?studentId=${encodedId}`,
    );
    if (user.value?.userId !== studentId) return false;
    updateNotificationState(studentId, incoming);
    return true;
  } catch (problem) {
    console.error("Could not check for connection updates.", problem);
    return false;
  }
}

async function loadConnectionsAndRequests({ silent = false } = {}) {
  const currentStudentId = user.value?.userId;
  if (!currentStudentId) return false;
  if (!silent) loading.value = true;
  error.value = "";
  try {
    const studentId = encodeURIComponent(currentStudentId);
    const [incoming, outgoing, connections] = await Promise.all([
      api(`/api/match-requests/pending?studentId=${studentId}`),
      api(`/api/match-requests/outgoing-pending?studentId=${studentId}`),
      api(`/api/connections?studentId=${studentId}`),
    ]);
    const [loadedIncoming, loadedOutgoing, loadedConnections] =
      await Promise.all([
        Promise.all(
          incoming.map(async (request) => {
            const sender = await api(`/api/students/${request.senderId}`);
            return { ...request, senderName: sender.name };
          }),
        ),
        Promise.all(
          outgoing.map(async (request) => {
            const recipient = await api(`/api/students/${request.recipientId}`);
            return { ...request, recipientName: recipient.name };
          }),
        ),
        Promise.all(
          connections.map(async (connection) => {
            const partnerId =
              connection.studentAId === currentStudentId
                ? connection.studentBId
                : connection.studentAId;
            const [partner, contact] = await Promise.all([
              api(`/api/students/${partnerId}`),
              api(`/api/connections/${connection.connectionId}/contact`),
            ]);
            return {
              ...connection,
              partnerName: partner.name,
              partnerEmail: contact.email,
              partnerContactNumber: contact.contactNumber,
            };
          }),
        ),
      ]);
    if (user.value?.userId !== currentStudentId) return false;
    incomingRequests.value = loadedIncoming;
    outgoingRequests.value = loadedOutgoing;
    activeConnections.value = loadedConnections;
    updateNotificationState(currentStudentId, loadedIncoming);
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
    await api(`/api/match-requests?${params}`, { method: "POST" });
    await loadConnectionsAndRequests();
    if (error.value) {
      toast("Request sent, but the connections list could not be refreshed.", "error");
      return true;
    }
    toast("Match request sent!");
    return true;
  } catch (problem) {
    toast(`Could not send match request: ${problem.message}`, "error");
    return false;
  }
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
      return;
    }
    toast(action === "accept" ? "Match request accepted!" : "Match request declined");
  } catch (problem) {
    toast(`Could not ${action} request: ${problem.message}`, "error");
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
    error,
    unreadConnectionUpdates,
    loadConnectionsAndRequests,
    checkForConnectionUpdates,
    markConnectionUpdatesRead,
    sendMatchRequest,
    acceptMatchRequest,
    declineMatchRequest,
    endStudyConnection,
  };
}
