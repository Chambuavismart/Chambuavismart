import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LeagueService, LeagueTableEntryDTO, League, GroupedLeagueDTO } from '../services/league.service';
import { Season, SeasonService } from '../services/season.service';

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
          <optgroup *ngFor="let g of groupedLeagues" [label]="g.groupLabel">
            <option *ngFor="let opt of g.options" [ngValue]="opt.leagueId">{{ opt.label }}</option>
          </optgroup>
        </select>

        <label *ngIf="selectedLeagueId" for="seasonSelect" class="label">Season</label>
        <select *ngIf="selectedLeagueId" id="seasonSelect" class="select" [ngModel]="seasonId" (ngModelChange)="onSeasonChange($event)">
          <option *ngFor="let s of seasons" [ngValue]="s.id">{{ s.name }}</option>
        </select>

        <span class="muted" *ngIf="!selectedLeagueId">Choose a league to view standings.</span>
      </div>

      <div *ngIf="loading" class="muted theme-container" style="padding-left:0">Loading...</div>
      <div *ngIf="error" class="banner" style="max-width:1100px">{{ error }}</div>
      <div *ngIf="!loading && table?.length === 0 && selectedLeagueId" class="muted">No data.</div>

      <div class="panel" *ngIf="!loading && table?.length">
        <div style="font-weight:700; margin-bottom:8px; color:#9fb3cd;">
          League Table – {{ headerLeagueName() }}
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
  // Grouped leagues for dropdown (grouped by Country — League Name, options per season latest->oldest)
  groupedLeagues: GroupedLeagueDTO[] = [];
  private optionById: Record<number, { leagueName: string; country: string; season: string }> = {};
  selectedLeagueId: number | null = null;
  seasons: Season[] = [];
  seasonId: number | null = null;
  table: LeagueTableEntryDTO[] = [];
  loading = false;
  error: string | null = null;

  constructor() {
    // Load grouped leagues for dropdown and build quick lookup map
    this.leagueService.getGroupedLeaguesForUpload().subscribe({ next: groups => {
      this.groupedLeagues = groups || [];
      this.optionById = {};
      for (const g of this.groupedLeagues) {
        for (const opt of (g.options || [])) {
          if (opt && typeof opt.leagueId === 'number') {
            this.optionById[opt.leagueId] = { leagueName: g.leagueName, country: g.country, season: opt.season };
          }
        }
      }
    }, error: _ => this.error = 'Failed to load leagues' });

    // If route param exists, load immediately and resolve seasons
    this.route.paramMap.subscribe(params => {
      const idStr = params.get('leagueId');
      const id = idStr ? Number(idStr) : null;
      if (id) {
        this.selectedLeagueId = id;
        this.loadSeasonsAndFetch(id);
      }
    });
  }

  onLeagueChange(id: number | null) {
    this.selectedLeagueId = id;
    this.table = [];
    this.seasons = [];
    this.seasonId = null;
    if (id) {
      this.loadSeasonsAndFetch(id);
    }
  }

  onSeasonChange(id: number | null) {
    this.seasonId = id;
    if (this.selectedLeagueId && this.seasonId) {
      this.fetch(this.selectedLeagueId, this.seasonId);
    }
  }

  private loadSeasonsAndFetch(leagueId: number) {
    this.loading = true;
    this.seasonService.listSeasons(leagueId).subscribe({
      next: (seasons) => {
        this.seasons = seasons ?? [];
        const today = new Date().toISOString().slice(0,10);
        const current = this.seasons.find(x => (!x.startDate || x.startDate <= today) && (!x.endDate || x.endDate >= today));
        this.seasonId = current ? current.id : (this.seasons[0]?.id ?? null);
        if (this.seasonId) {
          this.fetch(leagueId, this.seasonId);
        } else {
          this.loading = false;
        }
      },
      error: _ => { this.seasons = []; this.seasonId = null; this.loading = false; this.error = 'Failed to load seasons'; }
    });
  }

  private fetch(leagueId: number, seasonId: number) {
    this.loading = true;
    this.error = null;
    console.debug('[LeagueTable] fetch params', { leagueId, seasonId });
    this.leagueService.getLeagueTable(leagueId, seasonId).subscribe({
      next: data => { this.table = data; this.loading = false; },
      error: err => { this.error = err?.error?.message || 'Failed to load league table'; this.loading = false; }
    });
  }

  headerLeagueName() {
    const lg = this.leagues.find(l => l.id === this.selectedLeagueId);
    if (lg) return `${lg.name} (${lg.country} ${lg.season})`;
    const info = (this.selectedLeagueId != null) ? this.optionById[this.selectedLeagueId] : null;
    return info ? `${info.leagueName} (${info.country} ${info.season})` : '';
  }
}
