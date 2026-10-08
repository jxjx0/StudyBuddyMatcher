<script setup>
import { ref, watch, onScopeDispose } from "vue";
import { useRoute } from "vue-router";
import { useAppState } from "../../composables/useAppState";
import { useConnections } from "../../composables/useConnections";
const { loadPublicProfile } = useAppState();
const { sendMatchRequest } = useConnections();
const route = useRoute();
const loading = ref(false);
const error = ref("");
const selectedProfile = ref(null);
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
    selectedProfile.value = await loadPublicProfile(route.query.id, {
      signal: request.signal,
    });
  } catch (problem) {
    if (problem.name !== "AbortError") error.value = problem.message;
  } finally {
    if (request === controller) loading.value = false;
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
          <dt>Course needing a buddy</dt>
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
        <RouterLink class="secondary-button" to="/student/groups"
          >Explore study groups</RouterLink
        >
        <button
          class="primary-button"
          @click="sendMatchRequest(selectedProfile.id)"
        >
          Connect
        </button>
      </div>
    </section>
  </section>
</template>
