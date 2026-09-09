---
name: domain-review
description: The concrete procedure the domain-review agent follows to check that implemented code matches the ticketing platform's authoritative domain documents (business-rules.md, user-flow.md, ADRs). Use whenever verifying domain consistency after a feature lands — not for code-quality review (that's code-reviewer) or for implementing fixes.
---

# Domain-consistency review procedure

Evidence-first, neutral: state what the code does, what the doc says, and which one is more
likely stale — never assume the code is wrong just because it diverges from the doc.

## 1. Pick the doc, then find the code

Work from the authoritative doc outward, not the other way around — you're checking whether
implementation kept up with the documented contract, not auditing code for its own sake.

- **`docs/business-rules.md`**: for each entity/rule you're checking, extract the exact documented
  field list, status enum, or threshold (e.g. "max 6 seats," "10-minute hold TTL"). Then find the
  actual entity/constant in code and compare values exactly — not "roughly matches."
- **`docs/user-flow.md`**: for each screen you're checking, extract the documented route, API
  calls, and visual states (loading/empty/error, plus any domain-specific states like seat
  statuses). Then find the actual page/component and confirm each state's *trigger* still exists
  (a visual redesign can restyle a state without accidentally deleting the condition that shows
  it — check both).
- **`docs/adr/`**: for each recorded decision, identify the rule it establishes (e.g. ADR-0001:
  seat availability lives in booking, not event) and confirm no code path contradicts it.

## 2. Tag every finding

- **Match** — code and doc agree; no finding needed, don't manufacture one.
- **Mismatch** — code and doc disagree. State both sides plainly with file/line citations on the
  code side and section reference on the doc side.
- For a mismatch, make a judgment call on which side is more likely stale (a doc written before a
  later architectural constraint forced a different implementation is common — see this repo's
  own history of the admin-listing-endpoint doc drift) but say it's a judgment call, not a
  certainty, and name who owns fixing whichever side is wrong.

## 3. Cross-service consistency

For any workflow that spans services (the checkout saga, the admin write path), confirm the same
vocabulary (status names, topic names, event/command class names) is used identically in every
service's code and in the doc's description of the flow — a renamed status in one service without
a matching doc update is exactly the kind of drift this review exists to catch.

## 4. Report and hand off

Report neutrally. Route a doc-side fix to whichever agent owns that doc (`workflow-rules` for
business-rules.md, `figma-screen-design` for user-flow.md), a code-side fix to whichever
implementation agent owns that module, and a code-quality (not domain) question to
**code-reviewer** instead of answering it yourself. This agent has no `Write`/`Edit` tools — it
never touches docs or code directly.
