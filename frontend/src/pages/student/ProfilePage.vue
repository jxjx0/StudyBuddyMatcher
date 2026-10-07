<script setup>
import { CalendarDays, Plus, X } from "lucide-vue-next";
import { useAppState } from "../../composables/useAppState";
import { studyGoals, labelOf } from "../../utils/studyData";
const {
  profile,
  smuSchools,
  draftCourse,
  courses,
  addCourse,
  removeCourse,
  saveProfile,
  phoneError,
  phoneHint,
  initialsOf,
  dataLoading,
  dataError,
  loadAppData,
  profileSaving,
  profileLoaded,
  profileError,
  profileDirty,
} = useAppState();
</script>
<template>
  <section class="page-body narrow-page">
    <header class="page-heading">
      <h1>My study profile</h1>
      <p>Help other students find you. Your contact number stays private.</p>
    </header>
    <p v-if="dataLoading" role="status">Loading your profile…</p>
    <div v-else-if="!profileLoaded" class="error-message" role="alert">
      <p>{{ dataError || "Your profile could not be loaded." }}</p>
      <button class="secondary-button" @click="loadAppData">Try again</button>
    </div>
    <form v-else class="form-card profile-form" @submit.prevent="saveProfile()">
      <fieldset class="plain-fieldset" :disabled="profileSaving">
        <div class="profile-form-top">
          <div class="avatar avatar-xl avatar-purple">
            {{ initialsOf(profile.name) }}
          </div>
          <div>
            <h2>{{ profile.name || "Your profile" }}</h2>
            <p>{{ profile.programme }}, {{ profile.year }}</p>
          </div>
        </div>
        <h3>About you</h3>
        <p class="hint">
          Your name, school, programme and study preferences are public to other
          students.
        </p>
        <div class="form-grid">
          <label class="field"
            ><span>Name</span
            ><input v-model="profile.name" autocomplete="name" required
          /></label>
          <label class="field"
            ><span>School</span
            ><select v-model="profile.school" required>
              <option
                v-if="profile.school && !smuSchools.includes(profile.school)"
              >
                {{ profile.school }}
              </option>
              <option v-for="school in smuSchools" :key="school">
                {{ school }}
              </option>
            </select></label
          >
          <label class="field"
            ><span>Programme</span><input v-model="profile.programme" required
          /></label>
          <label class="field"
            ><span>Year of study</span
            ><select v-model="profile.year">
              <option v-for="year in 5" :key="year">Year {{ year }}</option>
            </select></label
          >
          <label class="field full"
            ><span>Contact number <small>Private, optional</small></span
            ><input
              v-model="profile.phone"
              type="tel"
              autocomplete="tel"
              placeholder="+65 8123 4567"
              :aria-invalid="Boolean(phoneError)"
              aria-describedby="phone-hint"
            /><small id="phone-hint" :class="{ 'field-error': phoneError }">{{
              phoneError || phoneHint
            }}</small></label
          >
        </div>
        <section class="form-section">
          <h3>Courses currently taken</h3>
          <div class="add-row">
            <label class="field"
              ><span>Course to add</span
              ><select v-model="draftCourse">
                <option v-for="course in courses" :key="course">
                  {{ course }}
                </option>
              </select></label
            ><button
              type="button"
              class="secondary-button"
              :disabled="
                !draftCourse || profile.currentCourses.includes(draftCourse)
              "
              @click="addCourse"
            >
              <Plus :size="16" />Add course
            </button>
          </div>
          <div class="chip-list">
            <span
              v-for="course in profile.currentCourses"
              :key="course"
              class="course-chip"
              >{{ course
              }}<button
                type="button"
                class="icon-button"
                :aria-label="`Remove ${course}`"
                @click="removeCourse(course)"
              >
                <X :size="16" /></button
            ></span>
          </div>
          <p v-if="!profile.currentCourses.length" class="hint">
            Add a course to choose where you would like a study buddy.
          </p>
        </section>
        <section class="form-section">
          <h3>Study preferences</h3>
          <fieldset>
            <legend>Courses where you want a buddy</legend>
            <p v-if="!profile.currentCourses.length" class="hint">
              Add your current courses above first.
            </p>
            <label
              v-for="course in profile.currentCourses"
              :key="course"
              class="check-label"
              ><input
                v-model="profile.buddyCourses"
                type="checkbox"
                :value="course"
              />{{ course }}</label
            >
          </fieldset>
          <div class="form-grid">
            <label class="field"
              ><span>Preferred study mode</span
              ><select v-model="profile.studyMode">
                <option>In-person</option>
                <option>Online</option>
                <option>Either</option>
              </select></label
            ><label class="field"
              ><span>Group preference</span
              ><select v-model="profile.groupPreference">
                <option>One-to-one</option>
                <option>Small group</option>
                <option>Either</option>
              </select></label
            >
          </div>
          <fieldset>
            <legend>Study goals</legend>
            <label v-for="goal in studyGoals" :key="goal" class="check-label"
              ><input
                v-model="profile.goals"
                type="checkbox"
                :value="labelOf(goal)"
              />{{ labelOf(goal) }}</label
            >
          </fieldset>
        </section>
        <section class="form-section">
          <h3>Weekly availability</h3>
          <p v-if="!profile.availability.length" class="hint">
            No time slots added. Let others know when you’re free.
          </p>
          <ul class="simple-list">
            <li v-for="(slot, index) in profile.availability" :key="index">
              {{ slot.day }}, {{ slot.time }}
            </li>
          </ul>
          <RouterLink class="secondary-button" to="/student/availability"
            ><CalendarDays :size="17" />Edit availability</RouterLink
          >
        </section>
      </fieldset>
      <p v-if="profileError" class="error-message" role="alert">
        {{ profileError }}
      </p>
      <div class="save-bar">
        <span role="status">{{
          profileDirty ? "You have unsaved changes" : "All changes saved"
        }}</span
        ><button
          class="primary-button"
          :disabled="profileSaving || !profileDirty || Boolean(phoneError)"
        >
          {{ profileSaving ? "Saving…" : "Save profile" }}
        </button>
      </div>
    </form>
  </section>
</template>
