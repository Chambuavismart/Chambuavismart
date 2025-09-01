import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LeagueService, LeagueTableEntryDTO, League } from '../services/league.service';

@Component({
  selector: 'app-league-table',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="p-4">
      <h1 class="text-2xl font-bold mb-4">League Table</h1>

      <div class="mb-4 flex items-center gap-2">
        <label for="leagueSelect" class="font-medium">Select League:</label>
        <select id="leagueSelect" class="border rounded px-2 py-1" [ngModel]="selectedLeagueId" (ngModelChange)="onLeagueChange($event)">
          <option [ngValue]="null">-- Choose a league --</option>
          <option *ngFor="let lg of leagues" [ngValue]="lg.id">{{ lg.name }} ({{ lg.country }} {{ lg.season }})</option>
        </select>
      </div>

      <div *ngIf="loading" class="text-gray-500">Loading...</div>
      <div *ngIf="error" class="text-red-600">{{ error }}</div>
      <div *ngIf="!loading && table?.length === 0 && selectedLeagueId" class="text-gray-500">No data.</div>
      <div class="overflow-x-auto" *ngIf="!loading && table?.length">
        <table class="min-w-full border border-gray-200 shadow-sm">
          <thead class="bg-gray-100">
            <tr>
              <th class="px-3 py-2 text-left">Pos</th>
              <th class="px-3 py-2 text-left">Team</th>
              <th class="px-3 py-2">MP</th>
              <th class="px-3 py-2">W</th>
              <th class="px-3 py-2">D</th>
              <th class="px-3 py-2">L</th>
              <th class="px-3 py-2">GF</th>
              <th class="px-3 py-2">GA</th>
              <th class="px-3 py-2">GD</th>
              <th class="px-3 py-2">Pts</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of table" class="border-t">
              <td class="px-3 py-2">{{ row.position }}</td>
              <td class="px-3 py-2 font-medium">{{ row.teamName }}</td>
              <td class="px-3 py-2 text-center">{{ row.mp }}</td>
              <td class="px-3 py-2 text-center">{{ row.w }}</td>
              <td class="px-3 py-2 text-center">{{ row.d }}</td>
              <td class="px-3 py-2 text-center">{{ row.l }}</td>
              <td class="px-3 py-2 text-center">{{ row.gf }}</td>
              <td class="px-3 py-2 text-center">{{ row.ga }}</td>
              <td class="px-3 py-2 text-center">{{ row.gd }}</td>
              <td class="px-3 py-2 text-center font-semibold">{{ row.pts }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class LeagueTableComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private leagueService = inject(LeagueService);

  leagues: League[] = [];
  selectedLeagueId: number | null = null;
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
        this.fetch(id);
      }
    });
  }

  onLeagueChange(id: number | null) {
    this.selectedLeagueId = id;
    this.table = [];
    if (id) {
      // Normalize URL for shareability
      this.router.navigate(['/league', id]);
      this.fetch(id);
    }
  }

  private fetch(leagueId: number) {
    this.loading = true;
    this.error = null;
    this.leagueService.getLeagueTable(leagueId).subscribe({
      next: data => { this.table = data; this.loading = false; },
      error: _ => { this.error = 'Failed to load league table'; this.loading = false; }
    });
  }
}
