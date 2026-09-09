---
name: progress-keeper
description: The concrete procedure the progress-keeper agent follows to update docs/memory.md after a roadmap phase, decision, or notable fix lands. Use whenever recording what happened so a future session can resume without re-deriving context — not for implementing work or maintaining any other doc.
---

# Memory-log update procedure

The point of `docs/memory.md` is that a future reader (a different Claude session, or the human)
never has to reconstruct *why* something was done from git history alone. A commit message says
what changed; this log says why, and what almost went wrong.

## 1. Establish ground truth first

Don't trust being told what happened — verify:
- `git log --oneline -N` since the last entry's recorded HEAD.
- `git diff`/`git show` on anything you're not sure about.
- `docs/roadmap.md` to know which phase/task number the work belongs to.
- The invoking session's own conversation context, when available — it often carries the *why*
  (a rejected alternative, a constraint, a bug hunt) that a commit message never captures. Use it,
  but still verify any factual claim against the repo before recording it.

## 2. Update "Current state" first

This is the section a time-pressed reader checks first. It must always reflect: current HEAD,
what phase/task things are at, and the one next thing to do. A few sentences, not a summary of
everything ever done.

## 3. Append to "What's done"

One dated entry per notable unit of work. For each: what changed, which commit(s), and the
non-obvious *why* — a rejected alternative and why it was rejected, a constraint that shaped the
final shape, a bug that was caught mid-work and how. Do not restate the commit message; add what
it doesn't carry. If the same work session produced multiple entries elsewhere in this log
already (e.g. an in-progress phase being extended further), extend that entry rather than
starting a disconnected new one.

## 4. Record environment/tooling quirks separately

Anything machine- or project-specific that cost time to discover (a version incompatibility, a
missing local dependency, a non-obvious required env var) goes in its own clearly-labeled
section/memory file — not buried inside a "what's done" narrative — so a future session's first
move is to check there before re-diagnosing from scratch.

## 5. Update "Next up"

Name the next concrete task and any precondition it depends on. Remove/resolve items that are now
done; never leave a stale "next up" pointing at something already shipped.

## 6. Correct stale history in place, never rewrite it

If a prior entry is now factually wrong (something marked in-progress that's since landed, or a
plan that changed), correct it where it stands. Don't delete history to make the log look tidier.

## 7. Boundary check before writing

If a fact you'd log actually belongs in a different doc (a business rule → `workflow-rules`
owns `docs/business-rules.md`; a UX/screen change → `figma-screen-design` owns
`docs/user-flow.md`), don't write it into `docs/memory.md` — name it in your report back instead,
so the right owner can pick it up. This agent's write scope is `docs/memory.md` only.
