---
name: agent-coordination
description: Claim, work, and release a GitHub issue without colliding with other agents in this repo. Use at the start of any session that will touch an issue, and whenever deciding what to work on next. Triggers on "what should I work on", "pick up an issue", "claim issue #N", "is anyone working on X", "check agent status", "what's in flight", "I'm done with this issue", or before opening a branch/PR for issue work.
---

# Agent coordination

Multiple agents work this repo concurrently from separate worktrees. They pick their own
issues, so the governing rule is:

> **Derive live state, never read it from a file.**

Every "who is doing what" fact comes from git and `gh` at the moment you ask. A previous
scheme cached claims in a gitignored `COORDINATION.md`; it was deleted and took every claim
with it. The only coordination facts stored in a file are the ones that genuinely can't be
derived — dependencies and file-overlap risk, in `AGENT_PLANS/DEPENDENCIES.md`.

## 1. Start: run the status script

Before anything else in a session that will touch issue work:

```bash
pwsh -File scripts/agent-status.ps1
```

It fetches (with prune) and then reports, all from ground truth: every worktree with its
branch, dirty-file count, ahead/behind vs `origin/master`, push state, and PR; every claimed
issue; every unclaimed open issue; every open PR; and a `Problems` list of pathologies it
detected.

Flags: `-NoFetch` (offline; remote columns go stale) and `-NoGitHub` (no `gh` auth; drops the
issue/PR sections). Avoid `-NoFetch` unless you actually have no network — see §6.

**Read the `Problems` section before claiming anything new.** If it flags unpushed commits or
a PR-less branch, that is likely *your own* prior session's work stranded on disk, and
finishing it beats starting something new.

## 2. Pick an unclaimed issue

The script's "Available issues" list is open issues *without* the `agent:in-progress` label.
That tells you an issue is free. It does not tell you whether it's *ready*.

Before claiming, check [`AGENT_PLANS/DEPENDENCIES.md`](../../../AGENT_PLANS/DEPENDENCIES.md)
for two things it holds and nothing else can:

- **Hard blockers** — e.g. #32/#33/#35 all need #31's schema first; #25 needs #19 finished.
  Claiming a blocked issue means either waiting or building on sand.
- **File-overlap risk** — issues with no formal dependency that edit the same files anyway.
  The Room schema row (#31, #27) is called out as the highest-conflict pair in the repo and
  should not run concurrently without agreeing migration version order first.

Two agents in different rows of the overlap table can work in parallel safely. Two in the
same row will merge-conflict even though nothing said they were dependent.

Also honor the notes there: #26 is externally blocked on product input — don't claim it
expecting to finish.

## 3. Claim it on the issue — before writing code

```bash
gh issue edit <N> --add-label "agent:in-progress" --add-assignee @me
gh issue comment <N> --body "Claimed by an agent. Branch: claude/issue-<N>-<slug>"
```

Both parts matter: the label is what the status script filters on, the comment records the
branch name so another agent can find your work without guessing.

Claims live on GitHub because it is the only store that is shared across worktrees, safe
under concurrency, visible without a terminal, and able to survive a local disaster. Do not
reintroduce a local claims file.

Branch naming is `claude/issue-<N>-<slug>`.

## 4. Push and open a draft PR after your first real commit

Not at the end. This is the step agents most often skip, and it is the one that makes work
visible:

- Uncommitted work is invisible to **everything** — including you, after a crash.
- An unpushed branch is invisible to every other agent and to the user.
- The PR is the durable record that the work exists.

So: commit early, push early, open the draft PR early, even when the work is half-finished.
Put `Closes #N` in the PR body so merging auto-closes the issue.

The status script actively flags the failure modes here — `NOT PUSHED` with commits ahead,
and "commits but NO pull request" both land in `Problems`.

**Note honestly what you did and didn't verify.** CI is `workflow_dispatch` only — it does
not run on push or PR — and there is no emulator anywhere in this project. If your change is
UI-facing, say "not visually verified on-device" in the PR rather than implying it was
checked. Run `gradlew.bat testDebugUnitTest` locally; that's the verification that actually
exists.

## 5. Release

Normal path: the PR merges, `Closes #N` closes the issue. If the `agent:in-progress` label
lingers after merge, drop it. Remove the worktree once its PR is merged — the status script
flags merged-PR worktrees as finished.

If you **abandon** an issue, say so explicitly:

```bash
gh issue edit <N> --remove-label "agent:in-progress" --remove-assignee @me
gh issue comment <N> --body "Unclaiming: <reason>. Work so far: <branch or 'none'>."
```

Silently walking away leaves the issue looking claimed forever.

**Reaping someone else's stale claim:** a claim with no commits and no PR after a day is
abandoned. Any agent may take it without asking. A claim *with* a pushed branch or an open PR
is live work — leave it alone regardless of age.

## 6. Stale-state rules that actually bite

- **Always `git fetch` before judging whether a branch is pushed or a PR exists.** Reading
  remote-tracking refs without fetching reports pushed branches as unpushed and open PRs as
  missing. This caused a real misdiagnosis here. The status script fetches for you — which is
  why `-NoFetch` is a last resort.
- **A worktree existing says nothing about whether it's active.** `git worktree list` is
  ground truth for what exists; `Ahead`/`Dirty`/`PR` in the script output is what tells you
  whether anything is happening in it.
- **Don't infer a claim from a branch name.** The label on the issue is the claim.

## 7. Before you touch worktree files

This repo has destroyed data on Windows twice. The specific footguns — junction deletion,
`git clean -xdf`, and the `AGENT_PLANS` junction that older instructions recommended — are in
the "Windows footguns" section of `CLAUDE.md`. They are deliberately kept there rather than
here, because they apply to every session, not just coordinated issue work. Re-read them
before removing or cleaning any worktree directory.

## Quick reference

| Situation | Action |
|---|---|
| Session start | `pwsh -File scripts/agent-status.ps1` |
| Choosing work | Unclaimed in script output **and** unblocked in `DEPENDENCIES.md` |
| Claiming | `agent:in-progress` label + assignee + branch-name comment |
| First commit | Push + draft PR immediately, with `Closes #N` |
| Done | Merge closes it; remove the worktree |
| Giving up | Remove label + assignee, comment why |
| Someone else's claim, no commits, >1 day | Free to take |
| Someone else's claim, pushed branch or PR | Leave it alone |
