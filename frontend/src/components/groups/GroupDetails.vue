<script setup>
import { computed, nextTick, ref, watch } from "vue";
import AppDialog from "../AppDialog.vue";
import { labelOf, requestTime, slotText } from "../../utils/studyData";
const props = defineProps({
  group: Object,
  requests: Array,
  busy: Boolean,
  error: String,
  notice: String,
});
const emit = defineEmits(["close", "edit", "action"]);
const confirmation = ref(null);
const pendingRequests = computed(() =>
  props.requests.filter((request) => request.status === "PENDING"),
);
const requestHistory = computed(() =>
  props.requests.filter((request) => request.status !== "PENDING"),
);
const noticePanel = ref(null);
watch(
  () => props.notice,
  async (message) => {
    if (!message) return;
    await nextTick();
    noticePanel.value?.focus();
  },
);
watch(
  () => props.group,
  () => {
    confirmation.value = null;
  },
);
const joiningMessage = computed(() => {
  const group = props.group;
  if (!group) return "";
  if (group.myRequestStatus === "PENDING") return "Request pending.";
  if (group.status === "CLOSED") return "This group is closed.";
  if (!group.leaderAvailable) return "Requests paused: leader unavailable.";
  if (group.memberCount >= group.maxGroupSize) return "This group is full.";
  return "Joining is unavailable.";
});
function sendAction(path, message, method = "POST", endGroup = false) {
  emit("action", { path, message, method, endGroup });
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
    path: `/api/groups/${props.group.groupId}/members/${member.studentId}`,
    method: "DELETE",
    message: "Member removed",
    button: "Remove member",
  };
}
function confirmClose() {
  confirmation.value = {
    title: "End this study group?",
    text: `“${props.group.groupName}” will close permanently. Pending requests will be rejected.`,
    path: `/api/groups/${props.group.groupId}/close`,
    method: "POST",
    message: `“${props.group.groupName}” is now closed.`,
    button: "End group permanently",
    endGroup: true,
  };
}
function confirm() {
  const action = confirmation.value;
  sendAction(action.path, action.message, action.method, action.endGroup);
}
</script>
<template>
  <AppDialog
    :open="Boolean(group)"
    :title="group?.groupName || 'Group details'"
    :busy="busy"
    close-label="Back to groups"
    @close="emit('close')"
  >
    <template v-if="group">
      <p
        v-if="notice"
        ref="noticePanel"
        tabindex="-1"
        class="group-notice"
        role="status"
      >
        {{ notice }}
      </p>
      <p v-if="group.status === 'CLOSED'" class="feature-note">
        Closed groups cannot be edited or reopened.
      </p>
      <div class="chip-list">
        <span class="tag">{{ group.courseCode }}</span
        ><span class="tag">{{ labelOf(group.status) }}</span
        ><span class="tag">{{ labelOf(group.meetingMode) }}</span>
      </div>
      <p v-if="group.description" class="preserve-lines">
        {{ group.description }}
      </p>
      <dl class="detail-grid">
        <div>
          <dt>Members</dt>
          <dd> {{ group.memberCount }} / {{ group.maxGroupSize }} </dd>
        </div>
        <div>
          <dt>Study goals</dt>
          <dd>
            {{ group.studyGoals.map(labelOf).join(", ") || "Any" }}
          </dd>
        </div>
        <div class="full">
          <dt>Availability</dt>
          <dd v-for="(slot, index) in group.availability" :key="index">
            {{ slotText(slot) }}
          </dd>
          <dd v-if="!group.availability.length">Flexible</dd>
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
      <p
        v-else-if="!group.isMember && group.status !== 'CLOSED'"
        class="feature-note"
      >
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
          <h3>Pending requests ({{ pendingRequests.length }})</h3>
          <div
            v-for="request in pendingRequests"
            :key="request.groupJoinRequestId"
            class="request-row"
          >
            <div>
              <strong>{{ request.studentName }}</strong>
              <p>Requested {{ requestTime(request.createdAt) }}</p>
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
              group.status === 'ACTIVE' &&
              pendingRequests.length
            "
            class="hint"
          >
            Increase its size to accept more members.
          </p>
          <details v-if="requestHistory.length" class="request-history">
            <summary>Request history ({{ requestHistory.length }})</summary>
            <div
              v-for="request in requestHistory"
              :key="request.groupJoinRequestId"
              class="request-row"
            >
              <div>
                <strong>{{ request.studentName }}</strong>
                <p>Requested {{ requestTime(request.createdAt) }}</p>
                <p v-if="request.respondedAt">
                  Responded {{ requestTime(request.respondedAt) }}
                </p>
              </div>
              <span class="tag">{{ labelOf(request.status) }}</span>
            </div>
          </details>
        </section>
      </template>
      <div class="dialog-footer group-detail-actions">
        <button class="primary-button" :disabled="busy" @click="emit('close')">
          Done
        </button>
        <template v-if="group.isLeader && group.status === 'ACTIVE'">
          <button
            class="secondary-button"
            :disabled="busy"
            @click="emit('edit', group)"
          >
            Edit group
          </button>
          <button
            class="text-button danger end-group-action"
            :disabled="busy"
            @click="confirmClose"
          >
            End group…
          </button>
        </template>
      </div>
    </template>
  </AppDialog>
  <AppDialog
    :open="Boolean(confirmation)"
    :title="confirmation?.title"
    :busy="busy"
    close-label="Cancel confirmation"
    @close="confirmation = null"
  >
    <template v-if="confirmation">
      <p v-if="confirmation.text">{{ confirmation.text }}</p>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <div class="dialog-footer">
        <button
          class="secondary-button"
          :disabled="busy"
          autofocus
          @click="confirmation = null"
        >
          {{ confirmation.endGroup ? "Keep group active" : "Keep member" }}
        </button>
        <button class="danger-button" :disabled="busy" @click="confirm">
          {{ busy ? "Updating…" : confirmation.button }}
        </button>
      </div>
    </template>
  </AppDialog>
</template>
