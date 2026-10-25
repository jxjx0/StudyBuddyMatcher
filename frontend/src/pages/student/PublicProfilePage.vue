<script setup>
import { computed, ref, watch, onScopeDispose } from "vue";
import { useRoute } from "vue-router";
import { useAppState } from "../../composables/useAppState";
import { useConnections } from "../../composables/useConnections";
import ConnectionRequestDialog from "../../components/ConnectionRequestDialog.vue";
const { loadPublicProfile } = useAppState();
const {
  connectionStatus,
  connectToStudent,
  cancelMatchRequestFor,
  loadConnectionsAndRequests,
} = useConnections();
const route = useRoute();
const loading = ref(false);
const error = ref("");
const selectedProfile = ref(null);
const requestDialogOpen = ref(false);
const submittingRequest = ref(false);
const profileConnectionStatus = computed(() =>
  selectedProfile.value
    ? connectionStatus(selectedProfile.value.id)
    : "Connect",
);
let controller;
async function load() {
  controller?.abort();
  controller = new AbortController();
  const request = controller;
  error.value = "";
  selectedProfile.value = null;
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
async function submitConnectionRequest(message = "") {
  if (!selectedProfile.value) return;
  submittingRequest.value = true;
  try {
    await connectToStudent(selectedProfile.value.id, message);
  } finally {
    submittingRequest.value = false;
    requestDialogOpen.value = false;
  }
  async function cancelConnectionRequest() {
    if (!selectedProfile.value) return;
    submittingRequest.value = true;
    try {
      await cancelMatchRequestFor(selectedProfile.value.id);
    } finally {
      submittingRequest.value = false;
    }
  }
}
watch(() => route.query.id, load, { immediate: true });
onScopeDispose(() => controller?.abort());
</script>
<template>
  <section class="page-body narrow-page">
    <RouterLink class="back-link" to="/student/discover"
      >Back to discover</RouterLink
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
      <p v-if="profileConnectionStatus !== 'Connected'" class="hint">
        Contact details remain private until your connection is accepted.
      </p>
      <div class="form-actions">
        <RouterLink class="secondary-button" to="/student/groups"
          >Explore study groups</RouterLink
        >
        <button
          v-if="profileConnectionStatus === 'Connect'"
          class="primary-button"
          :disabled="submittingRequest"
          @click="requestDialogOpen = true"
        >
          Connect
        </button>
        <button
          v-else-if="profileConnectionStatus === 'Accept request'"
          class="primary-button"
          :disabled="submittingRequest"
          @click="submitConnectionRequest()"
        >
          Accept request
        </button>
        <button
          v-else-if="profileConnectionStatus === 'Request Sent'"
          class="primary-button"
          :disabled="submittingRequest"
          @click="cancelConnectionRequest"
        >
          {{ submittingRequest ? "Cancelling…" : "Request Sent" }}
        </button>
        <button
          v-else
          class="primary-button"
          disabled
        >
          {{ profileConnectionStatus }}
        </button>
      </div>
    </section>
    <ConnectionRequestDialog
      :student="requestDialogOpen ? selectedProfile : null"
      :busy="submittingRequest"
      @close="requestDialogOpen = false"
      @send="submitConnectionRequest"
    />
  </section>
</template>
