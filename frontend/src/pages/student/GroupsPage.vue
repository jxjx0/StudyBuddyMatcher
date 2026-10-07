<script setup>
import { computed, ref } from "vue";
import { Plus, Search, Users } from "lucide-vue-next";
import { useGroups } from "../../composables/useGroups";
import { useAppState } from "../../composables/useAppState";
import GroupEditor from "../../components/groups/GroupEditor.vue";
import GroupDetails from "../../components/groups/GroupDetails.vue";
import {
  studyGoals,
  meetingModes,
  weekDays,
  labelOf,
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
const { toast } = useAppState();
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
  editingGroup.value = group;
  editorOpen.value = true;
}
async function save(draft) {
  const id = editingGroup.value?.groupId;
  const path = id ? `/api/groups/${id}` : "/api/groups";
  if (await mutate(path, id ? "PUT" : "POST", draft)) {
    editorOpen.value = false;
    tab.value = "mine";
    toast(id ? "Group updated" : "Group created");
  }
}
async function action({ path, message, method }) {
  if (await mutate(path, method)) toast(message);
}
function formatDate(value) {
  return new Date(value).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
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
    <p v-if="loading" role="status">Loading groups…</p>
    <p v-if="detailLoading" role="status">Opening group…</p>
    <template v-if="!loading && tab !== 'requests'">
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
        <p role="status">{{ visible.length }} groups found</p>
        <button class="text-button" @click="resetFilters">Reset filters</button>
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
              group.studyGoals.map(labelOf).join(" · ") ||
              "Flexible study goals"
            }}
          </p>
          <footer>
            <button
              class="secondary-button"
              :disabled="busy"
              :aria-label="`View ${group.groupName}`"
              @click="open(group.groupId)"
            >
              View group</button
            ><span class="hint">{{ membershipLabel(group) }}</span>
          </footer>
        </article>
      </div>
      <div v-else-if="!error" class="empty-state">
        <Users :size="32" />
        <h2>{{ tab === "mine" ? "No groups here yet" : "No groups found" }}</h2>
        <p>
          Create a group for your course, or reset your filters to explore more.
        </p>
        <div class="form-actions">
          <button class="secondary-button" @click="resetFilters">
            Reset filters</button
          ><button
            v-if="tab === 'mine'"
            class="secondary-button"
            @click="
              tab = 'browse';
              resetFilters();
            "
          >
            Browse groups
          </button>
        </div>
      </div>
    </template>
    <template v-else-if="!loading && tab === 'requests'"
      ><div v-if="!requests.length && !error" class="empty-state">
        <h2>No join requests yet</h2>
        <p>Browse groups and request to join one that fits your studies.</p>
        <button class="primary-button" @click="tab = 'browse'">
          Browse groups
        </button>
      </div>
      <article
        v-for="request in requests"
        :key="request.groupJoinRequestId"
        class="request-row"
      >
        <div>
          <h2>{{ request.groupName }}</h2>
          <p>
            <span class="tag">{{ labelOf(request.status) }}</span> Requested
            {{ formatDate(request.createdAt) }}
          </p>
        </div>
        <button
          class="secondary-button"
          :disabled="busy"
          @click="open(request.groupId)"
        >
          View group
        </button>
      </article></template
    >
    <GroupDetails
      :group="selected"
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
