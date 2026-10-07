import { computed, ref } from "vue";
import { api } from "../services/api";
import { smuSchools } from "../utils/studyData";
import { toast } from "./useNotifications";

const accounts = ref([]);
const accountBusy = ref(false);
const accountError = ref("");
const usageSummary = ref({
  totalAccounts: 0,
  students: 0,
  activeStudents: 0,
  administrators: 0,
});
const accountSearch = ref("");
const accountRole = ref("All roles");
const showAccountForm = ref(false);
const editingAccount = ref(null);
const accountDraft = ref({
  name: "",
  email: "",
  role: "Student",
  status: "Active",
  school: smuSchools[0],
});
const visibleAccounts = computed(() =>
  accounts.value.filter(
    (account) =>
      (accountRole.value === "All roles" ||
        account.role === accountRole.value) &&
      (!accountSearch.value ||
        `${account.name} ${account.email}`
          .toLowerCase()
          .includes(accountSearch.value.toLowerCase())),
  ),
);
function openAccountForm(account = null) {
  accountError.value = "";
  editingAccount.value = account;
  accountDraft.value = account
    ? { ...account }
    : {
        name: "",
        email: "",
        role: "Student",
        status: "Active",
        school: smuSchools[0],
      };
  showAccountForm.value = true;
}
async function loadAccounts() {
  accounts.value = (await api("/api/admin/accounts")).map((account) => ({
    id: account.userId,
    name: account.name,
    email: account.email,
    role: account.role === "ADMIN" ? "Admin" : "Student",
    status: account.accountStatus === "ACTIVE" ? "Active" : "Suspended",
    school: account.school || "—",
    currentCourseCount: account.currentCourseCount,
    studyGoalCount: account.studyGoalCount,
    availabilitySlotCount: account.availabilitySlotCount,
  }));
}
async function loadUsageSummary() {
  usageSummary.value = await api("/api/admin/usage");
}
async function saveAccount() {
  if (accountBusy.value) return;
  accountBusy.value = true;
  accountError.value = "";
  let detailsSaved = false;
  try {
    const draft = accountDraft.value;
    const details = {
      name: draft.name.trim(),
      email: draft.email.trim(),
      school: draft.school,
    };
    if (editingAccount.value) {
      const path = `/api/admin/accounts/${editingAccount.value.id}`;
      await api(path, { method: "PUT", body: JSON.stringify(details) });
      detailsSaved = true;
      await api(`${path}/status?status=${draft.status.toUpperCase()}`, {
        method: "PUT",
      });
    } else {
      await api("/api/admin/accounts", {
        method: "POST",
        body: JSON.stringify({
          ...details,
          password: draft.password,
          role: draft.role.toUpperCase(),
        }),
      });
    }
    showAccountForm.value = false;
    toast(editingAccount.value ? "Account updated" : "Account created");
    await Promise.all([loadAccounts(), loadUsageSummary()]);
  } catch (error) {
    accountError.value = detailsSaved
      ? `Account details were saved, but the status update or refresh failed: ${error.message}`
      : error.message;
  } finally {
    accountBusy.value = false;
  }
}

async function deleteAccount(account) {
  if (accountBusy.value) return false;
  accountBusy.value = true;
  accountError.value = "";
  let deleted = false;
  try {
    await api(`/api/admin/accounts/${account.id}`, { method: "DELETE" });
    deleted = true;
    toast("Account deleted");
    await Promise.all([loadAccounts(), loadUsageSummary()]);
  } catch (error) {
    accountError.value = deleted
      ? `Account deleted, but the list could not refresh: ${error.message}`
      : error.message;
  } finally {
    accountBusy.value = false;
  }
  return deleted;
}

export {
  accounts,
  usageSummary,
  accountSearch,
  accountRole,
  showAccountForm,
  editingAccount,
  accountDraft,
  visibleAccounts,
  openAccountForm,
  saveAccount,
  deleteAccount,
  loadAccounts,
  loadUsageSummary,
  accountBusy,
  accountError,
};
