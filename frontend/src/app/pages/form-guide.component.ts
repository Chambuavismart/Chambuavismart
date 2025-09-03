import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { League, LeagueService, FormGuideRowDTO } from '../services/league.service';

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
                <th class="center sortable" (click)="onSort('ppg')">PPG {{sortIcon('ppg')}}</th>
                <th class="center">{{ lastHeader() }}</th>
                <th class="center sortable" (click)="onSort('bttsPct')">BTTS % {{sortIcon('bttsPct')}}</th>
                <th class="center sortable" (click)="onSort('over15Pct')">Over 1.5 % {{sortIcon('over15Pct')}}</th>
                <th class="center sortable" (click)="onSort('over25Pct')">Over 2.5 % {{sortIcon('over25Pct')}}</th>
                <th class="center sortable" (click)="onSort('over35Pct')">Over 3.5 % {{sortIcon('over35Pct')}}</th>
              </tr>
            </thead>
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
                <td class="center">{{r.ppg | number:'1.2-2'}}</td>
                <td class="center">
                  <span *ngFor="let s of displayResults(r); let i = index" class="pill" [ngClass]="{
                    'win': s==='W', 'draw': s==='D', 'loss': s==='L'
                  }" [title]="s" aria-hidden="true">{{s}}</span>
                  <ng-container *ngIf="hasMore(r)">
                    <span class="pill" title="More results">+</span>
                  </ng-container>
                </td>
                <td class="center"><span class="percent" [style.background]="percentBg(r.bttsPct)">{{r.bttsPct}}%</span></td>
                <td class="center"><span class="percent" [style.background]="percentBg(r.over15Pct)">{{r.over15Pct}}%</span></td>
                <td class="center"><span class="percent" [style.background]="percentBg(r.over25Pct)">{{r.over25Pct}}%</span></td>
                <td class="center"><span class="percent" [style.background]="percentBg(r.over35Pct)">{{r.over35Pct}}%</span></td>
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
  `]
})
export class FormGuideComponent {
  private api = inject(LeagueService);

  leagues: League[] = [];
  leagueId: number | null = null;
  limit: number | 'all' = 'all';
  scope: 'overall'|'home'|'away' = 'overall';
  userSetLimit = false;

  rows: FormGuideRowDTO[] = [];
  sortedRows: FormGuideRowDTO[] = [];
  sortCol: keyof FormGuideRowDTO | 'ppg' | 'teamName' | 'mp' = 'ppg';
  sortDir: 'asc'|'desc' = 'desc'; // default PPG desc

  loading = false;
  error: string | null = null;

  constructor(){
    this.api.getLeagues().subscribe({ next: d => this.leagues = d ?? [], error: _ => this.error = 'Failed to load leagues' });
  }

  private load(){
    if (!this.leagueId) return;
    this.loading = true; this.error = null; // keep current rows to avoid flicker
    this.api.getFormGuide(this.leagueId, this.limit, this.scope).subscribe({
      next: d => { this.rows = d; this.applySort(); this.loading = false; },
      error: _ => { this.error = 'Failed to load form guide'; this.loading = false; }
    })
  }

  onLeagueChange(val: number | null){
    this.leagueId = val;
    // Reset user override on league change so default view shows total MP
    this.userSetLimit = false;
    if (this.leagueId) this.load();
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

  private applySort(){
    const col = this.sortCol;
    const dir = this.sortDir === 'asc' ? 1 : -1;
    const getVal = (r: FormGuideRowDTO): any => {
      switch(col){
        case 'teamName': return (r.teamName || '').toLowerCase();
        case 'mp': return this.matchesPlayed(r);
        case 'w': return r.w; case 'd': return r.d; case 'l': return r.l;
        case 'gf': return r.gf; case 'ga': return r.ga; case 'gd': return r.gd;
        case 'pts': return r.pts; case 'ppg': return r.ppg;
        case 'bttsPct': return r.bttsPct; case 'over15Pct': return r.over15Pct; case 'over25Pct': return r.over25Pct; case 'over35Pct': return r.over35Pct;
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
}
