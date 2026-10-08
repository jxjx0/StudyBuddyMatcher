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
  balanced:
    "Balanced matching keeps all criteria active without locking one priority.",
  "": "No strategy rules are applied; adjust criteria freely.",
};
const strategyHint = computed(() => strategyHints[matchingStrategy.value]);

function changeStrategy(value) {
  matchingStrategy.value = value;
  if (value === "course") {
    matchingWeights.value = {
      course: 40,
      availability: 15,
      mode: 15,
      goal: 15,
      groupSize: 15,
    };
  } else if (value === "availability") {
    matchingWeights.value = {
      course: 15,
      availability: 40,
      mode: 15,
      goal: 15,
      groupSize: 15,
    };
  } else if (value === "balanced") {
    matchingWeights.value = {
      course: 20,
      availability: 20,
      mode: 20,
      goal: 20,
      groupSize: 20,
    };
  }
}

function setWeight(key, value) {
  let lockedKey = "";
  if (matchingStrategy.value === "course") lockedKey = "course";
  if (matchingStrategy.value === "availability") lockedKey = "availability";
  try {
    matchingWeights.value = rebalanceWeights(
      matchingWeights.value,
      key,
      value,
      lockedKey,
    );
    matchingConfigError.value = "";
  } catch (error) {
    matchingConfigError.value = error.message;
  }
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
  for (const criterion of config.criteria || []) {
    const key = criterionKeys[criterion.type];
    if (key) weights[key] = Number(criterion.weight);
  }
  if (
    Object.values(weights).reduce((total, weight) => total + weight, 0) === 100
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
    weight: Number(matchingWeights.value[key]),
    enabled: true,
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

export {
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
};
