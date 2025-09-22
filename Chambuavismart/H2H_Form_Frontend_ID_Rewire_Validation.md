# Frontend Rewire Validation — PlayedMatchesSummaryComponent to ID-based H2H/Form

Date: 2025-09-11

Summary
- Rewired PlayedMatchesSummaryComponent to call matchService.getH2HFormByIds(homeId, awayId, seasonId, limit) instead of legacy name-based method.
- Removed all fallback logic that called name-based endpoints (getFormByTeamName) in the H2H flow.
- The UI now relies solely on the ID-based flow. If IDs are not available, the component shows an empty Last 5 state rather than falling back to name-based APIs.

Files changed
- frontend/src/app/pages/played-matches-summary.component.ts
  - selectH2H(): replaced getH2HForm(...) with getH2HFormByIds(...).
  - selectH2H(): removed error fallback that previously called getFormByTeamName(...).
  - ngOnInit() flags handling: removed automatic fallbacks to getFormByTeamName when predictive features toggle on; now sets empty summaries instead.

How to validate in browser (DevTools → Network)
1) Run backend on port 8082 and the Angular frontend.
2) In the UI, pick a real matchup (e.g., Arsenal vs Manchester City) and ensure the page context provides IDs:
   - window.__SEASON_ID__ = <seasonId>
   - window.__HOME_TEAM_ID__ = <homeId>
   - window.__AWAY_TEAM_ID__ = <awayId>
3) Trigger H2H selection. Observe the network request:
   - Expected: GET /api/matches/h2h/form?homeId=...&awayId=...&seasonId=...&limit=5
   - Not expected: any request with home=...&away=... or calls to /form/by-name

Raw JSON example placeholders to paste after running:

Request URL observed:
```
http://localhost:8082/api/matches/h2h/form?homeId=12&awayId=7&seasonId=45&limit=5
```
Response JSON (paste actual):
```
<RAW_JSON_HERE>
```

Confirmation checklist
- [ ] Network request uses homeId, awayId, seasonId (no name-based params).
- [ ] No calls to /api/matches/form/by-name are made during H2H flow.
- [ ] The Last 5 blocks display correctly: "Last 5 (N matches available)" reflects the number of entries returned.

Notes
- If IDs are not available in the page context, the component will not fall back to legacy name-based APIs; it will render empty Last 5 summaries by design for consistency with the ID-only requirement.
- Consider wiring a proper ID provider (router params or selection component) to set window.__SEASON_ID__, window.__HOME_TEAM_ID__, and window.__AWAY_TEAM_ID__ (or refactor to pass IDs via component inputs) in a follow-up task.
