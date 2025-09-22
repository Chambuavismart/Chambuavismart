import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { InsightsService, QuickInsightItem, QuickInsightsResponse } from '../services/insights.service';

@Component({
  selector: 'app-quick-insights',
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="container" style="padding: 1rem;">
    <h2>Quick Insights</h2>
    <p class="text-muted">High-interest fixtures within the next 48 hours</p>

    <div *ngIf="loading">Loading insights...</div>

    <!-- Section A: High-Interest -->
    <div *ngIf="!loading && (!highInterest || highInterest.length === 0)" class="text-muted">No fixtures met the high-interest thresholds.</div>

    <div class="list-group" *ngIf="!loading && highInterest && highInterest.length > 0">
      <div class="list-group-item list-group-item-action" *ngFor="let it of highInterest" (click)="openAnalysis(it)" style="cursor: pointer;">
        <div style="display:flex; justify-content: space-between; align-items: baseline;">
          <div>
            <div style="font-weight:600;">{{it.home}} vs {{it.away}}</div>
            <div class="text-muted">{{it.league}}</div>
          </div>
          <div class="text-end">
            <div>{{ formatKickoff(it.kickoff) }}</div>
          </div>
        </div>
        <div style="margin-top: .25rem;">
          <span class="badge bg-info text-dark" *ngFor="let r of (it.triggers || (it.trigger ? [it.trigger] : []))" style="margin-right:.25rem;">{{r}}</span>
        </div>
      </div>
    </div>

    <!-- Section B: Top Picks Fallback -->
    <div *ngIf="!loading && (!highInterest || highInterest.length === 0) && topPicks && topPicks.length > 0" style="margin-top:1rem;">
      <h4>Here are the Top Picks for the next 48 hours.</h4>
      <div class="list-group">
        <div class="list-group-item list-group-item-action" *ngFor="let it of topPicks" (click)="openAnalysis(it)" style="cursor: pointer;">
          <div style="display:flex; justify-content: space-between; align-items: baseline;">
            <div>
              <div style="font-weight:600;">{{it.home}} vs {{it.away}}</div>
              <div class="text-muted">{{it.league}}</div>
            </div>
            <div class="text-end">
              <div>{{ formatKickoff(it.kickoff) }}</div>
            </div>
          </div>
          <div style="margin-top: .25rem;">
            <span class="badge bg-secondary" *ngFor="let r of (it.triggers || (it.trigger ? [it.trigger] : []))" style="margin-right:.25rem;">{{r}}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty state if no fixtures at all -->
    <div *ngIf="!loading && (!highInterest || highInterest.length === 0) && (!topPicks || topPicks.length === 0)" class="text-muted" style="margin-top:1rem;">
      No fixtures scheduled in the next 48 hours.
    </div>
  </div>
  `
})
export class QuickInsightsComponent implements OnInit {
  private insights = inject(InsightsService);
  private router = inject(Router);

  highInterest: QuickInsightItem[] = [];
  topPicks: QuickInsightItem[] = [];
  loading = false;

  ngOnInit(): void {
    this.loading = true;
    this.insights.getQuickInsights().subscribe({
      next: (data: QuickInsightsResponse) => {
        this.highInterest = (data && Array.isArray(data.highInterest)) ? data.highInterest : [];
        this.topPicks = (data && Array.isArray(data.topPicks)) ? data.topPicks : [];
        this.loading = false;
      },
      error: _ => { this.highInterest = []; this.topPicks = []; this.loading = false; }
    });
  }

  formatKickoff(isoInstant?: string): string {
    if (!isoInstant) return '';
    const d = new Date(isoInstant);
    return d.toLocaleString();
  }

  openAnalysis(it: QuickInsightItem) {
    if (!it) return;
    this.router.navigate(['/match-analysis'], {
      queryParams: {
        leagueId: it.leagueId,
        homeTeamName: it.home,
        awayTeamName: it.away
      }
    });
  }
}
