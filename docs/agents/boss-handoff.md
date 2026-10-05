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
- **Running** (started 2026-10-05, both Opus, Auto):
  - Tester, `session_01Tg9rSCEYux8YXXCk6DUEYH`, on `claude/gallant-mendel-4yps2i`. Its brief
    also covers two owner findings sent later: all mode buttons fit on one screen with no
    sideways scroll, and an address is typed in place in the field the user tapped.
  - C2, `session_01SJEYEHM6Hf5wVrMqFpr4hn`, on `claude/p9-night-refresh`.

## Next actions, at most 2 agents at a time
1. When the tester's PR is green: the owner merges, then 0.7.0 is released.
2. When C2's PR is green: tester review, then designer review (both Sonnet), then the owner
   merges.
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
- **Auto mode blocks traffic redirection** (for example a Gradle mirror in `~/.gradle/init.d/`)
  and the agent stalls on a permission prompt. Interrupt it, then steer it with a message.
- **Agents copy the boss's permission mode.** The boss must run in **Auto** on the server
  (check `permission_mode` with `get_session`), or agents stall in plan/default mode.
- **The shared 5-hour usage limit:** resume stalled agents after the reset. Keep it to 2 agents.
- **Agents report once (done or blocked).** No check-ins, no CI subscriptions; one fallback
  check-in hours apart.
- **Nobody pushes to another agent's branch.** Archive each session once its PR merges.
