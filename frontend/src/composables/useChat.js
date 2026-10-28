import { computed, ref, watch } from "vue";
import { Client } from "@stomp/stompjs";
import { token, user, userRole } from "./useAuth";
import { api } from "../services/api";

// One live chat connection for the whole app, so unread badges update on every page.
// The server only delivers a conversation's messages to its two students.

const SEND_TIMEOUT_MS = 8000;

const summaries = ref([]);
const messagesByConnection = ref({});
const loadingHistory = ref(false);
const connected = ref(false);
const error = ref("");
const openConnectionId = ref(null);

let client = null;
let clientToken = null;
const subscriptions = new Map();
const pendingSends = new Map();

const currentStudentId = computed(() => user.value?.userId || user.value?.id);
const totalUnread = computed(() =>
  summaries.value.reduce((total, summary) => total + summary.unreadCount, 0),
);

function bySentAt(a, b) {
  return new Date(a.sentAt) - new Date(b.sentAt);
}

function updateSummary(connectionId, change) {
  summaries.value = summaries.value.map((summary) =>
    summary.connectionId === connectionId ? { ...summary, ...change(summary) } : summary,
  );
}

function addMessages(connectionId, incoming) {
  const existing = messagesByConnection.value[connectionId];
  if (!existing) return;
  const known = new Set(existing.map((message) => message.messageId));
  const added = incoming.filter((message) => !known.has(message.messageId));
  if (!added.length) return;
  messagesByConnection.value = {
    ...messagesByConnection.value,
    [connectionId]: [...existing, ...added].sort(bySentAt),
  };
}

function conversationIsOnScreen(connectionId) {
  return (
    openConnectionId.value === connectionId &&
    document.visibilityState === "visible"
  );
}

function receive(message) {
  const { connectionId } = message;
  const fromMe = message.senderId === currentStudentId.value;
  addMessages(connectionId, [message]);

  if (fromMe) {
    const pending = pendingSends.get(connectionId);
    if (pending && pending.content === message.content) {
      window.clearTimeout(pending.timer);
      pendingSends.delete(connectionId);
      pending.resolve(true);
    }
  }

  if (!summaries.value.some((summary) => summary.connectionId === connectionId)) {
    void loadSummaries();
    return;
  }
  const readNow = !fromMe && conversationIsOnScreen(connectionId);
  updateSummary(connectionId, (summary) => ({
    lastMessage:
      !summary.lastMessage || bySentAt(summary.lastMessage, message) <= 0
        ? message
        : summary.lastMessage,
    unreadCount: fromMe || readNow ? summary.unreadCount : summary.unreadCount + 1,
  }));
  if (readNow) void markRead(connectionId);
}

function syncSubscriptions() {
  if (!client?.connected) return;
  const wanted = new Set(summaries.value.map((summary) => summary.connectionId));
  for (const [connectionId, subscription] of subscriptions) {
    if (!wanted.has(connectionId)) {
      subscription.unsubscribe();
      subscriptions.delete(connectionId);
    }
  }
  for (const connectionId of wanted) {
    if (subscriptions.has(connectionId)) continue;
    subscriptions.set(
      connectionId,
      client.subscribe(`/topic/connections/${connectionId}`, (frame) => {
        try {
          receive(JSON.parse(frame.body));
        } catch {
          error.value = "A chat update could not be read.";
        }
      }),
    );
  }
}

async function loadSummaries() {
  if (!token.value) return;
  try {
    const loaded = await api("/api/connections/chats");
    // A list fetched just before a mark-read lands can still count the open chat as unread.
    summaries.value = loaded.map((summary) => {
      if (!summary.unreadCount || !conversationIsOnScreen(summary.connectionId)) return summary;
      void markRead(summary.connectionId);
      return { ...summary, unreadCount: 0 };
    });
    syncSubscriptions();
  } catch (problem) {
    if (problem.name !== "AbortError") error.value = problem.message;
  }
}

async function loadHistory(connectionId) {
  loadingHistory.value = true;
  try {
    const history = await api(`/api/connections/${connectionId}/messages`);
    messagesByConnection.value = {
      ...messagesByConnection.value,
      [connectionId]: [...(messagesByConnection.value[connectionId] || [])],
    };
    addMessages(connectionId, history);
  } catch (problem) {
    if (problem.name !== "AbortError") error.value = problem.message;
  } finally {
    loadingHistory.value = false;
  }
}

async function markRead(connectionId) {
  updateSummary(connectionId, () => ({ unreadCount: 0 }));
  try {
    await api(`/api/connections/${connectionId}/read`, { method: "POST" });
  } catch {
    // The badge clears locally; the server catches up the next time the chat is opened.
  }
}

function openConversation(connectionId) {
  openConnectionId.value = connectionId;
  error.value = "";
  if (!connectionId) return;
  void loadHistory(connectionId);
  void markRead(connectionId);
}

/** Resolves true once the server echoes the message back, false if it never arrives. */
function sendMessage(connectionId, content) {
  if (!client?.connected || pendingSends.has(connectionId)) return Promise.resolve(false);
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => {
      pendingSends.delete(connectionId);
      error.value = "Your message could not be sent. Please try again.";
      resolve(false);
    }, SEND_TIMEOUT_MS);
    pendingSends.set(connectionId, { content, timer, resolve });
    error.value = "";
    client.publish({
      destination: `/app/connections/${connectionId}/send`,
      body: JSON.stringify({ content }),
    });
  });
}

function onVisibilityChange() {
  const connectionId = openConnectionId.value;
  if (!connectionId || document.visibilityState !== "visible") return;
  const summary = summaries.value.find((item) => item.connectionId === connectionId);
  if (summary?.unreadCount) void markRead(connectionId);
}

function startChat() {
  if (!token.value || userRole.value !== "student") return;
  if (client && clientToken === token.value) return;
  stopChat();
  clientToken = token.value;
  const socketUrl = new URL("/ws", window.location.href);
  socketUrl.protocol = socketUrl.protocol === "https:" ? "wss:" : "ws:";
  // A previous account's client keeps firing callbacks while it shuts down; ignore those.
  const isCurrent = () => client === thisClient;
  const thisClient = new Client({
    brokerURL: socketUrl.toString(),
    connectHeaders: { Authorization: `Bearer ${clientToken}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      if (!isCurrent()) return;
      connected.value = true;
      error.value = "";
      subscriptions.clear();
      // Catch up on anything sent while the socket was down.
      void loadSummaries();
      if (openConnectionId.value) void loadHistory(openConnectionId.value);
    },
    onWebSocketClose: () => {
      if (!isCurrent()) return;
      connected.value = false;
      subscriptions.clear();
    },
    onStompError: (frame) => {
      if (!isCurrent()) return;
      error.value = frame.headers.message || "Chat connection failed. Please retry.";
    },
  });
  client = thisClient;
  client.activate();
  document.addEventListener("visibilitychange", onVisibilityChange);
}

function stopChat() {
  document.removeEventListener("visibilitychange", onVisibilityChange);
  void client?.deactivate();
  client = null;
  clientToken = null;
  subscriptions.clear();
  for (const pending of pendingSends.values()) {
    window.clearTimeout(pending.timer);
    pending.resolve(false);
  }
  pendingSends.clear();
  summaries.value = [];
  messagesByConnection.value = {};
  openConnectionId.value = null;
  connected.value = false;
  error.value = "";
}

// Signing out or switching account must never keep the previous student's socket open.
watch(token, (sessionToken) => {
  if (sessionToken !== clientToken) stopChat();
});

export function useChat() {
  return {
    summaries,
    messagesByConnection,
    loadingHistory,
    connected,
    error,
    openConnectionId,
    currentStudentId,
    totalUnread,
    startChat,
    loadSummaries,
    openConversation,
    sendMessage,
  };
}
