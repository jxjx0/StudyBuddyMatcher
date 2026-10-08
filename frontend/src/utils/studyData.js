export const weekDays = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];
export const studyGoals = [
  "CONCEPT_REVIEW",
  "PROBLEM_SOLVING",
  "EXAM_PREPARATION",
  "PROJECT_DISCUSSION",
];
export const meetingModes = ["EITHER", "ONLINE", "IN_PERSON"];

export function labelOf(value) {
  if (!value) return "Flexible";
  const text = value.toLowerCase().replaceAll("_", " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function requestTime(value) {
  if (!value) return "Time unavailable";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Time unavailable";
  return date.toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function to12h(time) {
  const [hour, minute] = time.split(":").map(Number);
  const suffix = hour >= 12 ? "PM" : "AM";
  return `${hour % 12 || 12}:${String(minute).padStart(2, "0")} ${suffix}`;
}

export function to24h(time) {
  const match = time.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)$/);
  if (!match) throw new Error("Choose a valid start and end time.");
  const hour = (Number(match[1]) % 12) + (match[3] === "PM" ? 12 : 0);
  return `${String(hour).padStart(2, "0")}:${match[2]}:00`;
}

export function slotProblem(day, startTime, endTime) {
  if (!day || !startTime || !endTime)
    return "Choose a day, start time and end time.";
  if (endTime.slice(0, 5) <= startTime.slice(0, 5))
    return "End time must be later than start time on the same day.";
  return "";
}

export function slotText(slot) {
  return `${labelOf(slot.day)}, ${to12h(slot.startTime)} – ${to12h(slot.endTime)}`;
}

export const smuSchools = [
  "School of Computing and Information Systems",
  "School of Social Sciences",
  "School of Business",
  "School of Accountancy",
  "School of Economics",
  "School of Law",
];
