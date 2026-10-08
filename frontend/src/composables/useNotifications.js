import { ref } from "vue";

const toastMessage = ref("");
const toastType = ref("success");

// type is 'success' or 'error'; errors stay up longer so there is time to read them.
// A new toast cancels the previous one's timer, so it is not hidden early.
let toastTimer;
function dismissToast() {
  clearTimeout(toastTimer);
  toastMessage.value = "";
}
function toast(message = "Profile saved", type = "success") {
  toastMessage.value = message;
  toastType.value = type;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(
    () => (toastMessage.value = ""),
    type === "error" ? 7000 : 4000,
  );
}

export { toastMessage, toastType, toast, dismissToast };
