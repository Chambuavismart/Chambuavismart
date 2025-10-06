import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { FixturesService, LeagueFixturesResponse, FixtureDTO } from '../services/fixtures.service';

interface FlatItem {
  leagueId: number;
  leagueName?: string;
  leagueCountry?: string;
  fixture: FixtureDTO;
}

@Component({
  selector: 'app-print-today',
  standalone: true,
  imports: [CommonModule, RouterLink, NgFor, NgIf, DatePipe],
  template: `
    <div class="print-layout">
      <header class="print-header no-print">
        <a routerLink="/" class="btn">← Back</a>
        <h1>Printable Fixtures</h1>
        <button class="btn" (click)="onPrint()">Print</button>
      </header>

      <section class="meta">
        <div>Time zone: Africa/Nairobi</div>
        <div>Date: {{ selectedIso() }}</div>
        <div>Total fixtures: {{ flatData().length }}</div>
      </section>

      <section *ngIf="loading()" class="muted">Loading fixtures…</section>
      <section *ngIf="!loading() && flatData().length===0" class="muted">No fixtures available for the selected date.</section>

      <section class="list" *ngIf="!loading() && flatData().length">
        <article class="fx pill" *ngFor="let item of flatData(); let i = index; trackBy: trackById">
          <div class="num" aria-hidden="true">{{ i + 1 }}</div>
          <div class="content">
            <div class="line1">
              <span class="time">{{ item.fixture.dateTime | date:'EEE, d MMM yyyy, HH:mm':'Africa/Nairobi' }}</span>
              <span class="sep">•</span>
              <span class="lg">{{ item.leagueCountry || '—' }} — {{ item.leagueName || 'League' }}</span>
            </div>
            <div class="tm" aria-label="Fixture teams">
              <span class="home">{{ item.fixture.homeTeam }}</span>
              <span class="vs">vs</span>
              <span class="away">{{ item.fixture.awayTeam }}</span>
            </div>
            <div class="line2">
              <span class="round">{{ item.fixture.round }}</span>
              <span class="sep">•</span>
              <span class="status">{{ item.fixture.status }}</span>
            </div>
          </div>
        </article>
      </section>
    </div>
  `,
  styles: [`
    .print-layout{ max-width:1000px; margin: 0 auto; padding:16px; color:#e6eef8; background:#0b1220; }
    .print-header{ display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:12px; }
    .btn{ background:#0f172a; color:#e6eef8; border:1px solid #334155; padding:6px 10px; border-radius:6px; text-decoration:none; cursor:pointer; }
    .meta{ display:flex; gap:16px; font-size:14px; color:#9fb3cd; margin-bottom:10px; }
    .muted{ color:#9fb3cd; }
    .list{ display:flex; flex-direction:column; gap:8px; }

    /* Pill layout */
    .fx{ display:grid; grid-template-columns: 48px 1fr; gap:10px; padding:10px 14px; border:1px solid #1f2937; border-radius:9999px; background:#0f172a; align-items:center; }
    .pill{ box-shadow: 0 2px 8px rgba(0,0,0,0.25); }
    .num{ width:32px; height:32px; line-height:32px; border-radius:50%; background:#19b562; color:#04110a; font-weight:800; text-align:center; justify-self:center; }
    .content{ display:flex; flex-direction:column; gap:4px; }
    .line1, .line2{ display:flex; align-items:center; gap:8px; color:#9fb3cd; font-size:14px; }
    .time{ color:#e6eef8; font-weight:600; }
    .tm{ display:flex; align-items:center; gap:8px; font-size:18px; }
    .vs{ opacity:.8; }
    .round, .status{ color:#9fb3cd; }
    .sep{ opacity:.6; }

    @media print {
      .no-print{ display:none !important; }
      .print-layout{ padding:0; background:#fff; color:#000; }
      .fx{ border:1px solid #000; background:#fff; box-shadow:none; }
      .num{ background:#000; color:#fff; }
    }
  `]
})
export class PrintTodayComponent {
  private api = inject(FixturesService);
  private route = inject(ActivatedRoute);

  loading = signal(true);
  data = signal<LeagueFixturesResponse[]>([]);
  // Selected ISO date for printing (defaults to Nairobi today)
  selectedIso = signal<string>(this.computeNairobiDateIso(new Date()));

  constructor(){
    // Watch for optional ?date=YYYY-MM-DD query param to override the selected date
    this.route.queryParamMap.subscribe(params => {
      const d = (params.get('date') || '').trim();
      const iso = this.normalizeIsoOrDefault(d);
      if (iso !== this.selectedIso()) {
        this.selectedIso.set(iso);
        this.fetch();
      } else {
        // If first load and no change, still fetch once
        if (this.data().length === 0) this.fetch();
      }
    });
  }

  onPrint(){
    window.print();
  }

  trackById = (_: number, it: FlatItem) => it.fixture.id;

  flatData = computed<FlatItem[]>(() => {
    const list: FlatItem[] = [];
    for (const grp of this.data()){
      for (const fx of grp.fixtures || []){
        list.push({ leagueId: grp.leagueId, leagueName: grp.leagueName, leagueCountry: grp.leagueCountry, fixture: fx });
      }
    }
    // Ensure chronological order across leagues
    list.sort((a,b) => new Date(a.fixture.dateTime).getTime() - new Date(b.fixture.dateTime).getTime());
    return list;
  });

  private fetch(){
    this.loading.set(true);
    const iso = this.selectedIso();
    this.api.getFixturesByDate(iso).subscribe({
      next: (res) => { this.data.set(res || []); this.loading.set(false); },
      error: () => { this.data.set([]); this.loading.set(false); }
    });
  }

  // Compute YYYY-MM-DD for Africa/Nairobi regardless of user local TZ
  private computeNairobiDateIso(d: Date): string {
    const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Nairobi', year: 'numeric', month: '2-digit', day: '2-digit' });
    return fmt.format(d);
  }

  private normalizeIsoOrDefault(d: string): string {
    const re = /^\d{4}-\d{2}-\d{2}$/;
    if (re.test(d)) return d;
    return this.computeNairobiDateIso(new Date());
  }
}
