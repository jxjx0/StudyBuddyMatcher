<script setup>
import { computed, ref, watch, onMounted, onScopeDispose } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Check, X } from "lucide-vue-next";
import { useAppState } from "../../composables/useAppState";
import { useConnections } from "../../composables/useConnections";
import ConnectionRequestDialog from "../../components/ConnectionRequestDialog.vue";
import AppDialog from "../../components/AppDialog.vue";
const { loadPublicProfile, user } = useAppState();
const {
  incomingRequests,
  outgoingRequests,
  activeConnections,
  sendMatchRequest,
  cancelMatchRequestFor,
  acceptMatchRequest,
  declineMatchRequest,
  loadConnectionsAndRequests,
  endStudyConnection,
} = useConnections();
const route = useRoute();
const router = useRouter();
const respondingAction = ref(null); // 'accept' | 'decline' | null
const loading = ref(false);
const error = ref("");
const selectedProfile = ref(null);
const sending = ref(false);
const cancelling = ref(false);
const justSent = ref(false);
const requestDialogOpen = ref(false);
const endConfirmationOpen = ref(false);
const endingConnection = ref(false);
let controller;

const activeConnection = computed(() => {
  const profileId = selectedProfile.value?.id;
  if (!profileId) return null;
  return activeConnections.value.find(
    (connection) =>
      connection.studentAId === profileId || connection.studentBId === profileId,
  ) || null;
});

const connectionStatus = computed(() => {
  const profileId = selectedProfile.value?.id;
  if (!profileId) return "Connect";
  if (user.value?.userId === profileId) return "Self";
  if (activeConnection.value) {
    return "Connected";
  }
  if (
    justSent.value ||
    outgoingRequests.value.some((req) => req.recipientId === profileId)
  ) {
    return "Request Sent";
  }
  return "Connect";
});


const incomingRequest = computed(() => {
  const profileId = selectedProfile.value?.id;
  if (!profileId) return null;
  return incomingRequests.value.find((req) => req.senderId === profileId);
});

async function handleAccept() {
  if (!incomingRequest.value || respondingAction.value) return;
  respondingAction.value = 'accept';
  try {
    await acceptMatchRequest(incomingRequest.value.requestId);
    router.push({ path: "/student/connections" });
  } finally {
    respondingAction.value = null;
  }
}

async function handleDecline() {
  if (!incomingRequest.value || respondingAction.value) return;
  respondingAction.value = 'decline';
  try {
    await declineMatchRequest(incomingRequest.value.requestId);
    router.push({ path: "/student/connections" });
  } finally {
    respondingAction.value = null;
  }
}

async function handleConnect(message = "") {
  if (!selectedProfile.value?.id || connectionStatus.value !== "Connect" || sending.value) {
    return;
  }
  sending.value = true;
  try {
    const success = await sendMatchRequest(selectedProfile.value.id, message);
    if (success) {
      justSent.value = true;
    }
  } finally {
    sending.value = false;
    requestDialogOpen.value = false;
  }
}

async function handleCancel() {
  if (!selectedProfile.value?.id || connectionStatus.value !== "Request Sent" || cancelling.value) {
    return;
  }
  cancelling.value = true;
  try {
    if (await cancelMatchRequestFor(selectedProfile.value.id)) {
      justSent.value = false;
    }
  } finally {
    cancelling.value = false;
  }
}

function handlePrimaryAction() {
  if (connectionStatus.value === "Connect") requestDialogOpen.value = true;
  else if (connectionStatus.value === "Request Sent") void handleCancel();
}

async function handleEndConnection() {
  if (!activeConnection.value || endingConnection.value) return;
  endingConnection.value = true;
  try {
    if (await endStudyConnection(activeConnection.value.connectionId)) {
      endConfirmationOpen.value = false;
    }
  } finally {
    endingConnection.value = false;
  }
}

async function load() {
  controller?.abort();
  controller = new AbortController();
  const request = controller;
  error.value = "";
  selectedProfile.value = null;
  justSent.value = false;
  if (!route.query.id) {
    error.value =
      "Choose a student from Discover buddies to view their profile.";
    return;
  }
  loading.value = true;
  try {
    const [profile] = await Promise.all([
      loadPublicProfile(route.query.id, { signal: request.signal }),
      loadConnectionsAndRequests({ silent: true }),
    ]);
    selectedProfile.value = profile;
  } catch (problem) {
    if (problem.name !== "AbortError") error.value = problem.message;
  } finally {
    if (request === controller) loading.value = false;
  }
}

const backDestination = computed(() => {
  if (route.query.from === "connections") {
    return { path: "/student/connections", label: "Back to connections" };
  }
  return { path: "/student/discover", label: "Back to discover" };
});

onMounted(() => {
  void loadConnectionsAndRequests({ silent: true });
});
watch(() => route.query.id, load, { immediate: true });
onScopeDispose(() => controller?.abort());
</script>
<template>
  <section class="page-body narrow-page">
    <RouterLink class="back-link" :to="backDestination.path"
      >{{ backDestination.label }}</RouterLink
    >
    <header class="page-heading">
      <h1>Public study profile</h1>
      <p>Get to know a student’s courses and study preferences.</p>
    </header>
    <div v-if="loading" class="loading-state" role="status">
      <span class="spinner" aria-hidden="true"></span>
      <p>Loading profile…</p>
    </div>
    <div v-else-if="error" class="error-message" role="alert">
      <p>{{ error }}</p>
      <button class="secondary-button" @click="load">Try again</button>
    </div>
    <section v-else-if="selectedProfile" class="form-card">
      <div class="profile-form-top">
        <div class="avatar avatar-xl avatar-purple">
          {{ selectedProfile.initials }}
        </div>
        <div>
          <div class="profile-name-row">
            <h2>{{ selectedProfile.name }}</h2>
            <span v-if="connectionStatus === 'Connected'" class="tag tag-success">
              Connected
            </span>
          </div>
          <p>{{ selectedProfile.programme }}</p>
        </div>
      </div>
      <dl class="detail-grid">
        <div>
          <dt>School</dt>
          <dd>{{ selectedProfile.school || "Not specified" }}</dd>
        </div>
        <div>
          <dt>Courses needing a buddy</dt>
          <dd>{{ selectedProfile.course || "Not specified" }}</dd>
        </div>
        <div>
          <dt>Study mode</dt>
          <dd>{{ selectedProfile.mode }}</dd>
        </div>
        <div>
          <dt>Group preference</dt>
          <dd>{{ selectedProfile.groupSize }}</dd>
        </div>
        <div>
          <dt>Weekly availability</dt>
          <dd>{{ selectedProfile.availability }}</dd>
        </div>
        <div>
          <dt>Study goals</dt>
          <dd>{{ selectedProfile.goal }}</dd>
        </div>
      </dl>
      <p v-if="connectionStatus !== 'Connected'" class="hint">
        Contact details remain private until your connection is accepted.
      </p>
      <div class="form-actions">
        <template v-if="connectionStatus === 'Connected'">
          <button
            type="button"
            class="danger-button"
            :disabled="endingConnection"
            @click="endConfirmationOpen = true"
          >
            {{ endingConnection ? "Ending connection…" : "End connection" }}
          </button>
        </template>
        <template v-if="incomingRequest && connectionStatus !== 'Connected'">
          <button
            class="primary-button"
            :disabled="Boolean(respondingAction)"
            @click="handleAccept"
          >
            <Check :size="16" aria-hidden="true" />
            {{ respondingAction === 'accept' ? "Accepting…" : "Accept request" }}
          </button>
          <button
            class="secondary-button"
            :disabled="Boolean(respondingAction)"
            @click="handleDecline"
          >
            <X :size="16" aria-hidden="true" />
            {{ respondingAction === 'decline' ? "Declining…" : "Decline" }}
          </button>
        </template>
        <button
          v-else-if="connectionStatus !== 'Self' && connectionStatus !== 'Connected'"
          class="primary-button"
          :disabled="sending || cancelling || !['Connect', 'Request Sent'].includes(connectionStatus)"
          :aria-label="
            connectionStatus === 'Request Sent'
              ? `Cancel connection request to ${selectedProfile.name}`
              : undefined
          "
          @click="handlePrimaryAction"
        >
          {{ sending ? "Sending…" : cancelling ? "Cancelling…" : connectionStatus }}
        </button>
      </div>
    </section>
    <ConnectionRequestDialog
      :student="requestDialogOpen ? selectedProfile : null"
      :busy="sending"
      @close="requestDialogOpen = false"
      @send="handleConnect"
    />
    <AppDialog
      :open="endConfirmationOpen"
      title="End connection?"
      :busy="endingConnection"
      close-label="Cancel ending connection"
      @close="endConfirmationOpen = false"
    >
      <p>
        End your study-buddy connection with {{ selectedProfile?.name }}?
        You can still read your chat history, but neither of you will be able
        to send messages unless you reconnect.
      </p>
      <div class="dialog-footer">
        <button
          type="button"
          class="secondary-button"
          :disabled="endingConnection"
          @click="endConfirmationOpen = false"
        >
          Keep connection
        </button>
        <button
          type="button"
          class="danger-button"
          :disabled="endingConnection"
          @click="handleEndConnection"
        >
          {{ endingConnection ? "Ending…" : "End connection" }}
        </button>
      </div>
    </AppDialog>
  </section>
</template>

<style scoped>
.profile-name-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.profile-name-row h2 {
  margin-bottom: 5px;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 32px 0;
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
</style>
