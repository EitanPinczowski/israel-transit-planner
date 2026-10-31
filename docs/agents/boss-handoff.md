# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-10-21, 00:25 Israel time)
- **Released:** **v0.9.1 (2026-10-20 23:08)**: E1, E7, E2, E3, R1. Saved-data sample 0.9.1 merged (#99).
  Next: **v0.9.2 = R2 (ride-alert fix); ask the owner first** (asked 2026-10-21 00:22, no answer yet).
- **Boss:** handing over from `session_011tJH15m5mUzgr2G4vFCaKt` (context 422k) on 2026-10-21.
- **Phase 11:** Waves 1–4 merged: T1 #79 ($5.5), T2 #80 ($4.3), E1 #83 ($23.9), T3 #84 ($11), E7 #82 (~$47),
  E2 #88 ($26.5), T7 #89 ($4.6), T5 #93 ($4.3), E3 #91 ($16), plus owner-approved fix packages R1 #95
  (restore car tabs + errands after process death, ~$14 + $0.6 review + fresh fix round) and the 0.9.1 sample #99 ($2).
  - **In flight: R2, PR #98** (`session_01N31TZYDzer5aTUXHQkq8Pd`, Opus, ~$8, ctx ~294k): the RideTracker get-off-alert
    fix. The review found the first version's time gate broke the early-bus case (blocking); the fix round adds
    ordered-stop progress arming (`matched ≥ n−3`, bounded ±2 matching) with the time gate as fallback, a liveness
    property and early-bus tests. It was waiting on a re-run of `s01_home_empty`. Merge when green and all review
    items are handled, then ask about v0.9.2.
  - **Parked: F1, PR #96** (`session_01VjnDAbmjEnxTcfcNi88ENU`, stood down): a `performScrollToNode` helper for the
    Settings dialog; the "multithreaded access to SnapshotStateObserver" race is still open (~1 in 12; stack
    `LayoutNode.getCollapsedSemantics` on the test thread). Don't merge: its push run had a monkey failure and one
    Paparazzi run was red on the same head.
  - **Next Opus package: F2, CI stability.** (1) the SnapshotStateObserver race, starting from F1's branch and report
    (Compose BOM 2024.12.01 / Compose 1.7.x, Espresso 3.6.1; try a BOM bump); (2) monkey "System appears to have
    crashed at event 1797 of 6000, seed 4242" on API 35, seen 3 times (#82, F1 ×2); (3) Paparazzi red on the PR run
    and green on the push run of the same head (F1 0d12e92). Flake-log issue #92 lists the one-offs.
  - **Then Wave 5 (E4 + T4)** per phase11.md. The 7-day limit reads allowed_warning until **Sun 25 Oct 17:00
    Israel**: one Opus + one Sonnet at a time.
  - Later, from R2's review: a late bus (> 3 min) loses its alert (pre-existing; ROADMAP line).
  - **Stall lesson:** at 08:16 UTC on 20 Oct the 5-hour limit stopped every session including the boss; nothing
    resumed for 11 h until the owner wrote. A boss `send_later` can't fire while the boss is limited.
- **Owner decisions this round:** "merge when green" re-confirmed in the boss session (2026-10-19 21:1x; the auto-mode
  classifier blocks merges until the owner says it in the session itself); pick-a-day package E7 (2026-10-18); 21-day timetable notice (2026-10-18);
  API keys allowed on a $0, no-card plan (2026-10-13, in CLAUDE.md); **"when green merge and continue"**
  (2026-10-18): the boss merges each package after green + review + fix round, without asking; still ask
  before any release or new owner decision.
- **Open owner to-dos:** uninstall the Gemini GitHub app; delete branches `claude/geocoder-check`,
  `corpus-run/search-fix`, `claude/a8-back-diag`, `claude/ci-emu-twin-1`, `claude/ci-emu-twin-2`,
  `scratch/p11-*`, `scratch/t3-deps-probe`; set up the Test Lab Spark project (T7 skipped it).
- **Lessons this round (follow them):**
  - **Never run 3 agents at once.** Twice, 3 concurrent sessions burned the shared 5-hour limit in ~20 min.
    Keep to 2, and a short review counts.
  - **Long agent contexts are the real cost.** E7 reached ~730k tokens; every turn re-reads it. For a fix round
    on a big session, prefer a fresh focused session with a precise brief.
  - **When a limit hits, schedule the resume yourself** (`send_later` at resetsAt + 5 min, for every stalled
    session). A check scheduled before the limit fires late, because the boss is limited too.
  - **Two green PRs that touch the same files are not green together.** After merging the first, have the second
    merge `main` and re-run CI before merging it (#82 on top of #84 broke six T3 tests through E7's departed filter).
  - **A fix round in a fresh session works:** E2's fresh fix session ($10.6) found and fixed a bug the 400k-token
    implementer had missed.
  - **Verify the head after merging a PR into main:** a PR green on its branch can still break main (E1 → j14 on
    API 36 was actually a time bomb in the test fixture, but it surfaced right after a merge).
  - A docs-only plan change that adds a package is fine to merge when green after the owner asked for it.

## Owner standing orders
- **Usage limits, the boss handles them alone (owner, 2026-10-06):** the owner should never have
  to wake the boss after a reset.
  - **Before starting any agent or review,** read `rate_limit_info` (`get_session` with no id).
  - **If the 5-hour limit reads `allowed_warning` or `rejected`:** don't start. Schedule one
    `send_later` for `resetsAt` + 5 min that starts the task then.
  - **If an agent stalls on "session limit · resets …":** schedule its resume the same way,
    unprompted.
  - **While the 7-day limit reads `allowed_warning`:** at most one Opus implementer plus one
    Sonnet review at a time.
  - **Rough costs:** an implementer package is $4–23; a Sonnet review is about $1; a fix round
    is $2–5.
- When an agent asks a question, the boss answers it if the answer is clear from the briefs,
  the skills and the owner's recorded decisions; otherwise it passes the question to the owner
  (2026-10-05).

- **Token pool (owner, 2026-10-12):** keep packages at $10–20, Sonnet passes targeted, and start a
  fresh boss session when the current one gets long (a long context is re-read every turn).
- **Handoff PRs need no extra permission (owner, 2026-10-12):** a docs-only PR that updates this
  file for a boss handoff is merged by the boss as soon as its checks are green, without asking.
  Feature PRs still need the owner's OK.
- **Claude project `israel-transit` (owner, 2026-10-12):** every session for this repo (boss, implementers,
  reviews, researchers, planners) is created with the tag `remote-agents-project:israel-transit` and
  nothing else for the project. The old `team` / `phase8` tags were moved over on 2026-10-12.

## Gotchas learned
- **Agents go idle on red CI.** PR events don't reach agent sessions. The briefs now tell agents
  to self-check once per push; if one still sits idle on a red PR, wake it with the failing job
  and log line.
- **Goldens go stale after every code push.** Emulator jobs first, goldens last, from the same
  head's run (`parallel-work`).
- **Auto mode blocks traffic redirection** (for example a Gradle mirror in `~/.gradle/init.d/`)
  and the agent stalls on a permission prompt. Interrupt it, then steer it with a message.
- **Agents copy the boss's permission mode.** The boss must run in **Auto** on the server
  (check `permission_mode` with `get_session`), or agents stall in plan/default mode.
- **The shared 5-hour usage limit:** resume stalled agents after the reset. Keep it to 2 agents.
- **Agents report once (done or blocked).** No check-ins, no CI subscriptions; one fallback
  check-in hours apart.
- **Nobody pushes to another agent's branch.** Archive each session once its PR merges.
