import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { League, LeagueService, FormGuideRowDTO } from '../services/league.service';
import { Season, SeasonService } from '../services/season.service';
import { Subject, of } from 'rxjs';
import { switchMap, distinctUntilChanged, map, tap, catchError, finalize } from 'rxjs/operators';

@Component({
  selector: 'app-form-guide',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="theme-container">
      <h1 class="page-title">Form Guide</h1>

      <div class="panel" style="display:flex; flex-wrap:wrap; gap:8px; align-items:center;">
        <label class="label">League</label>
        <select class="select" [ngModel]="leagueId" (ngModelChange)="onLeagueChange($event)">
          <option [ngValue]="null">-- Choose a league --</option>
          <option *ngFor="let lg of leagues" [ngValue]="lg.id">{{ lg.name }} ({{ lg.country }} {{ lg.season }})</option>
        </select>

        <label class="label">Season</label>
        <select class="select" [ngModel]="seasonId" (ngModelChange)="onSeasonChange($event)">
          <option *ngFor="let s of seasons" [ngValue]="s.id">{{ s.name }}</option>
        </select>

        <label class="label">Limit</label>
        <select class="select" [ngModel]="limit" (ngModelChange)="onLimitChange($event)">
          <option [ngValue]="3">3</option>
          <option [ngValue]="5">5</option>
          <option [ngValue]="6">6</option>
          <option [ngValue]="10">10</option>
          <option [ngValue]="'all'">Entire League</option>
        </select>

        <div class="tabs">
          <button class="tab" [class.active]="scope==='overall'" (click)="setScope('overall')">Overall</button>
          <button class="tab" [class.active]="scope==='home'" (click)="setScope('home')">Home</button>
          <button class="tab" [class.active]="scope==='away'" (click)="setScope('away')">Away</button>
        </div>
      </div>

      <div *ngIf="loading" class="muted">Loading...</div>
      <div *ngIf="error" class="banner">{{ error }}</div>

      <div class="panel" *ngIf="!loading && rows?.length">
        <div style="font-weight:700; margin-bottom:8px; color:#9fb3cd;">
          Form Guide – {{ headerLeagueName() }} ({{ headerSeasonName() }})
        </div>
        <div *ngIf="weightedWarning" class="banner warning">{{ weightedWarning }}</div>
        <div style="overflow-x:auto;">
          <table class="table rounded-table">
            <thead>
              <tr>
                <th (click)="onSort('teamName')" class="sortable">Team {{sortIcon('teamName')}}</th>
                <th class="center sortable" (click)="onSort('mp')" title="MP shows all completed matches; last 10 results are displayed for readability">MP {{sortIcon('mp')}}</th>
                <th class="center sortable" (click)="onSort('w')">W {{sortIcon('w')}}</th>
                <th class="center sortable" (click)="onSort('d')">D {{sortIcon('d')}}</th>
                <th class="center sortable" (click)="onSort('l')">L {{sortIcon('l')}}</th>
                <th class="center sortable" (click)="onSort('gf')">GF {{sortIcon('gf')}}</th>
                <th class="center sortable" (click)="onSort('ga')">GA {{sortIcon('ga')}}</th>
                <th class="center sortable" (click)="onSort('gd')">GD {{sortIcon('gd')}}</th>
                <th class="center sortable" (click)="onSort('pts')">Pts {{sortIcon('pts')}}</th>
                <th class="center sortable" (click)="onSort('ppg')" [title]="weightTip">Weighted PPG <span class="weighted" [title]="weightTip">⚖</span> {{sortIcon('ppg')}}</th>
                <th class="center">{{ lastHeader() }}</th>
                <th class="center sortable" (click)="onSort('bttsPct')" [title]="weightTip">Weighted BTTS % <span class="weighted" [title]="weightTip">⚖</span> {{sortIcon('bttsPct')}}</th>
                <th class="center sortable" (click)="onSort('over15Pct')" [title]="weightTip">Weighted Over 1.5 % <span class="weighted" [title]="weightTip">⚖</span> {{sortIcon('over15Pct')}}</th>
                <th class="center sortable" (click)="onSort('over25Pct')" [title]="weightTip">Weighted Over 2.5 % <span class="weighted" [title]="weightTip">⚖</span> {{sortIcon('over25Pct')}}</th>
                <th class="center sortable" (click)="onSort('over35Pct')" [title]="weightTip">Weighted Over 3.5 % <span class="weighted" [title]="weightTip">⚖</span> {{sortIcon('over35Pct')}}</th>
                <th class="center" title="Home/Away recency-weighted PPG">PPG H/A</th>
                <th class="center" title="Home/Away recency-weighted BTTS%">BTTS% H/A</th>
                <th class="center" title="Home/Away recency-weighted Over 1.5%">O1.5% H/A</th>
                <th class="center" title="Home/Away recency-weighted Over 2.5%">O2.5% H/A</th>
                <th class="center" title="Home/Away recency-weighted Over 3.5%">O3.5% H/A</th>
              </tr>
            </thead>
            <ng-template #naTpl><span class="muted-na">N/A</span></ng-template>
            <tbody>
              <tr *ngFor="let r of sortedRows">
                <td>
                  <div class="team">
                    <span class="badge">🏟️</span>
                    <span>{{ r.teamName }}</span>
                  </div>
                </td>
                <td class="center">{{ matchesPlayed(r) }}</td>
                <td class="center">{{r.w}}</td>
                <td class="center">{{r.d}}</td>
                <td class="center">{{r.l}}</td>
                <td class="center">{{r.gf}}</td>
                <td class="center">{{r.ga}}</td>
                <td class="center">{{r.gd}}</td>
                <td class="center">{{r.pts}}</td>
                <td class="center" [title]="weightTip">{{ safePpg(r) }}</td>
                <td class="center">
                  <span *ngFor="let s of displayResults(r); let i = index" class="pill" [ngClass]="{
                    'win': s==='W', 'draw': s==='D', 'loss': s==='L'
                  }" [title]="s" aria-hidden="true">{{s}}</span>
                  <ng-container *ngIf="hasMore(r)">
                    <span class="pill" title="More results">+</span>
                  </ng-container>
                </td>
                <td class="center">
                  <ng-container *ngIf="isNumber(r.bttsPct); else naTpl"><span class="percent" [style.background]="percentBg(r.bttsPct)" [title]="weightTip">{{r.bttsPct}}%</span></ng-container>
                </td>
                <td class="center">
                  <ng-container *ngIf="isNumber(r.over15Pct); else naTpl"><span class="percent" [style.background]="percentBg(r.over15Pct)" [title]="weightTip">{{r.over15Pct}}%</span></ng-container>
                </td>
                <td class="center">
                  <ng-container *ngIf="isNumber(r.over25Pct); else naTpl"><span class="percent" [style.background]="percentBg(r.over25Pct)" [title]="weightTip">{{r.over25Pct}}%</span></ng-container>
                </td>
                <td class="center">
                  <ng-container *ngIf="isNumber(r.over35Pct); else naTpl"><span class="percent" [style.background]="percentBg(r.over35Pct)" [title]="weightTip">{{r.over35Pct}}%</span></ng-container>
                </td>
                <td class="center">
                  {{ splitPpg(r).home }} / {{ splitPpg(r).away }}
                </td>
                <td class="center">
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeBTTSPercent', 'weightedAwayBTTSPercent', 'bttsPct').homeRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeBTTSPercent', 'weightedAwayBTTSPercent', 'bttsPct').home !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeBTTSPercent', 'weightedAwayBTTSPercent', 'bttsPct').home }}
                  </span>
                  /
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeBTTSPercent', 'weightedAwayBTTSPercent', 'bttsPct').awayRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeBTTSPercent', 'weightedAwayBTTSPercent', 'bttsPct').away !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeBTTSPercent', 'weightedAwayBTTSPercent', 'bttsPct').away }}
                  </span>
                </td>
                <td class="center">
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeOver15Percent', 'weightedAwayOver15Percent', 'over15Pct').homeRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeOver15Percent', 'weightedAwayOver15Percent', 'over15Pct').home !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeOver15Percent', 'weightedAwayOver15Percent', 'over15Pct').home }}
                  </span>
                  /
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeOver15Percent', 'weightedAwayOver15Percent', 'over15Pct').awayRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeOver15Percent', 'weightedAwayOver15Percent', 'over15Pct').away !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeOver15Percent', 'weightedAwayOver15Percent', 'over15Pct').away }}
                  </span>
                </td>
                <td class="center">
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeOver25Percent', 'weightedAwayOver25Percent', 'over25Pct').homeRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeOver25Percent', 'weightedAwayOver25Percent', 'over25Pct').home !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeOver25Percent', 'weightedAwayOver25Percent', 'over25Pct').home }}
                  </span>
                  /
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeOver25Percent', 'weightedAwayOver25Percent', 'over25Pct').awayRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeOver25Percent', 'weightedAwayOver25Percent', 'over25Pct').away !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeOver25Percent', 'weightedAwayOver25Percent', 'over25Pct').away }}
                  </span>
                </td>
                <td class="center">
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeOver35Percent', 'weightedAwayOver35Percent', 'over35Pct').homeRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeOver35Percent', 'weightedAwayOver35Percent', 'over35Pct').home !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeOver35Percent', 'weightedAwayOver35Percent', 'over35Pct').home }}
                  </span>
                  /
                  <span class="percent" [style.background]="percentBg(splitPercent(r, 'weightedHomeOver35Percent', 'weightedAwayOver35Percent', 'over35Pct').awayRaw)" [title]="weightTip"
                        *ngIf="splitPercent(r, 'weightedHomeOver35Percent', 'weightedAwayOver35Percent', 'over35Pct').away !== 'N/A'; else naTpl">
                    {{ splitPercent(r, 'weightedHomeOver35Percent', 'weightedAwayOver35Percent', 'over35Pct').away }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .rounded-table { border-radius: 10px; overflow:hidden; }
    .team { display:flex; align-items:center; gap:8px; }
    .badge { font-size: 18px; }
    .pill { display:inline-block; width: 18px; height: 18px; line-height: 18px; text-align:center; margin: 0 2px; border-radius: 4px; font-weight: 700; color:#04110a; }
    .pill.win { background:#19b562; }
    .pill.draw { background:#facc15; }
    .pill.loss { background:#ef4444; color: #fff; }
    .tabs { display:flex; gap:6px; margin-left:auto; }
    .tab { padding:6px 10px; border-radius:8px; border:1px solid #1f2937; background:#0f172a; color:#cfe0f4; cursor:pointer; }
    .tab.active { background:#19b562; color:#04110a; border-color:#19b562; }
    .percent { display:inline-block; padding:2px 6px; border-radius:6px; color:#04110a; font-weight:700; }
    .sortable { cursor: pointer; user-select: none; }
    .banner.warning { background: #facc1533; color: #d97706; border: 1px solid #d97706; padding: 6px 10px; border-radius: 8px; margin-bottom: 8px; }
    .muted-na { color: #9fb3cd; font-style: italic; }
    .ha-ppg { font-weight:700; }
    .weighted { color:#6b7280; font-size:12px; margin-left:6px; }
  `]
})
export class FormGuideComponent implements OnInit {
  // Tooltip explaining weighting methodology (UI-only)
  weightTip: string = 'Weighted metrics use recency-weighting and home/away context. Percentages are normalized to 0–100.';
  private api = inject(LeagueService);
  private seasonApi = inject(SeasonService);
  private router: Router = inject(Router);
  private route: ActivatedRoute = inject(ActivatedRoute);

  leagues: League[] = [];
  leagueId: number | null = null;
  limit: number | 'all' = 'all';
  scope: 'overall'|'home'|'away' = 'overall';
  userSetLimit = false;

  rows: FormGuideRowDTO[] = [];
  seasons: Season[] = [];
  seasonId: number | null = null;
  sortedRows: FormGuideRowDTO[] = [];
  sortCol: keyof FormGuideRowDTO | 'ppg' | 'teamName' | 'mp' = 'ppg';
  sortDir: 'asc'|'desc' = 'desc'; // default PPG desc

  loading = false;
  error: string | null = null;
  weightedWarning: string | null = null;

  private loadParams$ = new Subject<{ leagueId: number; seasonId: number; limit: number | 'all'; scope: 'overall'|'home'|'away' }>();

  constructor(){
    this.api.getLeagues().subscribe({ next: d => this.leagues = d ?? [], error: _ => this.error = 'Failed to load leagues' });
    // hydrate from URL query params if provided
    this.route.queryParamMap.subscribe(qp => {
      const lid = qp.get('leagueId');
      const sid = qp.get('seasonId');
      const nextLeagueId = lid ? Number(lid) : this.leagueId;
      const nextSeasonId = sid !== null ? Number(sid) : this.seasonId;
      const leagueChanged = nextLeagueId !== this.leagueId;
      const seasonChanged = nextSeasonId !== this.seasonId;
      this.leagueId = nextLeagueId;
      this.seasonId = nextSeasonId;
      if (this.leagueId) {
        if (leagueChanged) {
          this.onLeagueChange(this.leagueId);
        } else if (seasonChanged) {
          // If only season changed via URL, apply it without triggering extra navigations
          if (this.seasonId != null) {
            this.onSeasonChange(this.seasonId);
          }
        }
      }
    });
  }

  private load(){
    if (!this.leagueId) return;
    if (this.seasonId == null) return; // wait until season is resolved
    this.loading = true; this.error = null; this.weightedWarning = null; // keep current rows to avoid flicker
    this.loadParams$.next({
      leagueId: this.leagueId!,
      seasonId: this.seasonId!,
      limit: this.limit,
      scope: this.scope
    });
  }

  ngOnInit(): void {
    // Set up a single subscription that reacts to param changes and makes exactly one HTTP call per change.
    this.loadParams$
      .pipe(
        // Build a stable key for distinctUntilChanged
        map(p => ({ ...p, key: `${p.leagueId}|${p.seasonId}|${p.limit}|${p.scope}` })), 
        distinctUntilChanged((a, b) => a.key === b.key),
        tap(p => console.debug('[FormGuide] load trigger', p)),
        switchMap(p => {
          return this.api.getFormGuide(p.leagueId, p.seasonId!, p.limit, p.scope)
            .pipe(
              tap(rows => console.debug('[FormGuide] response', { count: rows?.length ?? 0, params: p })),
              catchError(err => {
                if (err?.status === 400) {
                  this.error = 'No data available for this season yet';
                } else {
                  this.error = 'Failed to load form guide';
                }
                // keep existing rows to avoid flicker, but stop loading
                return of([] as FormGuideRowDTO[]);
              }),
              finalize(() => { this.loading = false; })
            );
        })
      )
      .subscribe(rows => {
        if (rows && rows.length >= 0) {
          this.rows = rows;
          this.evaluateWeightedFields(rows);
          this.applySort();
        }
      });
  }

  onLeagueChange(val: number | null){
    this.leagueId = val;
    // Reset user override on league change so default view shows total MP
    this.userSetLimit = false;
    this.seasonId = null;
    this.seasons = [];
    if (this.leagueId) {
      // Do not navigate yet with seasonId=null; wait until seasonId is resolved to avoid duplicate navigations.
      this.seasonApi.listSeasons(this.leagueId).subscribe({ next: s => {
        this.seasons = s ?? [];
        const today = new Date().toISOString().slice(0,10);
        const current = this.seasons.find(x => (!x.startDate || x.startDate <= today) && (!x.endDate || x.endDate >= today));
        // Put current season at the top if present
        if (current) {
          this.seasons = [current, ...this.seasons.filter(x => x.id !== current.id)];
        }
        this.seasonId = current ? current.id : (this.seasons[0]?.id ?? null);
        // Now that seasonId is set, update URL only if changed, then load.
        const qp = this.route.snapshot.queryParamMap;
        const currLeague = qp.get('leagueId');
        const currSeason = qp.get('seasonId');
        const nextLeague = String(this.leagueId);
        const nextSeason = this.seasonId != null ? String(this.seasonId) : null;
        if (currLeague !== nextLeague || currSeason !== nextSeason) {
          this.router.navigate([], { queryParams: { leagueId: this.leagueId, seasonId: this.seasonId }, queryParamsHandling: 'merge', replaceUrl: true });
        }
        this.load();
      }, error: _ => { this.seasons = []; this.error = null; this.load(); } });
    }
  }

  onLimitChange(val: number | 'all'){
    this.limit = val;
    // User explicitly set a limit; MP should reflect the limit (except 'all')
    this.userSetLimit = true;
    if (this.leagueId) this.load();
  }

  setScope(val: 'overall'|'home'|'away'){
    if (this.scope === val) return;
    this.scope = val;
    if (this.leagueId) this.load();
  }

  onSeasonChange(val: number){
    this.seasonId = val;
    // Persist in URL only if changed, and avoid piling up history entries
    const qp = this.route.snapshot.queryParamMap;
    const currLeague = qp.get('leagueId');
    const currSeason = qp.get('seasonId');
    const nextLeague = this.leagueId != null ? String(this.leagueId) : null;
    const nextSeason = this.seasonId != null ? String(this.seasonId) : null;
    if (currLeague !== nextLeague || currSeason !== nextSeason) {
      this.router.navigate([], { queryParams: { leagueId: this.leagueId, seasonId: this.seasonId }, queryParamsHandling: 'merge', replaceUrl: true });
    }
    if (this.leagueId) this.load();
  }

  onSort(col: keyof FormGuideRowDTO | 'ppg' | 'teamName' | 'mp'){
    if (this.sortCol === col) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortCol = col;
      // default numeric columns to desc, teamName to asc
      this.sortDir = (col === 'teamName') ? 'asc' : 'desc';
    }
    this.applySort();
  }

  sortIcon(col: string) {
    if (this.sortCol !== col) return '';
    return this.sortDir === 'asc' ? '▲' : '▼';
  }

  private evaluateWeightedFields(rows: FormGuideRowDTO[]) {
    // Determine if weighted fields are present; if any are missing, show a warning but still display raw W/D/L and Pts
    const missing = rows.some(r => !this.isNumber(r.ppg) || !this.isNumber(r.bttsPct) || !this.isNumber(r.over15Pct) || !this.isNumber(r.over25Pct) || !this.isNumber(r.over35Pct));
    this.weightedWarning = missing ? 'Some weighted statistics are unavailable for this selection. Showing N/A where data is missing.' : null;
  }

  private pickSplitNumber(r: FormGuideRowDTO, homeVal?: number, awayVal?: number, overallVal?: number, side: 'home'|'away' = 'home'){
    const hm = r.weightedHomeMatches ?? 0;
    const am = r.weightedAwayMatches ?? 0;
    if (side === 'home') {
      if (this.isNumber(homeVal) && hm > 0) return homeVal as number;
      if (this.isNumber(overallVal)) return overallVal as number;
      return NaN;
    } else {
      if (this.isNumber(awayVal) && am > 0) return awayVal as number;
      if (this.isNumber(overallVal)) return overallVal as number;
      return NaN;
    }
  }

  splitPpg(r: FormGuideRowDTO){
    const h = this.pickSplitNumber(r, r.weightedHomePPG, r.weightedAwayPPG, r.ppg, 'home');
    const a = this.pickSplitNumber(r, r.weightedHomePPG, r.weightedAwayPPG, r.ppg, 'away');
    return {
      home: this.isNumber(h) ? (h as number).toFixed(2) : 'N/A',
      away: this.isNumber(a) ? (a as number).toFixed(2) : 'N/A'
    };
  }

  splitPercent(
    r: FormGuideRowDTO,
    homeKey: keyof FormGuideRowDTO,
    awayKey: keyof FormGuideRowDTO,
    overallKey: keyof FormGuideRowDTO
  ){
    const hv = r[homeKey] as any as number | undefined;
    const av = r[awayKey] as any as number | undefined;
    const ov = r[overallKey] as any as number | undefined;
    const h = this.pickSplitNumber(r, hv, av, ov, 'home');
    const a = this.pickSplitNumber(r, hv, av, ov, 'away');
    return {
      home: this.isNumber(h) ? `${h}%` : 'N/A',
      away: this.isNumber(a) ? `${a}%` : 'N/A',
      homeRaw: this.isNumber(h) ? (h as number) : -1,
      awayRaw: this.isNumber(a) ? (a as number) : -1,
    };
  }

  private applySort(){
    const col = this.sortCol;
    const dir = this.sortDir === 'asc' ? 1 : -1;
    const getVal = (r: FormGuideRowDTO): any => {
      switch(col){
        case 'teamName': return (r.teamName || '').toLowerCase();
        case 'mp': return this.matchesPlayed(r);
        case 'w': return r.w; case 'd': return r.d; case 'l': return r.l;
        case 'gf': return r.gf; case 'ga': return r.ga; case 'gd': return r.gd;
        case 'pts': return r.pts; case 'ppg': return this.numVal(r.ppg);
        case 'bttsPct': return this.numVal(r.bttsPct); case 'over15Pct': return this.numVal(r.over15Pct); case 'over25Pct': return this.numVal(r.over25Pct); case 'over35Pct': return this.numVal(r.over35Pct);
        default: return 0;
      }
    };
    this.sortedRows = (this.rows || []).slice().sort((a,b) => {
      const va = getVal(a); const vb = getVal(b);
      if (va < vb) return -1 * dir;
      if (va > vb) return 1 * dir;
      // tie-breakers: pts desc, gd desc, gf desc, name asc
      if (a.pts !== b.pts) return (b.pts - a.pts);
      if (a.gd !== b.gd) return (b.gd - a.gd);
      if (a.gf !== b.gf) return (b.gf - a.gf);
      return a.teamName.localeCompare(b.teamName);
    });
  }

  displayResults(r: FormGuideRowDTO){
    const allSeq = (r.lastResults || []);
    if (this.limit === 'all') {
      // Cap to last 10 for readability even when entire league selected
      return allSeq.slice(0, 10);
    }
    const seq = allSeq.slice(0, this.limit);
    while (seq.length < this.limit) seq.push('•');
    return seq;
  }

  hasMore(r: FormGuideRowDTO){
    if (this.limit === 'all') {
      // indicate there are more than the 10 shown if applicable
      return (r.lastResults?.length || 0) > 10;
    }
    return (r.lastResults?.length || 0) > (typeof this.limit === 'number' ? this.limit : 0);
  }

  matchesPlayed(r: FormGuideRowDTO){
    // By default (before user sets a limit), show total matches played so far.
    // When the user sets a numeric limit, show the limited window size.
    // If user chooses 'all', continue to show total matches.
    const total = (r?.totalMp ?? ((r?.w || 0) + (r?.d || 0) + (r?.l || 0)));
    if (this.limit === 'all') return total;
    if (!this.userSetLimit) return total;
    return r?.mp ?? total;
  }

  lastHeader(){
    if (this.limit === 'all') return 'Last 10';
    return `Last ${this.limit}`;
  }

  percentBg(val: number){
    // green to red gradient cutoff
    if (val >= 66) return '#19b56244';
    if (val >= 40) return '#facc1544';
    return '#ef444444';
  }

  isNumber(v: any): v is number {
    return typeof v === 'number' && !isNaN(v);
  }

  safePpg(r: FormGuideRowDTO){
    return this.isNumber(r.ppg) ? (r.ppg as number).toFixed(2) : 'N/A';
  }

  private numVal(v: any){
    return this.isNumber(v) ? v as number : Number.NEGATIVE_INFINITY;
  }

  headerLeagueName(){
    const lg = this.leagues.find(l => l.id === this.leagueId);
    return lg ? `${lg.name}` : '';
  }
  headerSeasonName(){
    const s = this.seasons.find(x => x.id === this.seasonId!);
    return s?.name || 'Season';
  }
}
