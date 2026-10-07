<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Plus, Search, Users, X } from 'lucide-vue-next'
import { useGroups } from '../../composables/useGroups'
import { useAppState } from '../../composables/useAppState'

const { groups, mine, requests, courses, selected, leaderRequests, loading, busy, error, load, open, closeDetail, mutate } = useGroups()
const { toast } = useAppState()
const tab = ref('browse'), query = ref(''), course = ref(''), goal = ref(''), mode = ref(''), day = ref(''), size = ref('')
const editor = ref(null), detail = ref(null), editingId = ref(null), confirmAction = ref(null)
const goals = ['CONCEPT_REVIEW', 'PROBLEM_SOLVING', 'EXAM_PREPARATION', 'PROJECT_DISCUSSION']
const modes = ['EITHER', 'ONLINE', 'IN_PERSON']
const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const label = value => value?.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase()) || 'Flexible'
const draft = reactive({ groupName: '', description: '', courseCode: '', maxGroupSize: 5, meetingMode: 'EITHER', studyGoals: [], availability: [] })
const visible = computed(() => (tab.value === 'mine' ? mine.value : groups.value).filter(g =>
  g.groupName.toLowerCase().includes(query.value.toLowerCase()) && (!course.value || g.courseCode === course.value) &&
  (!goal.value || g.studyGoals.includes(goal.value)) && (!mode.value || g.meetingMode === mode.value || g.meetingMode === 'EITHER') &&
  (!day.value || !g.availability.length || g.availability.some(s => s.day === day.value)) && (!size.value || g.maxGroupSize <= Number(size.value))))
const uniqueCourses = computed(() => [...new Map(courses.value.map(c => [c.courseCode, c])).values()])
const pendingRequests = computed(() => leaderRequests.value.filter(r => r.status === 'PENDING'))
const slotText = s => `${label(s.day)} ${s.startTime.slice(0, 5)}–${s.endTime.slice(0, 5)}`
watch(selected, async value => { await nextTick(); if (value && !detail.value?.open) detail.value?.showModal(); if (!value) detail.value?.close(); confirmAction.value = null })
function edit(group = null) {
  editingId.value = group?.groupId || null
  Object.assign(draft, { groupName: group?.groupName || '', description: group?.description || '', courseCode: group?.courseCode || uniqueCourses.value[0]?.courseCode || '',
    maxGroupSize: group?.maxGroupSize || 5, meetingMode: group?.meetingMode || 'EITHER', studyGoals: [...(group?.studyGoals || [])], availability: (group?.availability || []).map(s => ({ ...s })) })
  editor.value.showModal()
}
async function save() {
  const ok = await mutate(editingId.value ? `/api/groups/${editingId.value}` : '/api/groups', editingId.value ? 'PUT' : 'POST', { ...draft })
  if (ok) { editor.value.close(); tab.value = 'mine'; toast(editingId.value ? 'Group updated' : 'Group created') }
}
async function action(path, message, method = 'POST') {
  if (await mutate(path, method)) { confirmAction.value = null; toast(message) }
}
function dismiss() { if (!busy.value) { detail.value.close(); closeDetail() } }
function confirmRemove(member) { confirmAction.value = { text: `Remove ${member.name} from this group?`, path: `/api/groups/${selected.value.groupId}/members/${member.studentId}`, method: 'DELETE', message: 'Member removed' } }
function confirmClose() { confirmAction.value = { text: 'Close this group? Pending requests will be rejected and the group cannot be reopened.', path: `/api/groups/${selected.value.groupId}/close`, method: 'POST', message: 'Group closed' } }
</script>

<template>
  <section class="page-body groups-page">
    <section class="page-heading group-heading">
      <div><p class="eyebrow">STUDY TOGETHER</p><h1>Find your study group.</h1><p>Create a group, share your goals and learn together.</p></div>
      <button class="primary-button" :disabled="loading || busy || !courses.length" @click="edit()"><Plus :size="17" /> Create group</button>
    </section>
    <div class="group-tabs" aria-label="Group views">
      <button v-for="item in [['browse','Browse groups'],['mine','My groups'],['requests','My requests']]" :key="item[0]" :class="{ active: tab === item[0] }" :aria-pressed="tab === item[0]" @click="tab = item[0]">{{ item[1] }}</button>
      <button class="refresh" :disabled="loading || busy" @click="load">Refresh</button>
    </div>
    <p v-if="error" class="group-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">Loading groups…</p>
    <template v-else-if="tab !== 'requests'">
      <div class="toolbar"><label class="search-box"><Search :size="18" /><input v-model="query" aria-label="Search group name" placeholder="Search group name" /></label></div>
      <div class="group-filter-grid">
        <label class="field"><span>Course</span><select v-model="course"><option value="">Any course</option><option v-for="c in uniqueCourses" :key="c.courseCode" :value="c.courseCode">{{ c.courseCode }}</option></select></label>
        <label class="field"><span>Study goal</span><select v-model="goal"><option value="">Any goal</option><option v-for="g in goals" :key="g" :value="g">{{ label(g) }}</option></select></label>
        <label class="field"><span>Meeting mode</span><select v-model="mode"><option value="">Any mode</option><option v-for="m in modes" :key="m" :value="m">{{ label(m) }}</option></select></label>
        <label class="field"><span>Day</span><select v-model="day"><option value="">Any day</option><option v-for="d in days" :key="d" :value="d">{{ label(d) }}</option></select></label>
        <label class="field"><span>Maximum size</span><input v-model="size" type="number" min="2" placeholder="Any size" /></label>
      </div>
      <div v-if="visible.length" class="group-cards">
        <article v-for="g in visible" :key="g.groupId" class="large-group-card">
          <div class="match-title"><span class="soft-label">{{ g.status === 'CLOSED' ? 'CLOSED' : g.memberCount >= g.maxGroupSize ? 'FULL' : 'OPEN GROUP' }}</span><span>{{ g.isLeader ? 'You lead this group' : g.isMember ? 'Member' : g.myRequestStatus === 'PENDING' ? 'Request pending' : '' }}</span></div>
          <h2>{{ g.groupName }}</h2><p>{{ g.description || 'Study together at your own pace.' }}</p>
          <div class="group-meta"><span>{{ g.courseCode }}</span><span>{{ label(g.meetingMode) }}</span><span>{{ g.memberCount }} / {{ g.maxGroupSize }} members</span></div>
          <p class="group-goals">{{ g.studyGoals.map(label).join(' · ') || 'Flexible study goals' }}</p>
          <button class="secondary-button" :disabled="busy" @click="open(g.groupId)">View group</button>
        </article>
      </div>
      <div v-else class="group-empty"><Users :size="28" /><h2>{{ tab === 'mine' ? 'No groups yet' : 'No groups match' }}</h2><p>{{ tab === 'mine' ? 'Create a group or request to join one.' : 'Try different filters or create a group for your course.' }}</p></div>
    </template>
    <template v-else>
      <div v-if="!requests.length" class="group-empty"><h2>No requests yet</h2><p>Browse groups to find people studying your course.</p></div>
      <article v-for="r in requests" :key="r.groupJoinRequestId" class="request-row">
        <div><strong>{{ r.groupName }}</strong><p>{{ label(r.status) }} · {{ new Date(r.createdAt).toLocaleString() }}</p></div><button class="secondary-button" @click="open(r.groupId)">View group</button>
      </article>
    </template>

    <dialog ref="detail" class="group-dialog" aria-labelledby="group-title" @cancel.prevent="dismiss" @close="closeDetail">
      <template v-if="selected">
        <button class="dialog-close" aria-label="Close group details" :disabled="busy" @click="dismiss"><X /></button>
        <p class="eyebrow">{{ selected.status }} · {{ selected.courseCode }}</p><h2 id="group-title">{{ selected.groupName }}</h2><p>{{ selected.description }}</p>
        <p>{{ selected.memberCount }} / {{ selected.maxGroupSize }} members · {{ label(selected.meetingMode) }}</p>
        <p>{{ selected.studyGoals.map(label).join(' · ') || 'Flexible study goals' }}</p>
        <p v-for="(s, i) in selected.availability" :key="i">{{ slotText(s) }}</p><p v-if="!selected.availability.length">Flexible availability</p>
        <p v-if="!selected.leaderAvailable" class="group-error">The leader is unavailable. New requests are disabled.</p>
        <p v-if="error" class="group-error" role="alert">{{ error }}</p>
        <h3>Members</h3>
        <ul class="member-list"><li v-for="m in selected.members" :key="m.studentId"><span>{{ m.name }}{{ m.studentId === selected.leaderId ? ' · Leader' : '' }}{{ !m.available ? ' · Unavailable' : '' }}</span><button v-if="selected.isLeader && selected.status === 'ACTIVE' && m.studentId !== selected.leaderId" class="text-button" :disabled="busy" @click="confirmRemove(m)">Remove</button></li></ul>
        <button v-if="selected.canRequest" class="primary-button" :disabled="busy" @click="action(`/api/groups/${selected.groupId}/join-requests`, 'Join request sent')">Request to join</button>
        <p v-else-if="!selected.isMember">{{ selected.myRequestStatus === 'PENDING' ? 'Your request is pending.' : selected.status === 'CLOSED' ? 'This group is closed.' : selected.memberCount >= selected.maxGroupSize ? 'This group is full.' : 'Joining is unavailable.' }}</p>
        <template v-if="selected.isLeader">
          <div v-if="selected.status === 'ACTIVE'" class="group-actions"><button class="secondary-button" :disabled="busy" @click="edit(selected)">Edit group</button><button class="text-button danger" :disabled="busy" @click="confirmClose">Close group</button></div>
          <h3>Join requests</h3><p v-if="!pendingRequests.length">No pending requests.</p>
          <div v-for="r in leaderRequests" :key="r.groupJoinRequestId" class="request-row"><div><strong>{{ r.studentName }}</strong><p>{{ label(r.status) }}</p></div><div v-if="r.status === 'PENDING' && selected.status === 'ACTIVE'" class="group-actions"><button class="secondary-button" :disabled="busy || selected.memberCount >= selected.maxGroupSize" @click="action(`/api/groups/join-requests/${r.groupJoinRequestId}/accept`, 'Request accepted')">Accept</button><button class="text-button" :disabled="busy" @click="action(`/api/groups/join-requests/${r.groupJoinRequestId}/reject`, 'Request rejected')">Reject</button></div></div>
        </template>
        <div v-if="confirmAction" class="group-confirm" role="alert"><p>{{ confirmAction.text }}</p><div class="group-actions"><button class="primary-button" :disabled="busy" @click="action(confirmAction.path, confirmAction.message, confirmAction.method)">Confirm</button><button class="secondary-button" :disabled="busy" @click="confirmAction = null">Cancel</button></div></div>
      </template>
    </dialog>

    <dialog ref="editor" class="group-dialog" aria-labelledby="editor-title" @cancel="busy && $event.preventDefault()">
      <form @submit.prevent="save">
        <button type="button" class="dialog-close" aria-label="Close group form" :disabled="busy" @click="editor.close()"><X /></button>
        <h2 id="editor-title">{{ editingId ? 'Edit group' : 'Create a study group' }}</h2>
        <p v-if="error" class="group-error" role="alert">{{ error }}</p>
        <label class="field"><span>Group name</span><input v-model="draft.groupName" required maxlength="100" /></label>
        <label class="field"><span>Description</span><textarea v-model="draft.description" maxlength="2000" rows="3"></textarea></label>
        <div class="group-form-grid"><label class="field"><span>Course</span><select v-model="draft.courseCode" required :disabled="!!editingId"><option v-for="c in uniqueCourses" :key="c.courseCode" :value="c.courseCode">{{ c.courseCode }} {{ c.courseName }}</option></select></label>
        <label class="field"><span>Maximum members (including you)</span><input v-model.number="draft.maxGroupSize" type="number" min="2" step="1" required /></label>
        <label class="field"><span>Meeting mode</span><select v-model="draft.meetingMode"><option v-for="m in modes" :key="m" :value="m">{{ label(m) }}</option></select></label></div>
        <fieldset><legend>Study goals (optional)</legend><label v-for="g in goals" :key="g" class="goal-check"><input v-model="draft.studyGoals" type="checkbox" :value="g" /> {{ label(g) }}</label></fieldset>
        <fieldset><legend>Weekly availability (optional)</legend><div v-for="(s, i) in draft.availability" :key="i" class="slot-row"><label class="field"><span>Day</span><select v-model="s.day"><option v-for="d in days" :key="d" :value="d">{{ label(d) }}</option></select></label><label class="field"><span>Start</span><input v-model="s.startTime" type="time" required /></label><label class="field"><span>End</span><input v-model="s.endTime" type="time" required /></label><button type="button" class="text-button" @click="draft.availability.splice(i, 1)">Remove slot</button></div><button type="button" class="secondary-button" @click="draft.availability.push({ day: 'MONDAY', startTime: '18:00', endTime: '19:00' })">Add time slot</button></fieldset>
        <div class="group-actions"><button class="primary-button" :disabled="busy">{{ busy ? 'Saving…' : 'Save group' }}</button><button type="button" class="secondary-button" :disabled="busy" @click="editor.close()">Cancel</button></div>
      </form>
    </dialog>
  </section>
</template>

<style scoped>
.group-heading,.group-tabs,.group-actions,.request-row,.member-list li { display:flex;align-items:center;justify-content:space-between;gap:12px; }
.group-heading>div { flex:1; }.group-heading>.primary-button { flex-shrink:0; }
.group-tabs { justify-content:flex-start;border-bottom:1px solid #ddd8d2;margin-bottom:20px;flex-wrap:wrap; }
.group-tabs button { padding:12px 16px;border:0;background:transparent;color:#625b54;cursor:pointer; }.group-tabs button.active { color:#a6482c;border-bottom:3px solid #a6482c;font-weight:700; }.group-tabs .refresh { margin-left:auto; }
.group-filter-grid { display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin:18px 0 24px; }
.group-empty { text-align:center;padding:48px 16px;color:#746b62; }.group-error { padding:12px;background:#fff0ec;color:#932b24;border-radius:8px; }.group-goals { font-size:13px;color:#746b62; }
.group-dialog { border:1px solid #e5ded5;border-radius:18px;padding:30px;width:min(680px,calc(100vw - 32px));max-height:85vh;overflow:auto;color:#322d28;background:#fffdf9; }
.group-dialog::backdrop { background:#241d185e; }.group-dialog h2 { margin:8px 30px 18px 0; }.group-dialog h3 { margin-top:24px; }.group-dialog p { line-height:1.6; }.dialog-close { position:absolute;right:16px;top:16px;background:none;border:0;cursor:pointer; }
.group-dialog form { display:grid;gap:18px; }.group-dialog textarea { font:inherit;border:1px solid #d8d1c9;border-radius:8px;padding:10px;resize:vertical;width:100%; }.group-form-grid { display:grid;grid-template-columns:1fr 1fr;gap:14px; }
fieldset { border:1px solid #e5ded5;border-radius:10px;padding:14px; }legend { padding:0 6px;font-size:14px;font-weight:600; }.goal-check { display:inline-flex;align-items:center;gap:6px;margin:6px 16px 6px 0;font-size:14px; }.goal-check input { accent-color:#b85b3d; }
.slot-row { display:grid;grid-template-columns:1.5fr 1fr 1fr auto;gap:8px;align-items:end;margin-bottom:12px; }.group-actions { justify-content:flex-start;flex-wrap:wrap;margin-top:16px; }.member-list { list-style:none;padding:0; }.member-list li,.request-row { padding:14px 0;border-bottom:1px solid #ece6df; }.request-row p { margin:5px 0;font-size:14px;color:#746b62; }.text-button { background:none;border:0;cursor:pointer;color:#9b452a;text-decoration:underline;padding:8px; }.danger { color:#9f2f2a; }.group-confirm { border:1px solid #bf7159;border-radius:10px;padding:16px;margin-top:20px; }button:disabled { opacity:.5;cursor:not-allowed; }
@media(max-width:700px) { .group-heading { align-items:flex-start;flex-direction:column; }.group-filter-grid { grid-template-columns:1fr 1fr; }.group-form-grid { grid-template-columns:1fr; }.slot-row { grid-template-columns:1fr 1fr; }.group-dialog { padding:22px; }.request-row { align-items:flex-start;flex-wrap:wrap; } }
</style>
