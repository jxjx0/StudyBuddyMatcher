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
  loaded,
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
const tab = ref("browse");
const sendingRequestGroupId = ref(null);
const searchQuery = ref("");
const selectedCourse = ref("");
const selectedGoal = ref("");
const selectedMeetingMode = ref("");
const selectedDay = ref("");
const maximumGroupSize = ref("");
const showFilters = ref(false);
const editorOpen = ref(false);
const editingGroup = ref(null);
const views = [
  { key: "browse", label: "Browse groups" },
  { key: "mine", label: "My groups" },
  { key: "requests", label: "My requests" },
];
const visibleGroups = computed(() => {
  const source = tab.value === "mine" ? mine.value : groups.value;
  return source.filter(matchesGroupFilters);
});
function matchesGroupFilters(group) {
  if (tab.value === "mine" && group.status !== groupStatus.value) return false;

  const searchText = searchQuery.value.trim().toLowerCase();
  if (!group.groupName.toLowerCase().includes(searchText)) return false;
  if (selectedCourse.value && group.courseCode !== selectedCourse.value) {
    return false;
  }
  if (selectedGoal.value && !group.studyGoals.includes(selectedGoal.value)) {
    return false;
  }

  if (
    selectedMeetingMode.value &&
    group.meetingMode !== selectedMeetingMode.value &&
    group.meetingMode !== "EITHER"
  ) {
    return false;
  }

  if (
    selectedDay.value &&
    group.availability.length &&
    !group.availability.some((slot) => slot.day === selectedDay.value)
  ) {
    return false;
  }

  if (
    maximumGroupSize.value &&
    group.maxGroupSize > Number(maximumGroupSize.value)
  ) {
    return false;
  }
  return true;
}
const hasFilters = computed(() =>
  Boolean(
    searchQuery.value.trim() ||
    selectedCourse.value ||
    selectedGoal.value ||
    selectedMeetingMode.value ||
    selectedDay.value ||
    maximumGroupSize.value,
  ),
);
const emptyTitle = computed(() => {
  if (hasFilters.value) return "No groups match your filters";
  if (tab.value === "browse") return "No open groups yet";
  return groupStatus.value === "ACTIVE"
    ? "No active groups yet"
    : "No closed groups yet";
});
function browseGroups() {
  tab.value = "browse";
  resetFilters();
}
function resetFilters() {
  searchQuery.value = "";
  selectedCourse.value = "";
  selectedGoal.value = "";
  selectedMeetingMode.value = "";
  selectedDay.value = "";
  maximumGroupSize.value = "";
}
function statusLabel(group) {
  if (group.status === "CLOSED") return "Closed";
  if (!group.leaderAvailable) return "Requests paused";
  if (group.memberCount >= group.maxGroupSize) return "Full";
  return "Open to join";
}
function membershipLabel(group) {
  if (group.isLeader) return "Leader";
  if (group.isMember) return "Member";
  if (group.myRequestStatus === "PENDING") return "Request pending";
  return "";
}
function openEditor(group = null) {
  error.value = "";
  notice.value = "";
  editingGroup.value = group;
  editorOpen.value = true;
}
async function saveGroup(draft) {
  const groupId = editingGroup.value?.groupId;
  const path = groupId ? `/api/groups/${groupId}` : "/api/groups";
  const method = groupId ? "PUT" : "POST";
  const saved = await mutate(path, method, draft, { dismissDetail: true });
  if (saved) {
    editorOpen.value = false;
    tab.value = "mine";
    groupStatus.value = "ACTIVE";
    resetFilters();
    notice.value = groupId ? "Group updated." : "Group created.";
  }
}
async function handleGroupAction({ path, message, method, endGroup }) {
  notice.value = "";
  const saved = await mutate(path, method, undefined, {
    dismissDetail: endGroup,
  });
  if (!saved) return;
  notice.value = message;
  if (endGroup) {
    tab.value = "mine";
    groupStatus.value = "CLOSED";
    resetFilters();
  }
}
async function requestToJoinGroup(groupId) {
  sendingRequestGroupId.value = groupId;
  try {
    await handleGroupAction({
      path: `/api/groups/${groupId}/join-requests`,
      message: "Join request sent",
    });
  } finally {
    sendingRequestGroupId.value = null;
  }
}
function viewGroup(groupId) {
  notice.value = "";
  open(groupId);
}
</script>
<template>
  <section class="page-body groups-page">
    <header class="page-heading heading-with-action">
      <div>
        <h1>Study Groups</h1>
      </div>
      <button
        class="primary-button"
        :disabled="busy || !courses.length"
        @click="openEditor()"
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
        <span v-if="loaded && loading" role="status">Refreshing…</span>
        <span v-else>Refresh</span>
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
    <p v-if="loading && !loaded" role="status">Loading groups…</p>
    <p v-if="detailLoading" role="status">Opening group…</p>
    <template v-if="loaded && tab !== 'requests'">
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
      <div class="filter-surface">
        <div class="group-search">
          <label class="field"
            ><span>Search</span
            ><span class="search-box"
              ><Search :size="20" /><input
                v-model="searchQuery"
                type="search"
                aria-label="Search group name"
                placeholder="Search groups by name" /></span></label
          ><label class="field"
            ><span>Course</span
            ><select v-model="selectedCourse">
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
            ><select v-model="selectedGoal">
              <option value="">Any goal</option>
              <option v-for="item in studyGoals" :key="item" :value="item">
                {{ labelOf(item) }}
              </option>
            </select></label
          ><label class="field"
            ><span>Meeting mode</span
            ><select v-model="selectedMeetingMode">
              <option value="">Any mode</option>
              <option v-for="item in meetingModes" :key="item" :value="item">
                {{ labelOf(item) }}
              </option>
            </select></label
          ><label class="field"
            ><span>Day</span
            ><select v-model="selectedDay">
              <option value="">Any day</option>
              <option v-for="item in weekDays" :key="item" :value="item">
                {{ labelOf(item) }}
              </option>
            </select></label
          ><label class="field"
            ><span>Maximum group size</span
            ><input
              v-model="maximumGroupSize"
              type="number"
              min="2"
              placeholder="Any size"
          /></label>
        </div>
      </div>
      <div class="results-heading">
        <p role="status">
          {{ visibleGroups.length }}
          {{ visibleGroups.length === 1 ? "group" : "groups" }} found
        </p>
        <button v-if="hasFilters" class="text-button" @click="resetFilters">
          Reset filters
        </button>
      </div>
      <div v-if="visibleGroups.length" class="group-cards">
        <article
          v-for="group in visibleGroups"
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
          <p v-if="group.description" class="group-description">
            {{ group.description }}
          </p>
          <div class="group-meta">
            <span
              ><Users :size="16" />{{ group.memberCount }} /
              {{ group.maxGroupSize }} members</span
            ><span>{{ labelOf(group.meetingMode) }}</span>
          </div>
          <p v-if="group.studyGoals.length" class="hint">
            {{ group.studyGoals.map(labelOf).join(", ") }}
          </p>
          <footer>
            <button
              v-if="group.canRequest"
              class="primary-button"
              :disabled="busy"
              :aria-label="`Request to join ${group.groupName}`"
              @click="requestToJoinGroup(group.groupId)"
            >
              {{
                sendingRequestGroupId === group.groupId
                  ? "Sending…"
                  : "Request to join"
              }}
            </button>
            <button
              v-else-if="group.myRequestStatus === 'PENDING'"
              class="cancel-button"
              :disabled="busy"
              :aria-label="`Cancel request to ${group.groupName}`"
              @click="
                handleGroupAction({
                  path: `/api/groups/${group.groupId}/join-requests`,
                  method: 'DELETE',
                  message: 'Join request cancelled',
                })
              "
            >
              Cancel request
            </button>
            <button
              class="secondary-button"
              :disabled="busy"
              :aria-label="`View ${group.groupName}`"
              @click="viewGroup(group.groupId)"
            >
              View group</button
            ><span v-if="membershipLabel(group)" class="hint">{{
              membershipLabel(group)
            }}</span>
          </footer>
        </article>
      </div>
      <div v-else-if="!error" class="empty-state">
        <Users :size="32" />
        <h2>{{ emptyTitle }}</h2>
        <div class="form-actions">
          <button
            class="primary-button"
            :disabled="busy || !courses.length"
            @click="openEditor()"
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
    <template v-else-if="loaded && tab === 'requests'">
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
            Responded {{ requestTime(request.respondedAt) }}
          </p>
        </div>
        <div class="form-actions">
          <button
            v-if="request.status === 'PENDING'"
            class="cancel-button"
            :disabled="busy"
            :aria-label="`Cancel request to ${request.groupName}`"
            @click="
              handleGroupAction({
                path: `/api/groups/${request.groupId}/join-requests`,
                method: 'DELETE',
                message: 'Join request cancelled',
              })
            "
          >
            Cancel request
          </button>
          <button
            class="secondary-button"
            :disabled="busy"
            :aria-label="`View ${request.groupName}`"
            @click="viewGroup(request.groupId)"
          >
            View group
          </button>
        </div>
      </article>
    </template>
    <GroupDetails
      :group="selected"
      :notice="notice"
      :requests="leaderRequests"
      :busy="busy"
      :error="error"
      @close="closeDetail"
      @edit="openEditor"
      @action="handleGroupAction"
    />
    <GroupEditor
      :open="editorOpen"
      :group="editingGroup"
      :courses="courses"
      :busy="busy"
      :error="error"
      @close="editorOpen = false"
      @save="saveGroup"
    />
  </section>
</template>
