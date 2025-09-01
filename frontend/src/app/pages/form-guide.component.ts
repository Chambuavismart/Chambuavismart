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
        <select class="select" [ngModel]="leagueId" (ngModelChange)="leagueId=$event">
          <option [ngValue]="null">-- Choose a league --</option>
          <option *ngFor="let lg of leagues" [ngValue]="lg.id">{{ lg.name }} ({{ lg.country }} {{ lg.season }})</option>
        </select>

        <label class="label">Limit</label>
        <select class="select" [(ngModel)]="limit">
          <option [ngValue]="3">3</option>
          <option [ngValue]="5">5</option>
          <option [ngValue]="6">6</option>
          <option [ngValue]="10">10</option>
        </select>

        <div class="tabs">
          <button class="tab" [class.active]="scope==='overall'" (click)="scope='overall'">Overall</button>
          <button class="tab" [class.active]="scope==='home'" (click)="scope='home'">Home</button>
          <button class="tab" [class.active]="scope==='away'" (click)="scope='away'">Away</button>
        </div>

        <button class="btn btn-primary" (click)="load()" [disabled]="!leagueId">Load League</button>
      </div>

      <div *ngIf="loading" class="muted">Loading...</div>
      <div *ngIf="error" class="banner">{{ error }}</div>

      <div class="panel" *ngIf="!loading && rows?.length">
        <div style="overflow-x:auto;">
          <table class="table rounded-table">
            <thead>
              <tr>
                <th>Team</th>
                <th class="center">W</th>
                <th class="center">D</th>
                <th class="center">L</th>
                <th class="center">GF</th>
                <th class="center">GA</th>
                <th class="center">GD</th>
                <th class="center">Pts</th>
                <th class="center">PPG</th>
                <th class="center">Last {{limit}}</th>
                <th class="center">BTTS %</th>
                <th class="center">Over 1.5 %</th>
                <th class="center">Over 2.5 %</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of rows">
                <td>
                  <div class="team">
                    <span class="badge">🏟️</span>
                    <span>{{ r.teamName }}</span>
                  </div>
                </td>
                <td class="center">{{r.w}}</td>
                <td class="center">{{r.d}}</td>
                <td class="center">{{r.l}}</td>
                <td class="center">{{r.gf}}</td>
                <td class="center">{{r.ga}}</td>
                <td class="center">{{r.gd}}</td>
                <td class="center">{{r.pts}}</td>
                <td class="center">{{r.ppg | number:'1.2-2'}}</td>
                <td class="center">
                  <span *ngFor="let s of r.lastResults; let i = index" class="pill" [ngClass]="{
                    'win': s==='W', 'draw': s==='D', 'loss': s==='L'
                  }" [title]="s" aria-hidden="true">{{s}}</span>
                </td>
                <td class="center"><span class="percent" [style.background]="percentBg(r.bttsPct)">{{r.bttsPct}}%</span></td>
                <td class="center"><span class="percent" [style.background]="percentBg(r.over15Pct)">{{r.over15Pct}}%</span></td>
                <td class="center"><span class="percent" [style.background]="percentBg(r.over25Pct)">{{r.over25Pct}}%</span></td>
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
  `]
})
export class FormGuideComponent {
  private api = inject(LeagueService);

  leagues: League[] = [];
  leagueId: number | null = null;
  limit = 6;
  scope: 'overall'|'home'|'away' = 'overall';

  rows: FormGuideRowDTO[] = [];
  loading = false;
  error: string | null = null;

  constructor(){
    this.api.getLeagues().subscribe({ next: d => this.leagues = d ?? [], error: _ => this.error = 'Failed to load leagues' });
  }

  load(){
    if (!this.leagueId) return;
    this.loading = true; this.error = null; this.rows = [];
    this.api.getFormGuide(this.leagueId, this.limit, this.scope).subscribe({
      next: d => { this.rows = d; this.loading = false; },
      error: _ => { this.error = 'Failed to load form guide'; this.loading = false; }
    })
  }

  percentBg(val: number){
    // green to red gradient cutoff
    if (val >= 66) return '#19b56244';
    if (val >= 40) return '#facc1544';
    return '#ef444444';
  }
}
