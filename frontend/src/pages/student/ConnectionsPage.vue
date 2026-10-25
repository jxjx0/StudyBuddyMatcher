<script setup>
import { onMounted, ref } from "vue";
import {
  CalendarDays,
  Check,
  Mail,
  MessageCircle,
  Phone,
  UserRound,
  X,
} from "lucide-vue-next";
import { useConnections } from "../../composables/useConnections";
import ConnectionChatDialog from "../../components/ConnectionChatDialog.vue";

const {
  incomingRequests,
  outgoingRequests,
  activeConnections,
  loading,
  cancellingRequestIds,
  error,
  loadConnectionsAndRequests,
  markConnectionUpdatesRead,
  acceptMatchRequest,
  declineMatchRequest,
  cancelMatchRequest,
  endStudyConnection,
} = useConnections();

const connectionTab = ref("active");
const requestTab = ref("incoming");
const chatConnection = ref(null);

function initials(name) {
  return (name || "Study buddy")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

onMounted(async () => {
  if (await loadConnectionsAndRequests()) markConnectionUpdatesRead();
});
</script>

<template>
  <section class="page-body narrow-page">
    <header class="page-heading">
      <h1>My Connections</h1>
      <p>Manage study buddy requests and your active connections.</p>
    </header>

    <div v-if="error" class="error-message" role="alert">
      <p>{{ error }}</p>
      <button class="secondary-button" :disabled="loading" @click="loadConnectionsAndRequests">
        Try again
      </button>
    </div>
    <p v-else-if="loading" role="status">Loading your connections…</p>
    <template v-else>
      <div class="connection-tabs main-tabs" role="tablist" aria-label="Connection views">
        <button
          class="connection-tab"
          role="tab"
          id="active-connections-tab"
          aria-controls="active-connections-panel"
          :aria-selected="connectionTab === 'active'"
          :aria-pressed="connectionTab === 'active'"
          @click="connectionTab = 'active'"
        >
          Active connections <span class="tab-count">{{ activeConnections.length }}</span>
        </button>
        <button
          class="connection-tab"
          role="tab"
          id="pending-requests-tab"
          aria-controls="pending-requests-panel"
          :aria-selected="connectionTab === 'pending'"
          :aria-pressed="connectionTab === 'pending'"
          @click="connectionTab = 'pending'"
        >
          Pending requests
          <span class="tab-count">{{ incomingRequests.length + outgoingRequests.length }}</span>
        </button>
      </div>

      <section
        v-if="connectionTab === 'active'"
        id="active-connections-panel"
        role="tabpanel"
        aria-labelledby="active-connections-tab"
        class="connection-panel"
      >
        <div v-if="!activeConnections.length" class="empty-state">
          <UserRound :size="30" aria-hidden="true" />
          <h2>No active connections yet</h2>
          <p>Send a request from Discover to connect with another student.</p>
          <RouterLink class="primary-button" to="/student/discover">
            Discover buddies
          </RouterLink>
        </div>
        <div v-else class="connection-list">
          <article
            v-for="connection in activeConnections"
            :key="connection.connectionId"
            class="form-card connection-card"
          >
            <div class="connection-person">
              <div class="avatar avatar-lg avatar-coral" aria-hidden="true">
                {{ initials(connection.partnerName) }}
              </div>
              <div class="connection-person-info">
                <h2>{{ connection.partnerName || "Study buddy" }}</h2>
                <span class="tag tag-success">Connected</span>
              </div>
            </div>
            <div class="connection-details">
              <p class="connection-date">
                <CalendarDays :size="16" aria-hidden="true" />
                Connected {{ new Date(connection.startedAt).toLocaleDateString() }}
              </p>
              <p
                v-if="connection.partnerEmail"
                class="contact-detail"
              >
                <Mail :size="17" aria-hidden="true" />
                <span>{{ connection.partnerEmail }}</span>
              </p>
              <p v-else class="contact-unavailable">
                <Mail :size="17" aria-hidden="true" /> Email not provided
              </p>
              <p
                v-if="connection.partnerContactNumber"
                class="contact-detail"
              >
                <Phone :size="17" aria-hidden="true" />
                <span>{{ connection.partnerContactNumber }}</span>
              </p>
              <p v-else class="contact-unavailable">
                <Phone :size="17" aria-hidden="true" /> Phone not provided
              </p>
            </div>
            <div class="connection-actions">
              <button
                class="primary-button chat-connection"
                :aria-label="`Chat with ${connection.partnerName || 'study buddy'}`"
                @click="chatConnection = connection"
              >
                <MessageCircle :size="16" aria-hidden="true" />
                Chat
              </button>
              <button
                class="secondary-button end-connection"
                @click="endStudyConnection(connection.connectionId)"
              >
                <X :size="16" aria-hidden="true" />
                End connection
              </button>
            </div>
          </article>
        </div>
      </section>

      <section
        v-else
        id="pending-requests-panel"
        role="tabpanel"
        aria-labelledby="pending-requests-tab"
        class="connection-panel"
      >
        <div class="request-filter">
          <span class="request-filter-label">Show</span>
          <div class="request-tabs" role="group" aria-label="Filter pending requests">
            <button
              type="button"
              :aria-pressed="requestTab === 'incoming'"
              @click="requestTab = 'incoming'"
            >
              Received <span>{{ incomingRequests.length }}</span>
            </button>
            <button
              type="button"
              :aria-pressed="requestTab === 'outgoing'"
              @click="requestTab = 'outgoing'"
            >
              Sent <span>{{ outgoingRequests.length }}</span>
            </button>
          </div>
        </div>

        <div v-if="requestTab === 'incoming'" class="request-results">
          <div v-if="!incomingRequests.length" class="empty-state">
            <h2>No incoming requests</h2>
            <p>New requests from other students will appear here.</p>
          </div>
          <div v-else class="connection-list">
            <article
              v-for="request in incomingRequests"
              :key="request.requestId"
              class="form-card request-card"
            >
              <div class="request-main">
                <div class="avatar avatar-lg avatar-coral" aria-hidden="true">
                  {{ initials(request.senderName) }}
                </div>
                <div class="request-info">
                  <div class="request-heading">
                    <h2>{{ request.senderName || "A student" }}</h2>
                    <span class="tag request-status">Received</span>
                  </div>
                  <p v-if="request.message" class="request-message">
                    “{{ request.message }}”
                  </p>
                  <p class="request-date">
                    <CalendarDays :size="15" aria-hidden="true" />
                    Sent {{ new Date(request.createdAt).toLocaleDateString() }}
                  </p>
                </div>
              </div>
              <div class="request-actions">
                <button class="primary-button" @click="acceptMatchRequest(request.requestId)">
                  <Check :size="16" aria-hidden="true" /> Accept
                </button>
                <button class="secondary-button" @click="declineMatchRequest(request.requestId)">
                  <X :size="16" aria-hidden="true" /> Decline
                </button>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="request-results">
          <div v-if="!outgoingRequests.length" class="empty-state">
            <h2>No outgoing requests</h2>
            <p>Requests you send to other students will show up here.</p>
          </div>
          <div v-else class="connection-list">
            <article
              v-for="request in outgoingRequests"
              :key="request.requestId"
              class="form-card request-card"
            >
              <div class="request-main">
                <div class="avatar avatar-lg avatar-coral" aria-hidden="true">
                  {{ initials(request.recipientName) }}
                </div>
                <div class="request-info">
                  <div class="request-heading">
                    <h2>{{ request.recipientName || "A student" }}</h2>
                    <span class="tag request-status">Awaiting response</span>
                  </div>
                  <p v-if="request.message" class="request-message">
                    “{{ request.message }}”
                  </p>
                  <p class="request-date">
                    <CalendarDays :size="15" aria-hidden="true" />
                    Sent {{ new Date(request.createdAt).toLocaleDateString() }}
                  </p>
                </div>
              </div>
              <div class="request-actions">
                <button
                  class="secondary-button"
                  :disabled="loading || cancellingRequestIds.has(request.requestId)"
                  :aria-label="`Cancel connection request to ${request.recipientName || 'student'}`"
                  @click="cancelMatchRequest(request.requestId)"
                >
                  {{
                    cancellingRequestIds.has(request.requestId)
                      ? "Cancelling…"
                      : "Cancel request"
                  }}
                </button>
              </div>
            </article>
          </div>
        </div>
      </section>
      <ConnectionChatDialog
        v-if="chatConnection"
        :connection="chatConnection"
        @close="chatConnection = null"
      />
    </template>
  </section>
</template>

<style scoped>
.connection-list {
  display: grid;
  gap: 14px;
}

.connection-tabs {
  display: flex;
  gap: 10px;
  margin: 24px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.connection-tab {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 44px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface);
  color: #514943;
  font-size: 0.92rem;
  font-weight: 600;
}

.connection-tab[aria-selected="true"] {
  border-color: #dba89a;
  background: var(--soft);
  color: #963e2c;
}

.tab-count {
  display: inline-grid;
  place-items: center;
  min-width: 23px;
  height: 23px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgb(255 255 255 / 75%);
  color: inherit;
  font-size: 0.8rem;
}

.connection-panel {
  min-width: 0;
}

.connection-card,
.request-card {
  display: grid;
  align-items: center;
  gap: 20px;
  padding: 22px;
}

.connection-card {
  grid-template-columns: minmax(200px, 0.85fr) minmax(240px, 1.15fr) auto;
}

.connection-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 150px;
}

.connection-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  margin: 0;
}

.request-card {
  grid-template-columns: minmax(0, 1fr) auto;
}

.connection-person,
.request-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.connection-person-info,
.request-info {
  min-width: 0;
}

.connection-person-info h2,
.request-heading h2 {
  margin: 0 0 6px;
  font-size: 1.1rem;
  overflow-wrap: anywhere;
}

.connection-details {
  display: grid;
  gap: 7px;
  min-width: 0;
  font-size: 0.9rem;
}

.connection-date,
.request-date,
.contact-detail,
.contact-unavailable {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin: 0;
}

.connection-date,
.request-date,
.contact-detail,
.contact-unavailable {
  color: var(--muted);
}

.contact-detail {
  overflow-wrap: anywhere;
}

.contact-detail svg,
.contact-unavailable svg,
.connection-date svg,
.request-date svg {
  flex: 0 0 auto;
}

.end-connection {
  white-space: nowrap;
}

.request-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0 0 18px;
}

.request-filter-label {
  color: var(--muted);
  font-size: 0.9rem;
  font-weight: 600;
}

.request-tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #f1ebe2;
}

.request-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 7px 12px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #433b36;
  font-size: 0.9rem;
  font-weight: 600;
}

.request-tabs button[aria-pressed="true"] {
  border-color: var(--line);
  background: var(--surface);
  color: var(--accent);
  box-shadow: 0 1px 2px rgb(48 45 51 / 8%);
}

.request-tabs button span {
  color: var(--muted);
  font-size: 0.85em;
}

.request-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.request-status {
  white-space: nowrap;
}

.request-message {
  margin: 6px 0;
  color: #514943;
  overflow-wrap: anywhere;
}

.request-date {
  margin-top: 7px;
  font-size: 0.85rem;
}

.request-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.request-actions .primary-button,
.request-actions .secondary-button {
  margin: 0;
}

.request-results .empty-state {
  margin-top: 0;
}

@media (max-width: 1050px) {
  .connection-card,
  .request-card {
    grid-template-columns: minmax(180px, 1fr) minmax(220px, 1.2fr);
  }

  .end-connection,
  .request-actions {
    grid-column: 2;
    justify-self: start;
  }

  .connection-actions {
    grid-column: 2;
    flex-direction: row;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .connection-tabs {
    gap: 8px;
  }

  .connection-tab {
    flex: 1;
    justify-content: center;
    padding-inline: 8px;
    font-size: 0.85rem;
  }

  .connection-card,
  .request-card {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
    padding: 18px;
  }

  .connection-person,
  .request-main {
    align-items: flex-start;
  }

  .request-filter {
    align-items: flex-start;
    flex-direction: column;
  }

  .connection-actions,
  .request-actions {
    grid-column: auto;
    justify-self: stretch;
  }

  .connection-actions button {
    flex: 1 1 140px;
  }
}
</style>
