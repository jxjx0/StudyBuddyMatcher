<script setup>
import { ref, watch } from "vue";
import AppDialog from "./AppDialog.vue";

const props = defineProps({
  student: Object,
  busy: Boolean,
});
const emit = defineEmits(["close", "send"]);
const message = ref("");

watch(
  () => props.student,
  () => {
    message.value = "";
  },
);

function submit() {
  emit("send", message.value.trim());
}
</script>

<template>
  <AppDialog
    :open="Boolean(student)"
    :title="student ? `Connect with ${student.name}` : 'Connect with student'"
    :busy="busy"
    close-label="Cancel connection request"
    @close="emit('close')"
  >
    <form @submit.prevent="submit">
      <p>
        Send a connection request to {{ student?.name || "this student" }}.
        You can include an optional message.
      </p>
      <label class="field">
        <span>Message <small>(optional)</small></span>
        <textarea
          v-model="message"
          rows="4"
          maxlength="500"
          placeholder="Introduce yourself or share what you would like to study."
        ></textarea>
      </label>
      <div class="dialog-footer">
        <button
          type="button"
          class="secondary-button"
          :disabled="busy"
          @click="emit('close')"
        >
          Cancel
        </button>
        <button type="submit" class="primary-button" :disabled="busy">
          {{ busy ? "Sending…" : "Send request" }}
        </button>
      </div>
    </form>
  </AppDialog>
</template>
