<script setup>
import { Users } from "lucide-vue-next";
</script>
<template>
  <section class="page-body narrow-page">
    <header class="page-heading">
      <h1>My connections</h1>
      <p>A place for your study buddies and connection requests.</p>
    </header>
    <div class="empty-state">
      <Users :size="36" />
      <h2>Buddy connections are coming soon</h2>
      <p>
        Connection requests aren’t available in this version. You can already
        find people studying your course and request to join their study groups.
      </p>
      <div class="form-actions">
        <RouterLink class="primary-button" to="/student/groups"
          >Explore study groups</RouterLink
        ><RouterLink class="secondary-button" to="/student/discover"
          >Discover buddies</RouterLink
        >
      </div>
    </div>
  </section>
</template>
