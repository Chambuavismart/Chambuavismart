import { Component, OnInit, inject } from '@angular/core';
import { NgIf, NgFor, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FixturesService, LeagueWithUpcomingDTO } from '../services/fixtures.service';
import { LeagueService, League } from '../services/league.service';
import { MatchAnalysisService, MatchAnalysisResponse } from '../services/match-analysis.service';
import { Season, SeasonService } from '../services/season.service';
import { MatchService } from '../services/match.service';
import { TeamService } from '../services/team.service';
import { of, forkJoin } from 'rxjs';
import { map, catchError } from 'rxjs/operators';

@Component({
  selector: 'app-match-analysis',
  standalone: true,
  imports: [NgIf, NgFor, NgStyle, FormsModule, RouterLink],
  styles: [`
    :host { display:block; color:#e6eef8; }
    .layout { display:flex; gap: 16px; }
    .sidebar { width: 300px; background:#0b1220; border:1px solid #1f2937; border-radius:12px; padding:12px; }
    .content { flex: 1; display:flex; flex-direction:column; gap:12px; }
    .card { background:#0b1220; border:1px solid #1f2937; border-radius:16px; padding:12px; box-shadow: 0 2px 10px rgba(0,0,0,.25); overflow:auto; }
    .header { display:flex; align-items:center; justify-content:space-between; }
    .w3 { display:flex; gap:8px; align-items:flex-end; }
    .bar { height: 18px; border-radius:6px; }
    .bar.home { background:#19b562; }
    .bar.draw { background:#9ca3af; }
    .bar.away { background:#0ea5e9; }
    .stats { display:grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap:12px; }
    .stat { text-align:center; }
    .circle { width: 80px; height: 80px; border-radius: 50%; background: conic-gradient(#19b562 var(--p, 0deg), #1f2937 0); display:grid; place-items:center; margin:0 auto; }
    .circle span { font-weight:800; }
    .advice { font-weight:800; font-size: 18px; color: #04110a; background:#19b562; padding:12px; border-radius:12px; text-align:center; }

    .toolbar { display:flex; gap:8px; align-items:center; margin-bottom:8px; }
    .select, .input, .btn { background:#0b1220; border:1px solid #1f2937; color:#e6eef8; padding:6px 8px; border-radius:8px; }
    .btn.primary { background:#19b562; color:#04110a; border-color:#19b562; font-weight:800; cursor:pointer; }
    .weighted { color:#9fb3cd; font-size:12px; margin-left:6px; }

    @media (max-width:900px) { .layout { flex-direction:column; } .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  `],
  template: `
    <h1 style="font-weight:800; margin: 8px 0 12px;">Match Analysis</h1>

    <div class="toolbar">
      <label>League</label>
      <select class="select" [(ngModel)]="selectedLeagueId" (ngModelChange)="onLeagueChange()">
        <option [ngValue]="null">Select league...</option>
        <option *ngFor="let l of leagues" [ngValue]="l.leagueId">{{ l.leagueName }}</option>
      </select>
      <select class="select" [disabled]="!teamsForLeague.length" [(ngModel)]="homeTeamName">
        <option [ngValue]="''">Select home team...</option>
        <option *ngFor="let t of teamsForLeague" [ngValue]="t">{{ t }}</option>
      </select>
      <select class="select" [disabled]="!teamsForLeague.length" [(ngModel)]="awayTeamName">
        <option [ngValue]="''">Select away team...</option>
        <option *ngFor="let t of teamsForLeague" [ngValue]="t">{{ t }}</option>
      </select>
      <label *ngIf="selectedLeagueId">Season</label>
      <select class="select" *ngIf="selectedLeagueId" [ngModel]="seasonId" (ngModelChange)="onSeasonChange($event)">
        <option *ngFor="let s of seasons" [ngValue]="s.id">{{ s.name }}</option>
      </select>
      <button class="btn primary" (click)="analyze()">Analyze</button>
      <span style="flex:1"></span>
      <a routerLink="/fixtures" class="btn">Pick from Fixtures</a>
    </div>

    <!-- Head-to-head section placed directly below the top Analyze button -->
    <div *ngIf="analysis" class="card" style="margin-top:8px;">
      <div style="display:flex; align-items:baseline; justify-content:space-between; gap:8px;">
        <h3 style="font-weight:800; margin:0;">Head-to-head matches</h3>
        <div class="muted" *ngIf="(analysis?.h2hSummary?.matches?.length || 0) > 0 || (analysis?.headToHeadMatches?.length || 0) > 0">Across seasons</div>
      </div>
      <ng-container *ngIf="(analysis?.h2hSummary?.matches?.length || 0) > 0; else rawH2H">
        <div class="mt-2 space-y-2">
          <div *ngFor="let h2h of analysis?.h2hSummary?.matches; trackBy: trackH2HCompact" class="flex items-center justify-between border-b pb-1">
            <div class="text-sm text-gray-600 w-24">{{ h2h?.date }}</div>
            <div class="text-sm flex-1 text-right pr-2">{{ h2h?.home }}</div>
            <div class="text-sm w-8 text-center">{{ h2h?.score?.split('-')[0] }}</div>
            <div class="text-sm w-8 text-center">-</div>
            <div class="text-sm w-8 text-center">{{ h2h?.score?.split('-')[1] }}</div>
            <div class="text-sm flex-1 pl-2">{{ h2h?.away }}</div>
          </div>
        </div>
      </ng-container>
      <ng-template #rawH2H>
        <ng-container *ngIf="(analysis?.headToHeadMatches?.length || 0) > 0; else noH2HAnywhere">
          <div class="mt-2 space-y-2">
            <div *ngFor="let m of analysis?.headToHeadMatches; trackBy: trackH2HCompact" class="flex items-center justify-between border-b pb-1">
              <div class="text-sm text-gray-600 w-24">{{ m?.date }}</div>
              <div class="text-sm flex-1 text-right pr-2">{{ m?.homeTeam }}</div>
              <div class="text-sm w-8 text-center">{{ m?.homeGoals }}</div>
              <div class="text-sm w-8 text-center">-</div>
              <div class="text-sm w-8 text-center">{{ m?.awayGoals }}</div>
              <div class="text-sm flex-1 pl-2">{{ m?.awayTeam }}</div>
            </div>
          </div>
        </ng-container>
      </ng-template>
      <ng-template #noH2HAnywhere>
        <div class="muted">No prior head-to-head matches were found between {{ analysis?.homeTeam }} and {{ analysis?.awayTeam }} across available seasons.</div>
      </ng-template>
    </div>

    <div class="layout">
      <aside class="sidebar">
        <div style="font-weight:800; margin-bottom:8px;">Quick Search</div>
        <div style="display:flex; flex-direction:column; gap:8px;">
          <select class="select" [(ngModel)]="selectedLeagueId" (ngModelChange)="onLeagueChange()">
            <option [ngValue]="null">Select league...</option>
            <option *ngFor="let l of leagues" [ngValue]="l.leagueId">{{ l.leagueName }}</option>
          </select>
          <select class="select" [disabled]="!teamsForLeague.length" [(ngModel)]="homeTeamName">
            <option [ngValue]="''">Select home team...</option>
            <option *ngFor="let t of teamsForLeague" [ngValue]="t">{{ t }}</option>
          </select>
          <select class="select" [disabled]="!teamsForLeague.length" [(ngModel)]="awayTeamName">
            <option [ngValue]="''">Select away team...</option>
            <option *ngFor="let t of teamsForLeague" [ngValue]="t">{{ t }}</option>
          </select>
          <select class="select" *ngIf="selectedLeagueId" [ngModel]="seasonId" (ngModelChange)="onSeasonChange($event)">
            <option *ngFor="let s of seasons" [ngValue]="s.id">{{ s.name }}</option>
          </select>
          <button class="btn primary" (click)="analyze()">Analyze</button>
        </div>
      </aside>

      <section class="content">
        <div *ngIf="loading" class="card">Loading analysis...</div>
        <div *ngIf="error" class="card" style="background:#3f1d1d; border-color:#b91c1c; color:#fde1e1;">{{ error }}</div>
        <div *ngIf="showingCached && !loading" class="card" style="background:#0b1627; border-color:#234; color:#b8cee8;">Showing cached result; refreshing in background…</div>
        <div *ngIf="!loading && !analysis" class="card">Load a match to see analysis.</div>

        <div *ngIf="analysis">
          <!-- Header Card -->
          <div class="card header">
            <div style="font-size:20px; font-weight:800;">{{ analysis.homeTeam }} vs {{ analysis.awayTeam }} – {{ analysis.league }}</div>
          </div>

          <!-- W/D/W bar chart -->
          <div class="card">
            <div style="font-weight:700; margin-bottom:8px;">Win/Draw/Win (Weighted PPG Home/Away) <span class="weighted" [title]="weightTip">⚖</span></div>
            <div class="w3">
              <div class="bar home" [style.width.%]="safePct(analysis?.winProbabilities?.homeWin)"></div>
              <div class="bar draw" [style.width.%]="safePct(analysis?.winProbabilities?.draw)"></div>
              <div class="bar away" [style.width.%]="safePct(analysis?.winProbabilities?.awayWin)"></div>
            </div>
            <div style="display:flex; justify-content:space-between; margin-top:6px; color:#9fb3cd;">
              <div [title]="weightTip">Home: {{ safePct(analysis?.winProbabilities?.homeWin) }}%</div>
              <div [title]="weightTip">Draw: {{ safePct(analysis?.winProbabilities?.draw) }}%</div>
              <div [title]="weightTip">Away: {{ safePct(analysis?.winProbabilities?.awayWin) }}%</div>
            </div>
          </div>

          <!-- Stats section -->
          <div class="card">
            <div class="stats">
              <div class="stat">
                <div class="muted">BTTS Probability (Weighted Form) <span class="weighted" [title]="weightTip">⚖</span></div>
                <div style="font-weight:800; font-size: 18px;" [title]="weightTip">{{ safePct(analysis?.bttsProbability) }}%</div>
              </div>
              <div class="stat">
                <div class="muted">Over 2.5 Probability (Weighted Form) <span class="weighted" [title]="weightTip">⚖</span></div>
                <div style="font-weight:800; font-size: 18px;" [title]="weightTip">{{ safePct(analysis?.over25Probability) }}%</div>
              </div>
              <div class="stat">
                <div class="muted">Expected Goals (Home, Weighted) <span class="weighted" [title]="weightTip">⚖</span></div>
                <div style="font-weight:800; font-size: 18px;" [title]="weightTip">{{ safeXg(analysis?.expectedGoals?.home) }}</div>
              </div>
              <div class="stat">
                <div class="muted">Expected Goals (Away, Weighted) <span class="weighted" [title]="weightTip">⚖</span></div>
                <div style="font-weight:800; font-size: 18px;" [title]="weightTip">{{ safeXg(analysis?.expectedGoals?.away) }}</div>
              </div>
            </div>
          </div>

          <!-- H2H and Form Blend Summary -->
          <div class="card">
            <div style="display:flex; justify-content:space-between; align-items:baseline; gap:8px; margin-bottom:8px;">
              <div style="font-weight:700;">Form vs H2H vs Blended</div>
              <div class="muted" *ngIf="analysis?.h2hSummary; else noH2H" [title]="h2hTooltip()">H2H window: last {{ analysis?.h2hSummary?.lastN }} matches</div>
              <ng-template #noH2H><span class="muted">No prior H2H found – using team form only</span></ng-template>
            </div>
            <div style="overflow:auto;">
              <table style="width:100%; border-collapse:collapse;">
                <thead>
                  <tr style="text-align:left; color:#9fb3cd;">
                    <th style="padding:6px; border-bottom:1px solid #1f2937;">Metric</th>
                    <th style="padding:6px; border-bottom:1px solid #1f2937;">Form</th>
                    <th style="padding:6px; border-bottom:1px solid #1f2937;">H2H</th>
                    <th style="padding:6px; border-bottom:1px solid #1f2937;">Blended <span class="weighted" [title]="weightTip">⚖</span></th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td style="padding:6px;">Home Win %</td>
                    <td style="padding:6px;">{{ safePct(analysis?.formSummary?.homeWin) }}%</td>
                    <td style="padding:6px;">{{ analysis?.h2hSummary ? safePct(h2hHomeFromPpg(analysis?.h2hSummary)) + '%' : '—' }}</td>
                    <td style="padding:6px;" [title]="weightTip">{{ safePct(analysis?.winProbabilities?.homeWin) }}%</td>
                  </tr>
                  <tr>
                    <td style="padding:6px;">Draw %</td>
                    <td style="padding:6px;">{{ safePct(analysis?.formSummary?.draw) }}%</td>
                    <td style="padding:6px;">{{ analysis?.h2hSummary ? safePct(h2hDrawFromPpg(analysis?.h2hSummary)) + '%' : '—' }}</td>
                    <td style="padding:6px;" [title]="weightTip">{{ safePct(analysis?.winProbabilities?.draw) }}%</td>
                  </tr>
                  <tr>
                    <td style="padding:6px;">Away Win %</td>
                    <td style="padding:6px;">{{ safePct(analysis?.formSummary?.awayWin) }}%</td>
                    <td style="padding:6px;">{{ analysis?.h2hSummary ? safePct(h2hAwayFromPpg(analysis?.h2hSummary)) + '%' : '—' }}</td>
                    <td style="padding:6px;" [title]="weightTip">{{ safePct(analysis?.winProbabilities?.awayWin) }}%</td>
                  </tr>
                  <tr>
                    <td style="padding:6px;">BTTS %</td>
                    <td style="padding:6px;">{{ safePct(analysis?.formSummary?.btts) }}%</td>
                    <td style="padding:6px;">{{ analysis?.h2hSummary ? safePct(analysis?.h2hSummary?.bttsPct) + '%' : '—' }}</td>
                    <td style="padding:6px;" [title]="weightTip">{{ safePct(analysis?.bttsProbability) }}%</td>
                  </tr>
                  <tr>
                    <td style="padding:6px;">Over 2.5 %</td>
                    <td style="padding:6px;">{{ safePct(analysis?.formSummary?.over25) }}%</td>
                    <td style="padding:6px;">{{ analysis?.h2hSummary ? safePct(analysis?.h2hSummary?.over25Pct) + '%' : '—' }}</td>
                    <td style="padding:6px;" [title]="weightTip">{{ safePct(analysis?.over25Probability) }}%</td>
                  </tr>
                  <tr>
                    <td style="padding:6px;">Over 3.5 %</td>
                    <td style="padding:6px;">—</td>
                    <td style="padding:6px;">—</td>
                    <td style="padding:6px;" [title]="weightTip">{{ safePct(analysis?.over35Percentage) }}%</td>
                  </tr>
                  <tr *ngIf="analysis?.h2hSummary">
                    <td style="padding:6px;">H2H PPG (Home, Away)</td>
                    <td style="padding:6px;">—</td>
                    <td style="padding:6px;">{{ safeXg(analysis?.h2hSummary?.ppgHome) }} / {{ safeXg(analysis?.h2hSummary?.ppgAway) }}</td>
                    <td style="padding:6px;">—</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- H2H last-N compact list -->
            <div *ngIf="analysis?.h2hSummary?.matches?.length" style="margin-top:10px;">
              <div class="muted" style="margin-bottom:6px;">Head-to-head recent matches</div>
              <div style="display:flex; flex-direction:column; gap:6px;">
                <div *ngFor="let m of analysis?.h2hSummary?.matches" style="display:flex; justify-content:space-between; border:1px solid #1f2937; border-radius:6px; padding:6px 8px;">
                  <div style="color:#9fb3cd; min-width:95px;">{{ m.date }}</div>
                  <div style="flex:1; text-align:center; font-weight:600;">{{ m.home }} <span style="color:#9fb3cd;">{{ m.score }}</span> {{ m.away }}</div>
                </div>
              </div>
            </div>
          </div>


          <!-- Confidence & Advice -->
          <div class="card" style="display:flex; align-items:center; gap:16px;">
            <div class="circle" [ngStyle]="{'--p': (safePct(analysis?.confidenceScore) * 3.6) + 'deg'}">
              <span>{{ safePct(analysis?.confidenceScore) }}%</span>
            </div>
            <div style="flex:1">
              <div class="muted">Confidence Score</div>
              <div style="font-size:18px; font-weight:800;">This analysis has {{ safePct(analysis?.confidenceScore) }}% confidence.</div>
            </div>
          </div>

          <div class="advice">{{ analysis?.advice || '—' }}</div>
        </div>
      </section>
    </div>
  `
})
export class MatchAnalysisComponent implements OnInit {
  trackH2H(index: number, item: any) { return index; }
  trackH2HCompact(index: number, item: any) { return index; }
  private fixturesApi = inject(FixturesService);
  private leaguesApi = inject(LeagueService);
  private analysisApi = inject(MatchAnalysisService);
  private seasonService = inject(SeasonService);
  private route = inject(ActivatedRoute);
  private matchApi = inject(MatchService);
  private teamApi = inject(TeamService);

  // Tooltip explaining weighting methodology
  weightTip: string = 'Weighted metrics use recency-weighted form and home/away context to adjust raw rates. See methodology for details.';

  leagues: LeagueWithUpcomingDTO[] = [];
  selectedLeagueId: number | null = null;
  seasons: Season[] = [];
  seasonId: number | null = null;
  homeTeamName = '';
  awayTeamName = '';
  teamsForLeague: string[] = [];

  // simple in-memory cache by key: leagueId|home|away|seasonId
  private cache = new Map<string, MatchAnalysisResponse>();
  showingCached = false;

  loading = false;
  error: string | null = null;
  analysis: MatchAnalysisResponse | null = null;

  ngOnInit(): void {
    this.showingCached = false;
    // Load all leagues (not only those with fixtures)
    this.leaguesApi.getAll().subscribe((ls: League[]) => {
      // Map to the DTO shape used by the templates
      this.leagues = (ls || [])
        .map(l => ({ leagueId: l.id, leagueName: l.name, leagueCountry: l.country, season: l.season, upcomingCount: 0 }))
        .sort((a, b) => a.leagueName.localeCompare(b.leagueName, undefined, { sensitivity: 'base' }));
    });

    this.route.queryParamMap.subscribe(p => {
      const leagueIdStr = p.get('leagueId');
      const home = p.get('homeTeamName');
      const away = p.get('awayTeamName');
      this.selectedLeagueId = leagueIdStr ? Number(leagueIdStr) : this.selectedLeagueId;
      if (this.selectedLeagueId) {
        this.onLeagueChange(false);
      }
      this.homeTeamName = home ?? this.homeTeamName;
      this.awayTeamName = away ?? this.awayTeamName;
      if (this.selectedLeagueId && this.homeTeamName && this.awayTeamName) {
        this.analyze();
      }
    });
  }

  onLeagueChange(resetTeams: boolean = true) {
    if (!this.selectedLeagueId) {
      this.teamsForLeague = [];
      this.seasons = [];
      this.seasonId = null;
      if (resetTeams) { this.homeTeamName = ''; this.awayTeamName = ''; }
      return;
    }
    // Clear selections on league change to avoid mismatch
    if (resetTeams) { this.homeTeamName = ''; this.awayTeamName = ''; }
    this.teamsForLeague = [];
    this.seasons = [];
    this.seasonId = null;

    // Load seasons first so we can query team lists from season-scoped endpoints
    this.seasonService.listSeasons(this.selectedLeagueId).subscribe(list => {
      this.seasons = (list ?? []).slice();
      // Sort seasons by startDate desc (nulls last), then by id desc as tiebreaker
      this.seasons.sort((a, b) => {
        const ad = a.startDate ? a.startDate : '';
        const bd = b.startDate ? b.startDate : '';
        if (ad === bd) return (b.id ?? 0) - (a.id ?? 0);
        // Newer date first
        return ad < bd ? 1 : -1;
      });
      const today = new Date().toISOString().slice(0,10);
      const current = this.seasons.find(x => (!x.startDate || x.startDate <= today) && (!x.endDate || x.endDate >= today));
      // Prefer current; otherwise choose the latest by startDate (due to sort above)
      this.seasonId = current ? current.id : (this.seasons[0]?.id ?? null);
      console.debug('[MatchAnalysis][onLeagueChange] leagueId=', this.selectedLeagueId, 'resolvedSeasonId=', this.seasonId, 'seasonName=', this.seasons.find(s=>s.id===this.seasonId)?.name);
      // After resolving season, load teams using multiple sources merged (fixtures + form guide + league table)
      this.loadTeamsForLeague();
      // If teams were pre-filled via query params, auto-run analysis now that seasonId is resolved
      if (this.homeTeamName && this.awayTeamName) {
        this.analyze();
      }
    }, _ => {
      // Even if seasons fail, attempt to load teams from fixtures
      this.loadTeamsForLeague();
      if (this.homeTeamName && this.awayTeamName) {
        this.analyze();
      }
    });
  }

  private loadTeamsForLeague(){
    if (!this.selectedLeagueId) { this.teamsForLeague = []; return; }
    console.debug('[MatchAnalysis][loadTeamsForLeague][REQ] leagueId=', this.selectedLeagueId, 'seasonId=', this.seasonId);
    // 1) Teams from fixtures (season-agnostic)
    const fixtures$ = this.fixturesApi.getLeagueFixtures(this.selectedLeagueId).pipe(
      map(res => {
        const set = new Set<string>();
        for (const f of res.fixtures) {
          if (f.homeTeam) set.add(f.homeTeam.trim());
          if (f.awayTeam) set.add(f.awayTeam.trim());
        }
        return Array.from(set);
      }),
      catchError(err => { console.debug('[MatchAnalysis][loadTeamsForLeague][fixtures][ERR]', err); return of([] as string[]); })
    );
    // 2) Teams from form guide rows (season-scoped)
    const guide$ = (this.seasonId ? this.leaguesApi.getFormGuide(this.selectedLeagueId, this.seasonId, 'all', 'overall').pipe(
      map(rows => (rows || []).map(r => r.teamName?.trim()).filter(Boolean) as string[]),
      catchError(err => { console.debug('[MatchAnalysis][loadTeamsForLeague][formGuide][ERR]', err); return of([] as string[]); })
    ) : of([] as string[]));
    // 3) Teams from league table (fallback if form guide not available)
    const table$ = (this.seasonId ? this.leaguesApi.getLeagueTable(this.selectedLeagueId, this.seasonId).pipe(
      map(rows => (rows || []).map(r => r.teamName?.trim()).filter(Boolean) as string[]),
      catchError(err => { console.debug('[MatchAnalysis][loadTeamsForLeague][table][ERR]', err); return of([] as string[]); })
    ) : of([] as string[]));

    forkJoin([fixtures$, guide$, table$]).subscribe(([fa, ga, ta]) => {
      const set = new Set<string>();
      for (const name of [...fa, ...ga, ...ta]) {
        if (name && name.trim()) set.add(name.trim());
      }
      this.teamsForLeague = Array.from(set).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
      console.debug('[MatchAnalysis][loadTeamsForLeague][RESP] counts', { fixtures: fa.length, formGuide: ga.length, table: ta.length, merged: this.teamsForLeague.length });
    });
  }

  onSeasonChange(id: number | null) {
    this.seasonId = id;
    console.debug('[MatchAnalysis][onSeasonChange] leagueId=', this.selectedLeagueId, 'seasonId=', this.seasonId, 'seasonName=', this.seasons.find(s=>s.id===this.seasonId)?.name);
    // Refresh team list when season changes to ensure form-guide/table sources align
    this.loadTeamsForLeague();
  }

  analyze() {
    this.error = null;
    if (!this.selectedLeagueId || !this.homeTeamName || !this.awayTeamName) {
      this.error = 'Please select a league and enter both team names.';
      return;
    }
    if (!this.seasonId) {
      return;
    }
    const key = `${this.selectedLeagueId}|${this.seasonId}|${this.homeTeamName.trim().toLowerCase()}|${this.awayTeamName.trim().toLowerCase()}`;

    // if cached, show immediately and mark as cached while we refresh
    const cached = this.cache.get(key);
    this.showingCached = !!cached;
    if (cached) {
      this.analysis = cached;
    }

    console.debug('[MatchAnalysis][analyze][REQ]', { leagueId: this.selectedLeagueId, seasonId: this.seasonId, home: this.homeTeamName, away: this.awayTeamName, usingCached: !!cached });
    console.time('[MatchAnalysis][analyze]');
    this.loading = true;
    this.analysisApi.analyze({
      leagueId: this.selectedLeagueId,
      seasonId: this.seasonId,
      homeTeamName: this.homeTeamName,
      awayTeamName: this.awayTeamName
    }).subscribe({
      next: (res) => {
        console.timeEnd('[MatchAnalysis][analyze]');
        const compactN = (res?.h2hSummary?.matches?.length || 0);
        const flatN = (res?.headToHeadMatches?.length || 0);
        console.debug('[MatchAnalysis][analyze][RESP]', { hasH2H: !!res?.h2hSummary, h2hN: res?.h2hSummary?.lastN, compactMatches: compactN, flatMatches: flatN, confidence: res?.confidenceScore });
        this.analysis = res;
        this.cache.set(key, res);
        this.loading = false;
        this.showingCached = false;

        // Client-side safety net: if backend didn't include H2H arrays, fetch by IDs+season
        if (compactN === 0 && flatN === 0 && this.selectedLeagueId && this.seasonId && this.homeTeamName && this.awayTeamName) {
          console.debug('[MatchAnalysis][fallback][H2H][start]', { leagueId: this.selectedLeagueId, seasonId: this.seasonId, home: this.homeTeamName, away: this.awayTeamName });
          forkJoin({
            homeId: this.teamApi.getScopedTeamId(this.homeTeamName, this.selectedLeagueId),
            awayId: this.teamApi.getScopedTeamId(this.awayTeamName, this.selectedLeagueId)
          }).subscribe(({ homeId, awayId }) => {
            if (!homeId || !awayId) {
              console.warn('[MatchAnalysis][fallback][H2H] Could not resolve team IDs', { homeId, awayId });
              return;
            }
            this.matchApi.getH2HMatchesByIds(homeId, awayId, this.seasonId!, 50).subscribe({
              next: (list) => {
                console.debug('[MatchAnalysis][fallback][H2H][RESP]', { count: list?.length || 0 });
                const mapped = (list || []).map(m => {
                  const parts = (m.result || '').split('-');
                  const hg = parts.length >= 2 ? Number(parts[0]) : NaN;
                  const ag = parts.length >= 2 ? Number(parts[1]) : NaN;
                  return {
                    date: m.date || '',
                    competition: m.season || '',
                    homeTeam: m.homeTeam || '',
                    awayTeam: m.awayTeam || '',
                    homeGoals: isNaN(hg) ? 0 : hg,
                    awayGoals: isNaN(ag) ? 0 : ag,
                  } as any;
                });
                if (this.analysis) {
                  // Merge into analysis for UI rendering
                  (this.analysis as any).headToHeadMatches = mapped;
                }
              },
              error: (e) => {
                console.warn('[MatchAnalysis][fallback][H2H][ERR]', e);
              }
            });
          });
        }
      },
      error: (err) => {
        console.timeEnd('[MatchAnalysis][analyze]');
        console.debug('[MatchAnalysis][analyze][ERR]', err);
        // keep showing cached if available; otherwise clear analysis
        if (!cached) {
          this.analysis = null;
        }
        this.error = err?.error?.message || 'Failed to fetch analysis.';
        this.loading = false;
        this.showingCached = !!cached;
      }
    });
  }

  // Helpers to ensure graceful fallback display

  // Build tooltip with the exact H2H matches used in the window
  h2hTooltip(): string {
    const hs = this.analysis?.h2hSummary;
    let list = hs?.matches || [];
    // Fallback: if compact matches are missing but detailed H2H exists, format from there
    if (!list.length) {
      const raw = this.analysis?.headToHeadMatches || [];
      if (raw.length) {
        const linesRaw = raw.map(m => `${m.date}: ${m.homeTeam} ${m.homeGoals}-${m.awayGoals} ${m.awayTeam}`);
        return `H2H matches used (last ${raw.length}):\n` + linesRaw.join('\n');
      }
      return 'No head-to-head matches found in the selected window.';
    }
    const lines = list.map(m => `${m.date}: ${m.home} ${m.score} ${m.away}`);
    return `H2H matches used (last ${hs?.lastN}):\n` + lines.join('\n');
  }
  safePct(val: any): number {
    const n = Number(val);
    if (isNaN(n)) return 0;
    if (!isFinite(n)) return 0;
    return Math.max(0, Math.min(100, Math.round(n)));
  }
  safeXg(val: any): string {
    const n = Number(val);
    if (isNaN(n) || !isFinite(n)) return '-';
    return n.toFixed(2);
  }

  // Derive H2H W/D/W % from PPG values (scaled to 75% band; draw is remainder)
  h2hHomeFromPpg(h2h: any): number {
    const hp = Number(h2h?.ppgHome);
    const ap = Number(h2h?.ppgAway);
    if (!isFinite(hp) || !isFinite(ap) || hp + ap <= 0) return 33;
    const hw = hp / (hp + ap);
    return this.safePct(hw * 100 * 0.75);
  }
  h2hAwayFromPpg(h2h: any): number {
    const hp = Number(h2h?.ppgHome);
    const ap = Number(h2h?.ppgAway);
    if (!isFinite(hp) || !isFinite(ap) || hp + ap <= 0) return 33;
    const aw = ap / (hp + ap);
    return this.safePct(aw * 100 * 0.75);
  }
  h2hDrawFromPpg(h2h: any): number {
    const h = this.h2hHomeFromPpg(h2h);
    const a = this.h2hAwayFromPpg(h2h);
    const d = 100 - (h + a);
    return this.safePct(d);
  }
}
