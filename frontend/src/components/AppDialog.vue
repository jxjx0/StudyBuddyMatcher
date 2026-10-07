<script setup>
import { nextTick, ref, useId, watch } from "vue";
import { X } from "lucide-vue-next";

const props = defineProps({ open: Boolean, title: String, busy: Boolean });
const emit = defineEmits(["close"]);
const dialog = ref(null);
const titleId = useId();

watch(
  () => props.open,
  async (open) => {
    await nextTick();
    const element = dialog.value;
    if (!element) return;
    if (open && !element.open) element.showModal();
    if (!open && element.open) {
      element.close();
      // A refreshed list may have removed the button that originally opened this dialog.
      if (document.activeElement === document.body) {
        document.querySelector("#main-content")?.focus();
      }
    }
  },
  { immediate: true },
);

function close() {
  if (!props.busy) emit("close");
}
</script>

<template>
  <dialog
    ref="dialog"
    class="app-dialog"
    :aria-labelledby="titleId"
    @cancel.prevent="close"
  >
    <header class="dialog-header">
      <h2 :id="titleId">{{ title }}</h2>
      <button
        type="button"
        class="icon-button"
        :aria-label="`Close ${title}`"
        :disabled="busy"
        @click="close"
      >
        <X :size="20" />
      </button>
    </header>
    <div class="dialog-body"><slot /></div>
  </dialog>
</template>
