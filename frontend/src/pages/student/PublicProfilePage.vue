<script setup>
import { computed, ref, watch, onMounted, onScopeDispose } from "vue";
import { useRoute } from "vue-router";
import { useAppState } from "../../composables/useAppState";
import { useConnections } from "../../composables/useConnections";
const { loadPublicProfile, user } = useAppState();
const {
  outgoingRequests,
  activeConnections,
  sendMatchRequest,
  loadConnectionsAndRequests,
} = useConnections();
const route = useRoute();
const loading = ref(false);
const error = ref("");
const selectedProfile = ref(null);
const sending = ref(false);
const justSent = ref(false);
let controller;

const connectionStatus = computed(() => {
  const profileId = selectedProfile.value?.id;
  if (!profileId) return "Connect";
  if (user.value?.userId === profileId) return "Self";
  if (
    activeConnections.value.some(
      (conn) => conn.studentAId === profileId || conn.studentBId === profileId,
    )
  ) {
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

async function handleConnect() {
  if (!selectedProfile.value?.id || connectionStatus.value !== "Connect" || sending.value) {
    return;
  }
  sending.value = true;
  try {
    const success = await sendMatchRequest(selectedProfile.value.id);
    if (success) {
      justSent.value = true;
    }
  } finally {
    sending.value = false;
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
    selectedProfile.value = await loadPublicProfile(route.query.id, {
      signal: request.signal,
    });
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
    <p v-if="loading" role="status">Loading profile…</p>
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
          <h2>{{ selectedProfile.name }}</h2>
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
      <p class="hint">
        Contact details remain private until your connection is accepted.
      </p>
      <div class="form-actions">
        <button
          v-if="connectionStatus !== 'Self'"
          class="primary-button"
          :disabled="sending || connectionStatus !== 'Connect'"
          @click="handleConnect"
        >
          {{ sending ? "Sending…" : connectionStatus }}
        </button>
      </div>
    </section>
  </section>
</template>
