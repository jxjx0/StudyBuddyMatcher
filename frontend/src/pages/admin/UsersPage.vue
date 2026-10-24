<script setup>
import { ref } from "vue";
import { Plus, Search } from "lucide-vue-next";
import { useAppState } from "../../composables/useAppState";
import AppDialog from "../../components/AppDialog.vue";
const {
  accountSearch,
  accountRole,
  showAccountForm,
  editingAccount,
  accountDraft,
  smuSchools,
  openAccountForm,
  saveAccount,
  deleteAccount,
  visibleAccounts,
  dataError,
  dataLoading,
  loadAppData,
  accountBusy,
  accountError,
  initialsOf,
  user,
} = useAppState();
const deletingAccount = ref(null);
function confirmDelete(account) {
  accountError.value = "";
  deletingAccount.value = account;
}
async function removeAccount() {
  if (await deleteAccount(deletingAccount.value)) deletingAccount.value = null;
}
function resetFilters() {
  accountSearch.value = "";
  accountRole.value = "All roles";
}
</script>
<template>
  <section class="page-body">
    <header class="page-heading heading-with-action">
      <div>
        <h1>User accounts</h1>
        <p>Manage student and administrator access to StudyBuds.</p>
      </div>
      <button
        class="primary-button"
        :disabled="dataLoading || accountBusy"
        @click="openAccountForm()"
      >
        <Plus :size="18" />Create account
      </button>
    </header>
    <div class="account-toolbar">
      <label class="search-box"
        ><Search :size="20" /><input
          v-model="accountSearch"
          type="search"
          aria-label="Search accounts by name or email"
          placeholder="Search by name or email" /></label
      ><label class="field"
        ><span>Role</span
        ><select v-model="accountRole">
          <option>All roles</option>
          <option>Student</option>
          <option>Admin</option>
        </select></label
      >
    </div>
    <p v-if="dataLoading" role="status">Loading accounts…</p>
    <div v-else-if="dataError" class="error-message" role="alert">
      <p>{{ dataError }}</p>
      <button class="secondary-button" @click="loadAppData">Try again</button>
    </div>
    <template v-else
      ><p class="hint" role="status">
        {{ visibleAccounts.length }} accounts found
      </p>
      <div v-if="!visibleAccounts.length" class="empty-state">
        <h2>No accounts found</h2>
        <p>Try another name, email or role.</p>
        <button class="secondary-button" @click="resetFilters">
          Reset filters
        </button>
      </div>
      <div class="account-list">
        <article
          v-for="account in visibleAccounts"
          :key="account.id"
          class="account-card"
        >
          <div class="avatar avatar-purple">{{ initialsOf(account.name) }}</div>
          <div class="account-main">
            <div class="account-title">
              <h2>{{ account.name }}</h2>
              <span class="tag">{{ account.role }}</span
              ><span
                class="tag"
                :class="{ 'tag-success': account.status === 'Active' }"
                >{{ account.status }}</span
              >
            </div>
            <p>{{ account.email }}</p>
            <p class="hint">
              {{
                account.role === "Student"
                  ? account.school
                  : "Administrator account"
              }}
            </p>
            <div class="form-actions">
              <button
                class="secondary-button"
                :aria-label="`Edit ${account.name}`"
                @click="openAccountForm(account)"
              >
                Edit account</button
              ><button
                class="text-button danger"
                :disabled="account.id === user?.userId"
                :aria-label="`Delete ${account.name}`"
                @click="confirmDelete(account)"
              >
                Delete</button
              ><small v-if="account.id === user?.userId">Your account</small>
            </div>
          </div>
        </article>
      </div>
    </template>
    <p
      v-if="accountError && !showAccountForm && !deletingAccount"
      class="error-message"
      role="alert"
    >
      {{ accountError }}
    </p>
    <AppDialog
      :open="showAccountForm"
      :title="editingAccount ? 'Edit account' : 'Create account'"
      :busy="accountBusy"
      @close="showAccountForm = false"
    >
      <form @submit.prevent="saveAccount">
        <fieldset class="plain-fieldset" :disabled="accountBusy">
          <p class="hint">
            {{
              editingAccount
                ? "Update account details and access. The account role cannot be changed."
                : "All fields are required. New accounts start with active status."
            }}
          </p>
          <div class="form-grid">
            <label class="field"
              ><span>Full name</span
              ><input v-model="accountDraft.name" required /></label
            ><label class="field"
              ><span>Email</span
              ><input
                v-model="accountDraft.email"
                type="email"
                required /></label
            ><label v-if="!editingAccount" class="field full"
              ><span>Initial password</span
              ><input
                v-model="accountDraft.password"
                type="password"
                minlength="8"
                autocomplete="new-password"
                required
              /><small>At least 8 characters.</small></label
            ><label class="field"
              ><span>Role</span
              ><select
                v-model="accountDraft.role"
                :disabled="Boolean(editingAccount)"
              >
                <option>Student</option>
                <option>Admin</option>
              </select></label
            ><label v-if="accountDraft.role === 'Student'" class="field"
              ><span>School</span
              ><select v-model="accountDraft.school" required>
                <option
                  v-if="
                    accountDraft.school &&
                    !smuSchools.includes(accountDraft.school)
                  "
                >
                  {{ accountDraft.school }}
                </option>
                <option v-for="school in smuSchools" :key="school">
                  {{ school }}
                </option>
              </select></label
            ><label v-if="accountDraft.role === 'Student'" class="field"
              ><span>Programme</span
              ><input v-model="accountDraft.programme" maxlength="200" required
            /></label
            ><label v-if="accountDraft.role === 'Student'" class="field"
              ><span>Year of study</span
              ><select v-model.number="accountDraft.yearOfStudy" required>
                <option v-for="year in 5" :key="year" :value="year">
                  Year {{ year }}
                </option>
              </select></label
            ><label v-if="editingAccount" class="field"
              ><span>Status</span
              ><select v-model="accountDraft.status">
                <option>Active</option>
                <option>Suspended</option>
              </select></label
            >
          </div>
        </fieldset>
        <p v-if="accountError" class="error-message" role="alert">
          {{ accountError }}
        </p>
        <div class="form-actions">
          <button class="primary-button" :disabled="accountBusy">
            {{ accountBusy ? "Saving…" : "Save account" }}</button
          ><button
            type="button"
            class="secondary-button"
            :disabled="accountBusy"
            @click="showAccountForm = false"
          >
            Cancel
          </button>
        </div>
      </form>
    </AppDialog>
    <AppDialog
      :open="Boolean(deletingAccount)"
      title="Delete account?"
      :busy="accountBusy"
      @close="deletingAccount = null"
      ><template v-if="deletingAccount"
        ><p>
          <strong>{{ deletingAccount.name }}</strong> ({{
            deletingAccount.email
          }}) will lose access. This permanently removes their account and
          cannot be undone.
        </p>
        <p v-if="accountError" class="error-message" role="alert">
          {{ accountError }}
        </p>
        <div class="form-actions">
          <button
            class="secondary-button"
            :disabled="accountBusy"
            @click="deletingAccount = null"
          >
            Keep account</button
          ><button
            class="danger-button"
            :disabled="accountBusy"
            @click="removeAccount"
          >
            {{ accountBusy ? "Deleting…" : "Delete account permanently" }}
          </button>
        </div></template
      ></AppDialog
    >
  </section>
</template>
