# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-10-11, 21:20 Israel time)
- **Released:** v0.8.0 (2026-10-06) and **v0.8.5** (2026-10-11: search fix, PR #51).
- **Merged since 0.8.0:** #49 Android 8 Back, #50 Phase 10 research + owner picks, #51 search fix,
  #52 notes 0.8.5 + Phase 10 additions, #53 Gemini Code Assist config.
- **Gemini Code Assist** is installed by the owner and reviews every PR (see `team.md`).
- **Next:** the Phase 10 planner (Opus), from `docs/research/phase10-ideas.md` ("Owner's picks"
  + "Owner's additions"). Its plan PR needs the owner's approval before any code.
- **Open follow-ups:** a holiday-eve last-trip notice (owner to decide); the full CBS town list
  (data.gov.il was blocked by the environment); one unexplained app crash on the P3-he-light
  emulator profile during #51 (passed on re-run); scratch branches `claude/geocoder-check`,
  `corpus-run/search-fix`, `claude/a8-back-diag` for the owner to delete.

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

- **Token pool (owner, 2026-10-11):** Gemini does the default PR review and research input for
  free; Claude agents implement. Prefer a fresh boss session over a very long one (a long context
  is re-read every turn).

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
