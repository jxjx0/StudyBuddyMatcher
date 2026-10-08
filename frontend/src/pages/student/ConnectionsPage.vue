<script setup>
import { onMounted, ref } from "vue";
import { useConnections } from "../../composables/useConnections";

const {
  incomingRequests,
  outgoingRequests,
  activeConnections,
  loading,
  error,
  loadConnectionsAndRequests,
  markConnectionUpdatesRead,
  acceptMatchRequest,
  declineMatchRequest,
  endStudyConnection,
} = useConnections();

const connectionTab = ref("active");
const requestTab = ref("incoming");

onMounted(async () => {
  if (await loadConnectionsAndRequests()) markConnectionUpdatesRead();
});
</script>

<template>
  <section class="page-body narrow-page">
    <header class="page-heading">
      <h1>My connections</h1>
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
      <div class="form-actions connection-tabs main-tabs">
        <button
          class="secondary-button"
          :aria-pressed="connectionTab === 'active'"
          @click="connectionTab = 'active'"
        >
          Active connections ({{ activeConnections.length }})
        </button>
        <button
          class="secondary-button"
          :aria-pressed="connectionTab === 'pending'"
          @click="connectionTab = 'pending'"
        >
          Pending requests ({{ incomingRequests.length + outgoingRequests.length }})
        </button>
      </div>

      <section v-if="connectionTab === 'active'" aria-label="Active connections">
        <div v-if="!activeConnections.length" class="empty-state">
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
            <div>
              <h2>{{ connection.partnerName || "Study buddy" }}</h2>
              <p>Connected {{ new Date(connection.startedAt).toLocaleDateString() }}</p>
              <p class="hint">
                Contact details aren’t available on this page yet.
              </p>
            </div>
            <button
              class="secondary-button"
              @click="endStudyConnection(connection.connectionId)"
            >
              End connection
            </button>
          </article>
        </div>
      </section>

      <section v-else aria-label="Pending requests">
        <div class="request-filter">
          <span class="request-filter-label">View</span>
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
          </div>
          <div v-else class="connection-list">
            <article
              v-for="request in incomingRequests"
              :key="request.requestId"
              class="form-card connection-card"
            >
              <div>
                <h2>{{ request.senderName || "A student" }}</h2>
                <p v-if="request.message">{{ request.message }}</p>
                <p>Sent {{ new Date(request.createdAt).toLocaleDateString() }}</p>
              </div>
              <div class="form-actions">
                <button class="primary-button" @click="acceptMatchRequest(request.requestId)">
                  Accept
                </button>
                <button class="secondary-button" @click="declineMatchRequest(request.requestId)">
                  Decline
                </button>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="request-results">
          <div v-if="!outgoingRequests.length" class="empty-state">
            <h2>No outgoing requests</h2>
          </div>
          <div v-else class="connection-list">
            <article
              v-for="request in outgoingRequests"
              :key="request.requestId"
              class="form-card connection-card"
            >
              <div>
                <h2>{{ request.recipientName || "A student" }}</h2>
                <p v-if="request.message">{{ request.message }}</p>
                <p>Sent {{ new Date(request.createdAt).toLocaleDateString() }}</p>
              </div>
              <span class="tag">Pending</span>
            </article>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.connection-list {
  display: grid;
  gap: 16px;
  margin-top: 20px;
}

.connection-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.connection-card h2,
.connection-card p {
  margin: 0 0 8px;
}

.connection-tabs [aria-pressed="true"] {
  border-color: var(--accent);
  color: var(--accent);
}

.main-tabs {
  margin-bottom: 24px;
}

.request-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 28px 0 22px;
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

.request-results .empty-state {
  margin-top: 0;
}

@media (max-width: 640px) {
  .connection-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .request-filter {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
