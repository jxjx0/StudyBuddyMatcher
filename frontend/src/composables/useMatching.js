import { computed, ref } from "vue";
import { api } from "../services/api";
import { rebalanceWeights } from "../utils/matchingWeights";
import { toast } from "./useNotifications";

const matchingBusy = ref(false);
const matchingLoaded = ref(false);
const matchingStrategy = ref("");
const matchingWeights = ref({
  course: 30,
  availability: 20,
  mode: 20,
  goal: 20,
  groupSize: 10,
});
// Which criteria the matching engine uses. A switched-off criterion has weight 0
// and does not count towards a student's score.
const matchingEnabled = ref({
  course: true,
  availability: true,
  mode: true,
  goal: true,
  groupSize: true,
});
const matchingConfigError = ref("");
const matchingCriteria = [
  { key: "course", label: "Course match" },
  { key: "availability", label: "Availability" },
  { key: "mode", label: "Study mode" },
  { key: "goal", label: "Study goal" },
  { key: "groupSize", label: "Preferred group size" },
];
const criterionTypes = {
  course: "COURSE",
  availability: "AVAILABILITY",
  mode: "STUDY_MODE",
  goal: "STUDY_GOAL",
  groupSize: "GROUP_SIZE",
};
const criterionKeys = Object.fromEntries(
  Object.entries(criterionTypes).map(([key, type]) => [type, key]),
);
const totalWeight = computed(() =>
  Object.values(matchingWeights.value).reduce(
    (sum, value) => sum + Number(value || 0),
    0,
  ),
);
const strategyHints = {
  course: "Course match is locked as the highest priority.",
  availability: "Availability is locked as the highest priority.",
  balanced: "Balanced matching gives every criterion an equal weight.",
  "": "No strategy rules are applied; adjust criteria freely.",
};
const strategyHint = computed(() => strategyHints[matchingStrategy.value]);

// The criterion a "first" strategy keeps as the highest weight, or "" for none.
const lockedCriterion = computed(() => {
  if (matchingStrategy.value === "course") return "course";
  if (matchingStrategy.value === "availability") return "availability";
  return "";
});
const enabledKeys = () =>
  matchingCriteria
    .map(({ key }) => key)
    .filter((key) => matchingEnabled.value[key]);

// Whole-number weights for the enabled criteria that add up to 100.
// Switched-off criteria get 0.
function presetWeights(strategy) {
  const keys = enabledKeys();
  const weights = Object.fromEntries(
    matchingCriteria.map(({ key }) => [key, 0]),
  );
  const lead = strategy === "course" || strategy === "availability" ? strategy : "";
  if (!lead) {
    // Equal shares; any remainder goes to the first criteria.
    keys.forEach((key, index) => {
      weights[key] =
        Math.floor(100 / keys.length) + (index < 100 % keys.length ? 1 : 0);
    });
    return weights;
  }
  // The lead criterion starts at 40 and the others share the rest, so it is
  // strictly the highest. With only one other criterion it becomes 60/40.
  const others = keys.filter((key) => key !== lead);
  if (others.length === 0) {
    weights[lead] = 100;
    return weights;
  }
  const otherWeight =
    others.length === 1 ? 40 : Math.floor(60 / others.length);
  for (const key of others) weights[key] = otherWeight;
  weights[lead] = 100 - otherWeight * others.length;
  return weights;
}

function changeStrategy(value) {
  matchingStrategy.value = value;
  // A "first" strategy needs its lead criterion, so it is switched back on.
  if (value === "course" || value === "availability")
    matchingEnabled.value = { ...matchingEnabled.value, [value]: true };
  if (value) matchingWeights.value = presetWeights(value);
}

function setWeight(key, value) {
  // Balanced always uses equal weights, so its sliders cannot be moved.
  if (matchingStrategy.value === "balanced") return;
  if (!matchingEnabled.value[key]) return;
  const lockedKey = lockedCriterion.value;
  // Only enabled criteria share the 100%. If this is the only one that can move,
  // its weight is fixed by the others.
  const keys = enabledKeys();
  if (keys.filter((item) => item !== lockedKey).length <= 1) return;
  const enabledWeights = Object.fromEntries(
    keys.map((item) => [item, matchingWeights.value[item]]),
  );
  try {
    matchingWeights.value = {
      ...matchingWeights.value,
      ...rebalanceWeights(enabledWeights, key, value, lockedKey),
    };
    matchingConfigError.value = "";
  } catch (error) {
    matchingConfigError.value = error.message;
  }
}

function setCriterionEnabled(key, enabled) {
  if (key === lockedCriterion.value && !enabled) return;
  if (!enabled && enabledKeys().length === 1) {
    matchingConfigError.value = "At least one criterion must stay switched on.";
    return;
  }
  matchingConfigError.value = "";
  matchingEnabled.value = { ...matchingEnabled.value, [key]: enabled };
  const strategy = matchingStrategy.value;
  if (strategy) {
    // Strategies have fixed starting weights, so recompute them for the new set.
    matchingWeights.value = presetWeights(strategy);
  } else if (!enabled) {
    // Without a strategy the freed weight is shared among the criteria still on.
    const freed = matchingWeights.value[key];
    const weights = { ...matchingWeights.value, [key]: 0 };
    const keys = enabledKeys();
    keys.forEach((item, index) => {
      weights[item] +=
        Math.floor(freed / keys.length) + (index < freed % keys.length ? 1 : 0);
    });
    matchingWeights.value = weights;
  }
  // A criterion switched back on without a strategy starts at 0; the admin sets its weight.
}

async function loadMatchingConfiguration() {
  matchingLoaded.value = false;
  matchingConfigError.value = "";
  const config = await api("/api/matching/config");
  const strategyName =
    typeof config.strategy === "string"
      ? config.strategy
      : config.strategy?.name;
  const strategyKeys = {
    COURSE_FIRST: "course",
    AVAILABILITY_FIRST: "availability",
    BALANCED: "balanced",
  };
  matchingStrategy.value = strategyKeys[strategyName] || "";
  const weights = { ...matchingWeights.value };
  const enabled = { ...matchingEnabled.value };
  for (const criterion of config.criteria || []) {
    const key = criterionKeys[criterion.type];
    if (!key) continue;
    enabled[key] = criterion.enabled !== false;
    weights[key] = enabled[key] ? Number(criterion.weight) : 0;
  }
  matchingEnabled.value = enabled;
  if (matchingStrategy.value === "balanced") {
    // Balanced stores exact equal shares (e.g. 33.33); show them as whole numbers.
    matchingWeights.value = presetWeights("balanced");
  } else if (
    Math.round(Object.values(weights).reduce((total, weight) => total + weight, 0)) === 100
  )
    matchingWeights.value = weights;
  matchingLoaded.value = true;
}
async function saveMatchingConfiguration() {
  if (matchingBusy.value || totalWeight.value !== 100) return;
  const strategyNames = {
    course: "COURSE_FIRST",
    availability: "AVAILABILITY_FIRST",
    balanced: "BALANCED",
    "": "",
  };
  const criteria = matchingCriteria.map(({ key }) => ({
    type: criterionTypes[key],
    weight: matchingEnabled.value[key] ? Number(matchingWeights.value[key]) : 0,
    enabled: matchingEnabled.value[key],
  }));
  matchingBusy.value = true;
  matchingConfigError.value = "";
  let strategySaved = false;
  try {
    await api(
      `/api/matching/config/strategy?name=${strategyNames[matchingStrategy.value]}`,
      { method: "PUT" },
    );
    strategySaved = true;
    await api("/api/matching/config/criteria", {
      method: "PUT",
      body: JSON.stringify(criteria),
    });
    toast("Matching configuration saved");
  } catch (error) {
    matchingConfigError.value = strategySaved
      ? `The strategy was saved, but the weights were not. Review and retry: ${error.message}`
      : error.message;
  } finally {
    matchingBusy.value = false;
  }
}

// Ranked study buddy suggestions from matching-service, best match first.
// Only filters that are set are sent; an empty body means "score everyone".
async function findMatches(userId, { course, goal, mode, minOverlapMinutes } = {}) {
  const criteria = { filters: {} };
  if (course) criteria.course = { courseCode: course };
  if (goal) criteria.studyGoal = goal;
  if (mode) criteria.filters.meetingMode = mode;
  if (minOverlapMinutes) criteria.filters.minOverlapMinutes = Number(minOverlapMinutes);
  return api(`/api/matching/students/${userId}/search`, {
    method: "POST",
    body: JSON.stringify(criteria),
  });
}

export {
  findMatches,
  matchingStrategy,
  matchingWeights,
  matchingEnabled,
  matchingCriteria,
  lockedCriterion,
  totalWeight,
  strategyHint,
  changeStrategy,
  setWeight,
  setCriterionEnabled,
  saveMatchingConfiguration,
  loadMatchingConfiguration,
  matchingConfigError,
  matchingBusy,
  matchingLoaded,
};
