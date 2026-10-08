<script setup>
import { useRoute } from "vue-router";
import { useAppState } from "../composables/useAppState";
import { useConnections } from "../composables/useConnections";
const emit = defineEmits(["navigate"]);
const { nav } = useAppState();
const { unreadConnectionUpdates } = useConnections();
const route = useRoute();
function isActive(path) {
  if (path === "student/profile" && route.path === "/student/availability")
    return true;
  if (path === "student/discover" && route.path === "/student/public-profile")
    return true;
  return route.path === "/" + path;
}
</script>
<template>
  <nav aria-label="Main navigation">
    <RouterLink
      v-for="item in nav"
      :key="item.path"
      :to="'/' + item.path"
      class="nav-item"
      :class="{ active: isActive(item.path) }"
      @click="emit('navigate')"
    >
      <component :is="item.icon" :size="19" aria-hidden="true" />
      <span>{{ item.label }}</span>
      <span
        v-if="item.path === 'student/connections' && unreadConnectionUpdates"
        class="connection-notification"
        role="status"
        aria-live="polite"
        :aria-label="`${unreadConnectionUpdates} new connection updates`"
      >
        {{ unreadConnectionUpdates > 9 ? "9+" : unreadConnectionUpdates }}
      </span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.connection-notification {
  display: inline-grid;
  place-items: center;
  min-width: 21px;
  height: 21px;
  margin-left: auto;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--accent);
  color: white;
  font-size: 0.75rem;
  font-weight: 700;
  line-height: 1;
}
</style>
