<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Filter, Search } from "lucide-vue-next";
import { useAppState } from "../../composables/useAppState";
import { studyGoals, labelOf } from "../../utils/studyData";

const {
  courses,
  filters,
  query,
  showBuddyFilters,
  filteredStudents,
  loadStudents,
} = useAppState();
const router = useRouter();
const loading = ref(false);
const error = ref("");
const page = ref(1);
const pageSize = 10;
const pageCount = computed(() =>
  Math.ceil(filteredStudents.value.length / pageSize),
);
const pagedStudents = computed(() =>
  filteredStudents.value.slice(
    (page.value - 1) * pageSize,
    page.value * pageSize,
  ),
);
watch(
  [filters, query],
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
function resetFilters() {
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
  router.push({ name: "student-public-profile", query: { id } });
}
onMounted(refresh);
</script>

<template>
  <section class="page-body">
    <header class="page-heading">
      <h1>Discover study buddies</h1>
      <p>Find students who share your courses, goals and study style.</p>
    </header>
    <div class="filter-surface">
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
          loading
            ? "Loading students…"
            : `${filteredStudents.length} students found`
        }}
      </p>
      <button class="text-button" @click="resetFilters">Reset filters</button>
    </div>
    <div v-if="error" class="error-message" role="alert">
      <p>{{ error }}</p>
      <button class="secondary-button" @click="refresh">Try again</button>
    </div>
    <template v-else-if="!loading">
      <div v-if="!pagedStudents.length" class="empty-state">
        <Search :size="30" />
        <h2>No students found</h2>
        <p>Try a different name, or reset your filters to see everyone.</p>
        <button class="secondary-button" @click="resetFilters">
          Show all students
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
            <p>{{ student.programme }}</p>
            <p>{{ student.course || "No course listed yet" }}</p>
            <div class="chip-list">
              <span class="tag">{{ student.mode }}</span
              ><span v-for="goal in student.goals" :key="goal" class="tag">{{
                goal
              }}</span>
            </div>
          </div>
          <button
            class="secondary-button"
            :aria-label="`View ${student.name}'s public profile`"
            @click="viewProfile(student.id)"
          >
            View profile
          </button>
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
    <p class="feature-note">
      Buddy requests and match scores aren’t available yet. You can join a
      <RouterLink to="/student/groups">study group</RouterLink> today.
    </p>
  </section>
</template>
