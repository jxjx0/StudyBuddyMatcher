<script setup>
import { computed, reactive, ref } from "vue";
import { useAppState } from "../../composables/useAppState";
import {
  weekDays,
  labelOf,
  to12h,
  to24h,
  slotProblem,
} from "../../utils/studyData";
const {
  profile,
  saveProfile,
  profileDirty,
  profileSaving,
  profileLoaded,
  profileError,
  dataLoading,
  dataError,
  loadAppData,
} = useAppState();
const draft = reactive({ day: "MONDAY", start: "18:00", end: "19:00" });
const editingIndex = ref(null);
const error = ref("");
const week = computed(() =>
  weekDays.map((day) => ({
    day,
    slots: profile.value.availability.filter((slot) =>
      slot.day.toUpperCase().startsWith(day.slice(0, 3)),
    ),
  })),
);
function addSlot() {
  error.value = slotProblem(draft.day, draft.start, draft.end);
  if (error.value) return;
  const slot = {
    day: labelOf(draft.day).slice(0, 3),
    time: `${to12h(draft.start)} – ${to12h(draft.end)}`,
  };
  const duplicate = profile.value.availability.some(
    (existing, index) =>
      index !== editingIndex.value &&
      existing.day === slot.day &&
      existing.time === slot.time,
  );
  if (duplicate) {
    error.value = "This time slot is already on your schedule.";
    return;
  }
  if (editingIndex.value === null) profile.value.availability.push(slot);
  else profile.value.availability[editingIndex.value] = slot;
  resetEditor();
}
function editSlot(index) {
  const slot = profile.value.availability[index];
  const [start, end] = slot.time.split(" – ");
  draft.day = weekDays.find((day) => day.startsWith(slot.day.toUpperCase()));
  draft.start = to24h(start).slice(0, 5);
  draft.end = to24h(end).slice(0, 5);
  editingIndex.value = index;
  error.value = "";
}
function resetEditor() {
  editingIndex.value = null;
  error.value = "";
}
function removeSlot(index) {
  profile.value.availability.splice(index, 1);
  resetEditor();
}
</script>
<template>
  <section class="page-body narrow-page">
    <RouterLink class="back-link" to="/student/profile"
      >Back to my profile</RouterLink
    >
    <header class="page-heading">
      <h1>Weekly availability</h1>
      <p>
        Add the times you’re free for a study session. Times follow your usual
        local schedule.
      </p>
    </header>
    <p v-if="dataLoading" role="status">Loading availability…</p>
    <div v-else-if="!profileLoaded" class="error-message" role="alert">
      <p>{{ dataError || "Your profile could not be loaded." }}</p>
      <button class="secondary-button" @click="loadAppData">Try again</button>
    </div>
    <div v-else class="form-card">
      <form @submit.prevent="addSlot">
        <fieldset class="plain-fieldset" :disabled="profileSaving">
          <h2>
            {{ editingIndex === null ? "Add a time slot" : "Edit time slot" }}
          </h2>
          <div class="slot-row">
            <label class="field"
              ><span>Day</span
              ><select v-model="draft.day">
                <option v-for="day in weekDays" :key="day" :value="day">
                  {{ labelOf(day) }}
                </option>
              </select></label
            ><label class="field"
              ><span>Start time</span
              ><input v-model="draft.start" type="time" required /></label
            ><label class="field"
              ><span>End time</span
              ><input v-model="draft.end" type="time" required
            /></label>
          </div>
          <p v-if="error" class="error-message" role="alert">{{ error }}</p>
          <div class="form-actions">
            <button class="secondary-button">
              {{
                editingIndex === null ? "Add time slot" : "Update time slot"
              }}</button
            ><button
              v-if="editingIndex !== null"
              type="button"
              class="text-button"
              @click="resetEditor"
            >
              Cancel edit
            </button>
          </div>
        </fieldset>
      </form>
      <section class="form-section">
        <h2>Your weekly schedule</h2>
        <div class="week-overview">
          <div v-for="day in week" :key="day.day">
            <strong>{{ labelOf(day.day).slice(0, 3) }}</strong
            ><span v-for="(slot, index) in day.slots" :key="index">{{
              slot.time
            }}</span
            ><small v-if="!day.slots.length">No slots</small>
          </div>
        </div>
        <ul class="member-list">
          <li v-for="(slot, index) in profile.availability" :key="index">
            <span>{{ slot.day }}, {{ slot.time }}</span>
            <div class="form-actions">
              <button
                class="text-button"
                :aria-label="`Edit ${slot.day} ${slot.time}`"
                :disabled="profileSaving"
                @click="editSlot(index)"
              >
                Edit</button
              ><button
                class="text-button danger"
                :aria-label="`Remove ${slot.day} ${slot.time}`"
                :disabled="profileSaving"
                @click="removeSlot(index)"
              >
                Remove
              </button>
            </div>
          </li>
        </ul>
      </section>
      <p class="hint">
        Saving also saves any other changes you made on My profile.
      </p>
      <p v-if="profileError" class="error-message" role="alert">
        {{ profileError }}
      </p>
      <div class="save-bar">
        <span role="status">{{
          profileDirty ? "You have unsaved changes" : "All changes saved"
        }}</span
        ><button
          class="primary-button"
          :disabled="profileSaving || !profileDirty || editingIndex !== null"
          @click="saveProfile('Availability saved')"
        >
          {{ profileSaving ? "Saving…" : "Save availability" }}
        </button>
      </div>
    </div>
  </section>
</template>
