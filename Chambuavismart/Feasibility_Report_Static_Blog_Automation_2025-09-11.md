# Feasibility Report: Local Spring Boot → Daily Content Generation → Static Blog Auto-Publish (GitHub Pages/Netlify)

Date: 2025-09-11 10:33 (local)
Author: Junie (autonomous programmer)

## Executive Summary
It is technically viable to extend the existing Spring Boot backend to generate daily football content (match stats, predictions, analysis) and automatically publish it to a static blog hosted on GitHub Pages or Netlify. The current codebase already includes:
- A robust MatchAnalysis pipeline (MatchAnalysisService, DTOs) capable of producing structured analysis and human-readable insights text.
- Spring Boot 3.2.5 with scheduling enabled (@EnableScheduling) to drive daily tasks.
- Sufficient building blocks to render content as Markdown/HTML and to automate publication using either Git operations (JGit or shell) or HTTP calls to deployment webhooks.

Minimal additions are needed: a small content generation module (Markdown templates), a publisher component (Git push or webhook), and a few configuration properties/secrets. No architectural rewrites are required.

---

## What We Have Today (Leverageable Components)

### Platform & Core
- Spring Boot 3.2.5 (Java 17) with spring-boot-starter and spring-boot-starter-web.
- Scheduling already enabled: `@EnableScheduling` in `ChambuaViSmartApplication`.
- Profiles and config management via `application.yml` (dev/test/prod).

### Domain Logic Ready for Content
- Match analysis domain is implemented and tested:
  - `MatchAnalysisService` computes probabilities, expected goals, and H2H summaries.
  - `MatchAnalysisResponse` DTO includes:
    - `insightsText` (plain-text narrative suitable to seed blog content)
    - win probabilities, BTTS, Over2.5, expected goals, confidence score, advice
    - form summary and H2H summary including last-N items, GD summary, forms
    - flat `headToHeadMatches` list for rendering tables
  - Multiple unit tests confirm deterministic analysis paths.
  - `QuickInsightsService` exists and consumes `MatchAnalysisService`, hinting at quick content synthesis.

### Data Access and Sources
- MySQL is configured as the main datasource; H2 is present for tests.
- Repositories for teams, matches, leagues exist; upload endpoints present (data ingestion).

### Missing But Straightforward
- No explicit content templating dependency (Thymeleaf/Freemarker) in the backend POM yet.
- No Git automation or publisher class yet.
- No Netlify/GitHub Pages integration glue code yet.

---

## Target Workflow (High-Level)
Local run of app (daily or on-demand) → fetch matches to cover → compute analysis → render Markdown/HTML posts → write to local clone of a static-site repo → commit & push (or call deploy webhook) → host (GitHub Pages/Netlify) rebuilds and publishes.

This decouples online availability from the backend runtime: the static site is always up, even when the app is offline.

---

## Hosting Options and Trade-offs

1) GitHub Pages (Jekyll/Hugo/Eleventy or raw HTML):
- Publication model: push commits to a dedicated repo/branch (e.g., `username.github.io` or `gh-pages`).
- Trigger: Git push auto-builds Pages (or GitHub Action builds site → deploy to Pages branch).
- Pros: free, reliable, easy to browse history.
- Cons: build time is tied to GitHub infra; secrets for pushing from local require a PAT or SSH key.

2) Netlify (static site):
- Publication model: connect repo to Netlify; push triggers build; or hit a Netlify Build Hook URL to trigger redeploy.
- Pros: very fast builds, build hooks are simple to call via HTTP; good previews; supports Hugo/Eleventy/Next static export, etc.
- Cons: still need repo or storage of generated content; free tier limits apply.

3) Direct static hosting (e.g., `gh-pages` branch only with prebuilt HTML):
- Skip SSG build; generate HTML in the backend and push to `gh-pages` directly.
- Pros: simplest; no external build step.
- Cons: lose SSG features (taxonomies, archives) unless you implement them manually.

Recommendation: Start with GitHub Pages via `gh-pages` branch and prebuilt HTML/Markdown, or Netlify with a simple SSG like Eleventy/Hugo. Both are easy to automate with Git push.

---

## Content Generation Strategy

- Source content: Use `MatchAnalysisService` (and `QuickInsightsService`) to compute daily analyses for a selected set of matches (e.g., today’s fixtures across supported leagues or a curated shortlist).
- Output format: Markdown preferred (portable, readable in repo). Optionally also generate an index page and RSS feed.
- Rendering approach:
  - Option A (no new template engine): Compose Markdown manually using String builders; embed tables from DTO lists.
  - Option B (add lightweight template engine): Add Freemarker or Pebble to backend and render `.ftl`/`.peb` to Markdown/HTML. This improves maintainability.
- File naming: `content/posts/YYYY-MM-DD/<slug>.md` with front matter (title, date, tags, summary) for SSGs or a simple naming convention for plain static HTML.

---

## Automation and Scheduling

- Daily scheduler using Spring’s `@Scheduled(cron = ...)` to run at a set time in Africa/Nairobi time (timezone is already set globally on startup).
- The job performs:
  1) Query candidate matches (today/tomorrow, per leagues of interest).
  2) For each match, call the analysis service to get `MatchAnalysisResponse`.
  3) Render to Markdown using a template or string composition.
  4) Write files into a working directory that is a checked-out static site repository (e.g., sibling folder `../football-blog/`).
  5) Commit and push the changes (using JGit or shell `git`), or invoke Netlify Build Hook.

---

## Publication Mechanisms

- Git push from backend host:
  - Use `JGit` to commit and push (embed PAT/SSH in environment). Or call OS-level `git` via ProcessBuilder if allowed.
  - Configure remote `origin` to `git@github.com:username/football-blog.git` (SSH) or `https://<PAT>@github.com/username/football-blog.git`.
  - Security: store secrets (PAT or SSH private key path) in environment variables or Spring config placeholders; never hardcode.

- Netlify Build Hook:
  - Configure a build hook URL in Netlify; after writing or updating content in the connected repo, hit `POST` to trigger rebuild.
  - Alternatively, Netlify builds on push without the hook if the repo is connected.

---

## Configuration & Secrets

Add properties in `application.yml` under a `blog.publish` section with environment overrides:
- `blog.publish.enabled`: true/false
- `blog.publish.mode`: `git` or `webhook`
- `blog.publish.repoPath`: local filesystem path to checked-out static blog repo
- `blog.publish.branch`: e.g., `gh-pages` or `main`
- `blog.publish.commitAuthorName` / `commitAuthorEmail`
- `blog.publish.remote`: optional override of remote name/URL
- `blog.publish.webhookUrl`: for Netlify build hook
- `blog.content.maxMatchesPerDay`, `blog.content.leagues`, `blog.content.dateRange`: selector knobs
- Secrets: `GIT_PAT` or `SSH_KEY_PATH` via environment vars; `NETLIFY_HOOK_URL`

No secrets are committed to the repo; they are provided at runtime.

---

## Step-by-Step Implementation Plan

1) Define target static site strategy (choose one):
   - A1: Prebuilt HTML/Markdown pushed directly to `gh-pages` branch (no SSG build).
   - A2: Use an SSG (Hugo/Eleventy). Backend writes Markdown with front matter; GitHub Pages or GitHub Action builds the site.

2) Prepare static site repo:
   - Create `football-blog` repo and initialize basic structure (`content/`, `posts/`, or plain `/` for HTML).
   - Enable GitHub Pages (branch+folder) or connect the repo to Netlify.

3) Add minimal backend content module:
   - Create `DailyContentService` which:
     - Selects matches (e.g., today’s fixtures from DB).
     - Invokes `MatchAnalysisService` (and optionally `QuickInsightsService`).
     - Produces Markdown strings per match and aggregates a daily roundup.
     - Writes files under `repoPath` with date-based folders.

4) Add a publisher component:
   - Mode `git`: Add a `GitPublisher` using JGit to add/commit/push, with configurable author and remote.
   - Mode `webhook`: Add a `WebhookPublisher` to POST to Netlify build hook.

5) Add a scheduler:
   - `@Scheduled(cron = "0 30 6 * * *", zone = "Africa/Nairobi")` to run every morning.
   - Config-guarded: only runs when `blog.publish.enabled=true`.

6) Add configuration properties:
   - `BlogPublishProperties` bound to `blog.publish.*` and `blog.content.*` with validation.

7) Local run flow:
   - Clone static blog repo locally.
   - Provide env vars (DB creds are already supported; add GIT/NETLIFY secrets if needed).
   - Run backend: it generates files, commits/pushes, triggers public update.

8) Optional polish:
   - Generate an `index.html/md` per day grouping posts.
   - Generate RSS/Atom feed.
   - Add retry/backoff and idempotency (skip if content already posted for the day).

9) Observability:
   - Log summaries: number of matches analyzed, files written, commit hash, remote URL, build status.

---

## Technical Viability Assessment

- Viable with minimal additions. Key enablers already present:
  - Rich analysis DTOs including `insightsText` and H2H/form summaries suitable for blog narratives.
  - Scheduling is enabled; timezone is already set to Africa/Nairobi at startup.
  - Java 17 environment suitable for templating and Git libraries.
- Gaps to close are straightforward:
  - Add rendering (simple Markdown or light templating).
  - Add publisher (JGit or webhook).
  - Add configuration/secrets management and a scheduled job wrapper.

Risk/Considerations:
- Database availability at scheduled time; handle empty/no fixtures gracefully.
- Publishing from a local machine requires credentials and network connectivity at run time.
- Ensure idempotence to avoid duplicate posts.
- Respect rate limits of hosting provider if frequent updates.

---

## Concrete Next Steps (No Code Yet)

- Decide host and repo structure (GitHub Pages vs Netlify + SSG choice).
- Create/prepare the static blog repo and branch.
- Confirm which leagues/match selection rules define daily content.
- Approve a minimal Markdown template format and front matter fields (title, date, tags, summary, canonical URL).
- Plan secrets provisioning method (env vars) and test a dry-run that writes local files without pushing.
- After approval, implement the small content and publishing modules and wire them into a scheduled task.

---

## Appendix: Relevant Files Noted
- `backend/pom.xml`: Spring Boot 3.2.5; web, JPA, Flyway; tests; no template engine yet.
- `backend/src/main/java/com/chambua/vismart/ChambuaViSmartApplication.java`: `@EnableScheduling`, Nairobi timezone.
- `backend/src/main/java/com/chambua/vismart/dto/MatchAnalysisResponse.java`: Insights text + rich analysis data for rendering.
- `backend/src/main/java/com/chambua/vismart/service/MatchAnalysisService.java`: Core analysis logic, deterministic outputs (referenced by tests).
- `backend/src/main/java/com/chambua/vismart/service/QuickInsightsService.java`: Builds quick textual insights from analysis.
- `backend/src/main/resources/application.yml`: Profiles; room to add `blog.publish.*` properties.

End of report.