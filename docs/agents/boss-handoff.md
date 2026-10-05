# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State
- **Phase 8 is done and merged** (#15, #18–#26, #29); v0.7.0 notes are in
  `docs/releases/v0.7.0.md`.
  - **0.7.0 waits for the tester's PR** (owner, 2026-10-05). Once it merges, the owner checks
    the build on the Pixel, then releases `0.7.0` (`release-apk` skill).
- **Phase 9 is approved:** #27 research, #28 plan, #30 decisions. **C6** (look & welcome) was
  added by the owner on 2026-10-05 after the phone test; it is in `phase9.md` and ROADMAP.
- **Merged today:** #33 (C6 plan, docs), #34 (C2 night refresh + Home).
- **Running:** the tester, `session_01Tg9rSCEYux8YXXCk6DUEYH`, on PR #32. Its last blocker is
  API 35 j12 (ride alert); the planner's fix plan was sent 2026-10-05. Owner decisions on #32:
  - Android 8 Back is a known issue (J4 on API 26 is recorded as a finding);
  - merge #32 when it's green.

## Next actions, at most 2 agents at a time
1. When #32 is green: merge it (owner-approved), then the owner checks 0.7.0 on the Pixel and
   releases it.
2. Archive the tester session after #32 merges.
3. **C1, earlier/later + platforms:** only after the tester's PR merges, because both edit
   `TripPanel`.
4. Then **C3 + C4**, then **C5 + C6**.
   - Each PR gets a tester review and a designer review (both Sonnet) before the owner merges.
   - The boss merges only when the owner says so.
5. **Close-out:** `docs/releases/v0.8.0.md` from `docs/releases/next/*`.

## Owner standing orders
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
