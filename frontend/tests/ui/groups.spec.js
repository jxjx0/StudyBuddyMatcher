import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { createApp, h, nextTick } from "vue";
import { createMemoryHistory, createRouter, RouterView } from "vue-router";
import GroupsPage from "../../src/pages/student/GroupsPage.vue";
import { api } from "../../src/services/api";
import { token } from "../../src/composables/useAuth";

vi.mock("../../src/services/api", () => ({ api: vi.fn() }));
vi.mock("../../src/composables/useAuth", async () => {
  const { ref } = await import("vue");
  return { token: ref("test-session") };
});

let app;
let router;
let group;
let joinRequests;
let ownRequests;
let failRefresh;
let failWrite;
let failDetail;
let pendingWrite;
let pendingOverview;

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
      return {
        groupJoinRequestId: "request-new",
        studentId: "student-1",
        groupId: group.groupId,
        status: "PENDING",
        createdAt: "2026-10-17T00:00:00Z",
        respondedAt: null,
      };
    } else if (options.body) {
      Object.assign(group, JSON.parse(options.body));
    }
    return structuredClone(group);
  }
  if (failRefresh && path === "/api/groups/overview")
    throw new Error("Network unavailable");
  if (path === "/api/groups/overview") {
    const result = {
      groups: group.status === "ACTIVE" ? [structuredClone(group)] : [],
      mine: group.isMember ? [structuredClone(group)] : [],
      requests: structuredClone(ownRequests),
    };
    if (pendingOverview) await pendingOverview;
    return result;
  }
  if (path === "/api/groups")
    return group.status === "ACTIVE" ? [structuredClone(group)] : [];
  if (path === "/api/groups?scope=mine")
    return group.isMember ? [structuredClone(group)] : [];
  if (path === "/api/groups/join-requests/mine")
    return structuredClone(ownRequests);
  if (path === "/api/courses")
    return [{ courseCode: "IS442", courseName: "Object Oriented Programming" }];
  if (path === "/api/groups/group-1/details") {
    if (failDetail) throw new Error("Details unavailable");
    return {
      group: structuredClone(group),
      requests: group.isLeader ? structuredClone(joinRequests) : [],
    };
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
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/groups", component: GroupsPage },
      { path: "/away", component: { render: () => h("p", "Another page") } },
    ],
  });
  await router.push("/groups");
  await router.isReady();
  app = createApp({ render: () => h(RouterView) });
  app.use(router);
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
      createdAt: "2026-10-08T10:00:00Z",
    },
  ];
  ownRequests = [];
  failRefresh = false;
  failWrite = false;
  failDetail = false;
  pendingWrite = null;
  pendingOverview = null;
  token.value = "";
  token.value = "test-session";
  api.mockReset().mockImplementation(fakeApi);
  document.body.innerHTML = '<main id="main-content" tabindex="-1"></main>';
});
afterEach(() => {
  app?.unmount();
  app = null;
  document.body.innerHTML = "";
});

test("loading all three tabs shares one group overview request", async () => {
  await mountPage();
  await click("My groups");
  await click("My requests");
  await click("Browse groups");
  expect(api.mock.calls.map(([path]) => path)).toEqual([
    "/api/groups/overview",
    "/api/courses",
  ]);
});

function pauseOverview() {
  let finish;
  pendingOverview = new Promise((resolve) => {
    finish = resolve;
  });
  return finish;
}

test("returning shows saved groups before the refresh finishes, then replaces them", async () => {
  await mountPage();
  await router.push("/away");
  const finish = pauseOverview();
  group.groupName = "Updated on the server";
  await router.push("/groups");
  await settle();
  expect(button("View Java study circle")).toBeDefined();
  expect(document.querySelector(".refresh").textContent).toContain(
    "Refreshing…",
  );
  expect(document.body.textContent).not.toContain("Loading groups…");
  expect(button("Create group").disabled).toBe(false);
  finish();
  await settle();
  expect(button("View Updated on the server")).toBeDefined();
  expect(document.body.textContent).not.toContain("Refreshing…");
});

test("a background refresh does not block creating a group or reset its draft", async () => {
  await mountPage();
  const finish = pauseOverview();
  await click("Refresh");
  await click("Create group");
  await fill('input[maxlength="100"]', "Draft during refresh");
  finish();
  await settle();
  expect(topDialog().querySelector('input[maxlength="100"]').value).toBe(
    "Draft during refresh",
  );
});

test("logout while away clears saved groups even when the same account logs in again", async () => {
  await mountPage();
  await router.push("/away");
  token.value = "";
  token.value = "test-session";
  const finish = pauseOverview();
  await router.push("/groups");
  await settle();
  expect(document.querySelectorAll(".large-group-card")).toHaveLength(0);
  expect(document.body.textContent).toContain("Loading groups…");
  finish();
  await settle();
});

test("switching accounts clears groups immediately while the new account loads", async () => {
  await mountPage();
  const finish = pauseOverview();
  group.groupName = "Other account's group";
  token.value = "other-session";
  await nextTick();
  expect(document.body.textContent).not.toContain("Java study circle");
  finish();
  await settle();
  expect(button("View Other account's group")).toBeDefined();
});

test("a failed background refresh clears saved groups and offers retry", async () => {
  await mountPage();
  await router.push("/away");
  failRefresh = true;
  await router.push("/groups");
  await settle();
  expect(document.body.textContent).toContain("Network unavailable");
  expect(document.querySelectorAll(".large-group-card")).toHaveLength(0);
  await router.push("/away");
  failRefresh = false;
  const finish = pauseOverview();
  await router.push("/groups");
  await settle();
  expect(document.querySelectorAll(".large-group-card")).toHaveLength(0);
  finish();
  await settle();
  expect(button("View Java study circle")).toBeDefined();
});

test("a late response from an unmounted page cannot replace the newer saved list", async () => {
  await mountPage();
  await router.push("/away");
  const finishOld = pauseOverview();
  await router.push("/groups");
  await settle();
  await router.push("/away");
  pendingOverview = null;
  group.groupName = "Newest group";
  await router.push("/groups");
  await settle();
  finishOld();
  await settle();
  await router.push("/away");
  const finishNew = pauseOverview();
  await router.push("/groups");
  await settle();
  expect(button("View Newest group")).toBeDefined();
  finishNew();
  await settle();
});

test("saved edits are used when returning before another refresh completes", async () => {
  await mountPage();
  await openGroup();
  await click("Edit group");
  await fill('input[maxlength="100"]', "Saved group name");
  await click("Save group", topDialog());
  await router.push("/away");
  const finish = pauseOverview();
  await router.push("/groups");
  await settle();
  expect(button("View Saved group name")).toBeDefined();
  finish();
  await settle();
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
  expect(topDialog().textContent).toContain("will close permanently");
  expect(topDialog().textContent).toContain(
    "Pending requests will be rejected",
  );
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
  expect(topDialog().textContent).toContain(
    "Closed groups cannot be edited or reopened",
  );
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

test("successful join does not reload the group details", async () => {
  group.isLeader = false;
  group.isMember = false;
  group.canRequest = true;
  await mountPage();
  await openGroup();
  const detailsReads = api.mock.calls.filter(([path]) =>
    path === "/api/groups/group-1/details",
  ).length;
  await click("Request to join", topDialog());
  expect(topDialog().textContent).toContain("Join request sent");
  expect(topDialog().textContent).toContain("Request pending");
  expect(
    api.mock.calls.filter(
      ([path]) => path === "/api/groups/group-1/details",
    ),
  ).toHaveLength(detailsReads);
  expect(writes()).toHaveLength(1);
});

test("eligible group cards can request to join and immediately show pending status", async () => {
  group.isLeader = false;
  group.isMember = false;
  group.canRequest = true;
  await mountPage();

  expect(button("Request to join Java study circle")).toBeDefined();
  await click("Request to join Java study circle");

  expect(button("View Java study circle")).toBeDefined();
  expect(button("Request to join Java study circle")).toBeUndefined();
  expect(document.body.textContent).toContain("Request pending");
  expect(document.body.textContent).toContain("Join request sent");
  expect(writes()).toHaveLength(1);
  expect(
    api.mock.calls.filter(([path]) => path === "/api/groups/overview"),
  ).toHaveLength(1);
});

test("creating a group does not show Sending on another group's join button", async () => {
  group.isLeader = false;
  group.isMember = false;
  group.canRequest = true;
  let finishWrite;
  pendingWrite = new Promise((resolve) => {
    finishWrite = resolve;
  });
  await mountPage();
  await click("Create group");
  await fill('input[maxlength="100"]', "New study circle");

  button("Save group", topDialog()).click();
  await settle();

  expect(button("Request to join Java study circle").textContent.trim()).toBe(
    "Request to join",
  );
  expect(button("Request to join Java study circle").disabled).toBe(true);
  finishWrite();
  await settle();
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
  expect(topDialog().textContent).toContain("Request pending");
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

test("leaders see pending work separately from completed request history", async () => {
  joinRequests.unshift({
    groupJoinRequestId: "old-request",
    studentName: "Previous applicant",
    status: "REJECTED",
    createdAt: "2026-10-07T10:00:00Z",
    respondedAt: "2026-10-07T11:00:00Z",
  });
  await mountPage();
  await openGroup();
  const dialog = topDialog();
  expect(dialog.textContent).toContain("Pending requests (1)");
  const history = dialog.querySelector("details");
  expect(history.open).toBe(false);
  expect(history.textContent).toContain("Previous applicant");
  expect(history.textContent).not.toContain("Carol");
  expect(history.querySelectorAll("button")).toHaveLength(0);
  expect(button("Accept Carol's request", dialog)).toBeDefined();
  history.querySelector("summary").click();
  expect(history.open).toBe(true);
  expect(history.textContent).toContain("Requested");
  expect(history.textContent).not.toContain("Time unavailable");
});

test("My requests separates pending applications from dated past decisions", async () => {
  const request = { groupId: "group-1", createdAt: "2026-10-08T10:00:00Z" };
  ownRequests = [
    {
      ...request,
      groupJoinRequestId: "past",
      groupName: "Past group",
      status: "ACCEPTED",
      respondedAt: "2026-10-08T11:00:00Z",
    },
    {
      ...request,
      groupJoinRequestId: "pending",
      groupName: "Pending group",
      status: "PENDING",
    },
  ];
  await mountPage();
  await click("My requests");
  expect(document.querySelector("article").textContent).toContain(
    "Pending group",
  );
  expect(document.querySelectorAll("article")).toHaveLength(1);
  await click("History (1)");
  expect(document.querySelector("article").textContent).toContain("Past group");
  expect(document.querySelector("article").textContent).not.toContain(
    "Pending group",
  );
  expect(button("View Past group")).toBeDefined();
});

test("an empty Active view explains its state without offering a useless filter reset", async () => {
  group.status = "CLOSED";
  await mountPage();
  await click("My groups");
  expect(document.querySelector(".empty-state h2").textContent).toBe(
    "No active groups yet",
  );
  expect(document.querySelector(".empty-state").textContent).not.toContain(
    "Reset filters",
  );
  await click("Closed (1)");
  expect(button("View Java study circle")).toBeDefined();
});

test("filtered empty results offer a reset that restores the visible group", async () => {
  await mountPage();
  const search = document.querySelector('input[type="search"]');
  search.value = "unmatched";
  search.dispatchEvent(new Event("input", { bubbles: true }));
  await settle();
  expect(document.querySelector(".empty-state h2").textContent).toBe(
    "No groups match your filters",
  );
  await click("Reset filters", document.querySelector(".empty-state"));
  expect(button("View Java study circle")).toBeDefined();
});

test("group filters work together and keep either-mode, flexible groups visible", async () => {
  group.meetingMode = "EITHER";
  await mountPage();
  await click("More filters");

  async function setFilter(label, value) {
    const field = [...document.querySelectorAll("label.field")].find(
      (element) => element.querySelector("span")?.textContent === label,
    );
    const input = field.querySelector("select, input");
    input.value = value;
    const eventType = input.tagName === "SELECT" ? "change" : "input";
    input.dispatchEvent(new Event(eventType, { bubbles: true }));
    await settle();
  }

  await setFilter("Course", "IS442");
  await setFilter("Study goal", "CONCEPT_REVIEW");
  await setFilter("Meeting mode", "IN_PERSON");
  await setFilter("Day", "TUESDAY");
  await setFilter("Maximum group size", "3");
  expect(button("View Java study circle")).toBeDefined();

  await setFilter("Maximum group size", "2");
  expect(document.querySelector(".group-cards")).toBeNull();
  await setFilter("Maximum group size", "3");
  await setFilter("Study goal", "EXAM_PREPARATION");
  expect(document.querySelector(".group-cards")).toBeNull();
  await setFilter("Study goal", "CONCEPT_REVIEW");

  group.meetingMode = "ONLINE";
  group.availability = [
    { day: "MONDAY", startTime: "18:00", endTime: "19:00" },
  ];
  await click("Refresh");
  expect(document.querySelector(".group-cards")).toBeNull();
  await setFilter("Meeting mode", "ONLINE");
  expect(document.querySelector(".group-cards")).toBeNull();
  await setFilter("Day", "MONDAY");
  expect(button("View Java study circle")).toBeDefined();

  await click("Reset filters");
  for (const input of document.querySelectorAll(
    ".filter-surface select, .filter-surface input",
  )) {
    expect(input.value).toBe("");
  }
  expect(button("View Java study circle")).toBeDefined();
});

test("Browse from empty My requests clears the previous group filters", async () => {
  await mountPage();
  const search = document.querySelector('input[type="search"]');
  search.value = "unmatched";
  search.dispatchEvent(new Event("input", { bubbles: true }));
  await click("My requests");
  await click("Browse groups", document.querySelector(".empty-state"));
  expect(button("View Java study circle")).toBeDefined();
});

test("route navigation keeps a dirty draft until the user explicitly discards it", async () => {
  await mountPage();
  await click("Create group");
  await fill('input[maxlength="100"]', "Draft protected from navigation");
  const firstNavigation = router.push("/away");
  await settle();
  expect(topDialog().textContent).toContain("Discard your changes?");
  await click("Keep editing", topDialog());
  await firstNavigation;
  expect(router.currentRoute.value.path).toBe("/groups");
  expect(topDialog().querySelector('input[maxlength="100"]').value).toBe(
    "Draft protected from navigation",
  );
  const secondNavigation = router.push("/away");
  await settle();
  await click("Discard changes", topDialog());
  await secondNavigation;
  expect(router.currentRoute.value.path).toBe("/away");
  expect(document.querySelectorAll("dialog[open]")).toHaveLength(0);
  expect(writes()).toHaveLength(0);
});

function unloadIsBlocked() {
  const event = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

test("reload protection exists only while an unsaved draft is open", async () => {
  await mountPage();
  expect(unloadIsBlocked()).toBe(false);
  await click("Create group");
  expect(unloadIsBlocked()).toBe(false);
  await fill('input[maxlength="100"]', "Unsaved changes");
  expect(unloadIsBlocked()).toBe(true);
  await click("Save group", topDialog());
  expect(unloadIsBlocked()).toBe(false);
  await router.push("/away");
  expect(router.currentRoute.value.path).toBe("/away");
});

test("an expired session can leave a dirty editor without being trapped by the guard", async () => {
  await mountPage();
  await click("Create group");
  await fill('input[maxlength="100"]', "Unsaved changes");
  token.value = "";
  await settle();
  expect(unloadIsBlocked()).toBe(false);
  await router.push("/away");
  expect(router.currentRoute.value.path).toBe("/away");
});

test("leaving during a save is blocked until that save completes", async () => {
  let finish;
  pendingWrite = new Promise((resolve) => {
    finish = resolve;
  });
  await mountPage();
  await click("Create group");
  await fill('input[maxlength="100"]', "Saving draft");
  await click("Save group", topDialog());
  await router.push("/away");
  expect(router.currentRoute.value.path).toBe("/groups");
  finish();
  await settle();
  await router.push("/away");
  expect(router.currentRoute.value.path).toBe("/away");
});
