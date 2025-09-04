import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LeagueService, LeagueTableEntryDTO, League } from '../services/league.service';
import { SeasonService, Season } from '../services/season.service';

@Component({
  selector: 'app-league-table',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="theme-container">
      <h1 class="page-title">League Table</h1>

      <div class="panel" style="margin-bottom:12px; display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
        <label for="leagueSelect" class="label">League</label>
        <select id="leagueSelect" class="select" [ngModel]="selectedLeagueId" (ngModelChange)="onLeagueChange($event)">
          <option [ngValue]="null">-- Choose a league --</option>
          <option *ngFor="let lg of leagues" [ngValue]="lg.id">{{ lg.name }} ({{ lg.country }} {{ lg.season }})</option>
        </select>

        <label class="label">Season</label>
        <select class="select" [ngModel]="seasonId" (ngModelChange)="onSeasonChange($event)">
          <option [ngValue]="null">⚡ Combined (All Seasons)</option>
          <option *ngFor="let s of seasons" [ngValue]="s.id">{{ s.name }}</option>
        </select>

        <span class="muted" *ngIf="!selectedLeagueId">Choose a league to view standings.</span>
      </div>

      <div *ngIf="loading" class="muted theme-container" style="padding-left:0">Loading...</div>
      <div *ngIf="error" class="banner" style="max-width:1100px">{{ error }}</div>
      <div *ngIf="!loading && table?.length === 0 && selectedLeagueId" class="muted">No data.</div>

      <div class="panel" *ngIf="!loading && table?.length">
        <div style="font-weight:700; margin-bottom:8px; color:#9fb3cd;">
          League Table – {{ headerLeagueName() }} ({{ headerSeasonName() }})
        </div>
        <div style="overflow-x:auto;">
          <table class="table league-table">
            <thead>
              <tr>
                <th style="width:48px;" class="center">Pos</th>
                <th>Team</th>
                <th class="center" style="width:56px;">MP</th>
                <th class="center" style="width:44px;">W</th>
                <th class="center" style="width:44px;">D</th>
                <th class="center" style="width:44px;">L</th>
                <th class="center" style="width:44px;">GF</th>
                <th class="center" style="width:44px;">GA</th>
                <th class="center" style="width:44px;">GD</th>
                <th class="center" style="width:56px;">Pts</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of table">
                <td class="center">{{ row.position }}</td>
                <td><a href="javascript:void(0)">{{ row.teamName }}</a></td>
                <td class="center">{{ row.mp }}</td>
                <td class="center">{{ row.w }}</td>
                <td class="center">{{ row.d }}</td>
                <td class="center">{{ row.l }}</td>
                <td class="center">{{ row.gf }}</td>
                <td class="center">{{ row.ga }}</td>
                <td class="center">{{ row.gd }}</td>
                <td class="center" style="font-weight:700;">{{ row.pts }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [
    `:host { display:block; }
     .league-table tbody tr:hover { background: #fafafa; }
    `
  ]
})
export class LeagueTableComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private leagueService = inject(LeagueService);
  private seasonService = inject(SeasonService);

  leagues: League[] = [];
  seasons: Season[] = [];
  selectedLeagueId: number | null = null;
  seasonId: number | null = null;
  table: LeagueTableEntryDTO[] = [];
  loading = false;
  error: string | null = null;

  constructor() {
    // Load leagues for dropdown
    this.leagueService.getLeagues().subscribe({
      next: data => this.leagues = data ?? [],
      error: _ => this.error = 'Failed to load leagues'
    });

    // If route param exists, load immediately
    this.route.paramMap.subscribe(params => {
      const idStr = params.get('leagueId');
      const id = idStr ? Number(idStr) : null;
      if (id) {
        this.selectedLeagueId = id;
        this.loadSeasons(id);
        this.fetch(id);
      }
    });
  }

  onLeagueChange(id: number | null) {
    this.selectedLeagueId = id;
    this.table = [];
    this.seasons = [];
    this.seasonId = null; // default Combined
    if (id) {
      // Normalize URL for shareability
      this.router.navigate(['/league', id]);
      this.loadSeasons(id);
      this.fetch(id);
    }
  }

  onSeasonChange(seasonId: number | null) {
    this.seasonId = seasonId;
    if (this.selectedLeagueId) this.fetch(this.selectedLeagueId);
  }

  private loadSeasons(leagueId: number) {
    this.seasonService.listSeasons(leagueId).subscribe({
      next: ss => {
        this.seasons = ss ?? [];
        // Default to current season if available: choose the one whose dates include today, otherwise the most recent by startDate (already ordered desc backend)
        const today = new Date().toISOString().slice(0,10);
        const current = this.seasons.find(s => (!s.startDate || s.startDate <= today) && (!s.endDate || s.endDate >= today));
        // Put current season at the top if present
        if (current) {
          this.seasons = [current, ...this.seasons.filter(x => x.id !== current.id)];
        }
        this.seasonId = current ? current.id : (this.seasons[0]?.id ?? null);
        // Now fetch table for the chosen default season
        this.fetch(leagueId);
      },
      error: _ => { this.seasons = []; this.seasonId = null; this.fetch(leagueId); }
    });
  }

  private fetch(leagueId: number) {
    this.loading = true;
    this.error = null;
    this.leagueService.getLeagueTable(leagueId, this.seasonId).subscribe({
      next: data => { this.table = data; this.loading = false; },
      error: _ => { this.error = 'Failed to load league table'; this.loading = false; }
    });
  }

  headerLeagueName() {
    const lg = this.leagues.find(l => l.id === this.selectedLeagueId);
    return lg ? `${lg.name}` : '';
  }
  headerSeasonName() {
    if (!this.seasonId) return 'Combined (All Seasons)';
    const s = this.seasons.find(x => x.id === this.seasonId);
    return s?.name || 'Season';
  }
}
