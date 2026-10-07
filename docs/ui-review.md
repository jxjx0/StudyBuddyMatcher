# UI review and handover

Reviewed and updated on 8 October 2026 on `feat/study-groups`.

The subsequent [study-group usability review](study-groups-ux-review.md) covers the close-group fix, all related group flows, completed browser actions and additional UI regression tests. The evidence below describes the earlier general pass.

The existing Vue app keeps its warm colours and DM Sans typography. This pass makes its controls easier to read and use, fixes misleading feedback, and separates large blocks of frontend logic into named components and modules. It does not add a new component library or change the Java services or shared database schema.

## Screen coverage

| Screen | Changes and local verification |
| --- | --- |
| Login | Clear account-type selection, show-password checkbox, disabled controls during submission, descriptive errors. Signed in as local students and administrator; signed out between roles. |
| Student signup | Required fields, readable phone/password guidance, password visibility, loading feedback and mobile form layout. Inspected the form and verified empty submission is blocked; did not create another account through the browser. |
| Discover buddies | Search, expandable filters in keyboard order, reset, loading/error/empty states and informative student rows. Verified name search, mode filtering, no-results reset and profile navigation. Removed hardcoded 80% scores and simulated connection success. |
| Public profile | Readable labelled details, back navigation, cancellable requests, loading/error handling and group discovery link. Verified a real student profile and private contact omission. |
| Connections | An honest explanation that buddy connections are unavailable in this branch, with working discovery and group links. Removed fictional badges and empty placeholder tabs. |
| Study groups | Clear group status/membership, expandable filters, request history, separate editor/details components, labelled controls and mobile dialogs. Created and edited a local group; Bob requested to join, Alice accepted, and the member count updated. Inspected removal/closure confirmations and cancelled them. Escape closes details and returns keyboard focus to the originating button. |
| My profile | Required fields, optional private phone guidance, named course-removal buttons, explicit unsaved/saving/saved states and error recovery. Saved school and study-goal changes, then reloaded to verify persistence. |
| Weekly availability | Replaced the broken drag grid with day/start/end controls, an editable slot list and weekly overview. Rejects reversed, duplicate and zero-length slots. Added, saved, reloaded and edited a slot using the actual profile endpoint. Saving also saves other pending profile changes, which the screen explains. |
| Admin dashboard | Readable live counts, loading/error states and direct links to administration tasks. Removed database implementation terminology from the screen. Verified against the local account summary. |
| User accounts | Search/role labels, real forms, save feedback, initial-password guidance and named deletion confirmation. Saved a synthetic student's school. Inspected creation and deletion dialogs and cancelled them; no account was deleted through the browser. |
| Matching configuration | Named numeric/range controls, explicit automatic redistribution, loading/save/error states and a fixed highest-priority criterion. Saved 40/39/7/7/7 course-first weights and confirmed the values survived reload. |

The shared shell now has working mobile navigation, current-page links, a named sign-out action, a skip link and page titles. New routes start at the top; browser back/forward can restore their saved scroll position. The browser walkthrough used desktop 1280×720 and mobile 390×844, including long forms and dialogs. The temporary viewport override was reset afterwards. No console warnings or errors were observed in the final browser check.

## Code organisation and added files

The pages keep their own small interaction handlers. Shared code lives in the files below; each has one clear responsibility.

| Added file | Responsibility |
| --- | --- |
| `frontend/src/components/AppDialog.vue` | Native dialog opening/closing, title, close button, busy protection and focus fallback. |
| `frontend/src/components/AppNavigation.vue` | The same navigation links for the sidebar and mobile menu. |
| `frontend/src/components/groups/GroupEditor.vue` | Group form draft, time validation and save event. |
| `frontend/src/components/groups/GroupDetails.vue` | Group details, requests, member actions and confirmations. |
| `frontend/src/composables/useAccounts.js` | Admin account data, search and save/delete functions. |
| `frontend/src/composables/useMatching.js` | Matching settings, strategy selection and persistence. |
| `frontend/src/composables/useNotifications.js` | Notification message, dismiss action and timer. |
| `frontend/src/services/api.js` | Shared authenticated HTTP calls, session expiry, stale-session protection and empty successful responses. Replaces `services/groupApi.js`. |
| `frontend/src/utils/matchingWeights.js` | A short loop that shares the remaining weight budget while preserving a locked priority. |
| `frontend/src/utils/studyData.js` | Shared options, readable enum/time labels and slot validation. |
| `frontend/tests/matchingWeights.test.js` | Weight boundaries, repeated edits, locked-priority invariants and inconsistent saved settings. |
| `frontend/tests/studyData.test.js` | Time round trips, noon/midnight and invalid slot boundaries. |
| `docs/ui-review.md` | This screen inventory and handover. |

Updated all existing page components, the app layout/toast, group and shared state modules, router scroll behaviour, shared CSS, HTML metadata, the frontend test command and `docs/study-groups.md`. Removed unused prototype UI/state and duplicate style rules. The existing `useAppState()` entry point remains available to pages; it delegates account, matching and notification work to the smaller modules.

To change appearance, start in `frontend/src/style.css`. To change a group form, start in `GroupEditor.vue`. To change weight redistribution, start in `matchingWeights.js` and run its tests. The functions use explicit intermediate values and straightforward conditions/loops instead of deeply chained expressions.

## Verification

Run from the repository root:

```sh
npm --prefix frontend test
npm --prefix frontend run build -- --outDir /tmp/studybuddy-ui-build
python3 scripts/group-smoke.py
```

- Seven frontend tests passed, including every editable integer percentage for both priority strategies.
- Production build passed. Build output was written outside the repository so tracked `dist` files were not replaced.
- The real gateway smoke script passed create/edit/request/accept/reject/remove/close, permission checks, capacity and validation, request history, and existing student/course/admin reads.
- Browser save checks used the temporary local PostgreSQL demo and synthetic accounts, not the team's shared database.
- The UI detector identified an unused Fraunces font import; it was removed. Shared contrast, focus styles, empty/error states and responsive layouts were also checked during the browser walkthrough.
- `git diff --check` passed.

The Java unit/integration suite was not rerun for this frontend-only pass; the live API regression smoke covered the existing group integration. The local demo is started with `python3 scripts/group-local.py demo` as described in [the group handover](study-groups.md).

## Existing limitations

Buddy connections and calculated match scores are not implemented in this branch. Their UI now states that clearly. Group join requests work independently. Matching strategy and weights are saved through two existing endpoints; if the second fails, the UI explains that only the strategy was saved and offers recovery. Profile/availability drafts survive in-app navigation but require saving before a full reload or sign-out.

Framework behaviour was checked against the current official [Vue watcher documentation](https://vuejs.org/guide/essentials/watchers.html), [Vue component documentation](https://vuejs.org/guide/components/props.html) and [Vue Router scroll behaviour](https://router.vuejs.org/guide/advanced/scroll-behavior.html), retrieved through Context7.
