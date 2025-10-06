import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { TeamService, TeamSuggestion } from '../services/team.service';
import { FixturesService, SearchFixtureItemDTO } from '../services/fixtures.service';
import { MatchService, TeamBreakdownDto } from '../services/match.service';

interface PriorOutcomeItem {
  priorResult: string;
  priorScoreLine: string;
  sampleSize: number;
  nextResults: { win: number; draw: number; loss: number };
}

@Component({
  standalone: true,
  selector: 'app-team-outcome-distribution',
  imports: [CommonModule, FormsModule],
  styles: [`
    :host { display:block; color:#e0e0e0; background:#0a0a0a; font-family: Inter, Roboto, Arial, sans-serif; }
  `],
  template: `
    <div class="mx-auto p-3 max-w-3xl">
      <div class="mx-auto w-full sm:max-w-xl bg-slate-900 text-slate-100 rounded-xl shadow border border-slate-700 p-3 sm:p-4">
        <div class="flex items-center justify-between mb-2">
          <h2 class="text-base sm:text-lg font-semibold text-slate-100">Outcome Distribution • Upcoming Fixtures</h2>
          <button class="hidden sm:inline-flex items-center gap-1 text-xs font-medium text-slate-300 hover:text-white"
                  (click)="selectedLabel=null; homeView=null; awayView=null; conclusion=null; prediction=null">Clear</button>
        </div>

        <!-- Search upcoming/live fixtures by team prefix -->
        <div class="mb-3 relative">
          <input class="w-full rounded-md border border-slate-600 bg-slate-800 text-slate-100 px-3 py-2 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-sky-500/60 focus:border-sky-500"
                 [(ngModel)]="query" (input)="onQuery()" placeholder="Search upcoming/live or pending past by team (min 3 letters)" />
          <div *ngIf="showDropdown" class="absolute left-0 right-0 mt-1 max-h-64 overflow-auto rounded-md border border-slate-600 bg-slate-800 shadow-lg z-50">
            <button type="button" class="w-full text-left px-3 py-2 hover:bg-slate-700 border-b border-slate-700 last:border-b-0"
                    *ngFor="let s of suggestions"
                    (click)="selectFixture(s)">
              <div class="flex items-center justify-between">
                <div class="text-sm font-medium text-slate-100">
                  {{ s.fixture.homeTeam }} <span class="text-slate-400 font-normal">vs</span> {{ s.fixture.awayTeam }}
                  <span class="text-xs text-slate-400" *ngIf="s.leagueName"> · {{ s.leagueName }}</span>
                </div>
                <span class="text-[10px] px-1.5 py-0.5 rounded bg-sky-900/40 text-sky-300">{{ s.fixture.status }}</span>
              </div>
              <div class="text-[11px] text-slate-400">Kickoff: {{ s.fixture.dateTime | date:'medium' }}</div>
            </button>
            <div *ngIf="!suggestions.length" class="px-3 py-2 text-xs text-slate-400">No upcoming, live, or pending past fixtures for this team.</div>
          </div>
        </div>

        <!-- Manual matchup simulation -->
        <div class="rounded-lg bg-slate-800 border border-slate-700 p-3 mb-3">
          <div class="text-sm font-medium text-slate-200 mb-2">Manual Matchup</div>
          <div class="grid grid-cols-1 sm:grid-cols-12 gap-2">
            <div class="sm:col-span-5 relative">
              <label class="block text-[11px] text-slate-300 mb-1">Home team</label>
              <input class="w-full rounded-md border border-slate-600 bg-slate-800 text-slate-100 px-2.5 py-2 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-sky-500/60 focus:border-sky-500"
                     [(ngModel)]="homeQuery" (input)="onHomeQuery()" placeholder="Search home team" />
              <div *ngIf="showHomeDropdown" class="absolute left-0 right-0 mt-1 max-h-56 overflow-auto rounded-md border border-slate-600 bg-slate-800 shadow-lg z-40">
                <button type="button" class="w-full text-left px-3 py-2 hover:bg-slate-700 border-b border-slate-700 last:border-b-0"
                        *ngFor="let t of homeSuggestions"
                        (click)="selectHome(t)">
                  <span class="text-sm text-slate-100">{{ t.name }}</span>
                  <span class="text-xs text-slate-400" *ngIf="t.leagueId"> · League {{ t.leagueId }}</span>
                </button>
                <div *ngIf="!homeSuggestions.length" class="px-3 py-2 text-xs text-slate-400">No teams found.</div>
              </div>
              <div class="text-[11px] text-slate-300 mt-1" *ngIf="homeTeam">Selected: <span class="font-medium text-slate-100">{{ homeTeam }}</span></div>

              <!-- Manual prior for Home -->
              <div class="mt-2 grid grid-cols-5 gap-2" *ngIf="homeTeam">
                <div class="col-span-2">
                  <label class="block text-[10px] text-slate-400 mb-1">Home prior result</label>
                  <select class="w-full rounded-md border border-slate-600 bg-slate-800 text-slate-100 px-2 py-1.5 text-[13px]"
                          [(ngModel)]="homeManualResult">
                    <option value="">— Use last played —</option>
                    <option value="Win">Win</option>
                    <option value="Draw">Draw</option>
                    <option value="Loss">Loss</option>
                  </select>
                </div>
                <div class="col-span-3">
                  <label class="block text-[10px] text-slate-400 mb-1">Home prior score</label>
                  <input class="w-full rounded-md border border-slate-600 bg-slate-800 text-slate-100 px-2 py-1.5 text-[13px] placeholder-slate-400"
                         [(ngModel)]="homeManualScore" placeholder="e.g. 2-1" />
                </div>
              </div>
            </div>
            <div class="sm:col-span-5 relative">
              <label class="block text-[11px] text-slate-300 mb-1">Away team</label>
              <input class="w-full rounded-md border border-slate-600 bg-slate-800 text-slate-100 px-2.5 py-2 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-sky-500/60 focus:border-sky-500"
                     [(ngModel)]="awayQuery" (input)="onAwayQuery()" placeholder="Search away team" />
              <div *ngIf="showAwayDropdown" class="absolute left-0 right-0 mt-1 max-h-56 overflow-auto rounded-md border border-slate-600 bg-slate-800 shadow-lg z-40">
                <button type="button" class="w-full text-left px-3 py-2 hover:bg-slate-700 border-b border-slate-700 last:border-b-0"
                        *ngFor="let t of awaySuggestions"
                        (click)="selectAway(t)">
                  <span class="text-sm text-slate-100">{{ t.name }}</span>
                  <span class="text-xs text-slate-400" *ngIf="t.leagueId"> · League {{ t.leagueId }}</span>
                </button>
                <div *ngIf="!awaySuggestions.length" class="px-3 py-2 text-xs text-slate-400">No teams found.</div>
              </div>
              <div class="text-[11px] text-slate-300 mt-1" *ngIf="awayTeam">Selected: <span class="font-medium text-slate-100">{{ awayTeam }}</span></div>

              <!-- Manual prior for Away -->
              <div class="mt-2 grid grid-cols-5 gap-2" *ngIf="awayTeam">
                <div class="col-span-2">
                  <label class="block text-[10px] text-slate-400 mb-1">Away prior result</label>
                  <select class="w-full rounded-md border border-slate-600 bg-slate-800 text-slate-100 px-2 py-1.5 text-[13px]"
                          [(ngModel)]="awayManualResult">
                    <option value="">— Use last played —</option>
                    <option value="Win">Win</option>
                    <option value="Draw">Draw</option>
                    <option value="Loss">Loss</option>
                  </select>
                </div>
                <div class="col-span-3">
                  <label class="block text-[10px] text-slate-400 mb-1">Away prior score</label>
                  <input class="w-full rounded-md border border-slate-600 bg-slate-800 text-slate-100 px-2 py-1.5 text-[13px] placeholder-slate-400"
                         [(ngModel)]="awayManualScore" placeholder="e.g. 0-1" />
                </div>
              </div>
            </div>
            <div class="sm:col-span-2 flex sm:items-end justify-end">
              <button class="inline-flex items-center justify-center whitespace-nowrap rounded-md border border-sky-600 bg-sky-600 text-white text-xs font-medium px-3 py-2 hover:bg-sky-700 hover:border-sky-700 disabled:opacity-50 disabled:cursor-not-allowed"
                      (click)="simulateManual()" [disabled]="!homeTeam || !awayTeam || homeTeam === awayTeam || loading">
                Simulate this matchup
              </button>
            </div>
          </div>
          <div *ngIf="homeTeam && awayTeam && homeTeam === awayTeam" class="text-[11px] text-red-600 mt-2">Home and Away must be different teams.</div>
        </div>

        <div *ngIf="loading" class="text-sm text-slate-300">Loading...</div>
        <div *ngIf="error" class="mt-2 text-xs rounded-md border border-amber-600 bg-amber-900/20 text-amber-200 px-3 py-2">{{ error }}</div>

        <div *ngIf="selectedLabel" class="mt-3">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-slate-100">Upcoming Match: <span class="font-bold">{{ selectedLabel }}</span></h3>
          </div>

          <!-- Team stats mini tables -->
          <div class="mt-2 grid grid-cols-1 sm:grid-cols-2 gap-3">
            <!-- Home -->
            <div *ngIf="homeView" class="rounded-lg border border-slate-700 bg-slate-800 p-3">
              <div class="text-sm font-semibold text-slate-100 truncate">{{ homeTeam }}</div>
              <div class="text-[11px] text-slate-400 mb-2">Last: {{ homeView.priorScore }} {{ homeView.priorResult }}<span *ngIf="homeView.priorDate">, {{ homeView.priorDate | date:'mediumDate' }}</span></div>
              <div class="text-[13px] text-slate-200 flex flex-wrap gap-x-3 gap-y-1">
                <span>Win <span class="font-semibold">{{ homeView.win | number:'1.0-2' }}%</span></span>
                <span>Draw <span class="font-semibold">{{ homeView.draw | number:'1.0-2' }}%</span></span>
                <span>Loss <span class="font-semibold">{{ homeView.loss | number:'1.0-2' }}%</span></span>
                <span class="text-[10px] text-slate-500">n={{ homeView.sampleSize }}</span>
                <span class="text-[10px] text-slate-500" *ngIf="homeView.totalMatches !== undefined">Total matches: {{ homeView.totalMatches }}</span>
                <span class="text-[11px] text-slate-300" *ngIf="homeView.longestStreakType && homeView.longestStreakCount">
                  · Longest ever:
                  <span class="inline-flex items-center gap-1 ml-1">
                    <span class="inline-block w-5 h-5 text-[11px] leading-5 text-center font-bold rounded" [ngStyle]="streakPillStyle(homeView.longestStreakType)">{{ (homeView.longestStreakType || '').toUpperCase() }}</span>
                    <span class="text-[11px] text-slate-300">× {{ homeView.longestStreakCount }}</span>
                  </span>
                </span>
              </div>
            </div>
            <!-- Away -->
            <div *ngIf="awayView" class="rounded-lg border border-slate-700 bg-slate-800 p-3">
              <div class="text-sm font-semibold text-slate-100 truncate">{{ awayTeam }}</div>
              <div class="text-[11px] text-slate-400 mb-2">Last: {{ awayView.priorScore }} {{ awayView.priorResult }}<span *ngIf="awayView.priorDate">, {{ awayView.priorDate | date:'mediumDate' }}</span></div>
              <div class="text-[13px] text-slate-200 flex flex-wrap gap-x-3 gap-y-1">
                <span>Win <span class="font-semibold">{{ awayView.win | number:'1.0-2' }}%</span></span>
                <span>Draw <span class="font-semibold">{{ awayView.draw | number:'1.0-2' }}%</span></span>
                <span>Loss <span class="font-semibold">{{ awayView.loss | number:'1.0-2' }}%</span></span>
                <span class="text-[10px] text-slate-500">n={{ awayView.sampleSize }}</span>
                <span class="text-[10px] text-slate-500" *ngIf="awayView.totalMatches !== undefined">Total matches: {{ awayView.totalMatches }}</span>
                <span class="text-[11px] text-slate-300" *ngIf="awayView.longestStreakType && awayView.longestStreakCount">
                  · Longest ever:
                  <span class="inline-flex items-center gap-1 ml-1">
                    <span class="inline-block w-5 h-5 text-[11px] leading-5 text-center font-bold rounded" [ngStyle]="streakPillStyle(awayView.longestStreakType)">{{ (awayView.longestStreakType || '').toUpperCase() }}</span>
                    <span class="text-[11px] text-slate-300">× {{ awayView.longestStreakCount }}</span>
                  </span>
                </span>
              </div>
            </div>
          </div>

          <!-- Conclusion / Prediction chips -->
          <div *ngIf="conclusion" class="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-2">
            <div class="rounded-lg border border-sky-700 bg-sky-900/30 text-sky-200 px-3 py-2 text-sm shadow-sm">
              <span class="mr-1">⚠️</span>
              <span class="font-medium">System:</span> <span class="font-normal">{{ conclusion }}</span>
            </div>
            <div *ngIf="prediction" class="rounded-lg border border-emerald-700 bg-emerald-900/30 text-emerald-200 px-3 py-2 text-sm shadow-sm">
              <span class="mr-1">✅</span>
              <span class="font-medium">Prediction:</span>
              <span class="font-semibold" [ngStyle]="predictionStyle">{{ prediction }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class TeamOutcomeDistributionComponent {
  private teamService = inject(TeamService);
  private fixturesService = inject(FixturesService);
  private matchService = inject(MatchService);

  // Fixtures search
  query = '';
  suggestions: SearchFixtureItemDTO[] = [];
  showDropdown = false;

  // Manual search
  homeQuery = '';
  awayQuery = '';
  homeSuggestions: TeamSuggestion[] = [];
  awaySuggestions: TeamSuggestion[] = [];
  showHomeDropdown = false;
  showAwayDropdown = false;

  loading = false;
  error: string | null = null;

  // Selection/result state
  selectedLabel: string | null = null;
  homeTeam: string | null = null;
  awayTeam: string | null = null;
  // Manual prior inputs
  homeManualResult: string = '';
  homeManualScore: string = '';
  awayManualResult: string = '';
  awayManualScore: string = '';
  // Computed views
  homeView: { priorResult: string; priorScore: string; priorDate?: string; win: number; draw: number; loss: number; sampleSize: number; totalMatches?: number; longestStreakType?: string | null; longestStreakCount?: number | null } | null = null;
  awayView: { priorResult: string; priorScore: string; priorDate?: string; win: number; draw: number; loss: number; sampleSize: number; totalMatches?: number; longestStreakType?: string | null; longestStreakCount?: number | null } | null = null;
  conclusion: string | null = null;
  prediction: string | null = null;
  predictionStyle: { [k: string]: string } | null = null;

  // Map streak type to the same colors used in Played Matches Summary: W=green, D=orange, L=red
  streakPillStyle(type?: string | null): { [k: string]: string } {
    const t = (type || '').toUpperCase();
    let bg = '#64748b'; // slate as fallback
    if (t === 'W') bg = '#16a34a';
    else if (t === 'L') bg = '#ef4444';
    else if (t === 'D') bg = '#f59e0b';
    return {
      background: bg,
      color: '#ffffff',
      border: '1px solid rgba(255,255,255,0.15)'
    };
  }

  onQuery() {
    const q = (this.query || '').trim();
    if (q.length < 3) { this.suggestions = []; this.showDropdown = false; return; }
    this.fixturesService.searchFixtures(q, 12, undefined, true).subscribe({
      next: (res) => { this.suggestions = res || []; this.showDropdown = true; },
      error: () => { this.suggestions = []; this.showDropdown = true; }
    });
  }

  onHomeQuery() {
    const q = (this.homeQuery || '').trim();
    if (q.length < 2) { this.homeSuggestions = []; this.showHomeDropdown = false; return; }
    this.teamService.searchTeamsScoped(q).subscribe({
      next: (res) => { this.homeSuggestions = res || []; this.showHomeDropdown = true; },
      error: () => { this.homeSuggestions = []; this.showHomeDropdown = true; }
    });
  }

  onAwayQuery() {
    const q = (this.awayQuery || '').trim();
    if (q.length < 2) { this.awaySuggestions = []; this.showAwayDropdown = false; return; }
    this.teamService.searchTeamsScoped(q).subscribe({
      next: (res) => { this.awaySuggestions = res || []; this.showAwayDropdown = true; },
      error: () => { this.awaySuggestions = []; this.showAwayDropdown = true; }
    });
  }

  selectHome(t: TeamSuggestion) {
    this.homeTeam = t?.name || null;
    this.homeQuery = t?.name || '';
    this.showHomeDropdown = false;
  }

  selectAway(t: TeamSuggestion) {
    this.awayTeam = t?.name || null;
    this.awayQuery = t?.name || '';
    this.showAwayDropdown = false;
  }

  selectFixture(item: SearchFixtureItemDTO) {
    this.showDropdown = false;
    const f = item.fixture;
    this.selectedLabel = `${f.homeTeam} vs ${f.awayTeam}${f.status ? ' (' + f.status + ')' : ''}`;
    this.homeTeam = f.homeTeam;
    this.awayTeam = f.awayTeam;
    this.error = null; this.conclusion = null; this.prediction = null; this.predictionStyle = null;

    // Load last played summaries and outcome distributions for both teams
    this.loading = true;
    Promise.all([
      firstValueFrom(this.teamService.getLastPlayedByName(f.homeTeam)),
      firstValueFrom(this.teamService.getLastPlayedByName(f.awayTeam)),
      firstValueFrom(this.teamService.getPriorOutcomes(f.homeTeam)),
      firstValueFrom(this.teamService.getPriorOutcomes(f.awayTeam)),
      firstValueFrom(this.matchService.getResultsBreakdownByTeamName(f.homeTeam)),
      firstValueFrom(this.matchService.getResultsBreakdownByTeamName(f.awayTeam)),
    ]).then(([homeLast, awayLast, homeDist, awayDist, homeBreak, awayBreak]: any[]) => {
      this.loading = false;
      const pick = this.pickRow.bind(this);
      const hv = pick(homeDist, homeLast);
      const av = pick(awayDist, awayLast);
      if (hv && homeBreak) {
        hv.longestStreakType = (homeBreak as TeamBreakdownDto)?.longestStreakType ?? null;
        hv.longestStreakCount = (homeBreak as TeamBreakdownDto)?.longestStreakCount ?? null;
      }
      if (av && awayBreak) {
        av.longestStreakType = (awayBreak as TeamBreakdownDto)?.longestStreakType ?? null;
        av.longestStreakCount = (awayBreak as TeamBreakdownDto)?.longestStreakCount ?? null;
      }
      this.homeView = hv;
      this.awayView = av;
      this.buildConclusion();
    }).catch(() => {
      this.loading = false;
      this.error = 'Failed to load outcome distributions';
    });
  }

  simulateManual() {
    const home = (this.homeTeam || '').trim();
    const away = (this.awayTeam || '').trim();
    if (!home || !away || home.toLowerCase() === away.toLowerCase()) return;
    this.selectedLabel = `${home} vs ${away} (Manual)`;
    this.error = null; this.conclusion = null; this.prediction = null; this.predictionStyle = null;
    this.loading = true;

    const hr = (this.homeManualResult || '').trim();
    const hs = (this.homeManualScore || '').trim();
    const ar = (this.awayManualResult || '').trim();
    const as = (this.awayManualScore || '').trim();

    Promise.all([
      firstValueFrom(this.teamService.getLastPlayedByName(home)),
      firstValueFrom(this.teamService.getLastPlayedByName(away)),
      firstValueFrom(this.teamService.getPriorOutcomes(home)),
      firstValueFrom(this.teamService.getPriorOutcomes(away)),
      firstValueFrom(this.matchService.getResultsBreakdownByTeamName(home)),
      firstValueFrom(this.matchService.getResultsBreakdownByTeamName(away)),
    ]).then(([homeLast, awayLast, homeDist, awayDist, homeBreak, awayBreak]: any[]) => {
      this.loading = false;
      const pick = this.pickRow.bind(this);
      const manualPick = this.pickManualOrLast.bind(this);
      const hv = manualPick(homeDist, homeLast, hr, hs);
      const av = manualPick(awayDist, awayLast, ar, as);
      if (hv && homeBreak) {
        hv.longestStreakType = (homeBreak as TeamBreakdownDto)?.longestStreakType ?? null;
        hv.longestStreakCount = (homeBreak as TeamBreakdownDto)?.longestStreakCount ?? null;
      }
      if (av && awayBreak) {
        av.longestStreakType = (awayBreak as TeamBreakdownDto)?.longestStreakType ?? null;
        av.longestStreakCount = (awayBreak as TeamBreakdownDto)?.longestStreakCount ?? null;
      }
      this.homeView = hv;
      this.awayView = av;
      this.buildConclusion();
    }).catch(() => {
      this.loading = false;
      this.error = 'Failed to load outcome distributions';
    });
  }

  private calcTotalMatches(dist: any): number {
    try {
      const stats = dist?.stats as PriorOutcomeItem[] | undefined;
      if (!stats || !Array.isArray(stats) || stats.length === 0) return 0;
      const sum = stats.reduce((acc, s) => acc + (typeof s.sampleSize === 'number' ? s.sampleSize : 0), 0);
      return sum > 0 ? sum + 1 : 0;
    } catch { return 0; }
  }

  private pickManualOrLast(dist: any, last: any, manualResult: string, manualScore: string) {
    if (!dist || !dist.stats) return null;
    const total = this.calcTotalMatches(dist);
    const normalizeRes = (s: string) => (s || '').trim().toLowerCase();
    const normalizeScore = (s: string) => (s || '').trim();
    const hasManual = normalizeRes(manualResult) && normalizeScore(manualScore);
    if (hasManual) {
      const row = (dist.stats as PriorOutcomeItem[]).find(r =>
        normalizeRes(r.priorResult) === normalizeRes(manualResult) && r.priorScoreLine === normalizeScore(manualScore)
      );
      if (!row) return { priorResult: manualResult, priorScore: manualScore, win: NaN, draw: NaN, loss: NaN, sampleSize: 0, totalMatches: total };
      return { priorResult: row.priorResult, priorScore: row.priorScoreLine, win: row.nextResults?.win, draw: row.nextResults?.draw, loss: row.nextResults?.loss, sampleSize: row.sampleSize ?? 0, totalMatches: total };
    }
    // Fallback to last played
    return this.pickRow(dist, last);
  }

  private pickRow(dist: any, last: any) {
    if (!dist || !dist.stats || !last) return null;
    const total = this.calcTotalMatches(dist);
    const row = (dist.stats as PriorOutcomeItem[]).find(r =>
      r.priorResult?.toLowerCase() === String(last.priorResult || '').toLowerCase() &&
      r.priorScoreLine === last.priorScoreLine
    );
    if (!row) return { priorResult: last?.priorResult || '-', priorScore: last?.priorScoreLine || '-', priorDate: last?.date, win: NaN, draw: NaN, loss: NaN, sampleSize: 0, totalMatches: total };
    return { priorResult: row.priorResult, priorScore: row.priorScoreLine, priorDate: last?.date, win: row.nextResults?.win, draw: row.nextResults?.draw, loss: row.nextResults?.loss, sampleSize: row.sampleSize ?? 0, totalMatches: total };
  }

  private applyPredictionStyle() {
    const p = (this.prediction || '').toLowerCase();
    if (!p) { this.predictionStyle = null; return; }
    // Defaults
    let bg = '#e9ecef'; // light gray
    let color = '#212529'; // dark text

    const solid = (hex: string, text: string = '#fff') => ({ background: hex, color: text });
    const gradient = (from: string, to: string, text: string = '#fff') => ({ background: `linear-gradient(90deg, ${from}, ${to})`, color: text });

    if (p.includes('unpredictable')) {
      this.predictionStyle = gradient('#adb5bd', '#ced4da', '#212529'); // gray gradient
      return;
    }
    if (p.includes('double chance')) {
      if (p.includes('home/away')) {
        // Green to light green
        this.predictionStyle = gradient('#28a745', '#8fd19e', '#0b2e13');
        return;
      }
      if (p.includes('home/draw') || p.includes('away/draw')) {
        // Orange to gray
        this.predictionStyle = gradient('#fd7e14', '#6c757d');
        return;
      }
    }
    if (p.includes('most likely a home win') || p.includes('most likely an away win') || p.startsWith('edge to home side') || p.startsWith('edge to away side')) {
      this.predictionStyle = solid('#28a745');
      return;
    }
    if (p.includes('draw')) {
      // Draw-centric statements
      this.predictionStyle = solid('#0dcaf0', '#052c65'); // info with dark-ish text
      return;
    }

    this.predictionStyle = { background: bg, color };
  }

  private buildConclusion() {
    const hv = this.homeView, av = this.awayView;
    this.conclusion = null; this.prediction = null;
    if (!hv && !av) { return; }

    // Helper: check numeric validity
    const valid = (v: any) => v && typeof v.win === 'number' && typeof v.draw === 'number' && typeof v.loss === 'number' && !isNaN(v.win) && !isNaN(v.draw) && !isNaN(v.loss);

    // Build a concise tendency line from individual leans
    const lean = (v: any) => {
      if (!valid(v)) return 'UNKNOWN';
      const max = Math.max(v.win ?? 0, v.draw ?? 0, v.loss ?? 0);
      if (max === (v.win ?? 0)) return 'WIN';
      if (max === (v.draw ?? 0)) return 'DRAW';
      return 'LOSS';
    };
    const hl = lean(hv);
    const al = lean(av);

    // Default tendency sentence (kept short, beginner-friendly)
    if (hl === 'WIN' && al === 'WIN') {
      this.conclusion = 'Both teams often follow with wins after these priors.';
    } else if (hl === 'DRAW' && al === 'DRAW') {
      this.conclusion = 'Both teams often follow with draws after these priors.';
    } else if (hl === 'WIN' && al !== 'WIN') {
      this.conclusion = `${this.homeTeam} shows a stronger tendency to win after its prior result.`;
    } else if (al === 'WIN' && hl !== 'WIN') {
      this.conclusion = `${this.awayTeam} shows a stronger tendency to win after its prior result.`;
    } else if (hl === 'DRAW' && al !== 'DRAW') {
      this.conclusion = `${this.homeTeam} leans toward a draw after its prior result.`;
    } else if (al === 'DRAW' && hl !== 'DRAW') {
      this.conclusion = `${this.awayTeam} leans toward a draw after its prior result.`;
    } else {
      this.conclusion = 'Mixed tendencies from the prior results.';
    }

    // If we can't compute combined probabilities, keep the tendency only
    if (!valid(hv) || !valid(av)) { return; }

    // Combine into match-level outcomes from both perspectives (percentages already 0-100)
    const homeWin = (hv.win + av.loss) / 2;
    const draw = (hv.draw + av.draw) / 2;
    const awayWin = (hv.loss + av.win) / 2;

    // Determine highest and double-chance combos
    const maxVal = Math.max(homeWin, draw, awayWin);
    const maxKey = maxVal === homeWin ? 'HOME' : (maxVal === draw ? 'DRAW' : 'AWAY');

    const hd = homeWin + draw;
    const ad = awayWin + draw;
    const ha = homeWin + awayWin;

    // Rule precedence:
    // 1) If Draw is the highest → specific draw message
    if (maxKey === 'DRAW') {
      this.prediction = 'This match is most likely going to end as a draw.';
      this.applyPredictionStyle();
      return;
    }

    // 2) If one outcome clearly highest (≥50%) → Most likely a [Home/Away/Draw]
    if (maxVal >= 50) {
      if (maxKey === 'HOME') this.prediction = 'Most likely a home win.';
      else if (maxKey === 'AWAY') this.prediction = 'Most likely an away win.';
      else this.prediction = 'Most likely a draw.';
      // Add a small nod to the next-likely draw if applicable (draw cannot be highest here due to early return)
      if (draw > 25 && draw >= Math.min(homeWin, awayWin)) {
        this.prediction = this.prediction.replace(/\.$/, '') + ', though a draw is still possible.';
      }
      this.applyPredictionStyle();
      return;
    }

    // 3) If two outcomes combined ≥70% → Double Chance wording
    const combos: Array<{k:string; v:number; label:string}> = [
      { k: 'HD', v: hd, label: 'Home/Draw' },
      { k: 'AD', v: ad, label: 'Away/Draw' },
      { k: 'HA', v: ha, label: 'Home/Away' },
    ];
    combos.sort((a,b) => b.v - a.v);
    if (combos[0].v >= 70) {
      this.prediction = `More likely a Double Chance (${combos[0].label}).`;
      this.applyPredictionStyle();
      return;
    }

    // 4) If all outcomes fairly balanced (no outcome >40%)
    if (homeWin <= 40 && draw <= 40 && awayWin <= 40) {
      this.prediction = 'This match is unpredictable.';
      this.applyPredictionStyle();
      return;
    }

    // 5) Fallback: short edge phrasing
    if (homeWin > awayWin) {
      this.prediction = draw >= 30 ? 'Edge to home side; double chance on Home/Draw is safer.' : 'Edge to home side.';
    } else if (awayWin > homeWin) {
      this.prediction = draw >= 30 ? 'Edge to away side; double chance on Away/Draw is safer.' : 'Edge to away side.';
    } else {
      this.prediction = 'Strong chance of a draw.';
    }
    this.applyPredictionStyle();
  }
}
