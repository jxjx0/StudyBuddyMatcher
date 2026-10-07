<script setup>
import { computed, ref, watch, nextTick } from "vue";
import AppDialog from "../AppDialog.vue";
import { labelOf, slotText } from "../../utils/studyData";
const props = defineProps({
  group: Object,
  requests: Array,
  busy: Boolean,
  error: String,
});
const emit = defineEmits(["close", "edit", "action"]);
const confirmation = ref(null);
const confirmationPanel = ref(null);
watch(confirmation, async (value) => {
  if (!value) return;
  await nextTick();
  confirmationPanel.value?.focus();
});
watch(
  () => props.group,
  () => {
    confirmation.value = null;
  },
);
const joiningMessage = computed(() => {
  const group = props.group;
  if (!group) return "";
  if (group.myRequestStatus === "PENDING")
    return "Your join request is awaiting the leader’s decision.";
  if (group.status === "CLOSED") return "This group is closed.";
  if (!group.leaderAvailable)
    return "The leader is unavailable. New requests are paused.";
  if (group.memberCount >= group.maxGroupSize) return "This group is full.";
  return "Joining is unavailable.";
});
function sendAction(path, message, method = "POST") {
  emit("action", { path, message, method });
}
function requestToJoin() {
  sendAction(
    `/api/groups/${props.group.groupId}/join-requests`,
    "Join request sent",
  );
}
function decide(request, decision) {
  const message =
    decision === "accept" ? "Request accepted" : "Request rejected";
  sendAction(
    `/api/groups/join-requests/${request.groupJoinRequestId}/${decision}`,
    message,
  );
}
function confirmRemove(member) {
  confirmation.value = {
    title: `Remove ${member.name}?`,
    text: "They will no longer be a member of this group.",
    path: `/api/groups/${props.group.groupId}/members/${member.studentId}`,
    method: "DELETE",
    message: "Member removed",
    button: "Remove member",
  };
}
function confirmClose() {
  confirmation.value = {
    title: "Close this group?",
    text: "All pending requests will be rejected. The group cannot be reopened.",
    path: `/api/groups/${props.group.groupId}/close`,
    method: "POST",
    message: "Group closed",
    button: "Close group",
  };
}
function confirm() {
  const action = confirmation.value;
  sendAction(action.path, action.message, action.method);
}
</script>
<template>
  <AppDialog
    :open="Boolean(group)"
    :title="group?.groupName || 'Group details'"
    :busy="busy"
    @close="emit('close')"
  >
    <template v-if="group">
      <div class="chip-list">
        <span class="tag">{{ group.courseCode }}</span
        ><span class="tag">{{ labelOf(group.status) }}</span
        ><span class="tag">{{ labelOf(group.meetingMode) }}</span>
      </div>
      <p class="preserve-lines">
        {{ group.description || "No description added." }}
      </p>
      <dl class="detail-grid">
        <div>
          <dt>Members</dt>
          <dd>
            {{ group.memberCount }} of {{ group.maxGroupSize }} places filled
          </dd>
        </div>
        <div>
          <dt>Study goals</dt>
          <dd>
            {{
              group.studyGoals.map(labelOf).join(", ") ||
              "Flexible study goals"
            }}
          </dd>
        </div>
        <div class="full">
          <dt>Availability</dt>
          <dd v-for="(slot, index) in group.availability" :key="index">
            {{ slotText(slot) }}
          </dd>
          <dd v-if="!group.availability.length">Flexible schedule</dd>
        </div>
      </dl>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <button
        v-if="group.canRequest"
        class="primary-button"
        :disabled="busy"
        @click="requestToJoin"
      >
        {{ busy ? "Sending…" : "Request to join" }}
      </button>
      <p v-else-if="!group.isMember" class="feature-note">
        {{ joiningMessage }}
      </p>
      <section class="form-section">
        <h3>Members</h3>
        <ul class="member-list">
          <li v-for="member in group.members" :key="member.studentId">
            <div>
              <strong>{{ member.name }}</strong
              ><span v-if="member.studentId === group.leaderId" class="tag"
                >Leader</span
              ><small v-if="!member.available">Unavailable</small>
            </div>
            <button
              v-if="
                group.isLeader &&
                group.status === 'ACTIVE' &&
                member.studentId !== group.leaderId
              "
              class="text-button danger"
              :disabled="busy"
              :aria-label="`Remove ${member.name}`"
              @click="confirmRemove(member)"
            >
              Remove
            </button>
          </li>
        </ul>
      </section>
      <template v-if="group.isLeader"
        ><section class="form-section">
          <h3>Join requests</h3>
          <p v-if="!requests.length" class="hint">
            No requests yet. New requests will appear here.
          </p>
          <div
            v-for="request in requests"
            :key="request.groupJoinRequestId"
            class="request-row"
          >
            <div>
              <strong>{{ request.studentName }}</strong>
              <p>{{ labelOf(request.status) }}</p>
            </div>
            <div
              v-if="request.status === 'PENDING' && group.status === 'ACTIVE'"
              class="form-actions"
            >
              <button
                class="secondary-button"
                :disabled="busy || group.memberCount >= group.maxGroupSize"
                :aria-label="`Accept ${request.studentName}'s request`"
                @click="decide(request, 'accept')"
              >
                Accept</button
              ><button
                class="text-button"
                :disabled="busy"
                :aria-label="`Reject ${request.studentName}'s request`"
                @click="decide(request, 'reject')"
              >
                Reject
              </button>
            </div>
          </div>
          <p
            v-if="
              group.memberCount >= group.maxGroupSize &&
              group.status === 'ACTIVE'
            "
            class="hint"
          >
            The group is full. Increase its size before accepting more members.
          </p>
        </section>
        <div v-if="group.status === 'ACTIVE'" class="dialog-footer">
          <button
            class="secondary-button"
            :disabled="busy"
            @click="emit('edit', group)"
          >
            Edit group</button
          ><button
            class="text-button danger"
            :disabled="busy"
            @click="confirmClose"
          >
            Close group
          </button>
        </div>
      </template>
      <section
        v-if="confirmation"
        ref="confirmationPanel"
        tabindex="-1"
        class="confirmation"
        role="alert"
      >
        <h3>{{ confirmation.title }}</h3>
        <p>{{ confirmation.text }}</p>
        <div class="form-actions">
          <button
            class="secondary-button"
            :disabled="busy"
            @click="confirmation = null"
          >
            Cancel</button
          ><button class="danger-button" :disabled="busy" @click="confirm">
            {{ busy ? "Updating…" : confirmation.button }}
          </button>
        </div>
      </section>
    </template>
  </AppDialog>
</template>
