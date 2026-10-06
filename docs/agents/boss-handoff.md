# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-10-06, 22:50 Israel time)
- **Released:** v0.7.0 (2026-10-05).
- **Merged since:** #38 mode tabs, #39 place search town, #40 C1, #41 C3, #42 one-tap in-app
  update (works from 0.8.0 on), #43 UI-test idle fix, #44 C4 tile, #45 handoff order, #46 C5
  alerts, #47 C6 look & welcome. All Phase 9 packages are merged; no agent is running.
- **Close-out:** `docs/releases/v0.8.0.md` is assembled (fragments removed).
- **Next:**
  1. Owner checks each Phase 9 feature on the Pixel (night refresh needs one night on Wi-Fi
     and charging).
  2. Android 8 Back closing an open search: still open, listed as a known issue in the
     v0.8.0 notes. Fixing it is an implementer job, only if the owner wants it before 0.8.0.
  3. Ask the owner, then trigger `release.yml` on `main` with version `0.8.0`. 0.8.0 is
     installed once the usual way; later updates are one tap in the app.
- **Optional follow-up (owner to decide):** a holiday-eve notice for the last-trip alert (the
  19:00 check finds the last trip already gone on a weekday holiday eve; today it stays silent).

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
