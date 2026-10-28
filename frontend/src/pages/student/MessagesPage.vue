<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, MessageCircle } from "lucide-vue-next";
import { useConnections } from "../../composables/useConnections";
import { useChat } from "../../composables/useChat";
import { useAppState } from "../../composables/useAppState";

const route = useRoute();
const router = useRouter();
const { initialsOf } = useAppState();
const { activeConnections, hasLoadedOnce, loadConnectionsAndRequests } =
  useConnections();
const {
  summaries,
  messagesByConnection,
  loadingHistory,
  connected,
  error,
  currentStudentId,
  openConversation,
  sendMessage,
} = useChat();

const draft = ref("");
const sending = ref(false);
const messageList = ref(null);
const loadingList = ref(!hasLoadedOnce.value);

const selectedId = computed(() => route.query.connection || null);

// Every active connection is a conversation, newest activity first.
const conversations = computed(() => {
  const byId = new Map(summaries.value.map((summary) => [summary.connectionId, summary]));
  return activeConnections.value
    .map((connection) => {
      const summary = byId.get(connection.connectionId);
      return {
        connectionId: connection.connectionId,
        partnerName: connection.partnerName || "Study buddy",
        lastMessage: summary?.lastMessage || null,
        unreadCount: summary?.unreadCount || 0,
        activityAt: summary?.lastMessage?.sentAt || connection.startedAt,
      };
    })
    .sort((a, b) => new Date(b.activityAt) - new Date(a.activityAt));
});

const selected = computed(() =>
  conversations.value.find((item) => item.connectionId === selectedId.value),
);
const messages = computed(() => messagesByConnection.value[selectedId.value] || []);

function selectConversation(connectionId) {
  router.replace({ query: connectionId ? { connection: connectionId } : {} });
}

function preview(conversation) {
  const message = conversation.lastMessage;
  if (!message) return "No messages yet";
  return message.senderId === currentStudentId.value
    ? `You: ${message.content}`
    : message.content;
}

function shortTime(value) {
  if (!value) return "";
  const date = new Date(value);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }
  const days = (now - date) / 86_400_000;
  if (days < 7) return date.toLocaleDateString([], { weekday: "short" });
  return date.toLocaleDateString([], { day: "numeric", month: "short" });
}

async function scrollToLatest() {
  await nextTick();
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight;
}

async function submit() {
  const content = draft.value.trim();
  if (!content || content.length > 2000 || sending.value || !selectedId.value) return;
  sending.value = true;
  try {
    // The draft is kept until the server confirms, so a failed send never loses the text.
    if (await sendMessage(selectedId.value, content)) draft.value = "";
  } finally {
    sending.value = false;
  }
}

function submitOnEnter(event) {
  if (event.isComposing || event.shiftKey) return;
  event.preventDefault();
  void submit();
}

watch(selectedId, (connectionId) => {
  draft.value = "";
  openConversation(connectionId);
}, { immediate: true });
watch(() => messages.value.length, scrollToLatest);

onMounted(async () => {
  await loadConnectionsAndRequests({ silent: hasLoadedOnce.value });
  loadingList.value = false;
});
onUnmounted(() => openConversation(null));
</script>

<template>
  <section class="messages-page" :class="{ 'has-selection': selected }">
    <aside class="conversation-list" aria-label="Conversations">
      <header class="conversation-list-header">
        <h1>Messages</h1>
      </header>
      <div v-if="loadingList" class="loading-state" role="status">
        <span class="spinner" aria-hidden="true"></span>
        <p>Loading conversations…</p>
      </div>
      <div v-else-if="!conversations.length" class="conversation-empty">
        <MessageCircle :size="28" aria-hidden="true" />
        <p>Connect with a study buddy to start chatting.</p>
        <RouterLink class="secondary-button" :to="{ name: 'student-discover' }">
          Discover buddies
        </RouterLink>
      </div>
      <ul v-else role="list">
        <li v-for="conversation in conversations" :key="conversation.connectionId">
          <button
            class="conversation-item"
            :class="{
              active: conversation.connectionId === selectedId,
              unread: conversation.unreadCount,
            }"
            :aria-current="conversation.connectionId === selectedId ? 'true' : undefined"
            @click="selectConversation(conversation.connectionId)"
          >
            <span class="avatar avatar-purple" aria-hidden="true">
              {{ initialsOf(conversation.partnerName) }}
            </span>
            <span class="conversation-text">
              <span class="conversation-top">
                <strong>{{ conversation.partnerName }}</strong>
                <time
                  v-if="conversation.lastMessage"
                  :datetime="conversation.lastMessage.sentAt"
                >
                  {{ shortTime(conversation.lastMessage.sentAt) }}
                </time>
              </span>
              <span class="conversation-bottom">
                <span class="conversation-preview">{{ preview(conversation) }}</span>
                <span
                  v-if="conversation.unreadCount"
                  class="unread-badge"
                  :aria-label="`${conversation.unreadCount} unread`"
                >
                  {{ conversation.unreadCount > 99 ? "99+" : conversation.unreadCount }}
                </span>
              </span>
            </span>
          </button>
        </li>
      </ul>
    </aside>

    <div class="conversation-pane">
      <div v-if="!selected" class="conversation-placeholder">
        <MessageCircle :size="32" aria-hidden="true" />
        <p>Select a conversation to start chatting.</p>
      </div>
      <template v-else>
        <header class="conversation-header">
          <button
            class="icon-button conversation-back"
            aria-label="Back to conversations"
            @click="selectConversation(null)"
          >
            <ArrowLeft :size="20" />
          </button>
          <span class="avatar avatar-purple" aria-hidden="true">
            {{ initialsOf(selected.partnerName) }}
          </span>
          <div>
            <h2>{{ selected.partnerName }}</h2>
            <p class="conversation-status" role="status">
              {{ connected ? "Online chat connected" : "Connecting…" }}
            </p>
          </div>
        </header>

        <div ref="messageList" class="message-list" aria-live="polite">
          <p v-if="loadingHistory && !messages.length" class="message-empty">
            Loading conversation…
          </p>
          <p v-else-if="!messages.length" class="message-empty">
            No messages yet. Say hi to {{ selected.partnerName }}!
          </p>
          <article
            v-for="message in messages"
            :key="message.messageId"
            class="message"
            :class="{ own: message.senderId === currentStudentId }"
          >
            <div class="message-bubble">
              <p>{{ message.content }}</p>
              <time :datetime="message.sentAt">{{ shortTime(message.sentAt) }}</time>
            </div>
          </article>
        </div>

        <p v-if="error" class="error-message chat-error" role="alert">{{ error }}</p>
        <form class="composer" @submit.prevent="submit">
          <label class="visually-hidden" for="chat-draft">Message</label>
          <textarea
            id="chat-draft"
            v-model="draft"
            rows="1"
            maxlength="2000"
            :disabled="!connected"
            placeholder="Write a message…"
            @keydown.enter="submitOnEnter"
          ></textarea>
          <button
            type="submit"
            class="primary-button"
            :disabled="!connected || sending || !draft.trim()"
          >
            {{ sending ? "Sending…" : "Send" }}
          </button>
        </form>
        <small v-if="draft.length > 1800" class="draft-count">{{ draft.length }}/2000</small>
      </template>
    </div>
  </section>
</template>

<style scoped>
.messages-page {
  display: grid;
  grid-template-columns: minmax(260px, 340px) 1fr;
  height: calc(100vh - 76px);
  height: calc(100dvh - 76px);
  background: var(--surface);
}

.conversation-list {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--line);
}

.conversation-list-header {
  padding: 20px 20px 12px;
}

.conversation-list-header h1 {
  margin: 0;
  font-size: 1.4rem;
}

.conversation-list ul {
  flex: 1;
  margin: 0;
  padding: 0 8px 12px;
  overflow-y: auto;
  list-style: none;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  color: var(--muted);
  font-size: 0.95rem;
}

.loading-state p {
  margin: 0;
}

.spinner {
  display: inline-block;
  width: 22px;
  height: 22px;
  border: 3px solid var(--line);
  border-top-color: var(--accent);
  border-radius: 50%;
  flex-shrink: 0;
  animation: spin 0.75s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.conversation-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 0;
  border-radius: 10px;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

/* Drawn inside the item: the scrolling list would clip the global outline-offset. */
.conversation-item:focus-visible {
  outline-width: 2px;
  outline-offset: -2px;
}

.conversation-item:hover {
  background: #f4efe8;
}

.conversation-item.active {
  background: var(--soft);
}

.conversation-text {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 2px;
}

.conversation-top,
.conversation-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conversation-top strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.conversation-top time {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 0.75rem;
}

.conversation-preview {
  overflow: hidden;
  color: var(--muted);
  font-size: 0.875rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item.unread strong,
.conversation-item.unread .conversation-preview {
  color: inherit;
  font-weight: 700;
}

.conversation-item.unread time {
  color: var(--accent);
  font-weight: 600;
}

.unread-badge {
  display: inline-grid;
  min-width: 21px;
  height: 21px;
  flex: 0 0 auto;
  place-items: center;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--accent);
  color: white;
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1;
}

.conversation-empty,
.conversation-placeholder {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 40px 24px;
  color: var(--muted);
  text-align: center;
}

.conversation-empty p,
.conversation-placeholder p {
  margin: 0;
}

.conversation-empty svg,
.conversation-placeholder svg {
  color: var(--accent);
}

.conversation-placeholder {
  align-content: center;
  height: 100%;
}

.conversation-pane {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: #f8f6f1;
}

.conversation-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.conversation-header h2 {
  margin: 0;
  font-size: 1.05rem;
}

.conversation-status {
  margin: 0;
  color: var(--muted);
  font-size: 0.8rem;
}

.conversation-back {
  display: none;
}

.message-list {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 6px;
  padding: 20px;
  overflow-y: auto;
}

.message-empty {
  margin: auto;
  color: var(--muted);
  text-align: center;
}

.message {
  display: flex;
  max-width: min(75%, 560px);
  align-self: flex-start;
}

.message.own {
  align-self: flex-end;
}

.message-bubble {
  min-width: 0;
  padding: 8px 12px;
  border-radius: 14px 14px 14px 4px;
  background: var(--surface);
  box-shadow: 0 1px 0 var(--line);
  overflow-wrap: anywhere;
}

.message.own .message-bubble {
  border-radius: 14px 14px 4px 14px;
  background: var(--soft);
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
}

.message-bubble time {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 0.7rem;
  text-align: right;
}

.chat-error {
  margin: 0 20px 8px;
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--line);
  background: var(--surface);
}

.composer textarea {
  flex: 1;
  min-height: 44px;
  max-height: 160px;
  padding: 10px 14px;
  border: 1px solid #cfc5bb;
  border-radius: 12px;
  background: white;
  resize: vertical;
}

.draft-count {
  padding: 0 20px 8px;
  background: var(--surface);
  color: var(--muted);
  text-align: right;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 800px) {
  .messages-page {
    grid-template-columns: 1fr;
    height: calc(100vh - 68px);
    height: calc(100dvh - 68px);
  }

  .messages-page.has-selection .conversation-list,
  .messages-page:not(.has-selection) .conversation-pane {
    display: none;
  }

  .conversation-list {
    border-right: 0;
  }

  .conversation-back {
    display: inline-flex;
  }

  .conversation-header,
  .message-list,
  .composer {
    padding-right: 16px;
    padding-left: 16px;
  }

  .message {
    max-width: 85%;
  }
}
</style>
