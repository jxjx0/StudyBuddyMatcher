<script setup>
import { useRoute } from "vue-router";
import { useAppState } from "../composables/useAppState";
const emit = defineEmits(["navigate"]);
const { nav } = useAppState();
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
      <component :is="item.icon" :size="19" aria-hidden="true" /><span>{{
        item.label
      }}</span>
    </RouterLink>
  </nav>
</template>
