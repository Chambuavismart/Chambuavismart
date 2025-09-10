import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchService } from '../services/match.service';

@Component({
  selector: 'app-played-matches-summary',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="container">
      <h1>Played Matches</h1>
      <div class="kpi-card">
        <div class="kpi-title">Total Matches Played</div>
        <div class="kpi-value" [class.loading]="loading">
          <ng-container *ngIf="!loading; else loadingTpl">{{ total | number }}</ng-container>
        </div>
        <ng-template #loadingTpl>Loading…</ng-template>
      </div>
    </section>
  `,
  styles: [`
    .container { max-width: 1000px; margin: 0 auto; padding: 16px; color: #e6eef8; }
    h1 { margin: 0 0 16px; font-size: 22px; font-weight: 700; }
    .kpi-card { background: #0b1220; border: 1px solid #1f2937; border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.25); }
    .kpi-title { color: #9fb6d4; font-weight: 600; margin-bottom: 8px; }
    .kpi-value { font-size: 40px; font-weight: 800; letter-spacing: .5px; color: #19b562; min-height: 48px; }
    .kpi-value.loading { color: #6b7280; }
    :host { display: block; }
    body { background: #0a0f1a; }
  `]
})
export class PlayedMatchesSummaryComponent implements OnInit {
  private matchService = inject(MatchService);
  total = 0;
  loading = true;

  ngOnInit(): void {
    this.matchService.getTotalPlayedMatches().subscribe({
      next: (v) => { this.total = v ?? 0; this.loading = false; },
      error: () => { this.total = 0; this.loading = false; }
    });
  }
}
