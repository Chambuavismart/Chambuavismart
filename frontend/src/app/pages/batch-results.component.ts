import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CommonModule, NgFor, NgIf } from '@angular/common';
import { BatchAnalysisService, BatchStatus, FixtureAnalysisResult } from '../services/batch-analysis.service';

@Component({
  selector: 'app-batch-results',
  standalone: true,
  imports: [CommonModule, RouterModule, NgIf, NgFor],
  template: `
  <div class="container">
    <h2>Chambua-Leo</h2>
    <div *ngIf="status">
      <div>Fixtures for {{ status?.date }} EAT</div>
      <div class="progress">
        <div class="bar" [style.width.%]="progressPct"></div>
      </div>
      <div class="counters">Total: {{ status.total }} • Completed: {{ status.completed }} • Failed: {{ status.failed }} • In-Progress: {{ status.inProgress }} • ETA: {{ status.etaSeconds }}s</div>
      <div *ngIf="status.status==='COMPLETED'" class="downloads">
        <a [href]="consolidatedUrl" target="_blank" class="btn">Consolidated PDF</a>
        <a [href]="zipUrl" target="_blank" class="btn">ZIP Archive</a>
      </div>
    </div>

    <div *ngIf="results?.length">
      <div *ngFor="let group of groupedByLeague()" class="league">
        <details open>
          <summary>{{ group.league }}</summary>
          <div class="cards">
            <div *ngFor="let r of group.items" class="card" [class.error]="!r.success">
              <div class="title">{{ r.homeTeam }} vs {{ r.awayTeam }}</div>
              <div class="kickoff">{{ r.kickoff | date:'d MMM, HH:mm' }}<span *ngIf="r.cacheHit" style="margin-left:6px; color:#16a34a; font-weight:600">• cache</span></div>
              <div *ngIf="r.success && r.payload as p" class="stats">
                <div><strong>W/D/L:</strong> {{ displayPercent(p.winProbabilities?.homeWin) }} / {{ displayPercent(p.winProbabilities?.draw) }} / {{ displayPercent(p.winProbabilities?.awayWin) }}</div>
                <div><strong>BTTS:</strong> {{ displayPercent(p.bttsProbability) }}</div>
                <div><strong>Over 1.5:</strong> {{ displayPercent(p.over15Probability) }}</div>
                <div><strong>Over 2.5:</strong> {{ displayPercent(p.over25Probability) }}</div>
                <div><strong>Over 3.5:</strong> {{ displayPercent(p.over35Probability) }}</div>
                <div *ngIf="p.expectedGoals"><strong>xG:</strong> λ(Home) = {{ p.expectedGoals.home | number:'1.2-2' }}, λ(Away) = {{ p.expectedGoals.away | number:'1.2-2' }}</div>
                <div *ngIf="p.notes"><strong>Notes:</strong> {{ p.notes }}</div>
                <div *ngIf="p.correctScores?.length"><strong>Correct Scores:</strong> {{ formatScores(p.correctScores) }}</div>
              </div>
              <div *ngIf="!r.success" class="err">{{ r.error }}</div>
            </div>
          </div>
        </details>
      </div>
    </div>
  </div>
  `,
  styles: [`
    .container { padding: 12px; }
    .progress { width: 100%; height: 8px; background: #e5e7eb; border-radius: 999px; overflow: hidden; margin: 6px 0; }
    .bar { height: 100%; background: #16a34a; }
    .counters { color: #334155; font-size: 12px; margin-bottom: 8px; }
    .btn { display: inline-block; margin-right: 8px; padding: 6px 10px; border: 1px solid #cbd5e1; border-radius: 6px; text-decoration: none; }
    .league { margin-top: 10px; }
    .cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
    .card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px; background: #ffffff; }
    .card.error { border-color: #fecaca; background: #fff1f2; }
    .title { font-weight: 700; }
    .kickoff { color: #64748b; font-size: 12px; }
    .err { color: #b91c1c; font-weight: 600; }
  `]
})
export class BatchResultsComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(BatchAnalysisService);

  jobId: string | null = null;
  status: BatchStatus | null = null;
  results: FixtureAnalysisResult[] = [];
  pollHandle: any;

  get progressPct() { return this.status && this.status.total ? ((this.status.completed + this.status.failed) / this.status.total) * 100 : 0; }
  get consolidatedUrl() { return this.jobId ? this.api.consolidatedPdf(this.jobId) : '#'; }
  get zipUrl() { return this.jobId ? this.api.zip(this.jobId) : '#'; }

  ngOnInit(): void {
    this.jobId = this.route.snapshot.paramMap.get('jobId');
    // eslint-disable-next-line no-console
    console.debug('[BatchResultsComponent][ngOnInit] jobId=', this.jobId);
    if (!this.jobId) return;
    this.load();
    this.pollHandle = setInterval(() => this.load(), 3000);
  }

  ngOnDestroy(): void { if (this.pollHandle) { clearInterval(this.pollHandle); this.pollHandle = null; /* eslint-disable-next-line no-console */ console.debug('[BatchResultsComponent][ngOnDestroy] stopped polling'); } }

  private load() {
    if (!this.jobId) return;
    // eslint-disable-next-line no-console
    console.debug('[BatchResultsComponent][load][status] jobId=', this.jobId);
    this.api.status(this.jobId).subscribe(st => {
      this.status = st;
      // eslint-disable-next-line no-console
      console.debug('[BatchResultsComponent][status] =', st);
      if (st.status === 'COMPLETED' || st.status === 'FAILED') {
        // load results once complete
        // eslint-disable-next-line no-console
        console.debug('[BatchResultsComponent][load][results]');
        this.api.results(this.jobId!).subscribe((page: any) => {
          this.results = page?.content || [];
          // eslint-disable-next-line no-console
          console.debug('[BatchResultsComponent][results] count=', this.results.length);
        });
        if (this.pollHandle) { clearInterval(this.pollHandle); this.pollHandle = null; /* eslint-disable-next-line no-console */ console.debug('[BatchResultsComponent] polling stopped'); }
      }
    });
  }

  groupedByLeague() {
    const groups: { league: string, items: FixtureAnalysisResult[] }[] = [];
    const map: any = {};
    for (const r of this.results) {
      const key = r.leagueName || 'League';
      if (!map[key]) { map[key] = []; }
      map[key].push(r);
    }
    Object.keys(map).sort().forEach(k => groups.push({ league: k, items: map[k] }));
    return groups;
  }

  displayPercent(prob: any): string {
    const p = typeof prob === 'number' ? prob : Number(prob || 0);
    const v = isNaN(p) ? 0 : (p <= 1 ? p * 100 : p);
    return `${Math.round(v)}%`;
  }

  formatScores(list: { score: string, probability: number }[]): string {
    return (list || []).slice(0, 5).map(s => `${s.score} (${(s.probability <= 1 ? s.probability * 100 : s.probability).toFixed(1)}%)`).join(', ');
  }
}
