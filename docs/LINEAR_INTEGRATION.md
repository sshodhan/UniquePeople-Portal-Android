# Linear Issue Tracking Integration

The Portal app can file problems you notice straight into the shared
[Linear](https://linear.app) **UniquePeople** project (team `AUR` / Aura
Agents), so bugs become tracked tickets instead of scattered notes. The web
companion ([uniquepeople-web](https://github.com/sshodhan/uniquepeople-web))
shares the same integration and the same project.

## How it works

```
Settings screen "Report a Problem"       uniquepeople-web deployment
IssueReporter.reportAsync()  ── POST ──► /api/report-issue ── GraphQL ──► Linear
        (no secrets in APK)                (holds LINEAR_API_KEY)
```

- `IssueReporter.java` posts the report to
  `https://uniquepeople-web.vercel.app/api/report-issue`
  (`MainActivity.DEFAULT_REPORT_ISSUE_URL`).
- The server holds the Linear API key and creates the ticket. **No Linear
  credentials ever ship inside the APK** — same pattern as the assistant,
  where API keys stay on the web server.
- Every ticket automatically includes the Portal's device ID, friendly name,
  app version, Android version, device model, display mode, and tile renderer,
  so a report like "photos stopped changing" arrives with enough context to
  triage.

## Using it on the Portal

Settings → **Report a Problem** → describe the issue, pick a severity, tap
**Send Report**. Severity maps to Linear priority (critical → Urgent,
high → High, medium → Normal, low → Low). On success the screen shows the
created issue's identifier (e.g. `AUR-42`).

If the server has no `LINEAR_API_KEY` configured, the endpoint returns 503 and
the Portal shows "Issue tracking is not set up on the server yet" — the app
keeps working normally.

## Server-side setup

All configuration lives in the uniquepeople-web deployment (one setup enables
both projects):

```bash
LINEAR_API_KEY=lin_api_xxxxxxxxxxxxxxxxxxxx
LINEAR_TEAM_KEY=AUR
LINEAR_PROJECT_ID=753102ab-9147-4af9-bd5f-14288e2df1b3   # UniquePeople project
```

See `docs/LINEAR_INTEGRATION.md` in the uniquepeople-web repo for the full
setup guide, the endpoint's abuse guardrails (rate limit + input caps), and a
script for filing issues from markdown files.
