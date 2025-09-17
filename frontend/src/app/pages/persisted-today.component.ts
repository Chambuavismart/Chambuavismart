import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe, NgFor, NgIf } from '@angular/common';
import { Router } from '@angular/router';
import { PersistedAnalysesService } from '../services/persisted-analyses.service';
import { MatchAnalysisResponse } from '../services/match-analysis.service';

@Component({
  selector: 'app-persisted-today',
  standalone: true,
  imports: [CommonModule, NgIf, NgFor, DatePipe],
  template: `
    <div class="panel" style="padding:12px;">
      <h2>Today's Persisted Analyses ({{ today | date:'yyyy-MM-dd' }})</h2>
      <div *ngIf="loading" class="muted">Loading...</div>
      <div *ngIf="!loading && analyses.length === 0" class="muted">
        No analyses persisted for today.
        <button (click)="goToFixtures()" class="btn secondary" style="margin-left:8px;">Analyze fixtures individually first</button>
      </div>
      <div *ngIf="!loading && analyses.length > 0" style="overflow:auto;">
        <table class="table" style="width:100%; border-collapse:collapse;">
          <thead>
            <tr>
              <th style="text-align:left; padding:6px; border-bottom:1px solid #e5e7eb;">Fixture</th>
              <th style="text-align:right; padding:6px; border-bottom:1px solid #e5e7eb;">Home%</th>
              <th style="text-align:right; padding:6px; border-bottom:1px solid #e5e7eb;">Draw%</th>
              <th style="text-align:right; padding:6px; border-bottom:1px solid #e5e7eb;">Away%</th>
              <th style="text-align:right; padding:6px; border-bottom:1px solid #e5e7eb;">BTTS%</th>
              <th style="text-align:right; padding:6px; border-bottom:1px solid #e5e7eb;">O2.5%</th>
              <th style="text-align:left; padding:6px; border-bottom:1px solid #e5e7eb;">Advice</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of analyses">
              <td style="padding:6px; border-bottom:1px solid #f1f5f9; font-weight:700;">{{ r.homeTeam }} vs {{ r.awayTeam }}<div style="font-weight:400; color:#64748b;">{{ r.league }}</div></td>
              <td style="padding:6px; border-bottom:1px solid #f1f5f9; text-align:right;">{{ pct(r.winProbabilities?.homeWin) }}</td>
              <td style="padding:6px; border-bottom:1px solid #f1f5f9; text-align:right;">{{ pct(r.winProbabilities?.draw) }}</td>
              <td style="padding:6px; border-bottom:1px solid #f1f5f9; text-align:right;">{{ pct(r.winProbabilities?.awayWin) }}</td>
              <td style="padding:6px; border-bottom:1px solid #f1f5f9; text-align:right;">{{ r.bttsProbability }}</td>
              <td style="padding:6px; border-bottom:1px solid #f1f5f9; text-align:right;">{{ r.over25Probability }}</td>
              <td style="padding:6px; border-bottom:1px solid #f1f5f9;">{{ r.advice }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div style="margin-top:10px;">
        <a [href]="pdfUrl" class="btn primary" download="persisted-analyses-{{ today | date:'yyyy-MM-dd' }}.pdf">Download Consolidated PDF</a>
      </div>
    </div>
  `
})
export class PersistedTodayComponent implements OnInit {
  private persistedService = inject(PersistedAnalysesService);
  private router = inject(Router);

  analyses: MatchAnalysisResponse[] = [];
  pdfUrl: string = '';
  today = new Date();
  loading = false;

  ngOnInit(): void {
    this.pdfUrl = this.persistedService.getPersistedTodayPdfUrl();
    this.loading = true;
    this.persistedService.getPersistedToday().subscribe({
      next: (analyses) => { this.analyses = analyses || []; this.loading = false; },
      error: () => { this.analyses = []; this.loading = false; }
    });
  }

  goToFixtures(): void {
    const iso = this.today.toISOString().split('T')[0];
    this.router.navigate(['/fixtures'], { queryParams: { date: iso } });
  }

  pct(v: number | undefined | null): string {
    if (v == null) return '-';
    const x = (v <= 1 ? v * 100 : v);
    return Math.round(x).toString();
  }
}
