<script setup>
import { useAppState } from "../../composables/useAppState";
const { usageSummary, dataError, dataLoading, loadAppData } = useAppState();
</script>
<template>
  <section class="page-body">
    <header class="page-heading">
      <h1>Admin dashboard</h1>
      <p>
        Manage the accounts and matching settings that keep StudyBuds running.
      </p>
    </header>
    <p v-if="dataLoading" role="status">Loading account summary…</p>
    <div v-else-if="dataError" class="error-message" role="alert">
      <p>{{ dataError }}</p>
      <button class="secondary-button" @click="loadAppData">Try again</button>
    </div>
    <dl v-else class="stats-grid">
      <div>
        <dt>Active students</dt>
        <dd>{{ usageSummary.activeStudents }}</dd>
        <p>Students with active accounts</p>
      </div>
      <div>
        <dt>Student accounts</dt>
        <dd>{{ usageSummary.students }}</dd>
        <p>Including suspended accounts</p>
      </div>
      <div>
        <dt>Total accounts</dt>
        <dd>{{ usageSummary.totalAccounts }}</dd>
        <p>{{ usageSummary.administrators }} administrator accounts</p>
      </div>
    </dl>
    <section class="form-section">
      <h2>Manage StudyBuds</h2>
      <div class="action-list">
        <RouterLink to="/admin/users"
          ><strong>User accounts</strong
          ><span
            >Create accounts, edit details and manage account status.</span
          ></RouterLink
        ><RouterLink to="/admin/matching"
          ><strong>Matching configuration</strong
          ><span
            >Choose a strategy and adjust the importance of each
            criterion.</span
          ></RouterLink
        >
      </div>
    </section>
  </section>
</template>
