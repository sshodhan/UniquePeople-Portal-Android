# Project instructions

## Pre-PR review (mandatory)

Before creating any pull request, read and follow the full checklist in
`.github/claude-review.md`. It covers rebasing onto `main`, the build and V1
guardrails, the risky-surface checks (WebView/audio, web contract consumption,
self-contained build), the Linear-wired learning loop, and the required review
summary output.

## Learning loop (read before writing code)

`LEARNINGS.md` holds generalized lessons from past bugs and reviews, each with
a stable `§N` id. Read it before changing code in an area it covers, and cite
the relevant `§N` in PR descriptions and reviews. If a change fixes a bug whose
root cause could recur elsewhere, add or reinforce a lesson — see
`.github/claude-review.md` §3 for the exact requirements.

## Issue tracking is Linear, not markdown

Bugs, deferred findings, and follow-ups are tracked as Linear issues in the
**Aura Agents** team (`AUR-NN` ids), not in a bug-log file. When a review or a
sibling sweep surfaces something out of scope for the current PR, file it in
Linear with the PR linked, and cite the `AUR-NN` id wherever the finding is
mentioned. Lessons in `LEARNINGS.md` cross-reference the `AUR-NN` incidents
that spawned them.

## Build and checks

- `./build.sh` builds the APK self-contained: no Gradle, no network, only the
  local Android SDK + a JDK. Keep it that way (`LEARNINGS.md` §3).
- The `Build` CI workflow runs `INCLUDE_VIDEO=0 bash build.sh` on every PR —
  every change must produce an installable APK, not just pass review.
- `scripts/check-v1-compatibility.sh` runs the V1 stability guardrails and
  must pass before any push (CI runs it on every PR).
- `scripts/check-learnings-format.sh` guards `LEARNINGS.md` numbering.
- `scripts/check-hosted-release.sh` verifies the public install guide
  (`docs/INSTALL.md`) matches the hosted APK; public releases follow
  `docs/RELEASE_CHECKLIST.md`.

## Relationship to uniquepeople-web

This app consumes the API served by `sshodhan/uniquepeople-web` and renders
its assistant inside a WebView. Contract fields are append-only on the server
side; on this side, never assume a new field exists on older servers, and keep
reading legacy fields until a coordinated migration (`LEARNINGS.md` §1).
