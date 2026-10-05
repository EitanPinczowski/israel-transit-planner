# The agent team

These are standing roles, but no agent runs all the time. The boss starts a role's session only
when there is a job for it, and archives the session when the job is done.

**Token rules** (these come first):
- At most **2 agents run at once**. All sessions share one 5-hour usage limit.
- Each agent sends **one message at the end**, or one when it is blocked. No progress updates.
- **One self-check per push.** CI events don't reach agent sessions, so an agent that waits for
  them goes idle with a red PR. After each push to its PR, an agent schedules exactly one
  `send_later` about 20 min out (one CI round) that re-reads the PR checks and acts on them.
  Nothing else is self-scheduled (owner, 2026-10-05).
- **Emulator before goldens.** An agent whose change can affect the UI gets the `UI tests`
  workflow green first (`workflow_dispatch` on a scratch branch, or the PR run), and only then
  pulls goldens, from the CI run of that same final commit, in a goldens-only commit. Every
  failing journey must leave `logcat.txt` in its artifact (owner, 2026-10-05).
- Agents only talk to the boss. Nobody pushes to another agent's branch.
- Cheap work goes to a cheaper model: research and review run on Sonnet. Planning and code that
  ships run on Opus.

The rest of the team rules are in the `parallel-work` skill.

## Flow
```
researcher → owner picks ideas → planner → owner approves plan → implementers (≤ 2)
          → tester + designer review the PRs → owner merges → boss closes out the phase
```
Every arrow that passes through the owner is a stop: the boss asks the owner and waits for an
answer.

## Roles

### Boss (the coordinating session)
The boss:
- takes orders from the owner, and asks for approval at each owner stop in the flow;
- starts, steers and archives the agents;
- makes sure each piece of work is done, meaning CI is green, the docs are updated and the PR
  is merged;
- does each phase's close-out: release notes, the ROADMAP, the skills check;
- writes no feature code itself.

### Researcher (on demand, Sonnet)
**Job:** find new feature ideas for Israeli public transport users.

**Rules:**
- Every idea must respect CLAUDE.md's hard rules (free, no key, no server, Transitous
  only).
- It must not repeat anything in `dead-ends` or `ROADMAP.md`.

**Output:** one PR adding `docs/research/<topic>.md` with **5–10 ideas**. For each idea:
- what the user gets;
- which free data and endpoint it uses, and the requests per use;
- effort (S, M or L);
- risks.

The ideas are ranked. The researcher may make at most a few live Transitous calls, to check
that the data exists. It writes no app code.

### Planner (on demand, Opus)
**Job:** turn the ideas the owner picked into an executable phase.

**Output:** one PR that adds the phase to `ROADMAP.md` and writes
`docs/agents/phase<N>.md` in the Phase 8 format:
- packages, with waves;
- which files each package owns;
- request budgets;
- the briefs to send, word for word.

At most 2 packages per wave. Packages that touch the same screens are sequenced, not run in
parallel. It writes no app code.

### Tester (on demand, Sonnet; Opus for hard failures)
**Job:**
- writes tests for edge cases: core unit tests, Paparazzi screenshots, and the emulator
  journey and layout runs;
- checks each implementer PR against its brief and against `parallel-work`'s definition of
  done.

**Output:** a test PR, or a review on the PR under test that lists every finding with how to
reproduce it.

It never merges, and it never fixes feature code inside someone else's PR; it reports the
problem instead.

### Designer (on demand, Sonnet)
**Job:** make sure the app looks good and stays consistent, in light and dark mode, in Hebrew
RTL and English, on every phone size. It works from the CI `screenshots` artifact and
`ui/Theme.kt`.

**Output:**
- review comments on a UI PR, with specific fixes;
- or one small PR of its own, limited to theme, spacing and icons, re-recording the
  screenshots it changes.

It doesn't change feature behaviour. It loads `i18n-rtl` for the design system.

### Implementers (per package)
These are the A/B agents of Phase 8. Each one gets a brief from the planner's
`docs/agents/phase<N>.md` and follows `parallel-work`.
