import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatchUploadService } from '../services/match-upload.service';
import { LeagueService, LeagueDto } from '../services/league.service';
import { SeasonService, Season } from '../services/season.service';
import { HttpClient } from '@angular/common/http';
import { COUNTRIES } from '../shared/countries.constant';

@Component({
  selector: 'app-match-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <h1 class="text-2xl font-bold mb-4">Match Upload</h1>

      <div class="tabs mb-4">
        <button (click)="activeTab='csv'" [class.active]="activeTab==='csv'">CSV Upload</button>
        <button (click)="activeTab='text'" [class.active]="activeTab==='text'">Raw Text Upload</button>
        <button (click)="activeTab='fixtures'" [class.active]="activeTab==='fixtures'">Fixtures Upload</button>
      </div>

      <!-- Upload Type Selection (applies to CSV / Raw Text) -->
      <div *ngIf="activeTab!=='fixtures'" class="card">
        <div class="mb-2">
          <div class="grid" style="grid-template-columns: 1fr 1fr; gap: 6px;">
            <label title="Create a brand new league with provided country and season.">
              <input type="radio" [(ngModel)]="uploadType" value="NEW_LEAGUE" (change)="onUploadTypeChange()" />
              New League Upload
            </label>
            <label title="Replace all existing matches for a selected league and season.">
              <input type="radio" [(ngModel)]="uploadType" value="FULL_REPLACE" (change)="onUploadTypeChange()" />
              Complete Replacement of Existing League
            </label>
            <label title="Update results while keeping existing fixtures; safe to re-run.">
              <input type="radio" [(ngModel)]="uploadType" value="INCREMENTAL" (change)="onUploadTypeChange()" />
              Incremental Update of Existing League
            </label>
          </div>
        </div>

        <!-- Dynamic Fields per Upload Type (CSV/Text) -->
        <div class="grid">
          <!-- Existing league selection for FULL_REPLACE / INCREMENTAL -->
          <ng-container *ngIf="requiresExistingLeague; else newLeagueBlock">
            <label>
              League
              <select [(ngModel)]="selectedLeague" (change)="onLeagueSelected()">
                <option value="">Select league...</option>
                <option *ngFor="let league of leagues" [value]="league.id">{{ league.name }}</option>
              </select>
            </label>
          </ng-container>
          <ng-template #newLeagueBlock>
            <label>
              League Name
              <input [(ngModel)]="leagueName" placeholder="e.g., Premier League"/>
            </label>
          </ng-template>

          <!-- Country handling -->
          <label>
            Country
            <div class="country-select">
              <input type="text" class="country-filter" [(ngModel)]="countryFilter" placeholder="Search country..." [disabled]="requiresExistingLeague"/>
              <select [(ngModel)]="country" [disabled]="requiresExistingLeague">
                <option value="">Select country...</option>
                <option *ngFor="let c of filteredCountries" [value]="c">{{c}}</option>
              </select>
            </div>
          </label>

          <!-- Season text input is required for NEW_LEAGUE; for existing league types it is read-only (pre-filled) but can be overridden for FULL_REPLACE -->
          <label>
            Season
            <input [(ngModel)]="season" placeholder="e.g., 2024/2025" [readonly]="uploadType==='INCREMENTAL'"/>
          </label>

          <!-- Season assignment (seasonId) available when a league is selected -->
          <label>
            Season (assign to)
            <select [(ngModel)]="seasonId" [disabled]="!selectedLeague">
              <option [ngValue]="null">Auto/current</option>
              <option *ngFor="let s of seasons" [ngValue]="s.id">{{s.name}}</option>
            </select>
          </label>

          <!-- Help text per type -->
          <div style="grid-column: 1 / -1;" class="hint">
            <ng-container [ngSwitch]="uploadType">
              <div *ngSwitchCase="'NEW_LEAGUE'">Creates a new league. All matches will be inserted under the provided season.</div>
              <div *ngSwitchCase="'FULL_REPLACE'">Deletes existing matches for the selected league/season and uploads the provided data.</div>
              <div *ngSwitchCase="'INCREMENTAL'">Updates results for existing fixtures; safe to run multiple times.</div>
            </ng-container>
          </div>
        </div>
      </div>

      <div *ngIf="activeTab==='csv'" class="card">
        <h2>CSV Upload</h2>
        <input type="file" (change)="onFile($event)"/>
        <p class="hint">Expected columns: date,round,homeTeam,awayTeam,homeGoals,awayGoals
          (also accepts aliases: Date/HomeTeam/AwayTeam/FTHG/FTAG, Round/Matchweek/GW; round is optional)
        </p>
        <button (click)="uploadCsv()" [disabled]="!file">Upload CSV</button>
      </div>

      <div *ngIf="activeTab==='text'" class="card">
        <h2>Raw Text Upload</h2>
        <p class="hint">Accepted formats:
          <br>A) Single-line: YYYY-MM-DD, Home Team - Away Team, X-Y
          <br>B) Vertical blocks (dates like dd.MM. HH:mm, year inferred from Season):
        </p>
        <pre class="hint" style="white-space: pre-wrap; background:#f8fafc; padding:8px; border-radius:6px; border:1px solid #e5e7eb;">
Round 22
01.09. 01:30
Nublense
Nublense
U. Espanola
U. Espanola
1
2
Round 21
25.08. 00:30
Deportes Iquique
Deportes Iquique
Nublense
Nublense
0
2
        </pre>
        <textarea [(ngModel)]="text" rows="12" placeholder="Paste your rounds and matches here..."></textarea>
        <button (click)="uploadText()" [disabled]="!text.trim()">Upload Text</button>
      </div>

      <!-- Fixtures Upload Tab -->
      <div *ngIf="activeTab==='fixtures'" class="card">
        <h2>Fixtures Upload</h2>
        <p class="hint">Do not include goals here. Use Match Upload when results are available.</p>
        <div class="grid">
          <label>
            League
            <select [(ngModel)]="fixturesLeagueId" (ngModelChange)="onFixturesLeagueChange()">
              <option [ngValue]="null">Select a league...</option>
              <option *ngFor="let l of fixturesLeagues" [ngValue]="l.id">{{l.name}} ({{l.season}})</option>
            </select>
          </label>
          <label style="grid-column: 1 / -1;">
            <input type="checkbox" [(ngModel)]="fullReplace"/> Full replace existing fixtures
          </label>
        </div>

        <p class="hint" style="margin-top:8px;">Paste raw fixtures in blocks. Example:</p>
        <pre class="hint" style="white-space: pre-wrap; background:#f8fafc; padding:8px; border-radius:6px; border:1px solid #e5e7eb;">
Round 29
01.09. 20:00
Agropecuario
Agropecuario
Almirante Brown
Almirante Brown
-
-

01.09. 23:10
Nueva Chicago
Nueva Chicago
Gimnasia Mendoza
Gimnasia Mendoza
-
-
        </pre>
        <textarea [(ngModel)]="fixturesRawText" rows="12" placeholder="Paste raw fixtures text here..."></textarea>
        <button (click)="uploadFixtures()" [disabled]="!fixturesLeagueId || !fixturesRawText.trim()">Upload Fixtures</button>
      </div>

      <div *ngIf="message" class="alert" [class.success]="success" [class.error]="!success">
        {{message}}
      </div>

      <!-- Upload feedback grouped -->
      <div *ngIf="results.updated?.length" class="card" style="border-color:#10b981">
        <h3 style="color:#065f46">Updated</h3>
        <table style="width:100%">
          <tr><th>Fixture ID</th><th>Home</th><th>Away</th><th>Result</th><th>Status</th></tr>
          <tr *ngFor="let u of results.updated">
            <td>{{u.fixtureId}}</td>
            <td>{{u.homeTeam}}</td>
            <td>{{u.awayTeam}}</td>
            <td>{{u.result}}</td>
            <td>{{u.status}}</td>
          </tr>
        </table>
      </div>
      <div *ngIf="results.warnings?.length" class="card" style="border-color:#fb923c">
        <h3 style="color:#b45309">Warnings</h3>
        <ul>
          <li *ngFor="let w of results.warnings">{{w.homeTeam}} vs {{w.awayTeam}} — {{w.reason}}</li>
        </ul>
      </div>
      <div *ngIf="results.skipped?.length" class="card" style="border-color:#ef4444">
        <h3 style="color:#991b1b">Skipped</h3>
        <ul>
          <li *ngFor="let s of results.skipped">{{s.homeTeam}} vs {{s.awayTeam}} — {{s.reason}}</li>
        </ul>
      </div>

      <div *ngIf="errors.length" class="alert error">
        <div>Errors:</div>
        <ul>
          <li *ngFor="let e of errors">{{e}}</li>
        </ul>
      </div>
    </div>
  `,
  styles: [`
    .tabs button{margin-right:8px;padding:6px 12px;border:1px solid #ddd;background:#fafafa;cursor:pointer}
    .tabs button.active{background:#2563eb;color:#fff;border-color:#2563eb}
    .card{border:1px solid #e5e7eb;padding:12px;border-radius:8px;margin-bottom:12px}
    .grid{display:grid;grid-template-columns:repeat(2, minmax(0, 1fr));gap:8px}
    input, textarea, select{width:100%;padding:6px;border:1px solid #d1d5db;border-radius:6px}
    .country-select { display: grid; grid-template-columns: 1fr; gap: 6px; }
    .country-filter { }
    button{padding:8px 16px;background:#10b981;color:#fff;border:none;border-radius:6px;cursor:pointer}
    .hint{color:#6b7280;font-size:12px}
    .alert{padding:10px;border-radius:6px;margin-top:12px}
    .alert.success{background:#ecfdf5;color:#065f46;border:1px solid #10b981}
    .alert.error{background:#fef2f2;color:#991b1b;border:1px solid #ef4444}
  `]
})
export class MatchUploadComponent {
  activeTab: 'csv' | 'text' | 'fixtures' = 'csv';
  // Upload type for CSV/Text flows
  uploadType: 'NEW_LEAGUE' | 'FULL_REPLACE' | 'INCREMENTAL' = 'NEW_LEAGUE';

  leagueName = '';
  country = '';
  season = '';

  // Country dropdown support
  countryFilter: string = '';
  countries: readonly string[] = COUNTRIES;
  get filteredCountries(): readonly string[] {
    const q = this.countryFilter?.toLowerCase().trim();
    if (!q) return this.countries;
    return this.countries.filter(c => c.toLowerCase().includes(q));
  }

  // Fixtures-only toggle remains
  fullReplace = true; // for fixtures tab checkbox

  // Existing leagues dataset
  leagues: { id: number; name: string; }[] = [];
  selectedLeague: string = '';

  // Fixtures upload state
  fixturesLeagueId: number | null = null;
  fixturesSeason: string = '';
  fixturesLeagueName: string = '';
  fixturesCountry: string = '';
  fixturesRawText: string = '';
  fixturesLeagues: { id: number; name: string; season: string; }[] = [];

  file?: File;
  text = '';

  message = '';
  success = false;
  errors: string[] = [];
  results: { updated?: any[]; skipped?: any[]; warnings?: any[] } = {};

  seasons: Season[] = [];
  seasonId: number | null = null;

  get requiresExistingLeague(): boolean {
    return this.uploadType === 'FULL_REPLACE' || this.uploadType === 'INCREMENTAL';
  }

  constructor(private api: MatchUploadService, private leagueApi: LeagueService, private seasonApi: SeasonService, private http: HttpClient) {
    // load leagues for fixtures tab
    this.leagueApi.getLeagues().subscribe(ls => {
      this.fixturesLeagues = ls.map(l => ({ id: l.id, name: `${l.name} (${l.country})`, season: l.season }));
    });
  }

  onFile(ev: Event){
    const input = ev.target as HTMLInputElement;
    if (input.files && input.files.length){
      this.file = input.files[0];
    }
  }

  onFixturesLeagueChange(){
    const l = this.fixturesLeagues.find(x => x.id === this.fixturesLeagueId);
    if (l) this.fixturesSeason = l.season;
    if (this.fixturesLeagueId) {
      this.leagueApi.getLeagueDetails(this.fixturesLeagueId).subscribe(details => {
        this.fixturesLeagueName = details.name;
        this.fixturesCountry = details.country;
        this.fixturesSeason = details.season;
      });
    } else {
      this.fixturesLeagueName = '';
      this.fixturesCountry = '';
    }
  }

  onUploadTypeChange(){
    // Load leagues when an existing-league-based type is selected
    if (this.requiresExistingLeague) {
      this.leagueApi.getAllLeagues().subscribe(data => {
        this.leagues = (data || []).map(d => ({ id: d.id, name: d.name }));
      });
    } else {
      // Reset fields for new league upload
      this.selectedLeague = '';
      this.leagues = [];
      this.country = '';
      this.season = '';
      this.seasons = [];
      this.seasonId = null;
    }
  }

  onLeagueSelected(){
    if (this.selectedLeague) {
      this.leagueApi.getLeagueDetails(this.selectedLeague).subscribe(details => {
        this.country = details.country;
        this.season = details.season;
        this.leagueName = details.name; // keep API contract
      });
      // load seasons for selected league
      const lid = Number(this.selectedLeague);
      if (!Number.isNaN(lid)) {
        this.seasonApi.listSeasons(lid).subscribe({ next: s => this.seasons = s ?? [], error: _ => this.seasons = [] });
      }
    } else {
      this.country = '';
      this.season = '';
    }
  }

  uploadCsv(){
    this.resetFeedback();
    if (!this.validateMeta()) return;
    if (!this.file) return;
    const leagueIdOpt = this.requiresExistingLeague && this.selectedLeague ? Number(this.selectedLeague) : null;
    this.api.uploadUnifiedCsv(this.uploadType, this.leagueName, this.country, this.season, this.file, { seasonId: this.seasonId, leagueId: leagueIdOpt, autoDetectSeason: false }).subscribe({
      next: res => this.handleResult(res),
      error: err => this.handleHttpError(err)
    });
  }

  uploadText(){
    this.resetFeedback();
    if (!this.validateMeta()) return;
    const leagueIdOpt = this.requiresExistingLeague && this.selectedLeague ? Number(this.selectedLeague) : null;
    this.api.uploadUnifiedText(this.uploadType, this.leagueName, this.country, this.season, this.text, { seasonId: this.seasonId, leagueId: leagueIdOpt, autoDetectSeason: false }).subscribe({
      next: res => this.handleResult(res),
      error: err => this.handleHttpError(err)
    });
  }

  uploadFixtures(){
    this.resetFeedback();
    if (!this.fixturesLeagueId || !this.fixturesRawText.trim()){
      this.success = false; this.message = 'Please select a league and paste fixtures text.'; return;
    }
    if (!this.fixturesLeagueName || !this.fixturesCountry || !this.fixturesSeason){
      this.success = false; this.message = 'League details missing. Please re-select the league.'; return;
    }
    const body = {
      leagueId: this.fixturesLeagueId,
      season: this.fixturesSeason,
      fullReplace: false,
      rawText: this.fixturesRawText
    };
    this.http.post('/api/fixtures/upload-text', body).subscribe({
      next: (res: any) => {
        this.handleResult(res);
        if (res?.success){
          this.fixturesRawText = '';
          this.message = res.message || `Fixtures uploaded successfully.`;
        }
      },
      error: err => this.handleHttpError(err)
    });
  }

  private handleResult(res: any){
    this.success = !!res?.success;
    this.errors = res?.errors || [];
    // Capture grouped results
    this.results = { updated: res?.updated || [], skipped: res?.skipped || [], warnings: res?.warnings || [] };
    const anyUpdated = (this.results.updated?.length || 0) > 0;
    const inserted = (res?.inserted !== undefined ? res.inserted : res?.insertedCount) ?? 0;
    const deleted = (res?.deleted !== undefined ? res.deleted : res?.deletedCount) ?? 0;
    const completedAll = (res?.completed ?? null);
    const completedUpToToday = (res?.completedUpToToday ?? null);
    const baseMsg = anyUpdated ? 'Results updated successfully.' : `Processed. Inserted ${inserted}, deleted ${deleted}.`;
    let suffix = '';
    if (completedUpToToday !== null) {
      suffix = ` Completed in DB (<= today): ${completedUpToToday}.`;
      if (completedAll !== null && completedAll !== completedUpToToday) {
        suffix += ` Total completed (all dates): ${completedAll}.`;
      }
    } else if (completedAll !== null) {
      suffix = ` Completed in DB: ${completedAll}.`;
    }
    if (this.success) {
      this.message = baseMsg + suffix;
    } else {
      // Summarize strict out-of-window errors when present
      const out = this.errors.filter(e => /out-of-window|outside season window/i.test(e)).length;
      this.message = out > 0 ? `${out} matches rejected because they are outside season window (strict mode).` : (res?.message || 'Upload failed.');
    }

    // Emit a fixtures refresh event if incremental updates occurred
    if (this.uploadType === 'INCREMENTAL' && anyUpdated) {
      const ev = new CustomEvent('fixtures:refresh', { detail: { leagueName: this.leagueName, season: this.season } });
      window.dispatchEvent(ev);
    }

    // Clear raw text textarea on successful upload as requested
    if (this.success && this.activeTab === 'text') {
      this.text = '';
    }
  }
  private handleHttpError(err: any){
    this.success = false;
    this.message = err?.error?.message || 'Server error during upload';
    if (err?.error?.errors) this.errors = err.error.errors;
    // Friendly summary for strict mode out-of-window rejections
    try {
      const errs: string[] = this.errors || [];
      const out = errs.filter(e => /out-of-window|outside season window/i.test(e)).length;
      if (out > 0) {
        this.message = `${out} matches rejected because they are outside season window (strict mode).`;
      }
    } catch { /* ignore */ }
  }
  private resetFeedback(){ this.message=''; this.success=false; this.errors=[]; }
  private validateMeta(){
    // Fixtures tab uses its own validation
    if (this.activeTab === 'fixtures') return true;

    // Existing league required types
    if (this.requiresExistingLeague) {
      if (!this.selectedLeague) {
        const scope = this.uploadType === 'INCREMENTAL' ? 'incremental update' : 'complete replacement';
        this.success = false; this.message = `Please select a league for ${scope}.`; return false;
      }
      if (!this.country.trim() || !this.season.trim()){
        this.success = false; this.message = 'League details missing. Please re-select the league.'; return false;
      }
      if (!this.leagueName.trim()) {
        this.success = false; this.message = 'Internal error: league name not set from selection.'; return false;
      }
      return true;
    }

    // NEW_LEAGUE requires all fields manually
    if (!this.leagueName.trim() || !this.country.trim() || !this.season.trim()){
      this.success = false; this.message = 'Please provide league name, country and season.'; return false;
    }
    return true;
  }
}
