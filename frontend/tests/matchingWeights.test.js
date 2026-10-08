import test from "node:test";
import assert from "node:assert/strict";
import { rebalanceWeights } from "../src/utils/matchingWeights.js";

const balanced = {
  course: 20,
  availability: 20,
  mode: 20,
  goal: 20,
  groupSize: 20,
};
function assertValid(weights) {
  assert.equal(
    Object.values(weights).reduce((sum, value) => sum + value, 0),
    100,
  );
  for (const value of Object.values(weights)) {
    assert.ok(Number.isInteger(value) && value >= 0 && value <= 100);
  }
}

test("free weights stay valid for boundary values and repeated edits", () => {
  let weights = { ...balanced };
  for (const key of Object.keys(weights)) {
    for (const value of [-20, 0, 1, 33.7, 99, 100, 120, "", "invalid"]) {
      weights = rebalanceWeights(weights, key, value);
      assertValid(weights);
    }
  }
  assert.deepEqual(balanced, {
    course: 20,
    availability: 20,
    mode: 20,
    goal: 20,
    groupSize: 20,
  });
});

test("each priority remains fixed and strictly higher after every editable percentage", () => {
  for (const priority of ["course", "availability"]) {
    let weights = {
      course: 15,
      availability: 15,
      mode: 15,
      goal: 15,
      groupSize: 15,
    };
    weights[priority] = 40;
    for (const key of Object.keys(weights)) {
      for (let value = 0; value <= 100; value++) {
        weights = rebalanceWeights(weights, key, value, priority);
        assertValid(weights);
        assert.equal(weights[priority], 40);
        for (const other of Object.keys(weights)) {
          if (other !== priority) assert.ok(weights[other] < weights[priority]);
        }
      }
    }
  }
});

test("assigning 100 percent and then reducing it restores the other criteria", () => {
  let weights = rebalanceWeights(balanced, "course", 100);
  weights = rebalanceWeights(weights, "course", 0);
  assert.deepEqual(weights, {
    course: 0,
    availability: 25,
    mode: 25,
    goal: 25,
    groupSize: 25,
  });
});

test("inconsistent saved priority weights fail clearly instead of looping", () => {
  assert.throws(
    () => rebalanceWeights(balanced, "availability", 10, "course"),
    /Choose a strategy again/,
  );
});
