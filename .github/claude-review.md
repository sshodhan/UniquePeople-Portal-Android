# Pre-PR Review Checklist

> **When to run:** Before creating any pull request. Work through every
> section; sections with triggers that don't fire are marked N/A in the
> summary — with one sentence saying why, never silently skipped.

## 1. Rebase and checks

- [ ] Feature branch is rebased onto the latest `main`. Resolve conflicts with
      full awareness of surrounding changes — never mechanically.
- [ ] `scripts/check-v1-compatibility.sh` passes.
- [ ] The build guard passes: CI's `Build` workflow runs the real
      `INCLUDE_VIDEO=0 bash build.sh` on every PR and must be green. Run
      `./build.sh` locally too when an Android SDK is available; if the
      change touches Java sources, never rely on review alone to prove they
      compile.

## 2. Risky-surface checks

Each subsection lists its triggers. If a trigger fires, run ALL of that
subsection's checks; otherwise mark it N/A in the summary.

### 2a. WebView, audio, and permissions

**Triggers:** changes to `AssistantActivity.java`, WebView settings, the
manifest's permissions, or anything touching microphone/camera capture.

- [ ] `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS` stay declared, and the
      `WebChromeClient.onPermissionRequest` grant path (including the pending
      request held across the runtime-permission prompt) is preserved — the
      web assistant's microphone dies silently without it.
- [ ] Audio capture stays inside the WebView (`getUserMedia`), where
      Chromium routes it through the voice-communication pipeline with
      hardware AEC. If native capture is ever added, it must use
      `MediaRecorder.AudioSource.VOICE_COMMUNICATION` and attach the
      platform `AcousticEchoCanceler` / `NoiseSuppressor` when available —
      never raw `MIC` with software gain (`LEARNINGS.md` §2).
- [ ] No change amplifies or processes PCM in software; leveling belongs to
      the hardware profile.
- [ ] WebView settings that audio depends on (`mediaPlaybackRequiresUserGesture`,
      JavaScript enabled) are not regressed.

### 2b. Web contract consumption

**Triggers:** changes to code that parses responses from uniquepeople-web
(device config, update manifests, dashboard data).

- [ ] No parser starts *requiring* a field that older servers may omit —
      new fields are read defensively with fallbacks (`LEARNINGS.md` §1).
- [ ] Legacy fields this app reads (e.g. `timeZone` in device config) keep
      being read until a coordinated migration is agreed in both repos.
- [ ] If this PR needs a server-side field change, note it in the PR body so
      the web repo can follow up — server fields are append-only.

### 2c. Self-contained build and updater

**Triggers:** changes to `build.sh`, `AndroidUpdateManager.java`, signing, or
anything the OTA/hosted-update flow depends on.

- [ ] `build.sh` remains self-contained: no Gradle, no network fetches, no new
      build-time dependencies (`LEARNINGS.md` §3).
- [ ] Update/installer changes are exercised against
      `docs/V1_STABILITY.md` expectations and the V1 guardrails script.

## 3. Learning loop (Linear-wired)

The recurring failure mode this loop prevents: a bug gets fixed, but the
*generalized lesson* never lands anywhere, so the same class of bug recurs in a
sibling feature. Close the loop on every PR.

### Required when the PR fixes a bug or hit a non-obvious gotcha

- [ ] **A Linear issue exists** for each bug fixed (Aura Agents team,
      `AUR-NN`), linked to the PR, and is closed by (or referenced from) this
      PR. For bugs found but deferred, file the issue with the PR attached and
      a priority set — a finding mentioned only in a comment thread is lost.
- [ ] **A regression guard exists** — a check in
      `scripts/check-v1-compatibility.sh` (or a new script wired into CI) that
      fails against the pre-fix code and passes after.
- [ ] **Ask: is this an instance of a *class* of mistake?** If the same root
      cause could plausibly recur in another activity/manager/flow, a
      generalized lesson MUST be added to `LEARNINGS.md`:
      - New `## §N` section using the next unused number. Numbers are stable
        ids — never reuse one; gaps are fine. Cite lessons as `§N`.
      - State the **pattern**, not just the incident: what to check next time,
        and which sibling files share the risk.
      - Cross-reference the `AUR-NN` issue(s) and the guard that covers it.
      - A pure one-off (typo, copy tweak) does NOT need a lesson — say so
        explicitly in the summary rather than padding the doc.
- [ ] **Existing lesson reinforced rather than duplicated.** If a lesson
      already covers this class, add the new `AUR-NN` / example to that section
      instead of creating a near-duplicate.
- [ ] **Sibling sweep performed and ACTED ON (not just pondered).** For each
      bug fixed, grep the sibling files that share the root cause — the other
      activities, every call site of a changed helper, the matching settings /
      updater paths — and record: the **pattern searched**, the **files
      checked**, and for **each** a verdict — **"fixed in this PR"** or
      **"filed AUR-NN"**. A PR that changed one call site of a multi-site
      pattern without this evidence is incomplete.

## 4. Don't repeat known mistakes

- [ ] Read `LEARNINGS.md` before approving. Cross-check that this PR does not
      reintroduce a documented regression pattern.
- [ ] If a similar bug class has occurred before, verify the fix addresses the
      root cause — not just the symptom.

## 5. Review Summary (required output)

After completing all checks, output this summary so the human reviewer can
audit the reasoning:

```
### Pre-PR Review Summary

**Checks:** [guardrails/build results; rebase state]

**Risky surfaces:**
- 2a WebView/audio/permissions: [checks run and results, or "N/A — <why>"]
- 2b Web contract consumption: [same]
- 2c Self-contained build/updater: [same]

**Sibling / class sweep (per fix):**
- [pattern grepped, files checked, per-file verdict "fixed in this PR" /
  "filed AUR-NN"; or "no siblings share this class" WITH the grep that
  confirms it]

**Learning loop:**
- Linear: [AUR-NN filed/closed, or "none — no bug fixed"]
- Regression guard: [check added, or why none]
- LEARNINGS.md: [§N added/reinforced with one-line pattern, or "N/A — one-off,
  not a recurring class" with reason]

**Lessons applied:** [§N entries verified against this change]

**Risks considered:** [edge cases / failure modes thought through, and why
each is safe or what mitigation was added]
```
