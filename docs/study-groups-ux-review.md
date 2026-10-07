# Study groups usability review

Reviewed 8 October 2026 on `feat/study-groups`. Scope: browse/search/filter, My groups, My requests, create/edit, group details, membership, leader decisions and ending a group. The review used [Jakob Nielsen's ten usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) and task walkthroughs with the isolated local application.

This is an expert review with browser testing, not a study with recruited participants or an accessibility certification. The previous [general UI pass](ui-review.md) inspected and cancelled the end confirmation. This pass completed the action and checked persistence and its effects on another account.

## The close-group problem

The server was closing the group correctly. The frontend then fetched that same group again and left its details open. Two buttons said “Close group”, while the header X also said “Close [group name]”. Success appeared in a toast behind the native dialog. Ending the group and dismissing the window were too easy to confuse.

The new flow is:

1. **Done** or the header X returns to the list without changing membership or group status. Escape does the same.
2. **End group…** opens a separate confirmation naming the group and explaining that pending requests will be rejected and the action cannot be undone.
3. **Keep group active** receives initial focus. Escape cancels the confirmation.
4. **End group permanently** submits once, disables the controls while saving, dismisses both dialogs on success and opens **My groups → Closed** with a persistent completion message.
5. Closed details remain readable, with an explanation and a Done button. Edit, remove and end actions are unavailable.

Existing closed records were not reopened. Backend rules and routes are unchanged.

## Findings and changes

| Priority | Finding | Change and evidence |
| --- | --- | --- |
| High | Ending and dismissing used confusingly similar labels; closure left the dialog open. | Separate actions and confirmation. Completed closure in the browser, reloaded, and reopened the stored Closed record. |
| High | Success feedback appeared behind a modal. | Page and dialog status messages replace group toasts. In-dialog feedback receives focus after join, accept, reject and removal. |
| High | A write could succeed while its refresh failed, leaving stale actions available. | Loading functions report success/failure. A saved write remains successful; stale details/lists are cleared and a read retry is offered. Automated tests cover failed list and detail refreshes. |
| Medium | Closing an editor silently lost a changed draft. | Cancel, X and Escape offer Keep editing / Discard changes when the draft differs. Failed saves retain it. |
| Medium | My groups mixed active and closed records. | Active and Closed controls with counts; Active is the default. Closed-view guidance explains retained history. |
| Medium | A saved group could remain hidden behind an old filter. | Create/edit opens Active My groups and clears filters. Tested creation with a nonmatching search already applied. |
| Medium | Time errors did not identify the slot or receive focus. | Errors name the slot and receive focus; invalid ranges do not submit. |
| Low | One result used plural copy; empty results lacked a direct create action. | Correct singular/plural copy and a Create group action in the empty state. |
| Low | The new sticky details footer clipped a button during desktop inspection. | Corrected its bottom offset; verified Done remains inside the dialog. |

## Coverage of all ten heuristics

The labels below paraphrase the framework; the observations describe this application.

| Principle | What was checked |
| --- | --- |
| Immediate feedback | Loading, saving, request status, capacity, saved changes and closure completion. |
| Familiar language | Done, End group, members, requests and readable weekly times; consequences stated before submission. |
| Safe exits | Done/X/Escape, safe confirmation cancellation and protection for changed drafts. |
| Consistent controls | Shared native dialog, named actions and status vocabulary across cards, details and history. |
| Mistake prevention | Explicit irreversible confirmation, no duplicate submit, time validation, fixed course on edit and capacity limits. |
| Visible choices | Active/Closed counts, membership labels, members, request statuses and visible filter choices. |
| Efficient repeat use | Name/course/goal/mode/day/size filters, reset, keyboard actions and cleared filters after save. |
| Focused content | Closed records separated from active work; extra filters remain collapsible; dismissal separated from ending. |
| Useful recovery | Failed writes keep the draft/confirmation; failed refresh after a saved write asks for a read retry. |
| Guidance at the point of use | Capacity includes the leader, fixed course, optional schedule/goals, full-group explanation and closure consequences. |

## Browser walkthrough evidence

Used Alice (leader), Bob (member/applicant) and Carol (applicant) in the launcher's disposable PostgreSQL demo. No shared database or real student records were used. Desktop was 1280×720 and mobile 390×844; the viewport override was reset afterwards.

| Surface/task | Observed result |
| --- | --- |
| Browse and every filter | Wrong course, unmatched goal and too-small capacity yielded zero results. Either mode matched Online; a flexible schedule matched Friday. Name search and Reset filters worked. |
| Create and edit | Created `IS442 Practice Circle` with a description, IS442, two-member capacity, Concept review and a Monday slot. Invalid end time focused the error; correction saved. Edited and read back the description. |
| Draft protection | Cancel → Keep editing retained the name. On mobile, a long name stayed within its input; explicit discard restored the saved group. |
| Joining and history | Bob and Carol applied. Pending feedback appeared inside details and in My requests. Repeat joining was unavailable while pending. |
| Leader decisions | Accepted Bob. At 2/2, Carol's Accept was disabled with an explanation, while Reject worked. Carol saw the rejection in her history. |
| Membership | Cancelling removal kept Bob. Confirmed removal reduced membership to one. Bob reapplied and was accepted again. Ordinary-member details exposed no leader controls. |
| End with a pending request | Carol reapplied. Alice ended the group on mobile while Carol was pending. Both dialogs disappeared and Closed was selected. After reload, it remained closed. Carol's new request was rejected and the group's history remained readable. |
| Keyboard/focus | Confirmation focused Keep group active; Tab reached the destructive action inside it. Escape cancelled it. Escape from details returned focus to View. After ending, focus returned to main content when its opener had been replaced. |
| Responsive/visual | No page/dialog horizontal overflow at the tested widths. Mobile form and confirmation remained usable. Header dismissal target measured 44×44 pixels. Footer clipping was corrected. No console warnings/errors at the final check. |

An active `OOP Study Sessions` group was left in the local demo. Completed test groups remain in Closed history. Screenshots were saved outside the repository and provided in the chat.

## Repeatable verification

```sh
npm --prefix frontend test
npm --prefix frontend run build -- --outDir /tmp/studybuddy-ui-build
python3 scripts/group-local.py test
# With the isolated demo running:
python3 scripts/group-smoke.py
git diff --check
```

- 26 frontend tests: seven existing utility tests and 19 new group UI regression tests.
- 18 Java tests passed with no skips: domain, HTTP client and real PostgreSQL/API integration tests.
- Live gateway smoke passed create/edit, join, accept/reject, remove/close, validation, permissions, capacity and history.
- Production build passed with output outside tracked `frontend/dist`.
- The UI detector returned no findings on the changed UI files. Measured contrast was 7.64:1 for success text, 6.07:1 for closed-group guidance and 4.90:1 for the selected view control. No middle-dot separators were found in frontend source. `git diff --check` passed.
- Vitest/jsdom run the real Vue page and composable with a fake API. Tests cover write/refresh failures, duplicate clicks, account changes, draft retention and role/state rules. Their minimal native-dialog stub does not verify focus trapping/layout; browser checks cover the actual dialog.
- This run used Node 26.10.0. The new test dependencies require Node 22.22.2+, 24.15.0+, or 26+. Both existing lockfiles were updated. The existing `source-map-js` transitive dependency was updated to its compatible patched release; `npm audit` reported zero vulnerabilities after installation.

Failure paths were simulated in automated tests. This was not a browser/device matrix, network-chaos test or participant study. Draft protection covers the dialog's controls and Escape, not browser reload/tab closure. Reopening closed groups, leaving a group and cancelling a pending join request remain outside the current backend feature set.

## Files added and maintenance

| New file | Purpose |
| --- | --- |
| `frontend/vitest.config.js` | Reuses the Vite/Vue configuration and selects UI tests in jsdom. |
| `frontend/tests/ui/groups.spec.js` | Task-based regression cases with explicit fixtures, actions and assertions. |
| `docs/study-groups-ux-review.md` | This review, reproduction, changes and verification record. |

Updated `GroupsPage.vue`, `GroupDetails.vue`, `GroupEditor.vue`, `useGroups.js`, shared styles and the dialog's optional accessible close label. The existing label remains the default for other screens. No new production dependency or backend endpoint was added. Existing Node tests remain part of `npm test`; `npm run test:ui` runs only UI tests.

State changes use named handlers and explicit conditions. `useGroups.mutate()` deliberately distinguishes the write from its follow-up reads: a failed refresh must never be reported as a failed write. Preserve that distinction when adding actions.

Test configuration and mocking were checked against official [Vitest configuration](https://vitest.dev/config/) and [module mocking](https://vitest.dev/guide/mocking/modules.html) documentation, retrieved through Context7.
