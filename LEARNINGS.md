# Learnings and Regression Patterns

Generalized lessons from bugs and reviews. Each lesson states a **pattern** —
what to check next time and where the class of mistake can recur — not just the
incident that spawned it.

> **Numbering:** each `## §N` heading is a stable, unique id — cite it as `§N`
> from PRs, Linear issues, and reviews. Add a new lesson with the next unused
> number; **never reuse a number**. Numbers are ids, not a reading order, so
> gaps are expected and fine. Incidents are tracked in Linear (Aura Agents,
> `AUR-NN`) and cross-referenced from here; this file holds only the
> generalized classes. `scripts/check-learnings-format.sh` guards the
> numbering. The web repo (`sshodhan/uniquepeople-web`) keeps its own
> `LEARNINGS.md`; lessons that span both sides are recorded in each repo from
> that repo's perspective.

---

## §1. The web contract is append-only — consume it defensively

**Origin:** uniquepeople-web PR #8 (timezone derived from weather location),
2026-08.

**Incident:** the server began deriving `timeZone` from the configured weather
location; a transient-failure path could have blanked the legacy `timeZone`
field this app reads, silently breaking clocks on older clients.

**Pattern:** this app and the server evolve independently and update on
different schedules, so the contract must be safe in both directions:

- Never assume a new server field exists — an older deployment may omit it.
  Read defensively with a usable fallback.
- Never stop reading a legacy field until a coordinated migration lands in
  both repos; the server treats its fields as append-only (web repo
  `LEARNINGS.md` §2), and this side must hold up its half.
- A server field being *present but empty* is a real state to handle, not an
  impossibility — the incident above showed empty can arrive via a server bug.

**Sibling risk:** every response parser in this app — device config, update
manifests, dashboard data.

**Guards:** `scripts/check-v1-compatibility.sh`; review checklist §2b.

---

## §2. Portal audio capture must ride the voice-communication pipeline

**Origin:** uniquepeople-web PR #8 (Portal audio interruption hardening),
2026-08. The Portal's speaker output leaked into its microphone and the
assistant repeatedly interrupted itself.

**Pattern:** the Meta Portal's speaker/mic coupling makes echo the default
failure mode, and the defense lives in the capture pipeline, not downstream
logic:

- Keep capture inside the WebView (`getUserMedia` with `echoCancellation`),
  where Chromium routes audio through the platform voice-communication path
  with hardware AEC. The `WebChromeClient.onPermissionRequest` grant flow in
  `AssistantActivity` (including the pending request held across the runtime
  permission prompt) is what keeps that alive — treat it as load-bearing.
- If native capture is ever added, use
  `MediaRecorder.AudioSource.VOICE_COMMUNICATION` (never raw `MIC`) and attach
  the platform `AcousticEchoCanceler` / `NoiseSuppressor` when available.
- Keep software gain neutral — never boost raw PCM; leveling belongs to the
  hardware profile. `MODIFY_AUDIO_SETTINGS` stays declared for this reason.
- Client-side interruption logic can only compensate so much (see web repo
  `LEARNINGS.md` §1); a weakened capture pipeline shows up as the assistant
  interrupting itself, which no downstream debounce fully fixes.

**Sibling risk:** any future native audio feature (alarms, calls, wake words)
and any WebView settings change that touches media.

**Guards:** review checklist §2a; manifest permission checks in
`scripts/check-v1-compatibility.sh`.

---

## §3. The build stays self-contained — no Gradle, no network

**Origin:** repo design decision (`build.sh`), reaffirmed 2026-08.

**Pattern:** `build.sh` builds the APK with only the local Android SDK and a
JDK, which is what makes the Portal side-load / hosted-update flow reproducible
on any machine without dependency drift:

- No Gradle, no dependency downloads, no build-time network access.
- New libraries mean vendored jars in `app/libs` with an explicit reason —
  prefer no new dependencies at all.
- Anything that would make the build reach the network belongs in a separate
  tool, not in `build.sh`.

**Sibling risk:** any PR that "modernizes" the build or adds a dependency for
convenience.

**Guards:** review checklist §2c.

---

## Key takeaways

- §1 — Consume the web contract defensively: new fields may be absent, legacy
  fields keep being read, empty-but-present is a real state.
- §2 — Echo defense lives in the capture pipeline: WebView/hardware AEC path
  is load-bearing; native capture (if ever) uses VOICE_COMMUNICATION with
  neutral software gain.
- §3 — `build.sh` stays offline and Gradle-free; dependencies are vendored
  deliberately or not added.
