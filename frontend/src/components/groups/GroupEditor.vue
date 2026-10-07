<script setup>
import { reactive, ref, watch } from "vue";
import AppDialog from "../AppDialog.vue";
import {
  studyGoals,
  meetingModes,
  weekDays,
  labelOf,
  slotProblem,
} from "../../utils/studyData";
const props = defineProps({
  open: Boolean,
  group: Object,
  courses: Array,
  busy: Boolean,
  error: String,
});
const emit = defineEmits(["close", "save"]);
const validationError = ref("");
const draft = reactive({});
watch(
  () => props.open,
  (open) => {
    if (!open) return;
    const group = props.group;
    validationError.value = "";
    Object.assign(draft, {
      groupName: group?.groupName || "",
      description: group?.description || "",
      courseCode: group?.courseCode || props.courses[0]?.courseCode || "",
      maxGroupSize: group?.maxGroupSize || 5,
      meetingMode: group?.meetingMode || "EITHER",
      studyGoals: [...(group?.studyGoals || [])],
      availability: (group?.availability || []).map((slot) => ({ ...slot })),
    });
  },
  { immediate: true },
);
function addSlot() {
  draft.availability.push({
    day: "MONDAY",
    startTime: "18:00",
    endTime: "19:00",
  });
}
function removeSlot(index) {
  draft.availability.splice(index, 1);
}
function save() {
  validationError.value = "";
  if (!draft.groupName.trim()) {
    validationError.value = "Give your group a name.";
    return;
  }
  for (const slot of draft.availability) {
    const problem = slotProblem(slot.day, slot.startTime, slot.endTime);
    if (problem) {
      validationError.value = problem;
      return;
    }
  }
  emit("save", { ...draft, groupName: draft.groupName.trim() });
}
</script>
<template>
  <AppDialog
    :open="open"
    :title="group ? 'Edit group' : 'Create a study group'"
    :busy="busy"
    @close="emit('close')"
  >
    <form v-if="open" @submit.prevent="save">
      <fieldset class="plain-fieldset stacked-form" :disabled="busy">
        <label class="field"
          ><span>Group name</span
          ><input
            v-model="draft.groupName"
            required
            maxlength="100"
            placeholder="e.g. OOP revision circle"
        /></label>
        <label class="field"
          ><span>Description <small>Optional</small></span
          ><textarea
            v-model="draft.description"
            maxlength="2000"
            rows="3"
            placeholder="What will you study together?"
          ></textarea>
        </label>
        <div class="form-grid">
          <label class="field"
            ><span>Course</span
            ><select
              v-model="draft.courseCode"
              required
              :disabled="Boolean(group)"
            >
              <option
                v-for="course in courses"
                :key="course.courseCode"
                :value="course.courseCode"
              >
                {{ course.courseCode }} {{ course.courseName }}
              </option></select
            ><small v-if="group"
              >The course is fixed after a group is created.</small
            ></label
          ><label class="field"
            ><span>Maximum members</span
            ><input
              v-model.number="draft.maxGroupSize"
              type="number"
              :min="Math.max(2, group?.memberCount || 2)"
              step="1"
              required
            /><small>Includes you as the group leader.</small></label
          ><label class="field"
            ><span>Meeting mode</span
            ><select v-model="draft.meetingMode">
              <option v-for="mode in meetingModes" :key="mode" :value="mode">
                {{ labelOf(mode) }}
              </option>
            </select></label
          >
        </div>
        <fieldset>
          <legend>Study goals · optional</legend>
          <label v-for="goal in studyGoals" :key="goal" class="check-label"
            ><input
              v-model="draft.studyGoals"
              type="checkbox"
              :value="goal"
            />{{ labelOf(goal) }}</label
          >
        </fieldset>
        <fieldset>
          <legend>Weekly availability · optional</legend>
          <p v-if="!draft.availability.length" class="hint">
            Leave this empty if your group’s schedule is flexible.
          </p>
          <div
            v-for="(slot, index) in draft.availability"
            :key="index"
            class="slot-editor"
          >
            <div class="slot-row">
              <label class="field"
                ><span>Day</span
                ><select v-model="slot.day">
                  <option v-for="day in weekDays" :key="day" :value="day">
                    {{ labelOf(day) }}
                  </option>
                </select></label
              ><label class="field"
                ><span>Start</span
                ><input v-model="slot.startTime" type="time" required /></label
              ><label class="field"
                ><span>End</span
                ><input v-model="slot.endTime" type="time" required
              /></label>
            </div>
            <button
              type="button"
              class="text-button danger"
              :aria-label="`Remove time slot ${index + 1}`"
              @click="removeSlot(index)"
            >
              Remove slot
            </button>
          </div>
          <button type="button" class="secondary-button" @click="addSlot">
            Add time slot
          </button>
        </fieldset>
      </fieldset>
      <p v-if="validationError || error" class="error-message" role="alert">
        {{ validationError || error }}
      </p>
      <div class="dialog-footer">
        <button class="primary-button" :disabled="busy">
          {{ busy ? "Saving…" : "Save group" }}</button
        ><button
          type="button"
          class="secondary-button"
          :disabled="busy"
          @click="emit('close')"
        >
          Cancel
        </button>
      </div>
    </form>
  </AppDialog>
</template>
