# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-10-13, 12:40 Israel time)
- **Released:** v0.8.6 (2026-10-12). **No release since; owner: no release after Wave 4** (ask before one).
- **Phase 10 merged:** D1–D5 (#56–#59, #61), notes #60, D6 #62, D12 #65 (owner add-on: ≥ 1 min spare
  at changes, sort chips in every tab, no-unreasonable-chains rule), D7 #67, D8 #68, CI stability #69,
  D9 #70. Docs: #63, #64 (D12 brief), #66 (project tag).
  Costs incl. Sonnet reviews: D6 $15.0, D12 $10.8, D7 $12.9, D8 ≈ $13, CI fix $2.3, D9 $15.4.
- **Owner decisions this round** (in `phase10.md`): D12 chain window 10 min, also for the 5+ rule;
  soldiers in mandatory service ride free (D8); Open-Meteo approved + rain rule as proposed (D9);
  D10 as proposed (compileSdk 36, targetSdk 35).
- **Running:** MapLibre crash fix (session `session_016PBLHLfh1htmWtic2DsW8L`, Opus, $5–10, branch
  `claude/maplibre-crash-fix`). Root cause found by #69: a native SIGABRT
  (`jni::PendingJavaException`) in libmaplibre.so = a Kotlin exception escaping a map callback; it is
  the "Process crashed, 0 app crash logs" CI flake. It also removes #69's temporary MapLibre retry.
  No review planned beyond the boss checking runs; ask the owner to merge.
- **Next:** D10 Live Updates (owns `ui-tests.yml`; starts after the MapLibre fix merges), then D11.
- **Merge approvals:** the owner OKs each feature merge; docs-only handoff PRs merge when green.
- **Open owner to-dos:** uninstall the Gemini GitHub app; delete branches `claude/geocoder-check`,
  `corpus-run/search-fix`, `claude/a8-back-diag`, `claude/ci-emu-twin-1`, `claude/ci-emu-twin-2`
  (branch deletes are blocked from cloud sessions).
- **Gotchas learned:** Maven Central often 429s cloud sessions (rely on green CI); `gh run download` is
  refused (blob store) — reviewers read goldens + the `ci-screens` branch; concurrent UI-tests runs can
  race on the `ci-screens` push (harmless red "report → ci-screens").

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
