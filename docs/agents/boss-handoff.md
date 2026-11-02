# Boss handoff (2026-10-05)

Read `docs/agents/team.md` first: roles, flow, owner stops and token rules. Then
`docs/agents/phase9.md`: the plan and the briefs to send word for word, with the owner's
decisions already recorded.

## State (2026-11-02, 15:20 Israel time)
- **Released:** v0.9.2 (2026-10-31). **Next: v0.10.0. The owner said "wait on the release" (2026-11-02)**: ask before releasing.
  Before it: join `docs/releases/next/*` into `v0.10.0.md`, the owner runs `docs/testing/bug-bash.md` on the Pixel,
  then add the 0.10.0 saved-data sample after the tag (the `release-apk` skill).
- **Boss:** `session_01DH799Fr9pdL4XLVsi4uqeJ` (depth 0, started by the owner), took over 2026-11-02 from
  `session_011uenNPuCQdDbxyhH4S3ibu`. Hand over around 400k context.
- **Phase 11 is all merged**, and main is green after each merge:
  - E4 #105, T4 #106, T6 #110 (6 security fixes), T8 #109 (release smoke, startup gate, upgrade test, LeakCanary,
    MoreJourneys, bug-bash list), F2 #104, F3 #112 (4 flakes), F5 #111 (own actions via a non-exported alias),
    #113 (nightly perf jobs + the flake log skips cancelled jobs), F4 #114 (Compose 1.8.3 + Paparazzi 2.0.0-alpha02;
    the Settings race fixed, 144/144), E5 #115 (a11y + TalkBack), E6 #116 (NFC lab, **debug build only**),
    F6 #118 (follow-up bugs, ~$16; a "now" search is rounded to the minute so repeats hit the cache on every tab;
    A11Y-MAP: injected touches don't reach the app on the API 35 emulator, so the MapView-dispatched long-press
    blocks and the band press is report-only).
- **Owner decisions this round:**
  - E4: the payment apps confirmed on the Pixel; the revised low-data rule accepted.
  - No v0.9.3; the next release is v0.10.0.
  - The APK +2.28% from F4 accepted.
  - E6: go.
  - Release: wait.
- **In flight:** nothing.
- **Waiting on the owner:**
  - E6's two-scan Pixel test (steps in PR #116's body). With the result, write the go/no-go:
    - **go:** a follow-up package finds the end-date bits by comparing the scans with Rav-Kav Online;
    - **no-go:** a `dead-ends` row, and remove the lab.
    
    Then tick ROADMAP E6.
  - The word on v0.10.0, plus the bug-bash run. The bug-bash should include F6's Pixel check: a finger long-press
    on the map between the search card and the results drops a pin.
- **Follow-ups not yet packaged:**
  - at font 2.0, stop-sheet headsigns are cut and the park & ride header breaks into 6 lines (E5's PR body);
  - the timetable golden depends on its scroll slice (F4 review nit);
  - input injection (shell `input`, `UiAutomation.injectInputEvent`) doesn't reach apps on the API 35
    `google_apis` emulator (F6, run 41437345486). When fixed, restore the blocking A11Y-MAP band check (TODO in
    `A11yJourneysTest`);
  - `tools/ui_faults_run.sh` counts screens by matching `matrix(` text (F6 review nit).
- **Limits:** the 7-day limit reads `allowed_warning` until ~8 Nov (resetsAt 1794150000): one agent at a time.
- **Open owner to-dos:**
  - uninstall the Gemini GitHub app;
  - delete stale branches (#100's list, plus `scratch/p11-f3-evidence-1..3` and `claude/p11-compose-f4-ev1..3`,
    and now `claude/p11-followups-f6`);
  - set up the Test Lab Spark project.

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
- **Agents go idle while "waiting for CI"** despite the polling-loop brief (F2, T4, E6). Check `updated_at` at
  each check-in. If one is idle with finished runs, nudge it with the run ids, or verify and merge yourself.
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
