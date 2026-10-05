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

## One coordinator, and work outside the packages
- Exactly one coordinator session at a time. It keeps the status board at the end of
  `docs/agents/phase8.md` and is the only one that watches PRs it does not own. Other
  sessions stop their check-ins once a coordinator takes over.
- Work that is not a package (a design pass, a test harness) follows the same ownership
  table: it waits for the package that owns a shared file to merge, then merges `main`.
- Android code cannot compile in a cloud session (no SDK). Before pushing Kotlin in
  `android/`, check that every new name has its import. A missing import costs a full CI round.

## Keeping token use low
- Read the part of a file you need (`offset`/`limit`, `Grep`), not whole files; never read
  build output (the deny list blocks it).
- Reading another session: `list_events` with `kinds: ["assistant"]` and a small `limit`, or
  just its `get_session` summary. Full transcripts overflow the context.
- Don't poll. PR events wake you; one safety check-in at most, per the harness rules.
- Messages between sessions: a few lines, the decision and the file names, no pasted diffs.

## Agents never merge
Drive your PR to green and answer review comments. **The owner merges.** If you are blocked on
another package or on an owner decision, say so in the PR and to the planner session. Don't work
around it.
