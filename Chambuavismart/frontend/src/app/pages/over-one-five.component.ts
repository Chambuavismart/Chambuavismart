import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { TeamService, TeamSuggestion } from '../services/team.service';
import { FixturesSidebarComponent } from './fixtures-sidebar.component';
import { getApiBase } from '../services/api-base';
import { debounceTime, distinctUntilChanged, Subject, switchMap, forkJoin } from 'rxjs';
import { FixturesService, FixtureDTO, SearchFixtureItemDTO } from '../services/fixtures.service';

interface Over15StreakProfile {
  teamId?: number;
  teamName?: string;
  currentStreakLength?: number;
  currentStreakStartDate?: string | null;
  currentStreakEndDate?: string | null;
  longestStreakLength?: number;
  longestStreakStartDate?: string | null;
  longestStreakEndDate?: string | null;
  mostRecentLongestStartDate?: string | null;
  mostRecentLongestEndDate?: string | null;
  totalMatchesConsidered?: number;
  last20Over15Count?: number;
  last20Over15Pct?: number;
  last20Considered?: number;
}

type LastMatchBrief = import('../services/team.service').LastMatchBrief;

interface GoalsPillData {
  lastN: number;
  total: number;
  homeGoals: number;
  awayGoals: number;
  homePct: number;
  awayPct: number;
  balanceLabel: string;
  balanceClass: 'bal' | 'home' | 'away';
}

@Component({
  selector: 'app-over-one-five',
  standalone: true,
  imports: [CommonModule, FormsModule, FixturesSidebarComponent],
  template: `
    <section class="page">
      <div class="o15-right-sidebar" style="position: fixed; right: 0; top: 80px; width: 320px; bottom: 0; overflow: auto; background: #0f172a; border-left: 1px solid #334155; padding: 8px;">
        <app-fixtures-sidebar></app-fixtures-sidebar>
      </div>
      <h1>Over 1.5</h1>
      <p class="sub">Type at least 3 characters to search for a team. Select to view Over 1.5 goals streaks.</p>

      <div class="search-row">
        <div class="search">
          <input type="text" [(ngModel)]="query" (input)="onQueryChange(query)" placeholder="Search team..." />
          <ul class="suggest" *ngIf="suggestions.length && showSuggest">
            <li *ngFor="let s of suggestions" (click)="selectTeam(s)">
              <span class="name">{{ s.name }}</span>
              <span class="meta" *ngIf="s.leagueId">(league {{ s.leagueId }})</span>
            </li>
          </ul>
        </div>
        <div class="search">
          <input type="text" [(ngModel)]="fixtureQuery" (input)="onFixtureQueryChange(fixtureQuery)" placeholder="Search fixture by team..." />
          <ul class="suggest" *ngIf="fixtureSuggestions.length && showFixtureSuggest">
            <li *ngFor="let it of fixtureSuggestions" (click)="selectFixture(it)">
              <span class="name">{{ it.fixture.homeTeam }} <span class="vs">vs</span> {{ it.fixture.awayTeam }}</span>
              <span class="meta">{{ it.fixture.dateTime | date:'yyyy-MM-dd HH:mm' }}<ng-container *ngIf="it.leagueName"> • {{ it.leagueName }}</ng-container></span>
            </li>
          </ul>
        </div>
      </div>

      <div class="card" *ngIf="profile">
        <h2>
          <span class="h2-left">
            {{ profile.teamName || 'Selected Team' }}
            <span class="form-badges" *ngIf="last2Selected?.length">
              <span *ngFor="let r of last2Selected" class="badge" [class.w]="r.result==='W'" [class.d]="r.result==='D'" [class.l]="r.result==='L'" [title]="(r.season || '') + (r.season ? ' • ' : '') + (r.date || '') + ' • ' + r.opponent + ' • ' + r.scoreLine">{{ r.result }}</span>
            </span>
          </span>
          <span class="streaks-40" *ngIf="selectedTopStreaks?.topStreaks?.length">
            <span class="s-label">Top 2 (last 40):</span>
            <ng-container *ngFor="let s of selectedTopStreaks!.topStreaks; let i = index">
              <span class="s-item"><span class="s-val" [class.w]="s.outcome==='W'" [class.d]="s.outcome==='D'" [class.l]="s.outcome==='L'">{{ s.length }}{{ s.outcome }}</span> <span class="s-range">{{ s.startDate }} — {{ s.endDate }}</span></span><span *ngIf="i < (selectedTopStreaks!.topStreaks.length-1)">, </span>
            </ng-container>
          </span>
        </h2>
        <div class="mini-info" *ngIf="last2Selected?.length">
          <span *ngFor="let r of last2Selected; let i = index">{{ r.date }}<ng-container *ngIf="r.season"> • {{ r.season }}</ng-container><span *ngIf="i < (last2Selected.length-1)">, </span></span>
        </div>
        <div class="grid">
          <div class="stat">
            <div class="label">Current O1.5 streak</div>
            <div class="value">{{ profile.currentStreakLength ?? 0 }}</div>
            <div class="range" *ngIf="profile.currentStreakEndDate">{{ profile.currentStreakStartDate || '?' }} — {{ profile.currentStreakEndDate }}</div>
          </div>
          <div class="stat">
            <div class="label">Longest O1.5 streak (ever)</div>
            <div class="value">{{ profile.longestStreakLength ?? 0 }}</div>
            <div class="range" *ngIf="profile.longestStreakEndDate">{{ profile.longestStreakStartDate || '?' }} — {{ profile.longestStreakEndDate }}</div>
          </div>
          <div class="stat">
            <div class="label">Most recent longest O1.5 streak</div>
            <div class="value">{{ profile.longestStreakLength ?? 0 }}</div>
            <div class="range" *ngIf="profile.mostRecentLongestEndDate">{{ profile.mostRecentLongestStartDate || '?' }} — {{ profile.mostRecentLongestEndDate }}</div>
          </div>
        </div>
        <div class="foot">Total matches considered: {{ profile.totalMatchesConsidered ?? 0 }}</div>
        <div class="foot" *ngIf="profile.last20Considered !== undefined">
          Last 20 (most recent): Over 1.5 in {{ profile.last20Over15Count ?? 0 }} of {{ profile.last20Considered ?? 0 }} matches ({{ profile.last20Over15Pct ?? 0 }}%).
        </div>
      </div>

      <!-- Next fixture + goals balance -->
      <div class="next-and-pill" *ngIf="nextFixture">
        <div class="card next-card">
          <h3>Next Match</h3>
          <div class="fixture-row">
            <div class="teams">
              <span class="home" [class.me]="isHomeSelected">
                {{ nextFixture?.homeTeam }}
              </span>
              <span class="vs">vs</span>
              <span class="away" [class.me]="!isHomeSelected">
                {{ nextFixture?.awayTeam }}
              </span>
            </div>
            <div class="meta">
              <span class="dt">{{ nextFixture?.dateTime | date:'yyyy-MM-dd HH:mm' }}</span>
              <span class="league" *ngIf="nextFixture?.leagueName">• {{ nextFixture?.leagueName }}</span>
              <span class="status">• {{ nextFixture?.status }}</span>
            </div>
          </div>
        </div>

        <div class="pill" *ngIf="goalsPill">
          <div class="pill-head">Last {{ goalsPill.lastN }} matches (combined)</div>
          <div class="pill-total">Total goals: <b>{{ goalsPill.total }}</b></div>
          <div class="pill-sides">
            <div class="side home-side">
              <div class="side-name">Home: {{ nextFixture?.homeTeam }}</div>
              <div class="side-val">{{ goalsPill.homeGoals }} <span class="pct">({{ goalsPill.homePct }}%)</span></div>
            </div>
            <div class="side away-side">
              <div class="side-name">Away: {{ nextFixture?.awayTeam }}</div>
              <div class="side-val">{{ goalsPill.awayGoals }} <span class="pct">({{ goalsPill.awayPct }}%)</span></div>
            </div>
          </div>
          <div class="pill-balance" [ngClass]="goalsPill.balanceClass">Balance: {{ goalsPill.balanceLabel }}</div>
        </div>
      </div>

      <!-- Opponent profile -->
      <div class="card" *ngIf="opponentProfile">
        <h2>
          <span class="h2-left">
            {{ opponentProfile?.teamName || 'Opponent' }}
            <span class="form-badges" *ngIf="last2Opponent?.length">
              <span *ngFor="let r of last2Opponent" class="badge" [class.w]="r.result==='W'" [class.d]="r.result==='D'" [class.l]="r.result==='L'" [title]="(r.season || '') + (r.season ? ' • ' : '') + (r.date || '') + ' • ' + r.opponent + ' • ' + r.scoreLine">{{ r.result }}</span>
            </span>
          </span>
          <span class="streaks-40" *ngIf="opponentTopStreaks?.topStreaks?.length">
            <span class="s-label">Top 2 (last 40):</span>
            <ng-container *ngFor="let s of opponentTopStreaks!.topStreaks; let i = index">
              <span class="s-item"><span class="s-val" [class.w]="s.outcome==='W'" [class.d]="s.outcome==='D'" [class.l]="s.outcome==='L'">{{ s.length }}{{ s.outcome }}</span> <span class="s-range">{{ s.startDate }} — {{ s.endDate }}</span></span><span *ngIf="i < (opponentTopStreaks!.topStreaks.length-1)">, </span>
            </ng-container>
          </span>
        </h2>
        <div class="mini-info" *ngIf="last2Opponent?.length">
          <span *ngFor="let r of last2Opponent; let i = index">{{ r.date }}<ng-container *ngIf="r.season"> • {{ r.season }}</ng-container><span *ngIf="i < (last2Opponent.length-1)">, </span></span>
        </div>
        <div class="grid">
          <div class="stat">
            <div class="label">Current O1.5 streak</div>
            <div class="value">{{ opponentProfile?.currentStreakLength ?? 0 }}</div>
            <div class="range" *ngIf="opponentProfile?.currentStreakEndDate">{{ opponentProfile?.currentStreakStartDate || '?' }} — {{ opponentProfile?.currentStreakEndDate }}</div>
          </div>
          <div class="stat">
            <div class="label">Longest O1.5 streak (ever)</div>
            <div class="value">{{ opponentProfile?.longestStreakLength ?? 0 }}</div>
            <div class="range" *ngIf="opponentProfile?.longestStreakEndDate">{{ opponentProfile?.longestStreakStartDate || '?' }} — {{ opponentProfile?.longestStreakEndDate }}</div>
          </div>
          <div class="stat">
            <div class="label">Most recent longest O1.5 streak</div>
            <div class="value">{{ opponentProfile?.longestStreakLength ?? 0 }}</div>
            <div class="range" *ngIf="opponentProfile?.mostRecentLongestEndDate">{{ opponentProfile?.mostRecentLongestStartDate || '?' }} — {{ opponentProfile?.mostRecentLongestEndDate }}</div>
          </div>
        </div>
        <div class="foot">Total matches considered: {{ opponentProfile?.totalMatchesConsidered ?? 0 }}</div>
        <div class="foot" *ngIf="opponentProfile?.last20Considered !== undefined">
          Last 20 (most recent): Over 1.5 in {{ opponentProfile?.last20Over15Count ?? 0 }} of {{ opponentProfile?.last20Considered ?? 0 }} matches ({{ opponentProfile?.last20Over15Pct ?? 0 }}%).
        </div>
      </div>
    </section>
  `,
  styles: [`
    .page { padding: 16px; color: #e6eef8; }
    h1 { margin: 0 0 8px; }
    .sub { margin: 0 0 16px; color: #cfe0f4; }
    .search-row { display:flex; gap:12px; align-items:flex-start; flex-wrap: wrap; }
    .search { position: relative; flex:1 1 360px; min-width: 280px; }
    input { width: 100%; padding: 10px 12px; border-radius: 8px; border: 1px solid #3b4a61; background:#0f172a; color:#e6eef8; }
    input::placeholder { color: #cfe0f4; opacity: 0.8; }
    .suggest { list-style: none; padding: 6px; margin: 6px 0 0; background:#0f172a; border:1px solid #3b4a61; border-radius: 8px; max-height: 260px; overflow:auto; }
    .suggest li { padding: 8px 10px; cursor: pointer; display:flex; align-items:center; gap:8px; justify-content:space-between; }
    .suggest li:hover { background:#15243a; }
    .suggest .name { font-weight:600; color:#e6eef8; }
    .suggest .meta { color:#cfe0f4; font-size:12px; }
    .card { margin-top: 16px; background:#0f172a; border:1px solid #334155; border-radius: 8px; padding: 12px; }
    .grid { display:grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 12px; }
    .stat { background:#13233a; border:1px solid #3b4a61; border-radius: 8px; padding:10px; }
    .label { color:#cfe0f4; font-size:12px; }
    .value { font-size: 28px; font-weight: 800; margin: 4px 0; color:#ffffff; }
    .range { font-size: 12px; color:#e6eef8; }
    .foot { margin-top: 8px; font-size: 12px; color:#cfe0f4; }
    .fixture-row { display:flex; flex-direction:column; gap:6px; }
    .teams { font-weight: 700; font-size:16px; display:flex; align-items:center; gap:8px; }
    .teams .vs { color:#cfe0f4; font-weight: 500; }
    .teams .me { color:#19b562; }
    .meta { color:#cfe0f4; font-size:12px; }

    /* New: header right-streaks layout */
    h2 { display:flex; align-items:center; justify-content:space-between; gap: 8px; }
    .h2-left { display:inline-flex; align-items:center; flex-wrap: wrap; gap: 6px; }
    .streaks-40 { color:#e6eef8; font-size:12px; opacity:0.95; }
    .streaks-40 .s-label { color:#cfe0f4; margin-right: 4px; }
    .streaks-40 .s-item { white-space: nowrap; }
    .streaks-40 .s-range { color:#cfe0f4; }
    .streaks-40 .s-val { font-weight: 800; }
    .streaks-40 .s-val.w { color:#19b562; }
    .streaks-40 .s-val.d { color:#f59e0b; }
    .streaks-40 .s-val.l { color:#ef4444; }

    /* New: last-2 badges */
    .form-badges { display:inline-flex; gap:4px; margin-left: 8px; vertical-align: middle; }
    .badge { display:inline-flex; align-items:center; justify-content:center; width:18px; height:18px; border-radius:4px; font-weight:800; font-size:12px; color:#04110a; background:#334155; border:1px solid #3b4a61; }
    .badge.w { background:#19b562; border-color:#19b562; color:#04110a; }
    .badge.d { background:#f59e0b; border-color:#f59e0b; color:#04110a; }
    .badge.l { background:#ef4444; border-color:#ef4444; color:#04110a; }
    .mini-info { margin-top:4px; font-size:12px; color:#cfe0f4; }

    /* Layout for next fixture and pill */
    .next-and-pill { display:flex; gap:12px; align-items:stretch; }
    .next-card { flex: 1 1 auto; }
    .pill { margin-top:16px; background: linear-gradient(135deg, #13233a, #0f1a2d); border:1px solid #3b4a61; border-radius: 999px; padding:14px 16px; color:#e6eef8; min-width: 280px; max-width: 420px; align-self: flex-start; box-shadow: 0 6px 20px rgba(0,0,0,0.25), inset 0 0 0 1px rgba(255,255,255,0.02); }
    .pill-head { font-size:12px; color:#cfe0f4; opacity:0.9; margin-bottom:6px; }
    .pill-total { font-weight:800; margin-bottom:6px; }
    .pill-sides { display:flex; gap:16px; justify-content:space-between; }
    .side-name { font-size:12px; color:#cfe0f4; }
    .side-val { font-size:16px; font-weight:800; }
    .pct { color:#cfe0f4; font-weight:600; }
    .pill-balance { margin-top:6px; font-size:12px; font-weight:700; }
    .pill-balance.bal { color:#19b562; }
    .pill-balance.home { color:#60a5fa; }
    .pill-balance.away { color:#f59e0b; }

    @media (max-width: 900px) { 
      .grid { grid-template-columns: 1fr; }
      .next-and-pill { flex-direction: column; }
      .pill { align-self: stretch; border-radius: 16px; }
    }
  `]
})
export class OverOneFiveComponent implements OnInit {
  private teamApi = inject(TeamService);
  private fixturesApi = inject(FixturesService);
  private http = inject(HttpClient);
  private base = getApiBase();
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  query = '';
  private query$ = new Subject<string>();
  suggestions: TeamSuggestion[] = [];
  showSuggest = false;

  // Fixture search state
  fixtureQuery = '';
  private fixtureQuery$ = new Subject<string>();
  fixtureSuggestions: SearchFixtureItemDTO[] = [];
  showFixtureSuggest = false;

  profile: Over15StreakProfile | null = null;
  nextFixture: FixtureDTO | null = null;
  opponentProfile: Over15StreakProfile | null = null;
  private selectedTeamName: string | null = null;

  // Last two results caches
  last2Selected: LastMatchBrief[] = [];
  last2Opponent: LastMatchBrief[] = [];
  last2Home: LastMatchBrief[] = [];
  last2Away: LastMatchBrief[] = [];

  // Top-2 outcome streaks over last 40
  selectedTopStreaks: import('../services/team.service').TopOutcomeStreaksResponse | null = null;
  opponentTopStreaks: import('../services/team.service').TopOutcomeStreaksResponse | null = null;
  homeTopStreaks: import('../services/team.service').TopOutcomeStreaksResponse | null = null;
  awayTopStreaks: import('../services/team.service').TopOutcomeStreaksResponse | null = null;

  // Goals balance pill state
  lastX = 20; // total matches combined (e.g., 10 per team)
  goalsPill: GoalsPillData | null = null;

  get isHomeSelected(): boolean {
    if (!this.nextFixture || !this.selectedTeamName) return false;
    return (this.nextFixture.homeTeam || '').toLowerCase() === this.selectedTeamName.toLowerCase();
  }

  constructor(){
    this.query$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => {
        const t = (q || '').trim();
        if (t.length < 3) { this.suggestions = []; this.showSuggest = false; return this.teamApi.searchTeams('zz__no'); }
        return this.teamApi.searchTeams(t);
      })
    ).subscribe(list => {
      this.suggestions = list || [];
      this.showSuggest = !!this.suggestions.length;
    });

    // Fixture search stream
    this.fixtureQuery$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => {
        const t = (q || '').trim();
        if (t.length < 3) { this.fixtureSuggestions = []; this.showFixtureSuggest = false; return this.fixturesApi.searchFixtures('zz__no'); }
        return this.fixturesApi.searchFixtures(t, 10, undefined, true);
      })
    ).subscribe(list => {
      this.fixtureSuggestions = list || [];
      this.showFixtureSuggest = !!this.fixtureSuggestions.length;
    });
  }

  onQueryChange(q: string){
    this.query$.next(q ?? '');
  }

  onFixtureQueryChange(q: string){
    this.fixtureQuery$.next(q ?? '');
  }

  selectTeam(s: TeamSuggestion){
    this.showSuggest = false;
    this.query = s.name;
    this.selectedTeamName = s.name;
    this.nextFixture = null;
    this.opponentProfile = null;

    // Reset last-2 caches
    this.last2Selected = [];
    this.last2Opponent = [];
    this.last2Home = [];
    this.last2Away = [];

    const params: any = s.id ? { teamId: String(s.id) } : { teamName: s.name };
    // 1) Load selected team's Over 1.5 profile
    this.http.get<Over15StreakProfile>(`${this.base}/streaks/over15`, { params }).subscribe(p => {
      this.profile = p;
    });
    // 1b) Load selected team's last two played matches
    this.teamApi.getLastPlayedListByName(s.name, 2).subscribe(list => {
      this.last2Selected = list || [];
    });
    // 1c) Load selected team's top-2 outcome streaks over last 40
    this.teamApi.getLast40TopStreaksByName(s.name).subscribe(resp => {
      this.selectedTopStreaks = resp;
    });

    // 2) Load next fixture for this team by name
    const teamName = (s.name || '').trim();
    if (!teamName) return;
    this.fixturesApi.getNextForTeam(teamName).subscribe({
      next: (fx) => {
        this.nextFixture = fx;
        this.goalsPill = null;
        if (!fx) { this.opponentProfile = null; return; }
        const home = (fx.homeTeam || '').trim();
        const away = (fx.awayTeam || '').trim();
        // Load home/away last-2 for fixture labels
        if (home) this.teamApi.getLastPlayedListByName(home, 2).subscribe(v => this.last2Home = v || []);
        if (away) this.teamApi.getLastPlayedListByName(away, 2).subscribe(v => this.last2Away = v || []);
        const meIsHome = home.toLowerCase() === teamName.toLowerCase();
        const opponent = meIsHome ? away : home;
        if (!opponent) { this.opponentProfile = null; return; }
        const oppParams: any = { teamName: opponent };
        this.http.get<Over15StreakProfile>(`${this.base}/streaks/over15`, { params: oppParams }).subscribe(op => {
          this.opponentProfile = op;
          // Also fetch opponent last-2
          this.teamApi.getLastPlayedListByName(opponent, 2).subscribe(list => this.last2Opponent = list || []);
        });
        // Fetch top-2 outcome streaks for home/away, and map to selected/opponent
        if (home) this.teamApi.getLast40TopStreaksByName(home).subscribe(r => { this.homeTopStreaks = r; this.selectedTopStreaks = meIsHome ? r : this.selectedTopStreaks; this.opponentTopStreaks = !meIsHome ? r : this.opponentTopStreaks; });
        if (away) this.teamApi.getLast40TopStreaksByName(away).subscribe(r => { this.awayTopStreaks = r; this.selectedTopStreaks = !meIsHome ? r : this.selectedTopStreaks; this.opponentTopStreaks = meIsHome ? r : this.opponentTopStreaks; });
        // Compute goals balance pill for this fixture
        if (home && away) this.computeGoalsPillForFixture(home, away);
      },
      error: (_err) => {
        // No upcoming fixture: keep fixture/opponent sections hidden
        this.nextFixture = null;
        this.opponentProfile = null;
      }
    });
  }

  selectFixture(item: SearchFixtureItemDTO){
    this.showFixtureSuggest = false;
    const fx = item?.fixture;
    if (!fx) return;
    this.nextFixture = fx;
    this.fixtureQuery = `${fx.homeTeam} vs ${fx.awayTeam}`;

    // Load profiles for both teams (home as main, away as opponent)
    const home = (fx.homeTeam || '').trim();
    const away = (fx.awayTeam || '').trim();
    this.selectedTeamName = home || null;

    // Reset last-2 for fixture view
    this.last2Home = []; this.last2Away = []; this.last2Selected = []; this.last2Opponent = [];
    this.goalsPill = null;

    const homeParams: any = { teamName: home };
    const awayParams: any = { teamName: away };

    this.http.get<Over15StreakProfile>(`${this.base}/streaks/over15`, { params: homeParams }).subscribe(p => { this.profile = p; });
    this.http.get<Over15StreakProfile>(`${this.base}/streaks/over15`, { params: awayParams }).subscribe(op => { this.opponentProfile = op; });

    // Fetch last two for labels and cards
    if (home) this.teamApi.getLastPlayedListByName(home, 2).subscribe(v => { this.last2Home = v || []; this.last2Selected = v || []; });
    if (away) this.teamApi.getLastPlayedListByName(away, 2).subscribe(v => { this.last2Away = v || []; this.last2Opponent = v || []; });

    // Fetch top-2 outcome streaks for both teams and map to headers
    if (home) this.teamApi.getLast40TopStreaksByName(home).subscribe(r => { this.homeTopStreaks = r; this.selectedTopStreaks = r; });
    if (away) this.teamApi.getLast40TopStreaksByName(away).subscribe(r => { this.awayTopStreaks = r; this.opponentTopStreaks = r; });

    // Compute goals balance pill for this fixture
    if (home && away) this.computeGoalsPillForFixture(home, away);
  }

  private parseTeamGoals(scoreLine: string): number {
    if (!scoreLine) return 0;
    const m = scoreLine.match(/(\d+)\s*[-:x]\s*(\d+)/);
    if (!m) return 0;
    const mine = parseInt(m[1], 10);
    return isNaN(mine) ? 0 : mine;
  }

  private computeGoalsPillForFixture(homeName: string, awayName: string) {
    const perTeamN = Math.max(1, Math.floor(this.lastX / 2)); // e.g., 10 each when lastX=20
    const home$ = this.teamApi.getLastPlayedListByName(homeName, perTeamN);
    const away$ = this.teamApi.getLastPlayedListByName(awayName, perTeamN);
    forkJoin([home$, away$]).subscribe(([homeList, awayList]) => {
      const usedHome = (homeList || []).slice(0, perTeamN);
      const usedAway = (awayList || []).slice(0, perTeamN);
      const homeGoals = usedHome.reduce((sum, m) => sum + this.parseTeamGoals(m.scoreLine), 0);
      const awayGoals = usedAway.reduce((sum, m) => sum + this.parseTeamGoals(m.scoreLine), 0);
      const totalMatches = usedHome.length + usedAway.length;
      const totalGoals = homeGoals + awayGoals;
      let homePct = 0, awayPct = 0;
      if (totalGoals > 0) {
        homePct = Math.round((homeGoals / totalGoals) * 100);
        awayPct = 100 - homePct;
      }
      // Balance labeling
      let balanceLabel = '';
      let balanceClass: 'bal' | 'home' | 'away' = 'bal';
      const delta = Math.abs(homePct - 50);
      if (delta <= 5) {
        balanceLabel = 'Fairly balanced (good for Over 1.5 stability)';
        balanceClass = 'bal';
      } else if (homePct >= 66) {
        balanceLabel = 'Heavily home-dependent (Over 1.5 riskier if home team struggles)';
        balanceClass = 'home';
      } else if (homePct <= 34) {
        balanceLabel = 'Heavily away-dependent (Over 1.5 riskier if away team struggles)';
        balanceClass = 'away';
      } else if (homePct > 50) {
        balanceLabel = 'Skewed Home';
        balanceClass = 'home';
      } else {
        balanceLabel = 'Skewed Away';
        balanceClass = 'away';
      }
      this.goalsPill = {
        lastN: totalMatches,
        total: totalGoals,
        homeGoals,
        awayGoals,
        homePct,
        awayPct,
        balanceLabel,
        balanceClass,
      };
    });
  }

  ngOnInit(): void {
    // If navigated with a fixture in query params, load it
    this.route.queryParamMap.subscribe(qp => {
      const home = (qp.get('home') || '').trim();
      const away = (qp.get('away') || '').trim();
      const dt = (qp.get('dt') || '').trim();
      if (home && away) {
        this.loadFixtureFromQuery(home, away, dt);
      }
    });
  }

  private loadFixtureFromQuery(home: string, away: string, dt?: string){
    const fx: FixtureDTO = {
      id: 0,
      round: '',
      dateTime: dt || new Date().toISOString(),
      homeTeam: home,
      awayTeam: away,
      homeScore: null,
      awayScore: null,
      status: 'UPCOMING'
    } as FixtureDTO;

    // Reuse selectFixture logic path
    this.nextFixture = fx;
    this.fixtureQuery = `${fx.homeTeam} vs ${fx.awayTeam}`;
    const homeName = (fx.homeTeam || '').trim();
    const awayName = (fx.awayTeam || '').trim();
    this.selectedTeamName = homeName || null;

    // Reset last-2 and pill
    this.last2Home = []; this.last2Away = []; this.last2Selected = []; this.last2Opponent = [];
    this.goalsPill = null;

    const homeParams: any = { teamName: homeName };
    const awayParams: any = { teamName: awayName };

    this.http.get<Over15StreakProfile>(`${this.base}/streaks/over15`, { params: homeParams }).subscribe(p => { this.profile = p; });
    this.http.get<Over15StreakProfile>(`${this.base}/streaks/over15`, { params: awayParams }).subscribe(op => { this.opponentProfile = op; });

    if (homeName) this.teamApi.getLastPlayedListByName(homeName, 2).subscribe(v => { this.last2Home = v || []; this.last2Selected = v || []; });
    if (awayName) this.teamApi.getLastPlayedListByName(awayName, 2).subscribe(v => { this.last2Away = v || []; this.last2Opponent = v || []; });

    if (homeName && awayName) this.computeGoalsPillForFixture(homeName, awayName);

    // Fetch top-2 outcome streaks for both teams and map to headers (for query-param initiated loads)
    if (homeName) this.teamApi.getLast40TopStreaksByName(homeName).subscribe(r => { this.homeTopStreaks = r; this.selectedTopStreaks = r; });
    if (awayName) this.teamApi.getLast40TopStreaksByName(awayName).subscribe(r => { this.awayTopStreaks = r; this.opponentTopStreaks = r; });
  }
}
