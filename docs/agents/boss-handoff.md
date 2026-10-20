# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-10-20, 05:40 Israel time)
- **Released:** v0.9.0 (2026-10-13). Next proposed: v0.9.1 after Phase 11 Wave 4 (ask the owner first).
- **Boss:** session `session_011tJH15m5mUzgr2G4vFCaKt` (took over 2026-10-19 19:35).
- **Phase 11** (`docs/agents/phase11.md`, all 31 owner decisions "as proposed"; E7 added by the owner 2026-10-18):
  - **Waves 1–3 merged.** Plan #78 (+ E7 #81), T1 #79 ($5.5), T2 #80 ($4.3), E1 #83 ($23.9), j14 API-36 fix #85
    ($4.5), licence exception #86, T3 #84 ($11), E7 #82 (~$47: long context), E2 #88 ($26.5: $15.3 implementer +
    $0.6 review + $10.6 fresh-session fix round), T7 #89 ($4.6).
  - **Next: Wave 4 = E3 (leg labels, Opus) + T5 (routing correctness, Sonnet)** → then ask the owner about v0.9.1.
    T5 must also fix the stale golden-trip-12 check (ignores the 10-min window) and `tools/plan_summary.py`'s
    hard-coded +03:00 (Israel is UTC+2 from 25 Oct).
  - **Open owner question:** T7 found that after process death the 4 car tabs restore mode + places but not their
    results, and errands lose their stop (`SavedUi` keeps neither; notices in the `system` job). Boss proposed a
    small Opus fix package after Wave 3; waiting for the owner.
  - **Flakes seen (flake-log rule: twice in 14 days → fix package):** `s15_settings_bottom` twice on #82 (due a fix
    package); once each on #88's f2a93e0: j01 permission dialog (API 33), j02 suggestion (API 35), j15 test alarm
    > 90 s late (API 33), j05 timeout (API 33). T7's "CI flake log" issue records them from now on.
  - T7's nightly (`nightly-ui.yml`) runs for the first time now it is on main; read the "nightly" issue. T7 guessed
    `vm.savePlace` may crash on a failed DataStore write under low storage (unverified).
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
