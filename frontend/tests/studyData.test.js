import test from "node:test";
import assert from "node:assert/strict";
import {
  to12h,
  to24h,
  slotProblem,
  slotText,
  requestTime,
} from "../src/utils/studyData.js";

test("midnight, noon and evening availability survive display/storage conversion", () => {
  for (const time of [
    "00:00:00",
    "00:30:00",
    "11:59:00",
    "12:00:00",
    "18:30:00",
    "23:59:00",
  ]) {
    assert.equal(to24h(to12h(time)), time);
  }
  assert.equal(to12h("12:00"), "12:00 PM");
  assert.equal(to12h("00:00"), "12:00 AM");
});

test("incomplete, reversed and zero-length slots cannot be submitted", () => {
  assert.ok(slotProblem("", "18:00", "19:00"));
  assert.ok(slotProblem("MONDAY", "", "19:00"));
  assert.ok(slotProblem("MONDAY", "19:00", "18:00"));
  assert.ok(slotProblem("MONDAY", "18:00", "18:00"));
  assert.ok(slotProblem("MONDAY", "18:00", "18:00:00"));
  assert.equal(slotProblem("MONDAY", "18:00", "19:00"), "");
});

test("group availability displays full readable day and time labels", () => {
  assert.equal(
    slotText({ day: "WEDNESDAY", startTime: "12:00:00", endTime: "13:30:00" }),
    "Wednesday, 12:00 PM – 1:30 PM",
  );
});

test("request times distinguish multiple applications on the same day", () => {
  const first = requestTime("2026-10-08T10:00:00Z");
  const later = requestTime("2026-10-08T10:30:00Z");
  assert.notEqual(first, later);
  assert.match(first, /2026/);
  assert.ok(!first.includes("Invalid Date"));
});

test("missing request timestamps have a readable fallback", () => {
  for (const value of [null, undefined, "", "invalid"]) {
    assert.equal(requestTime(value), "Time unavailable");
  }
});
