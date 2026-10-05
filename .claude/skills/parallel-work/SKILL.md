---
name: parallel-work
description: Rules for working as one of several agents on separate branches at once - which files a package owns, how to avoid merge conflicts, the definition of done, release-note fragments. Load when picking up a Phase 8 (or later) package from ROADMAP.md or docs/agents/.
---

# Working in parallel with other agents

Each package in `ROADMAP.md` (Phase 8 onwards) is one cloud session, with one branch and one PR.
The brief each session got is in `docs/agents/phase8.md`. Several PRs are open at the same
time, so every rule below is about not breaking each other.

## Waves
- **Wave 0** (A1 API plumbing, A2 UI split) touches the shared files on purpose and merges
  first.
- **Wave 1** (B1–B4) starts from the `main` that has both.
- Never base a branch on another open package branch. Wait for it to merge, then merge `main`.

## New code goes in new files
- **Screen:** `android/.../ui/screens/<Feature>.kt`. Never grow `MainScreen.kt`, which after A2
  is only the scaffold.
- **State:** the feature's own ViewModel or state holder (`ui/<Feature>ViewModel.kt`). Add at
  most one field or callback to `MainViewModel` / `UiState` to reach it.
- **Strings:** `res/values/strings_<feature>.xml` and `res/values-iw/strings_<feature>.xml`.
  Android merges every file in `values/`, and two PRs that both append to `strings.xml`
  conflict on the last line. Load `i18n-rtl`.
- **Core logic:** a new file in the matching package (`features/`, `present/`, `plan/`,
  `diag/`…), with its own test class (`<Feature>Test.kt`). Don't append to `Phase1Test.kt` and
  the like.
- **New API models:** a new file in `core/api/` when they are more than a field or two.

## Shared files: edit as little as possible, and only where your package says
| file | who may edit it |
|---|---|
| `api/TransitApi.kt`, `api/MotisClient.kt`, `api/Guards.kt`, test `Fakes.kt` | A1 only. Wave 1 consumes. If you truly need a new call, say so in the PR. |
| `api/Models.kt` | A1. Others add fields only, never reorder. |
| `ui/MainScreen.kt`, `ui/MainViewModel.kt` | A2 moves code. Wave 1: one hook line each. |
| `AppMode` enum | B2 (`PARK_RIDE`) |
| `ui/MapController.kt` | B1 (vehicle layer) |
| `AndroidManifest.xml` | B3 (`READ_CALENDAR`), B4 (nothing expected) |
| `android/app/build.gradle.kts`, `.github/workflows/ci.yml` | B4 (extends #18's Paparazzi `screenshots` job) |
| `CLAUDE.md` | B2 (4th feature row); anyone adding a skill (table row) |
| `ROADMAP.md` | tick only your own package's `###` subsection |

## Screens have goldens (since B4)
A Wave 1 PR that changes how any screen looks, or adds a screen, makes the `screenshots` CI
job fail until it re-records: `python3 tools/pull_goldens.py` after the job ran on your HEAD,
then commit the images in your PR (`android-build` skill). A new screen also gets its shots in
`ui/PanelsTest.kt` (English + Hebrew). Never re-record another PR's screen to get yours green;
after merging `main`, re-record only if your own screen's pictures changed.

Before opening a PR, and again before saying it is done, merge `origin/main` into your branch
(never rebase a pushed branch). Resolve conflicts by keeping both sides.

## Definition of done (every package)
1. `./gradlew -p core test -q` passes (never piped) and `python tools/check_docs.py` passes.
2. CI is green on the PR head, including the `android` job that builds `app-debug`.
3. Any new request goes through `TransitApi`. A feature that sends more than one request per
   user action runs under `BudgetedTransitApi`, with the budget pinned by a test. The endpoint is
   listed in the `transitous-api` table.
4. Fixtures are real recordings (`tools/record_fixture.py`), never written by hand. Unit tests
   never touch the network.
5. The skill that owns the area is updated (table in CLAUDE.md), and your ROADMAP lines are
   ticked in the same commit.
6. Release notes go in a **fragment**: `docs/releases/next/<package>.md`, 1–3 bullets in English,
   then the same in Hebrew. The planner joins the fragments into `docs/releases/vX.Y.Z.md`
   (see `release-apk`).
7. A rejected approach goes into `dead-ends`, with the reason.

## Policy reminders
- The CLAUDE.md hard rules apply unchanged: free, no key, no server of our own, all traffic
  through `GuardedTransitApi`.
- Until Transitous answers `docs/transitous-contact.md`, live calls are for recording fixtures
  and the owner's own testing. Keep each package's recording to a handful of requests.
- Raising a budget, polling more often, or adding a background job that calls the network is a
  policy decision. Write it in the PR body; don't hide it in a constant.

## Agents never merge
Drive your PR to green and answer review comments. **The owner merges.** If you are blocked on
another package or on an owner decision, say so in the PR and to the planner session. Don't work
around it.

## Team roles
Tester, designer, researcher, planner and boss: who does what, the flow and the owner stops
are in `docs/agents/team.md`. Read it when you are started as one of those roles.

## Keep token use low (learned in Phase 8)
- **One coordinator.** Only the planner session talks to agents, and the agents report to it.
  Phase 8 ran three overlapping coordinators at one point, plus a long-lived session pushing
  onto agents' branches, and paid for it twice over.
- **At most 2 agents at a time.** All sessions share one 5-hour usage limit. Four parallel
  agents ran out of it halfway through, and each had to be resumed.
- **Report only at the end or when blocked.** Agents send one message when the PR is green and
  clean against `main`, or when they are blocked. No progress updates.
- **Event-driven, not polling.** Agents subscribe to their own PR's events. They don't schedule
  self check-ins. The coordinator keeps one long fallback check-in (hours, not minutes), and
  doesn't subscribe to every PR's CI.
- **Push small and often.** If the limit hits mid-task, the work survives on the branch.
- **Archive finished sessions** as soon as their PR merges.
- **Sequence work that touches the same screens** instead of running it in parallel. A
  conflict costs a re-merge on every open branch.
