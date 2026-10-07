<script setup>
import { computed, ref } from "vue";
import { Plus, Search, Users } from "lucide-vue-next";
import { useGroups } from "../../composables/useGroups";
import GroupEditor from "../../components/groups/GroupEditor.vue";
import GroupDetails from "../../components/groups/GroupDetails.vue";
import {
  studyGoals,
  meetingModes,
  weekDays,
  labelOf,
  requestTime,
} from "../../utils/studyData";
const {
  groups,
  mine,
  requests,
  courses,
  selected,
  leaderRequests,
  loading,
  detailLoading,
  busy,
  error,
  load,
  open,
  closeDetail,
  mutate,
} = useGroups();
const notice = ref("");
const groupStatus = ref("ACTIVE");
const requestStatus = ref("PENDING");
const pendingRequests = computed(() =>
  requests.value.filter((request) => request.status === "PENDING"),
);
const requestHistory = computed(() =>
  requests.value.filter((request) => request.status !== "PENDING"),
);
const visibleRequests = computed(() =>
  requestStatus.value === "PENDING"
    ? pendingRequests.value
    : requestHistory.value,
);
const requestEmptyTitle = computed(() => {
  if (!requests.value.length) return "No join requests yet";
  if (requestStatus.value === "PENDING") return "No pending requests";
  return "No past decisions yet";
});
const requestEmptyHelp = computed(() => {
  if (requestStatus.value === "HISTORY")
    return "Accepted and rejected requests will appear here.";
  if (!requests.value.length)
    return "Browse groups and request to join one that fits your studies.";
  return "Browse groups to send a request. Past decisions are kept in History.";
});
const tab = ref("browse");
const query = ref("");
const course = ref("");
const goal = ref("");
const mode = ref("");
const day = ref("");
const size = ref("");
const showFilters = ref(false);
const editorOpen = ref(false);
const editingGroup = ref(null);
const views = [
  { key: "browse", label: "Browse groups" },
  { key: "mine", label: "My groups" },
  { key: "requests", label: "My requests" },
];
const visible = computed(() => {
  const source = tab.value === "mine" ? mine.value : groups.value;
  return source.filter((group) => {
    if (tab.value === "mine" && group.status !== groupStatus.value)
      return false;
    if (
      !group.groupName.toLowerCase().includes(query.value.trim().toLowerCase())
    )
      return false;
    if (course.value && group.courseCode !== course.value) return false;
    if (goal.value && !group.studyGoals.includes(goal.value)) return false;
    if (
      mode.value &&
      group.meetingMode !== mode.value &&
      group.meetingMode !== "EITHER"
    )
      return false;
    if (
      day.value &&
      group.availability.length &&
      !group.availability.some((slot) => slot.day === day.value)
    )
      return false;
    if (size.value && group.maxGroupSize > Number(size.value)) return false;
    return true;
  });
});
const hasFilters = computed(() =>
  Boolean(
    query.value.trim() ||
    course.value ||
    goal.value ||
    mode.value ||
    day.value ||
    size.value,
  ),
);
const emptyTitle = computed(() => {
  if (hasFilters.value) return "No groups match your filters";
  if (tab.value === "browse") return "No open groups yet";
  return groupStatus.value === "ACTIVE"
    ? "No active groups yet"
    : "No closed groups yet";
});
const emptyHelp = computed(() => {
  if (hasFilters.value)
    return "Try a different search or reset your filters to see more groups.";
  if (tab.value === "browse")
    return "Be the first to create a group for your course.";
  if (groupStatus.value === "CLOSED")
    return "Groups you have ended or belonged to will appear here when they close.";
  return "Browse groups to find one to join, or create a group for your course.";
});
function browseGroups() {
  tab.value = "browse";
  resetFilters();
}
function resetFilters() {
  query.value = "";
  course.value = "";
  goal.value = "";
  mode.value = "";
  day.value = "";
  size.value = "";
}
function statusLabel(group) {
  if (group.status === "CLOSED") return "Closed";
  if (!group.leaderAvailable) return "Requests paused";
  if (group.memberCount >= group.maxGroupSize) return "Full";
  return "Open to join";
}
function membershipLabel(group) {
  if (group.isLeader) return "You lead this group";
  if (group.isMember) return "You’re a member";
  if (group.myRequestStatus === "PENDING") return "Request pending";
  return "";
}
function edit(group = null) {
  error.value = "";
  notice.value = "";
  editingGroup.value = group;
  editorOpen.value = true;
}
async function save(draft) {
  const id = editingGroup.value?.groupId;
  const path = id ? `/api/groups/${id}` : "/api/groups";
  if (await mutate(path, id ? "PUT" : "POST", draft, { dismissDetail: true })) {
    editorOpen.value = false;
    tab.value = "mine";
    groupStatus.value = "ACTIVE";
    resetFilters();
    notice.value = id
      ? "Group updated."
      : "Group created. Other students can now request to join.";
  }
}
async function action({ path, message, method, endGroup }) {
  notice.value = "";
  if (!(await mutate(path, method, undefined, { dismissDetail: endGroup })))
    return;
  notice.value = message;
  if (endGroup) {
    tab.value = "mine";
    groupStatus.value = "CLOSED";
    resetFilters();
  }
}
function viewGroup(id) {
  notice.value = "";
  open(id);
}
</script>
<template>
  <section class="page-body groups-page">
    <header class="page-heading heading-with-action">
      <div>
        <h1>Study groups</h1>
        <p>Find your course, share your goals and learn together.</p>
      </div>
      <button
        class="primary-button"
        :disabled="loading || busy || !courses.length"
        @click="edit()"
      >
        <Plus :size="18" />Create group
      </button>
    </header>
    <div class="group-tabs" aria-label="Group views">
      <button
        v-for="view in views"
        :key="view.key"
        :class="{ active: tab === view.key }"
        :aria-pressed="tab === view.key"
        @click="tab = view.key"
      >
        {{ view.label }}</button
      ><button class="refresh" :disabled="loading || busy" @click="load">
        Refresh
      </button>
    </div>
    <div
      v-if="error && !selected && !editorOpen"
      class="error-message"
      role="alert"
    >
      <p>{{ error }}</p>
      <button class="secondary-button" :disabled="loading" @click="load">
        Try again
      </button>
    </div>
    <p
      v-if="notice && !selected && !editorOpen"
      class="group-notice"
      role="status"
    >
      {{ notice }}
    </p>
    <p v-if="loading" role="status">Loading groups…</p>
    <p v-if="detailLoading" role="status">Opening group…</p>
    <template v-if="!loading && tab !== 'requests'">
      <div
        v-if="tab === 'mine'"
        class="group-status-controls"
        aria-label="My group status"
      >
        <button
          v-for="status in ['ACTIVE', 'CLOSED']"
          :key="status"
          class="secondary-button"
          :aria-pressed="groupStatus === status"
          @click="groupStatus = status"
        >
          {{ labelOf(status) }} ({{
            mine.filter((group) => group.status === status).length
          }})
        </button>
      </div>
      <p v-if="tab === 'mine' && groupStatus === 'CLOSED'" class="hint">
        Closed groups are kept for reference. Create a new group to study
        together again.
      </p>
      <div class="filter-surface">
        <div class="group-search">
          <label class="search-box"
            ><Search :size="20" /><input
              v-model="query"
              type="search"
              aria-label="Search group name"
              placeholder="Search groups by name" /></label
          ><label class="field"
            ><span>Course</span
            ><select v-model="course">
              <option value="">Any course</option>
              <option
                v-for="item in courses"
                :key="item.courseCode"
                :value="item.courseCode"
              >
                {{ item.courseCode }} {{ item.courseName }}
              </option>
            </select></label
          >
        </div>
        <button
          class="text-button"
          :aria-expanded="showFilters"
          aria-controls="group-filters"
          @click="showFilters = !showFilters"
        >
          {{ showFilters ? "Hide filters" : "More filters" }}
        </button>
        <div v-if="showFilters" id="group-filters" class="filter-grid">
          <label class="field"
            ><span>Study goal</span
            ><select v-model="goal">
              <option value="">Any goal</option>
              <option v-for="item in studyGoals" :key="item" :value="item">
                {{ labelOf(item) }}
              </option>
            </select></label
          ><label class="field"
            ><span>Meeting mode</span
            ><select v-model="mode">
              <option value="">Any mode</option>
              <option v-for="item in meetingModes" :key="item" :value="item">
                {{ labelOf(item) }}
              </option>
            </select></label
          ><label class="field"
            ><span>Day</span
            ><select v-model="day">
              <option value="">Any day</option>
              <option v-for="item in weekDays" :key="item" :value="item">
                {{ labelOf(item) }}
              </option>
            </select></label
          ><label class="field"
            ><span>Maximum group size</span
            ><input v-model="size" type="number" min="2" placeholder="Any size"
          /></label>
          <p class="hint full">
            Flexible schedules match any day. Groups meeting in either mode
            match online and in-person filters.
          </p>
        </div>
      </div>
      <div class="results-heading">
        <p role="status">
          {{ visible.length }}
          {{ visible.length === 1 ? "group" : "groups" }} found
        </p>
        <button v-if="hasFilters" class="text-button" @click="resetFilters">
          Reset filters
        </button>
      </div>
      <div v-if="visible.length" class="group-cards">
        <article
          v-for="group in visible"
          :key="group.groupId"
          class="large-group-card"
        >
          <div class="results-heading">
            <span
              class="tag"
              :class="{ 'tag-success': statusLabel(group) === 'Open to join' }"
              >{{ statusLabel(group) }}</span
            ><span class="hint">{{ group.courseCode }}</span>
          </div>
          <h2>{{ group.groupName }}</h2>
          <p class="group-description">
            {{ group.description || "Study together at your own pace." }}
          </p>
          <div class="group-meta">
            <span
              ><Users :size="16" />{{ group.memberCount }} /
              {{ group.maxGroupSize }} members</span
            ><span>{{ labelOf(group.meetingMode) }}</span>
          </div>
          <p class="hint">
            {{
              group.studyGoals.map(labelOf).join(", ") || "Flexible study goals"
            }}
          </p>
          <footer>
            <button
              class="secondary-button"
              :disabled="busy"
              :aria-label="`View ${group.groupName}`"
              @click="viewGroup(group.groupId)"
            >
              View group</button
            ><span class="hint">{{ membershipLabel(group) }}</span>
          </footer>
        </article>
      </div>
      <div v-else-if="!error" class="empty-state">
        <Users :size="32" />
        <h2>{{ emptyTitle }}</h2>
        <p>{{ emptyHelp }}</p>
        <div class="form-actions">
          <button
            class="primary-button"
            :disabled="busy || !courses.length"
            @click="edit()"
          >
            Create group
          </button>
          <button
            v-if="hasFilters"
            class="secondary-button"
            @click="resetFilters"
          >
            Reset filters</button
          ><button
            v-if="tab === 'mine'"
            class="secondary-button"
            @click="browseGroups"
          >
            Browse groups
          </button>
        </div>
      </div>
    </template>
    <template v-else-if="!loading && tab === 'requests'">
      <div
        v-if="requests.length"
        class="group-status-controls"
        aria-label="Request status"
      >
        <button
          class="secondary-button"
          :aria-pressed="requestStatus === 'PENDING'"
          @click="requestStatus = 'PENDING'"
        >
          Pending ({{ pendingRequests.length }})
        </button>
        <button
          class="secondary-button"
          :aria-pressed="requestStatus === 'HISTORY'"
          @click="requestStatus = 'HISTORY'"
        >
          History ({{ requestHistory.length }})
        </button>
      </div>
      <div v-if="!visibleRequests.length && !error" class="empty-state">
        <h2>{{ requestEmptyTitle }}</h2>
        <p>{{ requestEmptyHelp }}</p>
        <button class="primary-button" @click="browseGroups">
          Browse groups
        </button>
      </div>
      <article
        v-for="request in visibleRequests"
        :key="request.groupJoinRequestId"
        class="request-row"
      >
        <div>
          <h2>{{ request.groupName }}</h2>
          <p>
            <span class="tag">{{ labelOf(request.status) }}</span>
          </p>
          <p>Requested {{ requestTime(request.createdAt) }}</p>
          <p v-if="request.respondedAt">
            {{ labelOf(request.status) }} {{ requestTime(request.respondedAt) }}
          </p>
        </div>
        <button
          class="secondary-button"
          :disabled="busy"
          :aria-label="`View ${request.groupName}`"
          @click="viewGroup(request.groupId)"
        >
          View group
        </button>
      </article>
    </template>
    <GroupDetails
      :group="selected"
      :notice="notice"
      :requests="leaderRequests"
      :busy="busy"
      :error="error"
      @close="closeDetail"
      @edit="edit"
      @action="action"
    />
    <GroupEditor
      :open="editorOpen"
      :group="editingGroup"
      :courses="courses"
      :busy="busy"
      :error="error"
      @close="editorOpen = false"
      @save="save"
    />
  </section>
</template>
