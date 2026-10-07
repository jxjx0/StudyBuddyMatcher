import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { createApp, nextTick } from "vue";
import GroupsPage from "../../src/pages/student/GroupsPage.vue";
import { api } from "../../src/services/api";
import { token } from "../../src/composables/useAuth";

vi.mock("../../src/services/api", () => ({ api: vi.fn() }));
vi.mock("../../src/composables/useAuth", async () => {
  const { ref } = await import("vue");
  return { token: ref("test-session") };
});

let app;
let group;
let joinRequests;
let failRefresh;
let failWrite;
let failDetail;
let pendingWrite;

// jsdom has no native modal lifecycle. Real focus, Escape and layout are tested in the browser.
HTMLDialogElement.prototype.showModal = function () {
  this.open = true;
};
HTMLDialogElement.prototype.close = function () {
  this.open = false;
};

function makeGroup() {
  return {
    groupId: "group-1",
    groupName: "Java study circle",
    description: "Practise together",
    courseCode: "IS442",
    maxGroupSize: 3,
    memberCount: 2,
    status: "ACTIVE",
    meetingMode: "ONLINE",
    studyGoals: ["CONCEPT_REVIEW"],
    availability: [],
    leaderId: "alice",
    isLeader: true,
    isMember: true,
    leaderAvailable: true,
    canRequest: false,
    myRequestStatus: null,
    members: [
      { studentId: "alice", name: "Alice", available: true },
      { studentId: "bob", name: "Bob", available: true },
    ],
  };
}

async function fakeApi(path, options = {}) {
  if (options.method && options.method !== "GET") {
    if (pendingWrite) await pendingWrite;
    if (failWrite)
      throw new Error("The group could not be updated. Please try again.");
    if (path.endsWith("/close")) {
      group.status = "CLOSED";
      for (const request of joinRequests) request.status = "REJECTED";
    } else if (path.endsWith("/members/bob")) {
      group.members = group.members.filter(
        (member) => member.studentId !== "bob",
      );
      group.memberCount = group.members.length;
    } else if (path.endsWith("/join-requests")) {
      group.myRequestStatus = "PENDING";
      group.canRequest = false;
    } else if (options.body) {
      Object.assign(group, JSON.parse(options.body));
    }
    return structuredClone(group);
  }
  if (failRefresh && path === "/api/groups")
    throw new Error("Network unavailable");
  if (path === "/api/groups")
    return group.status === "ACTIVE" ? [structuredClone(group)] : [];
  if (path === "/api/groups?scope=mine")
    return group.isMember ? [structuredClone(group)] : [];
  if (path === "/api/groups/join-requests/mine") return [];
  if (path === "/api/courses")
    return [{ courseCode: "IS442", courseName: "Object Oriented Programming" }];
  if (path === "/api/groups/group-1/join-requests")
    return structuredClone(joinRequests);
  if (path === "/api/groups/group-1") {
    if (failDetail) throw new Error("Details unavailable");
    return structuredClone(group);
  }
  throw new Error(`Unexpected test route: ${path}`);
}

async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await nextTick();
}
function button(name, root = document) {
  const buttons = [...root.querySelectorAll("button")];
  const match = buttons.find(
    (element) =>
      element.getAttribute("aria-label") === name ||
      element.textContent.trim() === name,
  );
  if (!match) throw new Error(`Button not found: ${name}`);
  return match;
}
async function click(name, root = document) {
  button(name, root).click();
  await settle();
}
function topDialog() {
  return [...document.querySelectorAll("dialog[open]")].at(-1);
}
async function fill(selector, value) {
  const input = topDialog().querySelector(selector);
  input.value = value;
  input.dispatchEvent(new Event("input", { bubbles: true }));
  await nextTick();
}
function writes() {
  return api.mock.calls.filter(([, options]) => options?.method);
}
async function mountPage() {
  app = createApp(GroupsPage);
  app.mount(document.querySelector("#main-content"));
  await settle();
}
async function openGroup() {
  await click(`View ${group.groupName}`);
}

beforeEach(() => {
  group = makeGroup();
  joinRequests = [
    {
      groupJoinRequestId: "request-1",
      studentName: "Carol",
      status: "PENDING",
    },
  ];
  failRefresh = false;
  failWrite = false;
  failDetail = false;
  pendingWrite = null;
  token.value = "test-session";
  api.mockReset().mockImplementation(fakeApi);
  document.body.innerHTML = '<main id="main-content" tabindex="-1"></main>';
});
afterEach(() => {
  app?.unmount();
  app = null;
  document.body.innerHTML = "";
});

test("Done dismisses details without ending the group", async () => {
  await mountPage();
  await openGroup();
  await click("Done", topDialog());
  expect(topDialog()).toBeUndefined();
  expect(writes()).toHaveLength(0);
  expect(group.status).toBe("ACTIVE");
});

test("the safe confirmation action and Escape both keep the group active", async () => {
  await mountPage();
  await openGroup();
  await click("End group…");
  expect(topDialog().textContent).toContain("This cannot be undone");
  await click("Keep group active", topDialog());
  await click("End group…");
  topDialog().dispatchEvent(new Event("cancel", { cancelable: true }));
  await settle();
  expect(topDialog().textContent).toContain("Java study circle");
  expect(writes()).toHaveLength(0);
});

test("ending dismisses both dialogs and shows Closed; reopening has no edit or end action", async () => {
  await mountPage();
  await openGroup();
  await click("End group…");
  await click("End group permanently", topDialog());
  expect(topDialog()).toBeUndefined();
  expect(group.status).toBe("CLOSED");
  expect(joinRequests[0].status).toBe("REJECTED");
  expect(document.querySelector('[role="status"]').textContent).toContain(
    "is now closed",
  );
  expect(button("Closed (1)").getAttribute("aria-pressed")).toBe("true");
  await openGroup();
  expect(topDialog().textContent).toContain("This group has ended");
  expect(topDialog().textContent).not.toContain("End group…");
  expect(topDialog().textContent).not.toContain("Edit group");
});

test("a failed end leaves the confirmation open with an error and the group active", async () => {
  await mountPage();
  await openGroup();
  failWrite = true;
  await click("End group…");
  await click("End group permanently", topDialog());
  expect(topDialog().querySelector('[role="alert"]').textContent).toContain(
    "could not be updated",
  );
  expect(group.status).toBe("ACTIVE");
  expect(button("End group permanently", topDialog()).disabled).toBe(false);
});

test("rapid confirmation clicks cannot submit the end action twice", async () => {
  let finish;
  pendingWrite = new Promise((resolve) => {
    finish = resolve;
  });
  await mountPage();
  await openGroup();
  await click("End group…");
  const confirm = button("End group permanently", topDialog());
  confirm.click();
  confirm.click();
  await settle();
  expect(writes()).toHaveLength(1);
  expect(confirm.disabled).toBe(true);
  finish();
  await settle();
  expect(topDialog()).toBeUndefined();
});

test("successful end followed by refresh failure stays completed and offers a read retry", async () => {
  await mountPage();
  await openGroup();
  failRefresh = true;
  await click("End group…");
  await click("End group permanently", topDialog());
  expect(topDialog()).toBeUndefined();
  expect(document.body.textContent).toContain("Your change was saved");
  expect(document.querySelectorAll(".large-group-card")).toHaveLength(0);
  failRefresh = false;
  await click("Try again");
  expect(button("View Java study circle")).toBeDefined();
  expect(writes()).toHaveLength(1);
});

test("successful join followed by detail failure removes the stale Request button", async () => {
  group.isLeader = false;
  group.isMember = false;
  group.canRequest = true;
  await mountPage();
  await openGroup();
  failDetail = true;
  await click("Request to join", topDialog());
  expect(topDialog()).toBeUndefined();
  expect(document.body.textContent).toContain("Your change was saved");
  expect(writes()).toHaveLength(1);
});

test("a sent request displays feedback inside the dialog and prevents another request", async () => {
  group.isLeader = false;
  group.isMember = false;
  group.canRequest = true;
  await mountPage();
  await openGroup();
  await click("Request to join", topDialog());
  expect(topDialog().querySelector('[role="status"]').textContent).toBe(
    "Join request sent",
  );
  expect(topDialog().textContent).toContain("awaiting the leader’s decision");
  expect(topDialog().textContent).not.toContain("Request to join");
});

test("full groups explain why the leader cannot accept another member", async () => {
  group.maxGroupSize = 2;
  await mountPage();
  await openGroup();
  expect(button("Accept Carol's request", topDialog()).disabled).toBe(true);
  expect(topDialog().textContent).toContain("Increase its size");
  expect(button("Reject Carol's request", topDialog()).disabled).toBe(false);
});

test("ordinary members have a Done action but no leader controls", async () => {
  group.isLeader = false;
  await mountPage();
  await openGroup();
  expect(button("Done", topDialog())).toBeDefined();
  expect(topDialog().textContent).not.toContain("Edit group");
  expect(topDialog().textContent).not.toContain("End group…");
  expect(topDialog().textContent).not.toContain("Join requests");
  expect(topDialog().textContent).not.toContain("Remove");
});

test("a full group explains to an applicant why joining is unavailable", async () => {
  group.isLeader = false;
  group.isMember = false;
  group.maxGroupSize = 2;
  await mountPage();
  await openGroup();
  expect(topDialog().textContent).toContain("This group is full");
  expect(topDialog().textContent).not.toContain("Request to join");
});

test("closed records stay out of Browse and the default Active view", async () => {
  group.status = "CLOSED";
  await mountPage();
  expect(document.querySelectorAll(".large-group-card")).toHaveLength(0);
  await click("My groups");
  expect(document.querySelectorAll(".large-group-card")).toHaveLength(0);
  await click("Closed (1)");
  expect(button("View Java study circle")).toBeDefined();
});

test("removal requires confirmation and leaves visible success feedback", async () => {
  await mountPage();
  await openGroup();
  await click("Remove Bob");
  await click("Keep member", topDialog());
  expect(writes()).toHaveLength(0);
  await click("Remove Bob");
  await click("Remove member", topDialog());
  expect(topDialog().querySelector('[role="status"]').textContent).toBe(
    "Member removed",
  );
  expect(group.memberCount).toBe(1);
});

test("creating clears old filters so the new group is visible", async () => {
  await mountPage();
  const search = document.querySelector('input[type="search"]');
  search.value = "does not match";
  search.dispatchEvent(new Event("input", { bubbles: true }));
  await click("Create group");
  await fill('input[maxlength="100"]', "New study group");
  await click("Save group", topDialog());
  expect(topDialog()).toBeUndefined();
  expect(document.querySelector('input[type="search"]').value).toBe("");
  expect(button("View New study group")).toBeDefined();
  expect(button("Active (1)").getAttribute("aria-pressed")).toBe("true");
});

test("a failed save preserves the form for correction and retry", async () => {
  await mountPage();
  await click("Create group");
  await fill('input[maxlength="100"]', "Keep my draft");
  failWrite = true;
  await click("Save group", topDialog());
  expect(topDialog().querySelector('input[maxlength="100"]').value).toBe(
    "Keep my draft",
  );
  expect(topDialog().querySelector('[role="alert"]').textContent).toContain(
    "could not be updated",
  );
});

test("closing a dirty draft protects it until explicit discard", async () => {
  await mountPage();
  await click("Create group");
  await fill('input[maxlength="100"]', "Keep my draft");
  await click("Cancel", topDialog());
  await click("Keep editing", topDialog());
  expect(topDialog().querySelector('input[maxlength="100"]').value).toBe(
    "Keep my draft",
  );
  topDialog().dispatchEvent(new Event("cancel", { cancelable: true }));
  await settle();
  await click("Discard changes", topDialog());
  expect(topDialog()).toBeUndefined();
  expect(writes()).toHaveLength(0);
});

test("invalid time ranges name the slot, receive focus and do not send a write", async () => {
  await mountPage();
  await click("Create group");
  await fill('input[maxlength="100"]', "Valid name");
  await click("Add time slot", topDialog());
  const end = topDialog().querySelectorAll('input[type="time"]')[1];
  end.value = "17:00";
  end.dispatchEvent(new Event("input", { bubbles: true }));
  await click("Save group", topDialog());
  expect(document.activeElement.textContent).toContain(
    "Time slot 1: End time must be later",
  );
  expect(writes()).toHaveLength(0);
});

test("editing preserves the fixed course and returns to updated My groups", async () => {
  await mountPage();
  await openGroup();
  await click("Edit group");
  expect(topDialog().querySelector("select").disabled).toBe(true);
  await fill('input[maxlength="100"]', "Updated circle");
  await click("Save group", topDialog());
  expect(topDialog()).toBeUndefined();
  expect(button("View Updated circle")).toBeDefined();
  expect(writes()[0][1].method).toBe("PUT");
});

test("changing sessions during a write cannot reopen an old group or show success", async () => {
  let finish;
  pendingWrite = new Promise((resolve) => {
    finish = resolve;
  });
  await mountPage();
  await openGroup();
  await click("End group…");
  await click("End group permanently", topDialog());
  token.value = "";
  await settle();
  finish();
  await settle();
  expect(topDialog()).toBeUndefined();
  expect(document.body.textContent).not.toContain("is now closed");
});
