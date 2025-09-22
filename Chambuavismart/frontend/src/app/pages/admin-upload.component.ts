import { Component } from '@angular/core';

import { CommonModule } from '@angular/common';
import { AdminService, AdminLeagueSummaryDto } from '../services/admin.service';
import { OnInit, inject } from '@angular/core';

@Component({
  selector: 'app-admin-upload',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="container">
      <h1>Admin Panel — Leagues Overview</h1>
      <p class="hint">Review all leagues, grouped by country. Expand a country to see its leagues, seasons, and when the current season was last updated.</p>

      <ng-container *ngIf="list?.length; else loadingTpl">
        <div class="country-card" *ngFor="let g of groupedByCountryAndCategory(); trackBy: trackCountry">
          <button class="country-header" (click)="toggle(g.country)" [attr.aria-expanded]="isExpanded(g.country)">
            <span class="caret" [class.open]="isExpanded(g.country)">▸</span>
            <span class="country-name">{{ g.country }}</span>
            <span class="count">{{ g.leagues.length }} league(s)</span>
          </button>

          <div class="table-wrapper" *ngIf="isExpanded(g.country)">
            <div class="category-block" *ngFor="let cat of g.categories">
              <div class="category-header" [ngClass]="categoryClass(cat.name, g.country)">Category: <strong>{{ cat.name }}</strong> <span class="count">{{ cat.leagues.length }} item(s)</span></div>
              <table class="grid">
                <thead>
                  <tr>
                    <th style="width:32%">League</th>
                    <th>Seasons</th>
                    <th style="width:16%">Season</th>
                    <th style="width:24%">Last Updated (EAT)</th>
                    <th style="width:8%">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let l of cat.leagues">
                    <!-- seasons are displayed newest->oldest via getSeasonsSorted(l) -->
                    <td>{{ l.name }}</td>
                    <td>
                      <span class="badge" *ngFor="let s of getSeasonsSorted(l)" [title]="formatSeasonDates(s.startDate, s.endDate)">{{ s.name }}</span>
                    </td>
                    <td>
                      <span>{{ l.currentSeasonName || '—' }}</span>
                    </td>
                    <td>{{ formatInstantEAT(l.lastUpdatedAt) }}</td>
                    <td>
                      <button class="btn-danger" (click)="confirmDelete(l)" title="Delete league and all its matches">Delete</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </ng-container>

      <ng-template #loadingTpl>
        <div class="skeleton">\n          <div class="skeleton-line" style="width: 60%"></div>\n          <div class="skeleton-card"></div>\n          <div class="skeleton-card"></div>\n        </div>
      </ng-template>
    </section>
  `,
  styles: [`
    .container { max-width: 1160px; margin: 0 auto; padding: 18px; color: #e6eef8; }
    h1 { margin: 0 0 8px; font-size: 24px; font-weight: 800; letter-spacing: .2px; }
    .hint { color:#9fb6d4; margin-bottom: 14px; }

    .country-card { background:#0a111e; border:1px solid #1e2a3d; border-radius:14px; margin-bottom:14px; box-shadow: 0 6px 18px rgba(0,0,0,.3); }
    .country-header { width:100%; text-align:left; display:flex; align-items:center; gap:10px; padding:14px 16px; background:linear-gradient(180deg, rgba(19,29,48,.35), rgba(11,18,32,0)); border:0; color:#e6eef8; cursor:pointer; border-radius:14px 14px 0 0; }
    .country-header:hover { background:linear-gradient(180deg, rgba(23,36,61,.5), rgba(11,18,32,0)); }
    .caret { display:inline-block; transition: transform .15s ease; color:#93c5fd; }
    .caret.open { transform: rotate(90deg); }
    .country-name { font-weight:800; font-size:16px; }
    .count { margin-left:auto; color:#9fb6d4; font-size:12px; background:#0f213a; border:1px solid #223a5a; padding:2px 8px; border-radius:999px; }

    .table-wrapper { overflow:auto; border-top:1px solid #1f2937; }
    .category-block { padding: 8px 10px; }
    .category-header { color:#dbeafe; font-weight:800; margin: 8px 4px; display:flex; align-items:center; gap:8px; border-left: 8px solid transparent; padding-left: 14px; border-radius: 12px; letter-spacing:.2px; }
    .category-header .count { margin-left:auto; color:#9fb6d4; font-size:12px; background:#0f213a; border:1px solid #223a5a; padding:2px 8px; border-radius:999px; }
    /* Category color variants (strong accents for clearer differentiation) */
    .category-header.cat-color-0 { background: rgba(25,181,98,0.30); border-color: #19b562; box-shadow: 0 0 0 1px rgba(25,181,98,0.45); }
    .category-header.cat-color-1 { background: rgba(14,165,233,0.30); border-color: #0ea5e9; box-shadow: 0 0 0 1px rgba(14,165,233,0.45); }
    .category-header.cat-color-2 { background: rgba(168,85,247,0.30); border-color: #a855f7; box-shadow: 0 0 0 1px rgba(168,85,247,0.45); }
    .category-header.cat-color-3 { background: rgba(244,114,182,0.30); border-color: #f472b6; box-shadow: 0 0 0 1px rgba(244,114,182,0.45); }
    .category-header.cat-color-4 { background: rgba(245,158,11,0.30); border-color: #f59e0b; box-shadow: 0 0 0 1px rgba(245,158,11,0.45); }
    .category-header.cat-color-5 { background: rgba(239,68,68,0.30); border-color: #ef4444; box-shadow: 0 0 0 1px rgba(239,68,68,0.45); }
    .category-header.cat-color-6 { background: rgba(34,197,94,0.30); border-color: #22c55e; box-shadow: 0 0 0 1px rgba(34,197,94,0.45); }
    .category-header.cat-color-7 { background: rgba(99,102,241,0.30); border-color: #6366f1; box-shadow: 0 0 0 1px rgba(99,102,241,0.45); }

    table.grid { width:100%; border-collapse: collapse; }
    table.grid th, table.grid td { text-align:left; padding:12px 12px; border-bottom:1px solid #172235; vertical-align: top; }
    table.grid thead th { position: sticky; top: 0; background:#0b1220; color:#9fb6d4; font-weight:700; backdrop-filter: blur(2px); z-index: 1; }
    table.grid tbody tr:hover { background:#0f172a; }
    .badge { display:inline-block; background:#16233a; border:1px solid #2a3a55; color:#d7e6ff; padding:3px 10px; border-radius:999px; margin-right:6px; margin-bottom:6px; font-size:12px; }
    .tag { margin-left:8px; background:#0d2a45; border:1px solid #244c7a; color:#93c5fd; padding:2px 8px; border-radius:999px; font-size:11px; vertical-align:middle; }

    .btn-danger { background:#7f1d1d; color:#fee2e2; border:1px solid #991b1b; padding:6px 10px; border-radius:8px; cursor:pointer; }
    .btn-danger:hover { background:#991b1b; }

    /* Loading skeletons */
    .skeleton { display:block; }
    .skeleton-line { height: 14px; background: linear-gradient(90deg, #0e1626, #131f35, #0e1626); background-size: 200% 100%; animation: sh 1.2s infinite; border-radius: 6px; margin: 8px 0 14px; }
    .skeleton-card { height: 120px; border-radius: 12px; background: linear-gradient(90deg, #0e1626, #131f35, #0e1626); background-size: 200% 100%; animation: sh 1.2s infinite; margin-bottom: 12px; border:1px solid #1e2a3d; }
    @keyframes sh { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
  `]
})
export class AdminUploadComponent implements OnInit {
  private admin = inject(AdminService);
  list: AdminLeagueSummaryDto[] = [];
  // per-country deterministic color index for category names
  private categoryColorMap: Record<string, number> = {};
  expanded: Record<string, boolean> = {};

  ngOnInit(): void {
    this.load();
  }

  private load() {
    this.admin.getLeaguesSummary().subscribe({
      next: v => { this.list = v || []; },
      error: () => { this.list = []; }
    });
  }

  confirmDelete(l: AdminLeagueSummaryDto) {
    const ok = confirm(`Delete ${l.name} (${l.country}) and ALL its matches, fixtures and seasons? This cannot be undone.`);
    if (!ok) return;
    this.admin.deleteLeague(l.leagueId).subscribe({
      next: () => { this.load(); },
      error: (err) => { console.error('Failed to delete league', err); this.load(); }
    });
  }

  groupedByCountryAndCategory(): { country: string; leagues: AdminLeagueSummaryDto[]; categories: { name: string; leagues: AdminLeagueSummaryDto[] }[] }[] {
    const map = new Map<string, AdminLeagueSummaryDto[]>();
    for (const l of this.list || []) {
      const key = (l.country || 'Unknown').trim();
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(l);
    }
    const out = Array.from(map.entries()).map(([country, leagues]) => {
      const sorted = [...leagues].sort((a, b) => a.name.localeCompare(b.name));
      // Category defined as league name; group by that
      const catMap = new Map<string, AdminLeagueSummaryDto[]>();
      for (const l of sorted) {
        const cat = (l.name || 'Uncategorized').trim();
        if (!catMap.has(cat)) catMap.set(cat, []);
        catMap.get(cat)!.push(l);
      }
      const categories = Array.from(catMap.entries())
        .map(([name, ls]) => ({ name, leagues: ls }))
        .sort((a, b) => a.name.localeCompare(b.name));
      return { country, leagues: sorted, categories };
    });
    out.sort((a, b) => a.country.localeCompare(b.country));
    return out;
  }

  // Assign a stable color class for a category within a country
  categoryClass(categoryName: string, country: string): string {
    const key = country + '::' + categoryName;
    let idx = this.categoryColorMap[key];
    if (idx === undefined) {
      // hash to stable 0..7
      let h = 0;
      const s = key;
      for (let i = 0; i < s.length; i++) {
        h = (h * 31 + s.charCodeAt(i)) >>> 0;
      }
      idx = h % 8;
      this.categoryColorMap[key] = idx;
    }
    return 'cat-color-' + idx;
  }

  toggle(country: string) {
    const currentlyOpen = this.isExpanded(country);
    // Close all countries
    this.expanded = {};
    // Toggle the requested one: open it if it was previously closed; if it was open, leave all closed
    if (!currentlyOpen) {
      this.expanded[country] = true;
    }
  }
  isExpanded(country: string): boolean { return !!this.expanded[country]; }
  trackCountry = (_: number, g: { country: string }) => g.country;

  // Format to East Africa Time (Africa/Nairobi)
  formatInstantEAT(v?: string | null): string {
    if (!v) return '—';
    try {
      const d = new Date(v);
      if (Number.isNaN(d.getTime())) return v as string;
      const fmt = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Africa/Nairobi',
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
      });
      const parts: Record<string, string> = {} as any;
      for (const p of fmt.formatToParts(d)) { if (p.type !== 'literal') parts[p.type] = p.value; }
      const y = parts['year']; const m = parts['month']; const day = parts['day'];
      const hh = parts['hour']; const mm = parts['minute']; const ss = parts['second'];
      if (!y || !m || !day) return fmt.format(d) + ' EAT';
      return `${y}-${m}-${day} ${hh}:${mm}:${ss} EAT`;
    } catch {
      return v as string;
    }
  }

  // Seasons sorted newest -> oldest
  getSeasonsSorted(l: AdminLeagueSummaryDto) {
    const arr = [...(l.seasons || [])];
    arr.sort((a, b) => {
      // Prefer startDate desc
      const ad = a.startDate ? Date.parse(a.startDate) : NaN;
      const bd = b.startDate ? Date.parse(b.startDate) : NaN;
      if (!Number.isNaN(ad) || !Number.isNaN(bd)) {
        if (Number.isNaN(ad)) return 1; // a at end
        if (Number.isNaN(bd)) return -1;
        if (ad !== bd) return bd - ad; // desc
      }
      // Fallback to parse season name like 2025/2026 or 2025
      const as = this.parseSeasonName(a.name);
      const bs = this.parseSeasonName(b.name);
      if (as !== null || bs !== null) {
        if (as === null) return 1;
        if (bs === null) return -1;
        if (as !== bs) return bs - as; // desc
      }
      // Finally, id desc
      return (b.id ?? 0) - (a.id ?? 0);
    });
    return arr;
  }

  private parseSeasonName(name?: string | null): number | null {
    if (!name) return null;
    const s = String(name).trim();
    // Match "YYYY/YYYY" or single "YYYY"
    const m = s.match(/^(\d{4})(?:\/(\d{4}))?$/);
    if (!m) return null;
    const y1 = parseInt(m[1], 10);
    const y2 = m[2] ? parseInt(m[2], 10) : y1;
    if (Number.isNaN(y1) || Number.isNaN(y2)) return null;
    // Use the later year as the comparable value
    return Math.max(y1, y2);
  }

  formatSeasonDates(s?: string | null, e?: string | null): string {
    if (!s && !e) return '';
    const s2 = s ? new Date(s).toISOString().slice(0,10) : '';
    const e2 = e ? new Date(e).toISOString().slice(0,10) : '';
    if (s2 && e2) return `${s2} → ${e2}`;
    return s2 || e2;
  }
}
