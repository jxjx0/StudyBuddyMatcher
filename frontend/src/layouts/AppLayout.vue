<script setup>
import { computed, onMounted, onScopeDispose, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ChevronRight, LogOut, Menu, Sparkles } from "lucide-vue-next";
import { useAppState } from "../composables/useAppState";
import { useConnections } from "../composables/useConnections";
import AppNavigation from "../components/AppNavigation.vue";
import AppDialog from "../components/AppDialog.vue";

const { userRole, nav, user, signOut, initialsOf } = useAppState();
const {
  loadConnectionsAndRequests,
  checkForConnectionUpdates,
  markConnectionUpdatesRead,
} = useConnections();
const route = useRoute();
const menuOpen = ref(false);
const displayName = computed(() => user.value?.name || "StudyBuds member");
const currentLabel = computed(() => {
  if (route.path === "/student/availability") return "Weekly Availability";
  if (route.path === "/student/public-profile") return "Public Profile";
  return (
    nav.value.find((item) => route.path === "/" + item.path)?.label ||
    "StudyBuds"
  );
});
watch(
  () => route.path,
  () => {
    menuOpen.value = false;
    void refreshConnectionUpdates();
  },
);
watch(
  currentLabel,
  (label) => {
    document.title = `StudyBuds: ${label}`;
  },
  { immediate: true },
);

let updateTimer;
let checkingUpdates = false;
async function refreshConnectionUpdates() {
  if (userRole.value !== "student" || checkingUpdates) return;
  checkingUpdates = true;
  try {
    if (route.path === "/student/connections") {
      await loadConnectionsAndRequests({ silent: true });
    } else {
      await checkForConnectionUpdates();
    }
  } finally {
    checkingUpdates = false;
  }
}
onMounted(() => {
  void refreshConnectionUpdates();
  updateTimer = window.setInterval(refreshConnectionUpdates, 30_000);
  window.addEventListener("focus", refreshConnectionUpdates);
});
onScopeDispose(() => {
  window.clearInterval(updateTimer);
  window.removeEventListener("focus", refreshConnectionUpdates);
});
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">Skip to content</a>
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><Sparkles :size="18" /></span>study<span
          class="brand-accent"
          >buds</span
        >
      </div>
      <p class="workspace-label">
        {{ userRole === "admin" ? "Admin Console" : "My Space" }}
      </p>
      <AppNavigation />
      <div class="profile-mini">
        <div class="avatar avatar-purple">{{ initialsOf(displayName) }}</div>
        <div>
          <strong>{{ displayName }}</strong
          ><span>{{ userRole === "admin" ? "Administrator" : "Student" }}</span>
        </div>
      </div>
    </aside>
    <div class="main-content">
      <header class="topbar">
        <button
          class="icon-button mobile-menu"
          aria-label="Open navigation"
          :aria-expanded="menuOpen"
          @click="menuOpen = true"
        >
          <Menu :size="22" />
        </button>
        <div class="breadcrumb">
          <span>{{ userRole === "admin" ? "Admin Console" : "My Space" }}</span
          ><ChevronRight :size="14" /><strong>{{ currentLabel }}</strong>
        </div>
        <button class="text-button signout-button" @click="signOut">
          <LogOut :size="18" aria-hidden="true" />Sign out
        </button>
      </header>
      <main id="main-content" tabindex="-1"><RouterView /></main>
    </div>
    <AppDialog :open="menuOpen" title="Navigation" @close="menuOpen = false"
      ><AppNavigation @navigate="menuOpen = false"
    /></AppDialog>
  </div>
</template>
