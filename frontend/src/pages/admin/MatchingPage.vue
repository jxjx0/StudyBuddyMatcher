<script setup>
import { computed } from "vue";
import { useAppState } from "../../composables/useAppState";
import {
  matchingEnabled,
  setCriterionEnabled,
} from "../../composables/useMatching";
const {
  matchingStrategy,
  changeStrategy,
  strategyHint,
  totalWeight,
  matchingCriteria,
  matchingWeights,
  setWeight,
  saveMatchingConfiguration,
  matchingConfigError,
  matchingBusy,
  matchingLoaded,
  dataLoading,
  loadAppData,
} = useAppState();
const lockedKey = computed(() => {
  if (matchingStrategy.value === "course") return "course";
  if (matchingStrategy.value === "availability") return "availability";
  return "";
});
const enabledCriterionCount = computed(() =>
  Object.values(matchingEnabled.value).filter(Boolean).length,
);
const maximumWeight = computed(() =>
  lockedKey.value ? matchingWeights.value[lockedKey.value] - 1 : 100,
);
// Balanced uses equal weights, so every slider is locked.
const allLocked = computed(() => matchingStrategy.value === "balanced");
// setWeight may cap the value (e.g. below the locked weight). If the stored weight
// does not change, Vue does not redraw the input, so it is reset here.
function updateWeight(key, input) {
  setWeight(key, input.value);
  input.value = matchingWeights.value[key];
}
const isLocked = (key) =>
  allLocked.value || key === lockedKey.value || !matchingEnabled.value[key];
</script>
<template>
  <section class="page-body narrow-page">
    <header class="page-heading">
      <h1>Matching configuration</h1>
      <p>
        Choose a strategy, then adjust how much each study preference matters.
      </p>
    </header>
    <p v-if="dataLoading" role="status">Loading saved settings…</p>
    <form v-else class="form-card" @submit.prevent="saveMatchingConfiguration">
      <fieldset
        class="plain-fieldset"
        :disabled="matchingBusy || !matchingLoaded"
      >
        <label class="field"
          ><span>Matching strategy</span
          ><select
            :value="matchingStrategy"
            @change="changeStrategy($event.target.value)"
          >
            <option value="">No strategy</option>
            <option value="balanced">Balanced</option>
            <option value="availability">Availability first</option>
            <option value="course">Course first</option>
          </select></label
        >
        <p class="feature-note">{{ strategyHint }}</p>
        <div class="results-heading">
          <h2>Criterion weights</h2>
          <strong>{{ totalWeight }}% total</strong>
        </div>
        <p class="hint">
          Untick a criterion to leave it out of matching. Changing a weight
          shares the remaining percentage evenly among the other unlocked
          criteria.
        </p>
        <div class="weight-list">
          <div
            v-for="criterion in matchingCriteria"
            :key="criterion.key"
            class="weight-row"
          >
            <div>
              <label class="criterion-toggle"
                ><input
                  type="checkbox"
                  :checked="matchingEnabled[criterion.key]"
                  :disabled="criterion.key === lockedKey"
                  :aria-label="`Use ${criterion.label} in matching`"
                  @change="
                    setCriterionEnabled(criterion.key, $event.target.checked)
                  "
                /><span :id="`weight-label-${criterion.key}`">{{
                  criterion.label
                }}</span></label
              ><small v-if="!matchingEnabled[criterion.key]"
                >Not used in matching</small
              ><small v-else-if="criterion.key === lockedKey"
                >Locked highest priority</small
              ><small v-else-if="allLocked">Equal weight</small>
            </div>
            <!-- Every slider uses the same 0–100 scale so lengths can be compared.
                 The cap below the locked weight is applied by setWeight. -->
            <input
              type="range"
              min="0"
              max="100"
              :value="matchingWeights[criterion.key]"
              :aria-label="`${criterion.label} weight slider`"
              :disabled="isLocked(criterion.key)"
              @input="updateWeight(criterion.key, $event.target)"
            />
            <div class="weight-input">
              <input
                :id="`weight-${criterion.key}`"
                :aria-labelledby="`weight-label-${criterion.key}`"
                class="weight-number"
                type="number"
                min="0"
                :max="isLocked(criterion.key) ? 100 : maximumWeight"
                step="1"
                :value="matchingWeights[criterion.key]"
                :disabled="isLocked(criterion.key)"
                @input="updateWeight(criterion.key, $event.target)"
              /><span>%</span>
            </div>
          </div>
        </div>
      </fieldset>
      <div v-if="matchingConfigError" class="error-message" role="alert">
        <p>{{ matchingConfigError }}</p>
        <button
          type="button"
          class="secondary-button"
          :disabled="matchingBusy"
          @click="loadAppData"
        >
          Reload saved settings
        </button>
      </div>
      <div class="save-bar">
        <span>Weights must total 100%.</span
        ><button
          class="primary-button"
          :disabled="totalWeight !== 100 || matchingBusy || !matchingLoaded"
        >
          {{ matchingBusy ? "Saving…" : "Save configuration" }}
        </button>
      </div>
    </form>
  </section>
</template>
