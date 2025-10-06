import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, NgIf, NgFor, NgClass } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RecommendationsService, RecommendationSummaryDTO } from '../services/recommendations.service';

@Component({
  selector: 'app-analysis-hub',
  standalone: true,
  imports: [CommonModule, NgIf, NgFor, NgClass, RouterLink],
  styles: [`
    :host { display:block; color:#e0e0e0; background:#0a0a0a; font-family: Inter, Roboto, Arial, sans-serif; }
    .container { max-width: 1100px; margin: 0 auto; padding: 12px; }
    .card { background:#1a1a1a; border:1px solid #333; border-radius:12px; padding:12px; margin-bottom:12px; }
    .title { font-size:22px; font-weight:800; color:#fff; }
    .muted { color:#9aa0a6; }
    .chips { display:flex; flex-wrap:wrap; gap:8px; margin-top:8px; }
    .chip { border-radius:999px; padding:4px 10px; font-size:12px; border:1px solid #2d2d2d; background:#101010; }
    .chip.red { background:#3a1111; color:#ff6b6b; border-color:#7a1f1f; }
    .chip.amber { background:#2f2a11; color:#ffd166; border-color:#7a6a1f; }
    .chip.green { background:#113a2a; color:#28a745; border-color:#1f7a4c; }
    .chip.warn { background:#3a1111; color:#ff6b6b; border-color:#7a1f1f; }
    .row { display:flex; gap:12px; flex-wrap:wrap; }
    .col { flex:1; min-width:260px; }
    .btn { display:inline-block; border-radius:8px; padding:8px 12px; border:1px solid #2d2d2d; background:#2a2a2a; color:#fff; text-decoration:none; font-weight:700; }
    .btn:hover { background:#333; }
    .score-pill { background:#0b2f55; border:1px solid #123e73; color:#d1e9ff; border-radius:8px; padding:4px 8px; font-weight:800; }
  `],
  template: `
    <div class="container">
      <!-- Prompt to choose a fixture when required params are missing -->
      <div class="card" *ngIf="needParams">
        <div class="title">Choose a Fixture</div>
        <div class="muted">To run automatic analysis, select a fixture from the Fixtures page.</div>
        <div class="chips" style="margin-top:8px;">
          <a class="btn" [routerLink]="['/fixtures']">Go to Fixtures</a>
        </div>
      </div>

      <ng-container *ngIf="!needParams">
        <div class="card">
          <div class="title">✅ Final Recommendation</div>
          <div class="muted">Unified system-generated pick based on Fixture Analysis + Streak Insights</div>
          <div *ngIf="loading" class="muted">Loading recommendation…</div>
          <ng-container *ngIf="!loading && rec">
            <div class="row" style="align-items:center;">
              <div class="col">
                <div style="font-weight:800; font-size:18px; color:#fff;">Outcome: {{ friendlyOutcome(rec.outcomeLean) }}</div>
                <div class="muted" title="Confidence breakdown">{{ rec.confidenceBreakdownText || ('Confidence: ' + rec.outcomeConfidence + '/100') }}</div>
                <div class="chips" *ngIf="rec.divergenceWarning">
                  <span class="chip warn">⚠️ Divergence: {{ rec.divergenceNote || 'Fixture vs Streak conflict' }}</span>
                </div>
              </div>
              <div class="col">
                <div>BTTS: <strong>{{ friendlyBtts(rec.bttsRecommendation, rec.overUnderRecommendation) }}</strong></div>
                <div>Over/Under: <strong>{{ rec.overUnderRecommendation }}</strong><span *ngIf="rec.overUnderProbability != null"> ({{ rec.overUnderProbability }}% probability)</span></div>
              </div>
              <div class="col">
                <div class="muted">Correct Scores</div>
                <div class="chips">
                  <span *ngFor="let s of rec.correctScoreShortlist" class="score-pill">{{ s }}</span>
                </div>
                <div class="muted" *ngIf="rec.correctScoreContext" style="margin-top:6px;">{{ rec.correctScoreContext }}</div>
              </div>
            </div>
            <div class="chips" style="margin-top:8px;">
              <ng-container *ngIf="(rec.homeStreakInstances || 0) > 0; else homeNoteTpl">
                <span class="chip" [ngClass]="sampleClass(rec.homeStreakSampleLevel)">Home streak samples: {{ rec.homeStreakInstances }}</span>
              </ng-container>
              <ng-template #homeNoteTpl>
                <span class="chip warn">⚠️ {{ rec.homeStreakNote || 'No streak data available for Home — prediction weighted mainly on Fixture Analysis' }}</span>
              </ng-template>

              <ng-container *ngIf="(rec.awayStreakInstances || 0) > 0; else awayNoteTpl">
                <span class="chip" [ngClass]="sampleClass(rec.awayStreakSampleLevel)">Away streak samples: {{ rec.awayStreakInstances }}</span>
              </ng-container>
              <ng-template #awayNoteTpl>
                <span class="chip warn">⚠️ {{ rec.awayStreakNote || 'No streak data available for Away — prediction weighted mainly on Fixture Analysis' }}</span>
              </ng-template>
            </div>
          </ng-container>
        </div>

        <div class="card">
          <div class="title">Contribution by stage</div>
          <div class="row">
            <div class="col">
              <div class="muted">Matches used from Played Matches Summary</div>
              <div><strong>{{ rec?.analysisMatchesCount != null ? rec?.analysisMatchesCount : '?' }}</strong> <span class="muted">(H2H window)</span></div>
              <div class="chips" style="margin-top:6px;">
                <span class="chip" [ngClass]="sampleClass(rec?.homeStreakSampleLevel)">Home streak instances: {{ rec?.homeStreakInstances ?? 0 }}</span>
                <span class="chip" [ngClass]="sampleClass(rec?.awayStreakSampleLevel)">Away streak instances: {{ rec?.awayStreakInstances ?? 0 }}</span>
              </div>
            </div>
            <div class="col">
              <div><strong>Fixture Analysis contribution</strong></div>
              <ul>
                <li *ngFor="let f of (rec?.fixtureAnalysisFactors || [])">{{ f }}</li>
              </ul>
            </div>
            <div class="col">
              <div><strong>Streak Insight contribution</strong></div>
              <ul>
                <li *ngFor="let f of (rec?.streakInsightFactors || [])">{{ f }}</li>
              </ul>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="title">Details</div>
          <div class="row">
            <div class="col">
              <a class="btn" [routerLink]="['/played-matches-summary']" [queryParams]="{ h2hHome: homeTeamName, h2hAway: awayTeamName, leagueId: leagueId }">Open Fixture Analysis</a>
            </div>
            <div class="col">
              <a class="btn" [routerLink]="['/streak-insights']" [queryParams]="{ team: homeTeamName }">Open Streak Insight (Home)</a>
            </div>
            <div class="col">
              <a class="btn" [routerLink]="['/streak-insights']" [queryParams]="{ team: awayTeamName }">Open Streak Insight (Away)</a>
            </div>
          </div>
        </div>

        <div class="card" *ngIf="rec?.rationale?.length">
          <div class="title">Why this pick</div>
          <ul>
            <li *ngFor="let r of rec?.rationale">{{ r }}</li>
          </ul>
        </div>
      </ng-container>
    </div>
  `
})
export class AnalysisHubComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private svc = inject(RecommendationsService);

  rec: RecommendationSummaryDTO | null = null;
  loading = true;
  needParams = false;

  fixtureId: number | null = null;
  leagueId!: number;
  seasonId: number | null = null;
  homeTeamId: number | null = null;
  awayTeamId: number | null = null;
  leagueName: string | null = null;
  homeTeamName: string | null = null;
  awayTeamName: string | null = null;

  friendlyOutcome(lean: string | undefined | null): string {
    switch ((lean || 'UNKNOWN').toUpperCase()) {
      case 'HOME': return 'Home Win';
      case 'AWAY': return 'Away Win';
      case 'DRAW': return 'Draw';
      default: return 'No clear lean';
    }
  }

  friendlyBtts(btts: string | undefined | null, ou: string | undefined | null): string {
    const b = (btts || '').toLowerCase();
    if (b === 'yes' || b === 'no') return (b.charAt(0).toUpperCase() + b.slice(1));
    // Lean case: infer direction from O/U if available
    const o = (ou || '').toLowerCase();
    if (o.startsWith('under')) return 'Lean No';
    if (o.startsWith('over')) return 'Lean Yes';
    return 'Lean';
  }

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(p => {
      this.fixtureId = parseNum(p.get('fixtureId'));
      this.leagueId = parseNum(p.get('leagueId')) ?? 0;
      this.seasonId = parseNum(p.get('seasonId'));
      this.homeTeamId = parseNum(p.get('homeTeamId'));
      this.awayTeamId = parseNum(p.get('awayTeamId'));
      this.leagueName = p.get('leagueName');
      this.homeTeamName = p.get('homeTeamName');
      this.awayTeamName = p.get('awayTeamName');

      // Determine if we have enough to run an analysis
      const haveLeague = !!this.leagueId;
      const haveTeams = (!!this.homeTeamId && !!this.awayTeamId) || (!!(this.homeTeamName && this.homeTeamName.trim()) && !!(this.awayTeamName && this.awayTeamName.trim()));
      this.needParams = !(haveLeague && haveTeams);

      if (this.needParams) {
        this.loading = false;
        this.rec = null;
        return;
      }
      this.fetch();
    });
  }

  fetch() {
    if (this.needParams) { this.loading = false; return; }
    this.loading = true;
    this.svc.getFixtureRecommendation({
      fixtureId: this.fixtureId ?? undefined,
      leagueId: this.leagueId,
      seasonId: this.seasonId ?? undefined,
      homeTeamId: this.homeTeamId ?? undefined,
      awayTeamId: this.awayTeamId ?? undefined,
      leagueName: this.leagueName ?? undefined,
      homeTeamName: this.homeTeamName ?? undefined,
      awayTeamName: this.awayTeamName ?? undefined,
    }).subscribe({
      next: (res) => { this.rec = res; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  sampleClass(level?: string | null) {
    switch ((level || 'UNKNOWN').toUpperCase()) {
      case 'HIGH': return 'green';
      case 'MEDIUM': return 'amber';
      case 'LOW': return 'red';
      default: return '';
    }
  }
}

function parseNum(v: string | null): number | null {
  if (v == null) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}
