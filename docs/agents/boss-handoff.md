# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-10-12, 18:25 Israel time)
- **Released:** v0.8.5 (2026-10-11, search fix) and **v0.8.6** (2026-10-12: Phase 10 Waves 1–2: ride details +
  transfer chip, search hardening, holidays + dates).
- **Phase 10 merged:** #56 D1, #57 D2, #58 D3, #59 D4, #60 notes 0.8.6, #61 D5 (big-stop changes).
  Costs incl. Sonnet reviews: D1 $13.3, D2 $12.9, D3 $12.0, D4 $2.0, D5 $20.4 (over its $18 cap).
- **Owner decisions taken this round** (all recorded in `phase10.md` "Owner decisions"): transfer chip
  < 2 min (< 4 with a train), "Tight" only < 0; two-town neighbourhoods list one row per town (B);
  `HORIZON_DAYS = 8`, no extra calls; big-stop matching within 300 m with the walk counted (B).
- **Running:** D6 ways in (session `session_01BwYkZZfGtRamoBJXHphBpo`, started 17:54), Opus, $12–18. It was
  told #61 merged and to merge main in. Its review is the Sonnet logic pass from its "Reviewer checks".
- **Next:** Wave 4 (D7 stop timetables, D8 fares + report), one Opus implementer at a time while the
  7-day limit reads `allowed_warning` (resets about 2026-10-18 18:00 Israel time).
- **Merge approvals:** the owner OKs each merge (sometimes ahead: "if clean then merge"). The owner
  pre-approved releases at wave ends ("release without asking again", 2026-10-12, for 0.8.6).
- **Open follow-ups:** the full CBS town list (data.gov.il blocked); Photon is blocked from cloud
  sessions (JVM address corpus has known gaps); scratch branches `claude/geocoder-check`,
  `corpus-run/search-fix`, `claude/a8-back-diag` and the Gemini GitHub app for the owner to remove.
- **Gotcha learned:** Maven Central often answers 429 to cloud sessions; reviewers then rely on green CI.
  Transitous trip ids expire with each feed update (noted in `transitous-api`).

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
