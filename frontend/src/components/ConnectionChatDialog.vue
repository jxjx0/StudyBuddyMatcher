<script setup>
import { computed, nextTick, ref, watch } from "vue";
import { Client } from "@stomp/stompjs";
import AppDialog from "./AppDialog.vue";
import { api } from "../services/api";
import { token, user } from "../composables/useAuth";

const props = defineProps({
  connection: Object,
});
const emit = defineEmits(["close"]);
const messages = ref([]);
const draft = ref("");
const loading = ref(false);
const connected = ref(false);
const sending = ref(false);
const error = ref("");
const messageList = ref(null);
const currentStudentId = computed(() => user.value?.userId || user.value?.id);
let client;
let activeConnectionToken;

function senderName(message) {
  return message.senderId === currentStudentId.value
    ? user.value?.name || "You"
    : props.connection?.partnerName || "Study buddy";
}

function senderInitials(message) {
  return senderName(message)
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function addMessage(message) {
  if (!messages.value.some((item) => item.messageId === message.messageId)) {
    messages.value = [...messages.value, message].sort(
      (a, b) => new Date(a.sentAt) - new Date(b.sentAt),
    );
  }
}

async function scrollToLatest() {
  await nextTick();
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight;
  }
}

async function loadHistory(connectionId, isCurrent) {
  loading.value = true;
  try {
    const history = await api(`/api/connections/${connectionId}/messages`);
    if (!isCurrent()) return;
    for (const message of history) addMessage(message);
    await scrollToLatest();
  } catch (problem) {
    if (isCurrent()) error.value = problem.message;
  } finally {
    if (isCurrent()) loading.value = false;
  }
}

watch(
  () => props.connection?.connectionId,
  (connectionId, _previous, onCleanup) => {
    if (!connectionId) return;

    let active = true;
    const connectionToken = token.value;
    activeConnectionToken = connectionToken;
    const isCurrent = () =>
      active &&
      props.connection?.connectionId === connectionId &&
      Boolean(connectionToken) &&
      token.value === connectionToken;
    messages.value = [];
    error.value = "";
    connected.value = false;
    loading.value = true;

    const socketUrl = new URL("/ws", window.location.href);
    socketUrl.protocol = socketUrl.protocol === "https:" ? "wss:" : "ws:";
    client = new Client({
      brokerURL: socketUrl.toString(),
      connectHeaders: { Authorization: `Bearer ${connectionToken}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        if (!isCurrent()) return;
        connected.value = true;
        error.value = "";
        client.subscribe(
          `/topic/connections/${connectionId}`,
          (frame) => {
            if (!isCurrent()) return;
            try {
              addMessage(JSON.parse(frame.body));
              void scrollToLatest();
            } catch {
              error.value = "A chat update could not be read.";
            }
          },
        );
        void loadHistory(connectionId, isCurrent);
      },
      onWebSocketClose: () => {
        if (isCurrent()) connected.value = false;
      },
      onStompError: (frame) => {
        if (isCurrent()) {
          error.value =
            frame.headers.message || "Chat connection failed. Please retry.";
        }
      },
    });

    const activeClient = client;
    activeClient.activate();
    onCleanup(() => {
      active = false;
      if (activeConnectionToken === connectionToken) {
        activeConnectionToken = null;
      }
      connected.value = false;
      void activeClient.deactivate();
    });
  },
  { immediate: true },
);

watch(token, (sessionToken) => {
  if (sessionToken !== activeConnectionToken) {
    void client?.deactivate();
    connected.value = false;
    error.value = "Your session has ended. Sign in again to continue chatting.";
  }
});

function sendMessage() {
  const content = draft.value.trim();
  if (!content || !connected.value || sending.value || content.length > 2000) {
    return;
  }
  sending.value = true;
  try {
    client.publish({
      destination: `/app/connections/${props.connection.connectionId}/send`,
      body: JSON.stringify({ content }),
    });
    draft.value = "";
  } catch (problem) {
    error.value = problem.message || "Message could not be sent.";
  } finally {
    sending.value = false;
  }
}
</script>

<template>
  <AppDialog
    :open="Boolean(connection)"
    :title="`Chat with ${connection?.partnerName || 'your study buddy'}`"
    close-label="Close chat"
    @close="emit('close')"
  >
    <div class="chat-dialog">
      <p class="chat-connection-status" role="status">
        {{ connected ? "Live chat connected" : "Connecting to live chat…" }}
      </p>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <div ref="messageList" class="chat-message-list" aria-live="polite">
        <p v-if="loading && !messages.length" class="chat-empty-state">
          Loading conversation…
        </p>
        <p v-else-if="!messages.length" class="chat-empty-state">
          No messages yet. Start the conversation.
        </p>
        <article
          v-for="message in messages"
          :key="message.messageId"
          class="chat-message"
          :class="{
            'chat-message-own': message.senderId === currentStudentId,
          }"
        >
          <span
            class="chat-message-avatar"
            role="img"
            :aria-label="senderName(message)"
          >
            {{ senderInitials(message) }}
          </span>
          <div class="chat-message-bubble">
            <p>{{ message.content }}</p>
            <time :datetime="message.sentAt">
              {{ new Date(message.sentAt).toLocaleString() }}
            </time>
          </div>
        </article>
      </div>
      <form class="chat-composer" @submit.prevent="sendMessage">
        <label class="field">
          <span>Message</span>
          <textarea
            v-model="draft"
            rows="2"
            maxlength="2000"
            :disabled="!connected"
            placeholder="Write a message…"
            required
          ></textarea>
        </label>
        <div class="chat-composer-footer">
          <small>{{ draft.length }}/2000</small>
          <button
            type="submit"
            class="primary-button"
            :disabled="!connected || sending || !draft.trim()"
          >
            {{ sending ? "Sending…" : "Send" }}
          </button>
        </div>
      </form>
    </div>
  </AppDialog>
</template>

<style scoped>
.chat-dialog {
  display: grid;
  gap: 12px;
}

.chat-connection-status,
.chat-empty-state {
  margin: 0;
  color: var(--muted);
  text-align: center;
}

.chat-message-list {
  display: flex;
  min-height: 240px;
  max-height: min(50vh, 460px);
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}

.chat-message {
  display: flex;
  max-width: 92%;
  align-items: flex-end;
  align-self: flex-start;
  gap: 8px;
}

.chat-message-own {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-message-avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 50%;
  background: #e9dff5;
  color: #654781;
  font-size: 0.7rem;
  font-weight: 700;
}

.chat-message-own .chat-message-avatar {
  background: #f7ddd4;
  color: #924732;
}

.chat-message-bubble {
  min-width: 0;
  padding: 9px 12px;
  border-radius: 12px 12px 12px 3px;
  background: #f0ece6;
  overflow-wrap: anywhere;
}

.chat-message-own .chat-message-bubble {
  border-radius: 12px 12px 3px 12px;
  background: var(--soft);
}

.chat-message-bubble p {
  margin: 0;
  white-space: pre-wrap;
}

.chat-message-bubble time {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 0.75rem;
}

.chat-composer {
  display: grid;
  gap: 8px;
}

.chat-composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--muted);
}
</style>
