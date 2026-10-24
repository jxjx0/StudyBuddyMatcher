<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRouter } from "vue-router";
import { Filter, Search } from "lucide-vue-next";
import { useAppState } from "../../composables/useAppState";
import { useConnections } from "../../composables/useConnections";
import { findMatches } from "../../composables/useMatching";
import { studyGoals, labelOf } from "../../utils/studyData";

const {
  courses,
  filters,
  query,
  showBuddyFilters,
  filteredStudents,
  loadStudents,
  mapStudent,
  user,
} = useAppState();
const {
  outgoingRequests,
  activeConnections,
  sendMatchRequest,
} = useConnections();
const router = useRouter();

// "Best matches" asks the matching engine for a ranked, scored list.
// "Browse all" lists every student and filters them in the browser.
const tab = ref("matches");
const views = [
  { key: "matches", label: "Best matches" },
  { key: "browse", label: "Browse all" },
];

const loading = ref(false);
const error = ref("");

const matchFilters = ref({
  course: "",
  goal: "",
  mode: "",
  minOverlapMinutes: "",
});
const matches = ref([]);
const matchesLoading = ref(false);
const matchesError = ref("");
const criterionLabels = {
  COURSE: "Course",
  AVAILABILITY: "Availability",
  STUDY_MODE: "Study mode",
  STUDY_GOAL: "Study goal",
  GROUP_SIZE: "Group size",
};

const sendingRequestIds = ref(new Set());
const sentRequestIds = ref(new Set());
const page = ref(1);
const pageSize = 10;
const shownStudents = computed(() =>
  tab.value === "matches" ? matches.value : filteredStudents.value,
);
const shownLoading = computed(() =>
  tab.value === "matches" ? matchesLoading.value : loading.value,
);
const shownError = computed(() =>
  tab.value === "matches" ? matchesError.value : error.value,
);
const pageCount = computed(() =>
  Math.ceil(shownStudents.value.length / pageSize),
);
const pagedStudents = computed(() =>
  shownStudents.value.slice(
    (page.value - 1) * pageSize,
    page.value * pageSize,
  ),
);
watch(
  [filters, query, matchFilters, tab],
  () => {
    page.value = 1;
  },
  { deep: true },
);
watch(pageCount, (count) => {
  page.value = Math.min(page.value, Math.max(1, count));
});

async function refresh() {
  loading.value = true;
  error.value = "";
  try {
    await loadStudents();
  } catch (problem) {
    error.value = problem.message;
  } finally {
    loading.value = false;
  }
}

// A newer search replaces an older one, so a slow earlier response is ignored.
let latestMatchSearch = 0;
async function loadMatches() {
  const searchId = ++latestMatchSearch;
  matchesLoading.value = true;
  matchesError.value = "";
  try {
    const results = await findMatches(user.value.userId, matchFilters.value);
    if (searchId !== latestMatchSearch) return;
    matches.value = results.map((result) => ({
      ...mapStudent(result.candidate),
      score: Math.round(result.score),
      matched: (result.matchedCriteria || []).map(
        (criterion) => criterionLabels[criterion] || labelOf(criterion),
      ),
    }));
  } catch (problem) {
    if (searchId === latestMatchSearch && problem.name !== "AbortError")
      matchesError.value = problem.message;
  } finally {
    if (searchId === latestMatchSearch) matchesLoading.value = false;
  }
}
watch(matchFilters, loadMatches, { deep: true });

function retry() {
  if (tab.value === "matches") loadMatches();
  else refresh();
}
function resetFilters() {
  if (tab.value === "matches") {
    matchFilters.value = {
      course: "",
      goal: "",
      mode: "",
      minOverlapMinutes: "",
    };
    return;
  }
  query.value = "";
  filters.value = {
    course: "",
    goal: "",
    availability: "Any availability",
    mode: "Any mode",
    groupSize: "Any group size",
  };
}
function viewProfile(id) {
  router.push({ name: "student-public-profile", query: { id, from: "discover" } });
}
function requestStatus(studentId) {
  if (
    activeConnections.value.some(
      (connection) =>
        connection.studentAId === studentId ||
        connection.studentBId === studentId,
    )
  )
    return "Connected";
  if (
    sentRequestIds.value.has(studentId) ||
    outgoingRequests.value.some((request) => request.recipientId === studentId)
  )
    return "Request Sent";
  return "Connect";
}
async function connect(studentId) {
  if (sendingRequestIds.value.has(studentId) || requestStatus(studentId) !== "Connect")
    return;
  sendingRequestIds.value = new Set(sendingRequestIds.value).add(studentId);
  try {
    if (await sendMatchRequest(studentId))
      sentRequestIds.value = new Set(sentRequestIds.value).add(studentId);
  } finally {
    const pending = new Set(sendingRequestIds.value);
    pending.delete(studentId);
    sendingRequestIds.value = pending;
  }
}
onMounted(() => {
  loadMatches();
  refresh();
});
onBeforeRouteLeave(() => {
  query.value = "";
});
</script>

<template>
  <section class="page-body">
    <header class="page-heading">
      <h1>Discover Study Buddies</h1>
      <p>Find students who share your courses, goals and study style.</p>
    </header>
    <div class="group-tabs" aria-label="Discover views">
      <button
        v-for="view in views"
        :key="view.key"
        :class="{ active: tab === view.key }"
        :aria-pressed="tab === view.key"
        @click="tab = view.key"
      >
        {{ view.label }}
      </button>
    </div>

    <div v-if="tab === 'matches'" class="filter-surface">
      <p class="hint">
        Students are ranked by how well they match your profile. Narrow the
        list with the filters below.
      </p>
      <div class="form-grid">
        <label class="field"
          ><span>Course</span
          ><select v-model="matchFilters.course">
            <option value="">Any course</option>
            <option
              v-for="course in courses"
              :key="course"
              :value="course.split(' ')[0]"
            >
              {{ course }}
            </option>
          </select></label
        >
        <label class="field"
          ><span>Study goal</span
          ><select v-model="matchFilters.goal">
            <option value="">Any goal</option>
            <option v-for="goal in studyGoals" :key="goal" :value="goal">
              {{ labelOf(goal) }}
            </option>
          </select></label
        >
        <label class="field"
          ><span>Study mode</span
          ><select v-model="matchFilters.mode">
            <option value="">Any mode</option>
            <option value="ONLINE">Online</option>
            <option value="IN_PERSON">In-person</option>
          </select></label
        >
        <label class="field"
          ><span>Shared free time</span
          ><select v-model="matchFilters.minOverlapMinutes">
            <option value="">Any</option>
            <option value="60">At least 1 hour a week</option>
            <option value="120">At least 2 hours a week</option>
          </select></label
        >
      </div>
    </div>

    <div v-else class="filter-surface">
      <label class="search-box"
        ><Search :size="20" aria-hidden="true" /><input
          v-model="query"
          type="search"
          aria-label="Search students by name"
          placeholder="Search students by name"
      /></label>
      <div class="form-grid">
        <label class="field"
          ><span>Course</span
          ><select v-model="filters.course">
            <option value="">Any course</option>
            <option
              v-for="course in courses"
              :key="course"
              :value="course.split(' ')[0]"
            >
              {{ course }}
            </option>
          </select></label
        >
        <label class="field"
          ><span>Study goal</span
          ><select v-model="filters.goal">
            <option value="">Any goal</option>
            <option v-for="goal in studyGoals" :key="goal">
              {{ labelOf(goal) }}
            </option>
          </select></label
        >
      </div>
      <div class="toolbar">
        <p class="hint">
          Choosing both includes students matching either your course or your
          goal.
        </p>
        <button
          class="secondary-button"
          :aria-expanded="showBuddyFilters"
          aria-controls="buddy-filters"
          @click="showBuddyFilters = !showBuddyFilters"
        >
          <Filter :size="17" />More filters
        </button>
      </div>
      <div v-if="showBuddyFilters" id="buddy-filters" class="filter-grid">
        <label class="field"
          ><span>Availability</span
          ><select v-model="filters.availability">
            <option>Any availability</option>
            <option>Evenings</option>
            <option>Weekends</option>
          </select></label
        >
        <label class="field"
          ><span>Study mode</span
          ><select v-model="filters.mode">
            <option>Any mode</option>
            <option>Online</option>
            <option>In-person</option>
          </select></label
        >
        <label class="field"
          ><span>Preferred group size</span
          ><select v-model="filters.groupSize">
            <option>Any group size</option>
            <option>One-to-one</option>
            <option>Small group</option>
          </select></label
        >
        <p class="hint full">
          “Either” matches any mode or size. Evening slots end after 6 PM.
        </p>
      </div>
    </div>

    <div class="results-heading">
      <p role="status">
        {{
          shownLoading
            ? tab === "matches"
              ? "Finding your best matches…"
              : "Loading students…"
            : tab === "matches"
              ? `${shownStudents.length} matches found`
              : `${shownStudents.length} students found`
        }}
      </p>
      <button class="text-button" @click="resetFilters">Reset filters</button>
    </div>
    <div v-if="shownError" class="error-message" role="alert">
      <p>{{ shownError }}</p>
      <button class="secondary-button" @click="retry">Try again</button>
    </div>
    <template v-else-if="!shownLoading">
      <div v-if="!pagedStudents.length" class="empty-state">
        <Search :size="30" />
        <template v-if="tab === 'matches'">
          <h2>No matches found</h2>
          <p>
            Try removing a filter, or add courses and availability to your
            profile so we can find better matches.
          </p>
        </template>
        <template v-else>
          <h2>No students found</h2>
          <p>Try a different name, or reset your filters to see everyone.</p>
        </template>
        <button class="secondary-button" @click="resetFilters">
          Reset filters
        </button>
      </div>
      <div v-else class="student-list">
        <article
          v-for="student in pagedStudents"
          :key="student.id"
          class="student-row"
        >
          <div class="avatar avatar-lg avatar-coral">
            {{ student.initials }}
          </div>
          <div class="student-info">
            <h2>{{ student.name }}</h2>
            <p v-if="tab === 'matches'" class="match-score">
              {{ student.score }}% match
            </p>
            <p>{{ student.programme }}</p>
            <p>{{ student.course || "No course listed yet" }}</p>
            <div v-if="tab === 'matches'" class="chip-list">
              <span
                v-for="criterion in student.matched"
                :key="criterion"
                class="tag tag-success"
                >{{ criterion }}</span
              >
            </div>
            <div v-else class="chip-list">
              <span class="tag">{{ student.mode }}</span
              ><span v-for="goal in student.goals" :key="goal" class="tag">{{
                goal
              }}</span>
            </div>
          </div>
          <div class="form-actions">
            <button
              class="secondary-button"
              :aria-label="`View ${student.name}'s public profile`"
              @click="viewProfile(student.id)"
            >
              View profile
            </button>
            <button
              class="primary-button"
              :disabled="
                sendingRequestIds.has(student.id) ||
                requestStatus(student.id) !== 'Connect'
              "
              @click="connect(student.id)"
            >
              {{
                sendingRequestIds.has(student.id)
                  ? "Sending…"
                  : requestStatus(student.id)
              }}
            </button>
          </div>
        </article>
      </div>
      <nav
        v-if="pageCount > 1"
        class="pagination"
        aria-label="Student results pages"
      >
        <button class="secondary-button" :disabled="page === 1" @click="page--">
          Previous</button
        ><span>Page {{ page }} of {{ pageCount }}</span
        ><button
          class="secondary-button"
          :disabled="page === pageCount"
          @click="page++"
        >
          Next
        </button>
      </nav>
    </template>
  </section>
</template>
