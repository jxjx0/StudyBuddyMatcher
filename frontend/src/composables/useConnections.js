import { ref } from "vue";
import { user } from "./useAuth";
import { api } from "../services/api";
import { toast } from "./useNotifications";

const incomingRequests = ref([]);
const outgoingRequests = ref([]);
const activeConnections = ref([]);
const loading = ref(false);
const cancellingRequestIds = ref(new Set());
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
    const [incoming, outgoing] = await Promise.all([
      api(`/api/match-requests/pending?studentId=${encodedId}`),
      api(`/api/match-requests/outgoing-pending?studentId=${encodedId}`),
    ]);
    if (user.value?.userId !== studentId) return false;
    incomingRequests.value = incoming;
    outgoingRequests.value = outgoing;
    updateNotificationState(studentId, incoming);
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
