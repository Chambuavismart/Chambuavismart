import { Component, OnInit, inject } from '@angular/core';
import { NgIf, NgFor, AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FixturesService, LeagueWithUpcomingDTO } from '../services/fixtures.service';
import { MatchAnalysisService, MatchAnalysisResponse } from '../services/match-analysis.service';

@Component({
  selector: 'app-match-analysis',
  standalone: true,
  imports: [NgIf, NgFor, FormsModule, RouterLink],
  styles: [`
    :host { display:block; color:#e6eef8; }
    .layout { display:flex; gap: 16px; }
    .sidebar { width: 300px; background:#0b1220; border:1px solid #1f2937; border-radius:12px; padding:12px; }
    .content { flex: 1; display:flex; flex-direction:column; gap:12px; }
    .card { background:#0b1220; border:1px solid #1f2937; border-radius:16px; padding:12px; box-shadow: 0 2px 10px rgba(0,0,0,.25); }
    .header { display:flex; align-items:center; justify-content:space-between; }
    .w3 { display:flex; gap:8px; align-items:flex-end; }
    .bar { height: 18px; border-radius:6px; }
    .bar.home { background:#19b562; }
    .bar.draw { background:#9ca3af; }
    .bar.away { background:#0ea5e9; }
    .stats { display:grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap:12px; }
    .stat { text-align:center; }
    .circle { width: 80px; height: 80px; border-radius: 50%; background: conic-gradient(#19b562 var(--p), #1f2937 0); display:grid; place-items:center; margin:0 auto; }
    .circle span { font-weight:800; }
    .advice { font-weight:800; font-size: 18px; color: #04110a; background:#19b562; padding:12px; border-radius:12px; text-align:center; }

    .toolbar { display:flex; gap:8px; align-items:center; margin-bottom:8px; }
    .select, .input, .btn { background:#0b1220; border:1px solid #1f2937; color:#e6eef8; padding:6px 8px; border-radius:8px; }
    .btn.primary { background:#19b562; color:#04110a; border-color:#19b562; font-weight:800; cursor:pointer; }

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
      <button class="btn primary" (click)="analyze()">Analyze</button>
      <span style="flex:1"></span>
      <a routerLink="/fixtures" class="btn">Pick from Fixtures</a>
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
          <button class="btn primary" (click)="analyze()">Analyze</button>
        </div>
      </aside>

      <section class="content">
        <div *ngIf="loading" class="card">Loading analysis...</div>
        <div *ngIf="error" class="card" style="background:#3f1d1d; border-color:#b91c1c; color:#fde1e1;">{{ error }}</div>
        <div *ngIf="!loading && !analysis" class="card">Load a match to see analysis.</div>

        <div *ngIf="analysis">
          <!-- Header Card -->
          <div class="card header">
            <div style="font-size:20px; font-weight:800;">{{ analysis.homeTeam }} vs {{ analysis.awayTeam }} – {{ analysis.league }}</div>
          </div>

          <!-- W/D/W bar chart -->
          <div class="card">
            <div style="font-weight:700; margin-bottom:8px;">Win/Draw/Win</div>
            <div class="w3">
              <div class="bar home" [style.width.%]="analysis.winProbabilities.homeWin"></div>
              <div class="bar draw" [style.width.%]="analysis.winProbabilities.draw"></div>
              <div class="bar away" [style.width.%]="analysis.winProbabilities.awayWin"></div>
            </div>
            <div style="display:flex; justify-content:space-between; margin-top:6px; color:#9fb3cd;">
              <div>Home: {{ analysis.winProbabilities.homeWin }}%</div>
              <div>Draw: {{ analysis.winProbabilities.draw }}%</div>
              <div>Away: {{ analysis.winProbabilities.awayWin }}%</div>
            </div>
          </div>

          <!-- Stats section -->
          <div class="card">
            <div class="stats">
              <div class="stat">
                <div class="muted">BTTS Probability</div>
                <div style="font-weight:800; font-size: 18px;">{{ analysis.bttsProbability }}%</div>
              </div>
              <div class="stat">
                <div class="muted">Over 2.5 Probability</div>
                <div style="font-weight:800; font-size: 18px;">{{ analysis.over25Probability }}%</div>
              </div>
              <div class="stat">
                <div class="muted">Expected Goals (Home)</div>
                <div style="font-weight:800; font-size: 18px;">{{ analysis.expectedGoals.home }}</div>
              </div>
              <div class="stat">
                <div class="muted">Expected Goals (Away)</div>
                <div style="font-weight:800; font-size: 18px;">{{ analysis.expectedGoals.away }}</div>
              </div>
            </div>
          </div>

          <!-- Confidence & Advice -->
          <div class="card" style="display:flex; align-items:center; gap:16px;">
            <div class="circle" [style.--p]="(analysis.confidenceScore * 3.6) + 'deg'">
              <span>{{ analysis.confidenceScore }}%</span>
            </div>
            <div style="flex:1">
              <div class="muted">Confidence Score</div>
              <div style="font-size:18px; font-weight:800;">This analysis has {{ analysis.confidenceScore }}% confidence.</div>
            </div>
          </div>

          <div class="advice">{{ analysis.advice }}</div>
        </div>
      </section>
    </div>
  `
})
export class MatchAnalysisComponent implements OnInit {
  private fixturesApi = inject(FixturesService);
  private analysisApi = inject(MatchAnalysisService);
  private route = inject(ActivatedRoute);

  leagues: LeagueWithUpcomingDTO[] = [];
  selectedLeagueId: number | null = null;
  homeTeamName = '';
  awayTeamName = '';
  teamsForLeague: string[] = [];

  loading = false;
  error: string | null = null;
  analysis: MatchAnalysisResponse | null = null;

  ngOnInit(): void {
    this.fixturesApi.getLeagues().subscribe(ls => { this.leagues = ls; });

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
      if (resetTeams) { this.homeTeamName = ''; this.awayTeamName = ''; }
      return;
    }
    // Clear selections on league change to avoid mismatch
    if (resetTeams) { this.homeTeamName = ''; this.awayTeamName = ''; }
    this.teamsForLeague = [];
    this.fixturesApi.getLeagueFixtures(this.selectedLeagueId).subscribe(res => {
      const set = new Set<string>();
      for (const f of res.fixtures) {
        if (f.homeTeam) set.add(f.homeTeam.trim());
        if (f.awayTeam) set.add(f.awayTeam.trim());
      }
      this.teamsForLeague = Array.from(set).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
    });
  }

  analyze() {
    this.error = null;
    if (!this.selectedLeagueId || !this.homeTeamName || !this.awayTeamName) {
      this.error = 'Please select a league and enter both team names.';
      return;
    }
    this.loading = true;
    this.analysisApi.analyze({
      leagueId: this.selectedLeagueId,
      homeTeamName: this.homeTeamName,
      awayTeamName: this.awayTeamName
    }).subscribe({
      next: (res) => { this.analysis = res; this.loading = false; },
      error: (err) => { this.error = err?.error?.message || 'Failed to fetch analysis.'; this.loading = false; }
    });
  }
}
