import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatchUploadService } from '../services/match-upload.service';
import { LeagueService, LeagueDto, GroupedLeagueDTO } from '../services/league.service';
import { SeasonService, Season } from '../services/season.service';
import { HttpClient } from '@angular/common/http';
import { COUNTRIES } from '../shared/countries.constant';
import { COUNTRY_LEAGUES } from '../shared/country-leagues.constant';
import { LeagueContextService } from '../services/league-context.service';

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
              <select [(ngModel)]="selectedLeague" (ngModelChange)="onLeagueSelected()">
                <option [ngValue]="null">Select league...</option>
                <optgroup *ngFor="let g of groupedFixturesLeagues" [label]="g.groupLabel">
                  <option *ngFor="let opt of g.options" [ngValue]="opt.leagueId">{{ opt.label }}</option>
                </optgroup>
              </select>
            </label>
          </ng-container>
          <ng-template #newLeagueBlock>
            <!-- Country-dependent League selection with manual fallback -->
            <div class="grid" style="grid-template-columns: 1fr; gap: 6px;">
              <label>
                League
                <select [(ngModel)]="leagueSelect" [disabled]="!country" (ngModelChange)="onLeagueSelectChange()">
                  <option value="">Select league...</option>
                  <option *ngFor="let l of leaguesForCountry" [value]="l">{{ l }}</option>
                </select>
                <div class="hint" *ngIf="!country">Select a country first to choose a league.</div>
                <div class="hint" *ngIf="country && leaguesForCountry.length===0">No preset leagues for {{country}}. Use manual input below.</div>
              </label>
              <label>
                <input type="checkbox" [(ngModel)]="useManualLeague" (change)="onManualToggleChange()"/>
                Can't find your league? Enter manually
              </label>
              <label *ngIf="useManualLeague">
                League Name (manual)
                <input [(ngModel)]="leagueManual" (ngModelChange)="onLeagueManualChange()" placeholder="Type league name..."/>
              </label>
            </div>
          </ng-template>

          <!-- Country or Competition handling (MVP) -->
          <label>
            Country or Competition
            <div class="country-select">
              <input type="text" class="country-filter" [(ngModel)]="countryFilter" placeholder="Search country or competition..." [disabled]="requiresExistingLeague"/>
              <select [(ngModel)]="country" [disabled]="requiresExistingLeague" (ngModelChange)="onCountryChange()">
                <option value="">Select country or competition...</option>
                <option *ngFor="let c of filteredCountries" [value]="c">{{ displayContext(c) }}</option>
              </select>
            </div>
          </label>

          <!-- Season is required. For existing leagues it is shown read-only (no uploading to old seasons). -->
          <label>
            Season
            <ng-container *ngIf="requiresExistingLeague; else newLeagueSeasonSelect">
              <input [(ngModel)]="season" placeholder="e.g., 2024/2025" [readonly]="uploadType==='INCREMENTAL'"/>
            </ng-container>
            <ng-template #newLeagueSeasonSelect>
              <select [(ngModel)]="season">
                <option value="">Select season...</option>
                <option *ngFor="let s of allowedSeasons" [value]="s">{{ s }}</option>
              </select>
            </ng-template>
          </label>

          <!-- Help text per type -->
          <div style="grid-column: 1 / -1;" class="hint">
            <ng-container [ngSwitch]="uploadType">
              <div *ngSwitchCase="'NEW_LEAGUE'">Creates a new league. All matches will be inserted under the provided season.</div>
              <div *ngSwitchCase="'FULL_REPLACE'">Deletes existing matches for the selected league/season and uploads the provided data. You can manually change the season here to correct mistakes; a new season entry will be created if needed.</div>
              <div *ngSwitchCase="'INCREMENTAL'">Updates results for existing fixtures; safe to run multiple times. Uploading to past seasons is disabled.</div>
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
        <label style="display:block; margin:6px 0;" title="Enable when your text is clean and already in the accepted formats. Disable to let the parser ignore headers/noise.">
          <input type="checkbox" [(ngModel)]="strictText"/>
          Strict mode (reject messy inputs; require clean format)
        </label>
        <div class="hint" style="margin: -4px 0 8px 0;">
          When to use: turn this ON for clean, well-formatted EPL-style inputs or the single-line format so the system strictly validates and catches mistakes.
          Turn this OFF for messy copy-pastes that include extra headers like 'FRANCE:', 'Standings', group names, or status markers (AET/FT) so the parser can ignore that noise.
        </div>
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
              <optgroup *ngFor="let g of groupedFixturesLeagues" [label]="g.groupLabel">
                <option *ngFor="let opt of g.options" [ngValue]="opt.leagueId">{{ opt.label }}</option>
              </optgroup>
            </select>
          </label>
          <label>
            <input type="checkbox" [(ngModel)]="fixturesStrictMode"/>
            Strict mode (legacy exact 6-line blocks)
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

      <!-- Extra feedback for fixtures/text uploads -->
      <div *ngIf="processedMatches || (ignoredLines?.length || 0) > 0" class="card" style="border-color:#94a3b8">
        <h3 style="color:#334155">Parser Feedback</h3>
        <div *ngIf="processedMatches">Processed matches: {{processedMatches}}</div>
        <div *ngIf="ignoredLines?.length">Ignored lines (sample):
          <ul>
            <li *ngFor="let l of ignoredLines">{{l}}</li>
          </ul>
        </div>
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
      <div *ngIf="(warnObjects?.length || 0) > 0 || (warnStrings?.length || 0) > 0" class="card" style="border-color:#fb923c">
        <h3 style="color:#b45309">Warnings</h3>
        <ul *ngIf="(warnObjects?.length || 0) > 0">
          <li *ngFor="let w of warnObjects">{{w.homeTeam}} vs {{w.awayTeam}} — {{w.reason}}</li>
        </ul>
        <ul *ngIf="(warnStrings?.length || 0) > 0">
          <li *ngFor="let s of warnStrings">{{s}}</li>
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
  competitions: string[] = [];
  get filteredCountries(): readonly string[] {
    const q = this.countryFilter?.toLowerCase().trim();
    if (!q) return this.countries;
    return this.countries.filter(c => c.toLowerCase().includes(q));
  }
  displayContext(c: string): string {
    return this.competitions.includes(c) ? `[Comp] ${c}` : c;
  }

  // League selection for NEW_LEAGUE mode
  leagueSelect: string = '';
  leagueManual: string = '';
  useManualLeague: boolean = false;
  get leaguesForCountry(): readonly string[] {
    return this.country ? (COUNTRY_LEAGUES[this.country] || []) : [];
  }
  onCountryChange(){
    // Reset league selection when country changes
    this.leagueSelect = '';
    // Keep manual if user prefers manual mode, but clear value to avoid stale binding
    this.leagueManual = '';
    // Update composed league name
    this.syncLeagueNameFromInputs();
  }
  onLeagueSelectChange(){
    // When selecting from dropdown, turn off manual mode if a value chosen
    if (this.leagueSelect) this.useManualLeague = false;
    this.syncLeagueNameFromInputs();
  }
  onLeagueManualChange(){
    // When typing manually, prefer manual mode
    if (this.leagueManual?.trim()) this.useManualLeague = true;
    this.syncLeagueNameFromInputs();
  }
  onManualToggleChange(){
    // If manual mode is turned off, clear manual value
    if (!this.useManualLeague) this.leagueManual = '';
    // If manual mode is turned on, clear select to avoid confusion
    if (this.useManualLeague) this.leagueSelect = '';
    this.syncLeagueNameFromInputs();
  }
  private syncLeagueNameFromInputs(){
    if (this.requiresExistingLeague) return; // in these modes leagueName is set from selection API
    const choice = this.useManualLeague ? this.leagueManual?.trim() : this.leagueSelect?.trim();
    this.leagueName = (choice || '');
  }

  // Fixtures-only toggle remains
  fullReplace = true; // for fixtures tab checkbox

  // Existing leagues dataset
  leagues: { id: number; name: string; country: string; season: string; }[] = [];
  selectedLeague: number | null = null;

  // Fixtures upload state
  fixturesLeagueId: number | null = null;
  fixturesSeason: string = '';
  fixturesLeagueName: string = '';
  fixturesCountry: string = '';
  fixturesRawText: string = '';
  // Grouped leagues for fixtures dropdown
  groupedFixturesLeagues: GroupedLeagueDTO[] = [];
  private fixturesOptionById: Record<number, { season: string; country: string; leagueName: string }> = {};

  file?: File;
  text = '';

  message = '';
  success = false;
  errors: string[] = [];
  results: { updated?: any[]; skipped?: any[]; warnings?: any[] } = {};
  // Enhanced feedback fields (fixtures/text uploads)
  processedMatches: number = 0;
  ignoredLines: string[] = [];
  warnStrings: string[] = [];
  warnObjects: any[] = [];
  // Strict mode toggles
  strictText: boolean = false;
  fixturesStrictMode: boolean = false;

  seasons: Season[] = [];
  seasonId: number | null = null;

  // Allowed seasons for new league uploads (dropdown)
  allowedSeasons: string[] = Array.from({ length: 11 }, (_, i) => `${2015 + i}/${2016 + i}`);

  get requiresExistingLeague(): boolean {
    return this.uploadType === 'FULL_REPLACE' || this.uploadType === 'INCREMENTAL';
  }

  constructor(private api: MatchUploadService, private leagueApi: LeagueService, private seasonApi: SeasonService, private http: HttpClient, private leagueContext: LeagueContextService) {
    // Load grouped leagues for fixtures tab (grouped by country + league name; seasons latest -> oldest)
    this.leagueApi.getGroupedLeaguesForUpload().subscribe(groups => {
      this.groupedFixturesLeagues = groups || [];
      // Build quick lookup by leagueId to fill details on selection
      this.fixturesOptionById = {};
      for (const g of this.groupedFixturesLeagues) {
        for (const opt of (g.options || [])) {
          if (opt && typeof opt.leagueId === 'number') {
            this.fixturesOptionById[opt.leagueId] = { season: opt.season, country: g.country, leagueName: g.leagueName };
          }
        }
      }
    });

    // Dynamically load contexts (countries + competitions) with fallback to static constants
    this.http.get<any>('/api/matches/upload/api/options/contexts').subscribe({
      next: (res) => {
        try {
          const backendCountries = Array.isArray(res?.countries) ? (res.countries as string[]) : [];
          const countries = [...backendCountries, ...(COUNTRIES as string[])];
          const compGroups = res?.competitions || {};
          const flatComps: string[] = [];
          for (const k of Object.keys(compGroups)) {
            const arr = compGroups[k];
            if (Array.isArray(arr)) flatComps.push(...arr);
          }
          // Assign lists
          this.competitions = flatComps;
          // Merge for a single select list: Countries first, then competitions. De-duplicate and sort alphabetically.
          const merged = [...countries, ...this.competitions];
          const seen = new Set<string>();
          const deduped = merged.filter(v => {
            const key = (v || '').trim();
            if (!key) return false;
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
          });
          // Keep simple alpha sort for usability
          this.countries = deduped.slice().sort((a, b) => a.localeCompare(b));
        } catch {
          this.countries = COUNTRIES;
          this.competitions = [];
        }
      },
      error: _ => {
        this.countries = COUNTRIES;
        this.competitions = [];
      }
    });
  }

  onFile(ev: Event){
    const input = ev.target as HTMLInputElement;
    if (input.files && input.files.length){
      this.file = input.files[0];
    }
  }

  onFixturesLeagueChange(){
    if (this.fixturesLeagueId != null && this.fixturesOptionById[this.fixturesLeagueId]){
      const info = this.fixturesOptionById[this.fixturesLeagueId];
      this.fixturesLeagueName = info.leagueName;
      this.fixturesCountry = info.country;
      this.fixturesSeason = info.season;
    } else {
      this.fixturesLeagueName = '';
      this.fixturesCountry = '';
      this.fixturesSeason = '';
    }
  }

  onUploadTypeChange(){
    // No need to load flat leagues; grouped list is already loaded in ctor
    if (this.requiresExistingLeague) {
      // keep current grouped list and await user selection
    } else {
      // Reset fields for new league upload
      this.selectedLeague = null;
      this.leagues = [];
      this.country = '';
      this.season = '';
      this.seasons = [];
      this.seasonId = null;
      // Reset league selection UX for new league mode
      this.leagueSelect = '';
      this.leagueManual = '';
      this.useManualLeague = false;
      this.leagueName = '';
    }
  }

  onLeagueSelected(){
    if (this.selectedLeague) {
      const lid = Number(this.selectedLeague);
      if (!Number.isNaN(lid) && lid > 0) this.leagueContext.setCurrentLeagueId(lid);
      const info = this.fixturesOptionById[lid];
      if (info) {
        this.country = info.country;
        this.season = info.season;
        this.leagueName = info.leagueName;
      } else {
        this.leagueApi.getLeagueDetails(lid).subscribe(details => {
          this.country = details.country;
          this.season = details.season;
          this.leagueName = details.name; // keep API contract
        });
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
    this.api.uploadUnifiedCsv(this.uploadType, this.leagueName, this.country, this.season, this.file, { seasonId: null, leagueId: leagueIdOpt, autoDetectSeason: false, allowSeasonAutoCreate: this.uploadType==='FULL_REPLACE' }).subscribe({
      next: res => this.handleResult(res),
      error: err => this.handleHttpError(err)
    });
  }

  uploadText(){
    this.resetFeedback();
    if (!this.validateMeta()) return;
    const leagueIdOpt = this.requiresExistingLeague && this.selectedLeague ? Number(this.selectedLeague) : null;
    this.api.uploadUnifiedText(this.uploadType, this.leagueName, this.country, this.season, this.text, { seasonId: null, leagueId: leagueIdOpt, autoDetectSeason: false, strict: this.strictText, allowSeasonAutoCreate: this.uploadType==='FULL_REPLACE' }).subscribe({
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
      fullReplace: this.fullReplace,
      rawText: this.fixturesRawText,
      strictMode: this.fixturesStrictMode
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
    // Process warnings into strings vs objects for display compatibility
    this.warnStrings = [];
    this.warnObjects = [];
    const ws: any[] = this.results.warnings || [];
    for (const w of ws) {
      if (w && typeof w === 'object' && ('reason' in w || 'homeTeam' in w || 'awayTeam' in w)) {
        this.warnObjects.push(w);
      } else if (typeof w === 'string') {
        this.warnStrings.push(w);
      }
    }
    // Enhanced feedback fields
    this.processedMatches = res?.processedMatches ?? 0;
    this.ignoredLines = res?.ignoredLines || [];
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
    const skippedCount = (this.results.skipped?.length || 0);
    if (this.success) {
      const skippedNote = skippedCount === 0 ? ' No matches were skipped.' : ` Skipped ${skippedCount} match(es).`;
      this.message = baseMsg + suffix + skippedNote;
    } else {
      // Summarize strict out-of-window errors when present
      const out = this.errors.filter(e => /out-of-window|outside season window/i.test(e)).length;
      this.message = out > 0 ? `${out} matches rejected because they are outside season window (strict mode).` : (res?.message || 'Upload failed.');
    }

    // If upload succeeded, set the active league in context.
    if (this.success) {
      // Prefer returned leagueId if backend provides it
      const returnedLeagueId = (res && (res.leagueId || res.leagueID || res.league_id)) ? Number(res.leagueId || res.leagueID || res.league_id) : null;
      if (returnedLeagueId && !Number.isNaN(returnedLeagueId) && returnedLeagueId > 0) {
        this.leagueContext.setCurrentLeagueId(returnedLeagueId);
      } else if (this.requiresExistingLeague && this.selectedLeague) {
        const lid = Number(this.selectedLeague);
        if (!Number.isNaN(lid) && lid > 0) this.leagueContext.setCurrentLeagueId(lid);
      } else {
        // NEW_LEAGUE path: attempt to resolve the newly created league id by querying leagues and matching name/country/season
        this.leagueApi.getLeagues().subscribe((ls) => {
          const key = (s: string) => (s || '').trim().toLowerCase().replace(/\s+/g, ' ');
          const match = (ls || []).find(l => key(l.name) === key(this.leagueName) && key(l.country) === key(this.country) && key(l.season) === key(this.season));
          if (match?.id) this.leagueContext.setCurrentLeagueId(match.id);
        });
      }
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

    // NEW_LEAGUE requires country, season, and a league (from dropdown or manual)
    if (!this.country.trim()) { this.success = false; this.message = 'Please select a country or competition.'; return false; }
    if (!this.season.trim()) { this.success = false; this.message = 'Please select a season.'; return false; }
    const chosen = (this.useManualLeague ? this.leagueManual?.trim() : this.leagueSelect?.trim()) || '';
    if (!chosen) {
      this.success = false; this.message = 'Please select a league or enter it manually.'; return false;
    }
    this.leagueName = chosen; // ensure DTO binding uses the chosen league
    return true;
  }
}
