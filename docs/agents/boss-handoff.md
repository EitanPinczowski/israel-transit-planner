# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-10-19, 19:40 Israel time)
- **Released:** v0.9.0 (2026-10-13). Next proposed: v0.9.1 after Phase 11 Wave 4 (ask the owner first).
- **Phase 11** (`docs/agents/phase11.md`, all 31 owner decisions "as proposed"; E7 added by the owner 2026-10-18):
  - Merged: plan #78 (+ E7 #81), T1 #79 ($5.5), T2 #80 ($4.3), E1 #83 ($23.9, over budget: 4-day weekly-limit
    pause + fix round), API-36 j14 fix #85 ($4.5; cause: Android 16 drops notifications whose `when` is > 14 days
    old, the uitest fixture clock had aged), licence exception for guava listenablefuture #86 (boss-made,
    reported to the owner; he may revert).
  - Open: **T3 #84** (ViewModel tests, session `session_01AnaLPosPaeqWace6fxCo4B`, Sonnet): green except API 36,
    merging main now. **E7 #82** (Pick a day, `session_0147oE4BQDF62Rx1AcMBVmLU`, Opus, ~$36 so far): designer +
    tester review done (2 blocking test gaps + 6 nits), fix round in progress. Its scope grew on 2026-10-18:
    "past time → ask Tomorrow?", departed-options filter in `TripPlanner.plan`/`more`, `HORIZON_DAYS` 8 → 21.
    The reminder re-check stays unfiltered (boss call). Both: verify every check, then merge.
  - Next: Wave 3 = E2 (I missed it, Opus) + T7 (system + chaos journeys, Sonnet). Then Wave 4 (E3, T5) → v0.9.1.
    T5 must also fix the stale golden-trip-12 check (ignores the 10-min window) and `tools/plan_summary.py`'s
    hard-coded +03:00.
- **Owner decisions this round:** pick-a-day package E7 (2026-10-18); 21-day timetable notice (2026-10-18);
  API keys allowed on a $0, no-card plan (2026-10-13, in CLAUDE.md); **"when green merge and continue"**
  (2026-10-18): the boss merges each package after green + review + fix round, without asking; still ask
  before any release or new owner decision.
- **Open owner to-dos:** uninstall the Gemini GitHub app; delete branches `claude/geocoder-check`,
  `corpus-run/search-fix`, `claude/a8-back-diag`, `claude/ci-emu-twin-1`, `claude/ci-emu-twin-2`,
  `scratch/p11-*`, `scratch/t3-deps-probe`.
- **Lessons this round (follow them):**
  - **Never run 3 agents at once.** Twice, 3 concurrent sessions burned the shared 5-hour limit in ~20 min.
    Keep to 2, and a short review counts.
  - **Long agent contexts are the real cost.** E7 reached ~730k tokens; every turn re-reads it. For a fix round
    on a big session, prefer a fresh focused session with a precise brief.
  - **When a limit hits, schedule the resume yourself** (`send_later` at resetsAt + 5 min, for every stalled
    session). A check scheduled before the limit fires late, because the boss is limited too.
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
