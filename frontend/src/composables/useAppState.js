// Shared student profile, discovery filters and sign-in flows.
import { computed, ref, watch } from "vue";
import { GraduationCap, ShieldCheck, Sparkles, Users } from "lucide-vue-next";
import router from "../router";
import {
  authenticated,
  authError,
  loginRole,
  userRole,
  user,
  token,
  saveSession,
  clearSession,
} from "./useAuth";

import { api } from "../services/api";
import {
  toastMessage,
  toastType,
  toast,
  dismissToast,
} from "./useNotifications";
import {
  matchingStrategy,
  matchingWeights,
  matchingCriteria,
  totalWeight,
  strategyHint,
  changeStrategy,
  setWeight,
  saveMatchingConfiguration,
  loadMatchingConfiguration,
  matchingConfigError,
  matchingBusy,
  matchingLoaded,
} from "./useMatching";
import {
  accounts,
  usageSummary,
  accountSearch,
  accountRole,
  showAccountForm,
  editingAccount,
  accountDraft,
  visibleAccounts,
  openAccountForm,
  saveAccount,
  deleteAccount,
  loadAccounts,
  loadUsageSummary,
  accountBusy,
  accountError,
} from "./useAccounts";
import { to12h, to24h, smuSchools } from "../utils/studyData";

const query = ref("");
const showBuddyFilters = ref(false);

const courses = ref([]);

const profile = ref({
  name: "",
  school: smuSchools[0],
  programme: "",
  year: "Year 1",
  phone: "",
  currentCourses: [],
  buddyCourses: [],
  studyMode: "Either",
  groupPreference: "Either",
  goals: [],
  availability: [],
});
// A contact number must start with + and a country code, e.g. +65 8123 4567. Same rule as StudentRequest on the backend.
const validPhone = (phone) => /^\+\d[\d\s-]{5,}$/.test(phone.trim());
const phoneHint = "Start with + and your country code, e.g. +65 8123 4567";
// Optional on the profile page, but checked when given.
const phoneError = computed(() =>
  profile.value.phone.trim() && !validPhone(profile.value.phone)
    ? phoneHint
    : "",
);
const draftCourse = ref("");
const filters = ref({
  course: "",
  availability: "Any availability",
  mode: "Any mode",
  goal: "",
  groupSize: "Any group size",
});

const students = ref([]);
const dataError = ref("");
const dataLoading = ref(false);
const profileSaving = ref(false);
const profileLoaded = ref(false);
const profileError = ref("");
const savedProfile = ref("");
const profileDirty = computed(
  () =>
    profileLoaded.value && JSON.stringify(profile.value) !== savedProfile.value,
);
// 'Jack Tan' -> 'JT'; 'SB' (StudyBuds) when there is no name yet.
function initialsOf(name) {
  let initials = "";
  for (const part of (name || "").split(" ")) {
    if (part) initials += part[0];
    if (initials.length === 2) break;
  }
  return initials.toUpperCase() || "SB";
}

const displayCourse = (course) => `${course.courseCode} ${course.courseName}`;
const modeLabels = {
  IN_PERSON: "In-person",
  ONLINE: "Online",
  EITHER: "Either",
};
const groupSizeLabels = {
  ONE_TO_ONE: "One-to-one",
  SMALL_GROUP: "Small group",
  EITHER: "Either",
};
const goalLabel = (goal) =>
  goal
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (c) => c.toUpperCase());
// 'In-person' -> 'IN_PERSON', 'Small group' -> 'SMALL_GROUP'
const toEnum = (label) =>
  label.toUpperCase().replaceAll("-", "_").replaceAll(" ", "_");
// The backend stores days as 'MONDAY' and times as '18:00:00'; the pages show 'Mon' and '6:00 PM'.
const weekDays = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];
const fullDay = (day) =>
  weekDays.find((item) => item.startsWith(day.slice(0, 3).toUpperCase())) ||
  day.toUpperCase();
const shortDay = (day) => day.charAt(0) + day.slice(1, 3).toLowerCase();
const slotLabel = (slot) => ({
  day: shortDay(slot.day),
  time: `${to12h(slot.startTime)} – ${to12h(slot.endTime)}`,
});
function mapStudent(student) {
  const course = student.coursesForStudying?.[0] || student.currentCourses?.[0];
  const goals = (student.studyGoals || []).map(goalLabel);
  return {
    id: student.userId,
    name: student.name || "Student",
    initials: initialsOf(student.name),
    color: "coral",
    school: student.school,
    programme: `${student.programme || student.school}, Year ${student.yearOfStudy}`,
    course: course ? displayCourse(course) : "",
    courseCodes: [
      ...(student.coursesForStudying || []),
      ...(student.currentCourses || []),
    ].map((item) => item.courseCode.toLowerCase()),
    slots: student.weeklyAvailability || [],
    availability:
      (student.weeklyAvailability || [])
        .map(slotLabel)
        .map((slot) => `${slot.day} ${slot.time}`)
        .join(", ") || "Flexible",
    goals,
    goal: goals.join(", ") || "Study together",
    mode: modeLabels[student.meetingMode] || "Either",
    groupSize: groupSizeLabels[student.groupSizePreference] || "Either",
  };
}
async function loadCourses() {
  courses.value = (await api("/api/courses")).map(displayCourse);
  if (!courses.value.includes(draftCourse.value))
    draftCourse.value = courses.value[0] || "";
}
async function loadStudents() {
  students.value = (await api("/api/students"))
    .filter((student) => student.userId !== user.value?.userId)
    .map(mapStudent);
}
function applyProfile(saved, phone) {
  Object.assign(profile.value, {
    name: saved.name,
    school: saved.school,
    programme: saved.programme || "",
    year: `Year ${saved.yearOfStudy}`,
    phone: phone || "",
    currentCourses: (saved.currentCourses || []).map(displayCourse),
    buddyCourses: (saved.coursesForStudying || []).map(displayCourse),
    studyMode: modeLabels[saved.meetingMode] || "Either",
    groupPreference: groupSizeLabels[saved.groupSizePreference] || "Either",
    goals: (saved.studyGoals || []).map(goalLabel),
    availability: (saved.weeklyAvailability || []).map(slotLabel),
  });
}
// The contact number is private, so it comes from its own endpoint rather than the public profile.
async function loadOwnProfile() {
  if (!user.value?.userId || userRole.value !== "student") return;
  const [saved, contact] = await Promise.all([
    api(`/api/students/${user.value.userId}`),
    api(`/api/students/${user.value.userId}/contact`),
  ]);
  applyProfile(saved, contact.contactNumber);
  profileLoaded.value = true;
  savedProfile.value = JSON.stringify(profile.value);
}
async function loadPublicProfile(userId, options = {}) {
  return mapStudent(await api(`/api/students/${userId}`, options));
}
async function loadAppData() {
  dataLoading.value = true;
  dataError.value = "";
  try {
    await loadCourses();
    if (userRole.value === "admin") {
      await Promise.all([loadAccounts(), loadUsageSummary()]);
      try {
        await loadMatchingConfiguration();
      } catch (error) {
        matchingConfigError.value = `Could not load saved matching configuration: ${error.message}`;
      }
    } else if (userRole.value === "student") {
      await Promise.all([loadStudents(), loadOwnProfile()]);
    }
  } catch (error) {
    dataError.value = error.message;
    console.error("Could not load StudyBuds data", error);
  } finally {
    dataLoading.value = false;
  }
}
if (token.value && user.value) loadAppData();
async function saveProfile(message = "Profile saved") {
  if (profileSaving.value || !profileLoaded.value) return false;
  profileError.value = "";
  if (
    !profile.value.name.trim() ||
    !profile.value.school ||
    !profile.value.programme.trim()
  ) {
    profileError.value =
      "Add your name, school and programme on My profile before saving.";
    return false;
  }
  if (phoneError.value) {
    profileError.value = phoneError.value;
    return false;
  }
  profileSaving.value = true;
  try {
    const draft = profile.value;
    const phone = draft.phone.trim();
    const weeklyAvailability = [];
    for (const slot of draft.availability) {
      const [start, end] = slot.time.split(" – ");
      weeklyAvailability.push({
        day: fullDay(slot.day),
        startTime: to24h(start),
        endTime: to24h(end),
      });
    }
    const payload = {
      name: draft.name.trim(),
      school: draft.school,
      programme: draft.programme.trim(),
      yearOfStudy: Number(draft.year.replace("Year ", "")),
      contactNumber: phone || null,
      meetingMode: toEnum(draft.studyMode),
      groupSizePreference: toEnum(draft.groupPreference),
      studyGoals: draft.goals.map(toEnum),
      currentCourseCodes: draft.currentCourses.map(
        (label) => label.split(" ")[0],
      ),
      coursesForStudyingCodes: draft.buddyCourses.map(
        (label) => label.split(" ")[0],
      ),
      weeklyAvailability,
    };
    const saved = await api(`/api/students/${user.value.userId}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    applyProfile(saved, phone);
    savedProfile.value = JSON.stringify(profile.value);
    toast(message);
    return true;
  } catch (error) {
    profileError.value = error.message;
    return false;
  } finally {
    profileSaving.value = false;
  }
}

const nav = computed(() =>
  userRole.value === "admin"
    ? [
        {
          label: "Admin Dashboard",
          path: "admin/dashboard",
          icon: ShieldCheck,
        },
        { label: "User Accounts", path: "admin/users", icon: Users },
        {
          label: "Matching Configuration",
          path: "admin/matching",
          icon: Sparkles,
        },
      ]
    : [
        { label: "Discover Buddies", path: "student/discover", icon: Sparkles },
        { label: "My Connections", path: "student/connections", icon: Users },
        { label: "Study Groups", path: "student/groups", icon: Users },
        { label: "My Profile", path: "student/profile", icon: GraduationCap },
      ],
);

// A student's slots that fit the availability filter, as labels like 'Sat 10:00 AM – 12:00 PM'.
// 'Weekends' means a Saturday or Sunday slot; 'Evenings' means a slot ending after 6 PM.
function matchingSlots(student) {
  const matches = [];
  for (const slot of student.slots) {
    if (
      filters.value.availability === "Weekends" &&
      !["SATURDAY", "SUNDAY"].includes(slot.day)
    )
      continue;
    if (filters.value.availability === "Evenings" && slot.endTime <= "18:00")
      continue;
    const label = slotLabel(slot);
    matches.push(`${label.day} ${label.time}`);
  }
  return matches;
}
// Every student is listed until a filter narrows it down. A student who chose 'Either'
// for study mode or group size matches whichever option is filtered on.
const filteredStudents = computed(() => {
  const course = filters.value.course.trim().toLowerCase();
  const goal = filters.value.goal.trim().toLowerCase();
  const name = query.value.trim().toLowerCase();
  return students.value.filter((student) => {
    const matchesCourse = course && student.courseCodes.includes(course);
    const matchesGoal =
      goal && student.goals.some((item) => item.toLowerCase() === goal);
    const matchesName = !name || student.name.toLowerCase().includes(name);
    const matchesMode =
      filters.value.mode === "Any mode" ||
      student.mode === "Either" ||
      student.mode === filters.value.mode;
    const matchesGroupSize =
      filters.value.groupSize === "Any group size" ||
      student.groupSize === "Either" ||
      student.groupSize === filters.value.groupSize;
    const matchesAvailability =
      filters.value.availability === "Any availability" ||
      matchingSlots(student).length > 0;
    return (
      matchesName &&
      ((!course && !goal) || matchesCourse || matchesGoal) &&
      matchesMode &&
      matchesGroupSize &&
      matchesAvailability
    );
  });
});

function go(next) {
  router.push("/" + next);
}
async function signIn(email, password) {
  authError.value = "";
  try {
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    const session = await response.json().catch(() => null);
    if (response.status === 401)
      throw new Error(
        "Unable to log in. Check your email and password, and make sure the account is active.",
      );
    if (!response.ok)
      throw new Error(
        session?.message || `Login failed (${response.status}). Please try again.`,
      );
    if (!session)
      throw new Error(
        "The server returned an invalid response. Please try again.",
      );
    if (session.user.role.toLowerCase() !== loginRole.value)
      throw new Error(
        `This account is registered as ${session.user.role.toLowerCase()}. Choose the matching login type.`,
      );
    saveSession(session);
    await loadAppData();
    go(userRole.value === "admin" ? "admin/dashboard" : "student/discover");
  } catch (error) {
    authError.value = error.message || "Unable to connect to StudyBuds.";
  }
}
// Creates a student account; the backend replies with a session like login does, so the student is signed straight in.
async function signUp(details) {
  authError.value = "";
  try {
    const response = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(details),
    });
    const session = await response.json().catch(() => null);
    if (!response.ok)
      throw new Error(
        session?.message ||
          (response.status === 409
            ? "An account with this email already exists. Use another email or log in."
            : `Unable to create your account (${response.status}). Please try again.`),
      );
    if (!session)
      throw new Error(
        "The server returned an invalid response. Please try again.",
      );
    loginRole.value = "student";
    saveSession(session);
    await loadAppData();
    go("student/discover");
    toast(
      `Account created. Welcome to StudyBuds, ${session.user.name || details.name}!`,
    );
  } catch (error) {
    authError.value = error.message || "Unable to connect to StudyBuds.";
    toast(`Could not create your account: ${authError.value}`, "error");
  }
}

function signOut() {
  dismissToast();
  clearSession();
  router.push("/login");
}

function addCourse() {
  if (
    draftCourse.value &&
    !profile.value.currentCourses.includes(draftCourse.value)
  )
    profile.value.currentCourses.push(draftCourse.value);
}
function removeCourse(course) {
  profile.value.currentCourses = profile.value.currentCourses.filter(
    (item) => item !== course,
  );
  profile.value.buddyCourses = profile.value.buddyCourses.filter(
    (item) => item !== course,
  );
}
// Clear account-specific drafts immediately when signing out or switching accounts.
watch(
  token,
  () => {
    query.value = "";
    profileLoaded.value = false;
    profileError.value = "";
    students.value = [];
    accounts.value = [];
    showAccountForm.value = false;
    accountError.value = "";
  },
  { flush: "sync" },
);

export function useAppState() {
  return {
    authenticated,
    authError,
    loginRole,
    userRole,
    query,
    showBuddyFilters,
    toastMessage,
    toastType,
    dismissToast,
    courses,
    smuSchools,
    profile,
    draftCourse,
    filters,
    dataError,
    dataLoading,
    profileLoaded,
    profileSaving,
    profileError,
    profileDirty,
    accountBusy,
    accountError,
    matchingBusy,
    matchingLoaded,
    loadAppData,
    usageSummary,
    matchingStrategy,
    matchingWeights,
    matchingCriteria,
    totalWeight,
    strategyHint,
    changeStrategy,
    setWeight,
    saveMatchingConfiguration,
    matchingConfigError,
    students,
    accounts,
    accountSearch,
    accountRole,
    showAccountForm,
    editingAccount,
    accountDraft,
    visibleAccounts,
    openAccountForm,
    saveAccount,
    deleteAccount,
    nav,
    filteredStudents,
    mapStudent,
    matchingSlots,
    go,
    signIn,
    signUp,
    validPhone,
    phoneHint,
    signOut,
    toast,
    saveProfile,
    initialsOf,
    user,
    phoneError,
    loadPublicProfile,
    loadStudents,
    addCourse,
    removeCourse,
  };
}
