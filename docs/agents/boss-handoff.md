# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-11-01, 07:35 Israel time)
- **Released:** **v0.9.2 (2026-10-31 19:46)**: R2, the get-off alert fix. Saved-data sample 0.9.2 merged (#103).
  **No v0.9.3 (owner, 2026-11-01): the next release is v0.10.0**, with E4, F2's full-disk fix and later waves. Ask before releasing it.
- **Boss:** `session_011uenNPuCQdDbxyhH4S3ibu` (took over 2026-10-21; handing over 2026-11-01 at ~360k, no agents running). **The
  next boss must be started by the owner from claude.ai/code** (see Gotchas: lineage depth).
- **Merged since 21 Oct:** #100 handoff, #98 R2, #102 notes, #103 sample, #104 **F2 CI stability**
  (~$19 across F2, F2b and a Sonnet review), #106 **T4 fault matrix** (~$14; 144 cases, the `faults` job 10 min).
  #96 (F1) closed, superseded by #104.
- **E4 merged (#105, 2026-11-01 07:02 Israel)**, ~$15.5 + $2 review. Owner decisions 1 (apps confirmed on the Pixel)
  and 2 (the revised low-data rule) are recorded in its PR body. Main is green after it.
  Follow-ups: T4's check "E4 data card == server counts"; optional: the pass notice opens the chooser.
- **Wave 5 done (E4 + T4).**
- **Next: Wave 6 (T6 Opus + T8 Sonnet)** per `phase11.md`, then F3/F4. Then E5, then (optional) E6 → v0.10.0.
- **Fix packages owed** (flake policy, "flakes twice in 14 days"):
  - **F3 (one package):** `s01_home_empty` "tab … wide/tall enough" measured mid-relayout (R2, E4 and T4: 3 times);
    j14 sees E1's test-reminder notification (1005) left from an earlier journey (cleanup gap); `RestoreTabsStateTest:70`
    "found something" right after `settle{}` on real IO.
  - **F4, the Settings race (s15)**: root cause in #104's body; the fix is Compose 1.8+ with Paparazzi 2.x and re-recorded goldens.
  - JVM flakes seen by T4: `DayNoticeViewModelTest` (ConcurrentModification), `RestoreTabsStateTest` (timeout).
- **Bugs found by T4** (PR #106 body; not fixed):
  - Park & ride plans from `Instant.now()`, not `clock` (`ParkRideState.kt:45`), so a repeat search sends 4 requests instead of 0.
  - Photon is asked again after a 429 (not traced).
  - The rain note once sent nothing on the first search after offline (unconfirmed).
- **Open owner to-dos:** uninstall the Gemini GitHub app; delete the stale branches (listed in #100's State);
  set up the Test Lab Spark project.

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
- **Lineage depth (limit 8).** This boss is depth 7, so its agents are depth 8: they **can't use
  `send_later`**, and a boss it started couldn't create sessions. Agents poll CI in a blocking
  `gh run list` loop (say so in every brief), and the boss does the check-ins. The owner starts
  each new boss from claude.ai/code (depth 1).
- **The cloud proxy blocks Actions log and artifact downloads** (`gh run view --log`, blob hosts).
  Use the GitHub MCP `get_job_logs` with `return_content: true`. It works.
- **Maven Central answers 429** to Gradle in cloud containers at times. Rely on CI for builds.
- **Check an agent's claimed head** against `git ls-remote` before merging (E4 reported a typo'd sha).
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
