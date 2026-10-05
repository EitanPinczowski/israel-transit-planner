# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State
- **Phase 8 is done and merged** (#15, #18–#26, #29); v0.7.0 notes are in
  `docs/releases/v0.7.0.md`.
  - **Owner to do:** check the build on the Pixel, then release `0.7.0` (`release-apk` skill).
- **Phase 9 is approved:** #27 research, #28 plan, #30 decisions. None of it has started yet.
- **No agent sessions are running.** Every finished session is archived.

## Next actions, at most 2 agents at a time
1. **Tester** (Opus): continue on `claude/gallant-mendel-4yps2i`.
   - Merge `origin/main` (no rebase), keeping `main`'s design-pass structure.
   - Fix the round-2 UI findings:
     - landscape/tablet panel squeezes the search;
     - suggestions hidden behind the keyboard, so show only the edited field while editing;
     - first Back on API 26 only hides the keyboard;
     - tests must wait for the ⋮ "Save trip" item.
   - Add edge-case tests for Phase 8: trip details with no tripId or a failed request; Park &
     Ride with no station in range; calendar with permission denied, no events, all-day events
     only, or an approximate match; crash log empty and full.
   - One PR. Keep `TripPanel.kt` edits minimal.
2. **C2, night refresh + Home** (Opus): brief in `phase9.md`. Run it in parallel with the tester,
   since they touch different code.
3. **C1, earlier/later + platforms:** only after the tester's PR merges, because both edit
   `TripPanel`.
4. Then **C3 + C4**, then **C5**.
   - Each PR gets a tester review and a designer review (both Sonnet) before the owner merges.
   - The boss merges only when the owner says so.
5. **Close-out:** `docs/releases/v0.8.0.md` from `docs/releases/next/*`.

## Gotchas learned
- **Agents copy the boss's permission mode.** The boss must run in **Auto** on the server
  (check `permission_mode` with `get_session`), or agents stall in plan/default mode.
- **The shared 5-hour usage limit:** resume stalled agents after the reset. Keep it to 2 agents.
- **Agents report once (done or blocked).** No check-ins, no CI subscriptions; one fallback
  check-in hours apart.
- **Nobody pushes to another agent's branch.** Archive each session once its PR merges.
