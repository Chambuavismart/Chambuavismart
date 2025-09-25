import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatchUploadService } from '../services/match-upload.service';
import { COUNTRIES } from '../shared/countries.constant';
import { COUNTRY_LEAGUES } from '../shared/country-leagues.constant';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-archives-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <style>
      :host { display:block; color:#e0e0e0; font-family: Inter, Roboto, Arial, sans-serif; }
      .fs-layout { display:flex; min-height: calc(100vh - 0px); background:#0a0a0a; }
      .fs-sidebar { width: 20%; min-width: 240px; background:#1a1a1a; padding:16px 12px; position:sticky; top:0; height:100vh; overflow:auto; border-right:1px solid #2a2a2a; }
      .brand { color:#fff; font-weight:700; font-size:18px; margin-bottom:16px; }
      .nav-list { list-style:none; margin:0; padding:0; }
      .nav-item { display:flex; align-items:center; gap:10px; padding:10px 12px; color:#ccc; border-radius:8px; cursor:pointer; transition: background 0.2s ease, color 0.2s ease; }
      .nav-item:hover { background:#333333; color:#007bff; }
      .nav-item:hover i { color:#007bff; }
      .nav-item.active { background:#007bff; color:#ffffff; }
      .nav-item.active i { color:#ffffff; }
      .nav-item i { width:18px; text-align:center; color:#6ea8fe; }

      .fs-main { width: 80%; padding:20px; }
      .fs-grid { display:grid; grid-template-columns: 1fr; gap:16px; }
      .card { background:#121212; border:1px solid #232323; border-radius:8px; box-shadow: 0 2px 8px rgba(0,0,0,0.35); }
      .card .card-body { padding:16px; }
      .card .card-title { font-size:20px; font-weight:700; color:#ffffff; margin:0 0 6px 0; }
      .card .card-subtitle { color:#cccccc; font-size:14px; }

      .muted { color:#bbbbbb; }
      .hint { color:#9aa0a6; font-size:12px; }
      .hr { height:1px; background:#2a2a2a; border:0; margin:10px 0; }

      label { font-size:13px; color:#d0d0d0; margin-bottom:6px; }
      .input, select, textarea { width:100%; background:#2a2a2a; color:#ffffff; border:1px solid #404040; border-radius:8px; padding:10px 12px; outline:none; transition: border-color 0.2s ease, box-shadow 0.2s ease; }
      .input:focus, select:focus, textarea:focus { border-color:#007bff; box-shadow: 0 0 0 3px rgba(0,123,255,0.12); }
      textarea { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; line-height:1.5; }
      .row { display:grid; gap:12px; }
      .row-2 { grid-template-columns: 1fr 1fr; }

      .radio-group { display:flex; gap:16px; align-items:center; }
      .radio-group label { display:flex; gap:8px; align-items:center; cursor:pointer; }

      .btns { display:flex; gap:10px; justify-content:flex-end; }
      .btn { border-radius:8px; padding:10px 14px; border:1px solid transparent; cursor:pointer; transition: background 0.2s ease, border-color 0.2s ease, transform 0.05s ease; font-weight:600; }
      .btn:active { transform: translateY(1px); }
      .btn-primary { background:#007bff; color:#fff; }
      .btn-primary:hover { background:#0056b3; }
      .btn-secondary { background:#6c757d; color:#fff; }
      .btn-secondary:hover { background:#5a6268; }
      .badge { display:inline-block; font-size:12px; border-radius:9999px; padding:2px 8px; }
      .badge-green { background:#113a2a; color:#50fa7b; border:1px solid #1f7a4c; }
      .badge-red { background:#3a1111; color:#ff6b6b; border:1px solid #7a1f1f; }

      pre.example { background:#0f0f0f; color:#e0e0e0; border:1px solid #232323; border-radius:8px; padding:12px; font-size:12px; white-space:pre-wrap; }

      .topbar { display:flex; align-items:center; justify-content:space-between; }
      .title { font-size:24px; font-weight:800; color:#ffffff; }
      .subtitle { color:#c9d1d9; font-size:14px; display:flex; align-items:center; gap:8px; }
      .subtitle i { color:#6ea8fe; }

      .feedback { border:1px solid #2a2a2a; border-radius:8px; padding:12px; background:#101010; }
      .feedback h4 { margin:0 0 6px 0; font-weight:700; }

      .kpi { display:flex; gap:8px; align-items:center; }
      .kpi .dot { width:8px; height:8px; border-radius:50%; }
      .kpi .dot.green { background:#2ecc71; }
      .kpi .dot.red { background:#e74c3c; }

      .char-count { text-align:right; color:#9aa0a6; font-size:12px; margin-top:6px; }

      .sidebar-toggle { display:none; }
      @media (max-width: 1024px){ .fs-sidebar { position:relative; height:auto; } .fs-main { width:100%; padding:12px; } .fs-layout { flex-direction:column; } }
      @media (max-width: 768px){ .fs-sidebar.collapsed { display:none; } .sidebar-toggle { display:inline-flex; align-items:center; gap:8px; } }
      @media (max-width: 640px){ .row-2 { grid-template-columns: 1fr; } }
    </style>

    <div class="fs-layout" role="Main">
      <!-- Sidebar -->
      <aside class="fs-sidebar" [class.collapsed]="!sidebarOpen" [attr.aria-hidden]="!sidebarOpen ? 'true' : 'false'" aria-label="Primary Navigation">
        <div class="brand">ChambuVS</div>
        <ul class="nav-list" role="menu">
          <li class="nav-item" role="menuitem" title="Home" [class.active]="activeNav==='Home'" (click)="onNavClick('/', 'Home', $event)"><i class="fa fa-home" aria-hidden="true"></i> Home</li>
          <li class="nav-item" role="menuitem" title="Fixtures" [class.active]="activeNav==='Fixtures'" (click)="onNavClick('/fixtures', 'Fixtures', $event)"><i class="fa fa-calendar" aria-hidden="true"></i> Fixtures</li>
          <li class="nav-item" role="menuitem" title="Match Analysis" [class.active]="activeNav==='Match Analysis'" (click)="onNavClick('/match-analysis', 'Match Analysis', $event)"><i class="fa fa-chart-line" aria-hidden="true"></i> Match Analysis</li>
          <li class="nav-item" role="menuitem" title="Form Guide" [class.active]="activeNav==='Form Guide'" (click)="onNavClick('/form-guide', 'Form Guide', $event)"><i class="fa fa-stream" aria-hidden="true"></i> Form Guide</li>
          <li class="nav-item" role="menuitem" title="Quick Insights" [class.active]="activeNav==='Quick Insights'" (click)="onNavClick('/quick-insights', 'Quick Insights', $event)"><i class="fa fa-bolt" aria-hidden="true"></i> Quick Insights</li>
          <li class="nav-item" role="menuitem" title="League Table" [class.active]="activeNav==='League Table'" (click)="onNavClick('/league-table', 'League Table', $event)"><i class="fa fa-table" aria-hidden="true"></i> League Table</li>
          <li class="nav-item" role="menuitem" title="Fixtures Analysis" [class.active]="activeNav==='Fixtures Analysis'" (click)="onNavClick('/fixtures-analysis', 'Fixtures Analysis', $event)"><i class="fa fa-chart-bar" aria-hidden="true"></i> Fixtures Analysis</li>
          <li class="nav-item" role="menuitem" title="Fixture Analysis History" [class.active]="activeNav==='Fixture Analysis History'" (click)="onNavClick('/fixture-analysis-history', 'Fixture Analysis History', $event)"><i class="fa fa-history" aria-hidden="true"></i> Fixture Analysis History</li>
          <li class="nav-item" role="menuitem" title="Data Management" [class.active]="activeNav==='Data Management'" aria-current="page" (click)="onNavClick('/data-management', 'Data Management', $event)"><i class="fa fa-database" aria-hidden="true"></i> Data Management</li>
          <li class="nav-item" role="menuitem" title="Team Search" [class.active]="activeNav==='Team Search'" (click)="onNavClick('/team-search', 'Team Search', $event)"><i class="fa fa-search" aria-hidden="true"></i> Team Search</li>
          <li class="nav-item" role="menuitem" title="Admin" [class.active]="activeNav==='Admin'" (click)="onNavClick('/admin', 'Admin', $event)"><i class="fa fa-user-shield" aria-hidden="true"></i> Admin</li>
        </ul>
      </aside>

      <!-- Main Content -->
      <main class="fs-main">
        <div class="fs-grid">
          <!-- Top Card: Title -->
          <section class="card" aria-label="Page header">
            <div class="card-body topbar">
              <div>
                <div class="title">Upload Historical Matches</div>
                <div class="subtitle"><i class="fa fa-info-circle" aria-hidden="true"></i> Paste past season match data in the supported format below.</div>
              </div>
              <button class="btn btn-secondary sidebar-toggle" type="button" (click)="toggleSidebar()" aria-label="Toggle navigation sidebar" [attr.aria-expanded]="sidebarOpen ? 'true' : 'false'">
                <i class="fa fa-bars" aria-hidden="true"></i>
                <span style="font-weight:600">Menu</span>
              </button>
            </div>
          </section>

          <!-- Radio Card -->
          <section class="card" aria-label="Upload type">
            <div class="card-body">
              <div class="radio-group" role="radiogroup" aria-label="Upload Type">
                <label title="Create a brand new league with provided country and season.">
                  <input type="radio" [(ngModel)]="rawType" value="NEW_LEAGUE" aria-label="New League Upload" />
                  New League Upload
                </label>
                <label title="Replace all existing matches for a selected league and season.">
                  <input type="radio" [(ngModel)]="rawType" value="FULL_REPLACE" aria-label="Complete Replacement of Existing League" />
                  Complete Replacement
                </label>
              </div>
            </div>
          </section>

          <!-- League/Country Card -->
          <section class="card" aria-label="League and Country">
            <div class="card-body row row-2">
              <div>
                <label for="countrySelect">Country or Competition</label>
                <input id="countrySearch" class="input" type="text" [(ngModel)]="countryFilter" placeholder="Search country or competition..." aria-label="Search country or competition" />
                <select id="countrySelect" class="input" [(ngModel)]="rawCountry" (ngModelChange)="onCountryChange()" aria-label="Country or Competition">
                  <option value="">Select country or competition...</option>
                  <option *ngFor="let c of filteredCountries" [value]="c">{{ displayContext(c) }}</option>
                </select>
              </div>
              <div>
                <label for="leagueSelect">League</label>
                <select id="leagueSelect" class="input" [(ngModel)]="leagueSelect" [disabled]="!rawCountry" (ngModelChange)="onLeagueSelectChange()" aria-label="League">
                  <option value="">Select league...</option>
                  <option *ngFor="let l of leaguesForCountry" [value]="l">{{ l }}</option>
                </select>
                <div class="hint" *ngIf="!rawCountry">Select a country first to choose a league.</div>
                <label class="radio-group" style="margin-top:8px">
                  <input type="checkbox" [(ngModel)]="useManualLeague" (change)="onManualToggleChange()" aria-label="Enter league manually" />
                  <span>Can't find your league? Enter manually</span>
                </label>
                <input *ngIf="useManualLeague" class="input" [(ngModel)]="rawLeagueName" (ngModelChange)="onLeagueManualChange()" placeholder="Type league name..." aria-label="Manual league name" />
              </div>
            </div>
          </section>

          <!-- Season Card -->
          <section class="card" aria-label="Season">
            <div class="card-body">
              <label for="seasonSelect">Season</label>
              <select id="seasonSelect" class="input" [(ngModel)]="rawSeason" aria-label="Season">
                <option value="">Select season...</option>
                <option *ngFor="let s of seasons" [value]="s">{{ s }}</option>
              </select>
              <div class="hint" style="margin-top:6px">Creates a new league. All matches will be inserted under the provided season.</div>
            </div>
          </section>

          <!-- Format Card -->
          <section class="card" aria-label="Accepted formats">
            <div class="card-body">
              <div class="card-title">Format</div>
              <div class="muted">A) Single-line: YYYY-MM-DD, Home Team - Away Team, X-Y</div>
              <div class="muted">B) Vertical blocks (dates like dd.MM. HH:mm, year inferred from Season). Example:</div>
              <pre class="example">Round 22
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
2</pre>
            </div>
          </section>

          <!-- Textarea Card -->
          <section class="card" aria-label="Raw text input">
            <div class="card-body">
              <label for="rawText">Raw Text</label>
              <textarea id="rawText" rows="12" [(ngModel)]="rawText" placeholder="Paste your matches here..." aria-label="Raw match text" style="min-height:320px; resize:vertical;"></textarea>
              <div class="char-count">{{ (rawText?.length || 0) | number }} characters</div>
              <div class="hr"></div>
              <div class="btns">
                <button class="btn btn-secondary" type="button" (click)="rawText=''; rawMessage=''; rawErrors=[];">Clear</button>
                <button class="btn btn-primary" [disabled]="rawLoading || !rawLeagueName?.trim() || !rawCountry?.trim() || !rawSeason?.trim() || !rawText?.trim()" type="button" (click)="onUploadRawText()">
                  {{ rawLoading ? 'Uploading…' : 'Raw Text Upload' }}
                </button>
              </div>
            </div>
          </section>

          <!-- Feedback Cards -->
          <section class="card" *ngIf="processedMatches || (ignoredLines?.length || 0) > 0" aria-label="Parser feedback">
            <div class="card-body">
              <h4 class="card-title" style="font-size:16px">Parser Feedback</h4>
              <div *ngIf="processedMatches" class="kpi"><span class="dot green"></span>Processed matches: {{processedMatches}}</div>
              <div *ngIf="ignoredLines?.length" class="muted" style="margin-top:6px">Ignored lines (sample):
                <ul style="margin:6px 0 0 18px;">
                  <li *ngFor="let l of ignoredLines" style="font-size:12px; color:#c0c0c0;">{{l}}</li>
                </ul>
              </div>
            </div>
          </section>

          <section class="card" *ngIf="(warnObjects?.length || 0) > 0 || (warnStrings?.length || 0) > 0" aria-label="Warnings">
            <div class="card-body">
              <h4 class="card-title" style="font-size:16px; color:#ffd166">Warnings</h4>
              <ul *ngIf="(warnObjects?.length || 0) > 0" style="margin:6px 0 0 18px;">
                <li *ngFor="let w of warnObjects" style="font-size:12px; color:#ffd166;">{{w.homeTeam}} <ng-container *ngIf="w.awayTeam">vs {{w.awayTeam}}</ng-container> — {{w.reason}}</li>
              </ul>
              <ul *ngIf="(warnStrings?.length || 0) > 0" style="margin:6px 0 0 18px;">
                <li *ngFor="let s of warnStrings" style="font-size:12px; color:#ffd166;">{{s}}</li>
              </ul>
            </div>
          </section>

          <section class="card" *ngIf="(skipped?.length || 0) > 0" aria-label="Skipped">
            <div class="card-body">
              <h4 class="card-title" style="font-size:16px; color:#ff6b6b">Skipped</h4>
              <ul style="margin:6px 0 0 18px;">
                <li *ngFor="let s of skipped" style="font-size:12px; color:#ff6b6b;">{{s.homeTeam}} <ng-container *ngIf="s.awayTeam">vs {{s.awayTeam}}</ng-container> — {{s.reason}}</li>
              </ul>
            </div>
          </section>

          <section *ngIf="rawMessage" aria-label="Upload status">
            <div class="feedback" [ngStyle]="rawSuccess ? {'border-color':'#1f7a4c'} : {'border-color':'#7a1f1f'}">
              <span class="badge" [ngClass]="rawSuccess ? 'badge-green' : 'badge-red'">{{ rawSuccess? 'Success' : 'Error' }}</span>
              <div style="margin-top:6px; color:#e0e0e0;">{{ rawMessage }}</div>
              <div *ngIf="rawErrors?.length" style="margin-top:10px;">
                <div style="color:#ffb3b3;">Errors:</div>
                <ul style="margin:6px 0 0 18px;">
                  <li *ngFor="let e of rawErrors" style="font-size:12px; color:#ffb3b3;">{{ e }}</li>
                </ul>
              </div>
            </div>
          </section>

        </div>
      </main>
    </div>

    <!-- Font Awesome is globally included in index.html; duplicate link removed -->
  `
})
export class ArchivesUploadComponent {
  private http = inject(HttpClient);
  apiBase: string = '';
  archives: any[] = [];
  archivesLoading: boolean = false;
  archivesError: string = '';

  constructor(){
    // Dynamically load contexts (countries + competitions) with fallback to static COUNTRIES
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
          this.competitions = flatComps;
          const merged = [...countries, ...this.competitions];
          const seen = new Set<string>();
          const deduped = merged.filter(v => {
            const key = (v || '').trim();
            if (!key) return false;
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
          });
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
  countries: readonly string[] = COUNTRIES;
  competitions: string[] = [];
  countryFilter: string = '';
  seasons: readonly string[] = [];
  get filteredCountries(): readonly string[] {
    const q = this.countryFilter?.toLowerCase().trim();
    if (!q) return this.countries;
    return this.countries.filter(c => c.toLowerCase().includes(q));
  }
  displayContext(c: string): string {
    return this.competitions.includes(c) ? `[Comp] ${c}` : c;
  }
  private generateSeasons(startYear: number, endYear: number): string[] {
    const arr: string[] = [];
    for (let y = startYear; y <= endYear; y++) {
      arr.push(`${y}/${y + 1}`);
    }
    return arr;
  }
  leaguesForCountry: string[] = [];
  leagueSelect: string = '';
  useManualLeague: boolean = false;
  private matchUpload = inject(MatchUploadService);

  // Raw text archive inputs/state
  rawCountry: string = '';
  rawLeagueName: string = '';
  rawSeason: string = '';
  rawText: string = '';
  rawLoading: boolean = false;
  rawMessage: string = '';
  rawSuccess: boolean = false;
  rawErrors: string[] = [];
  // Enhanced parser feedback fields
  processedMatches: number = 0;
  ignoredLines: string[] = [];
  warnStrings: string[] = [];
  warnObjects: any[] = [];
  skipped: any[] = [];
  // Strict toggle for archives raw text
  rawStrict: boolean = false;
  // Upload type: incremental removed for archives
  rawType: 'NEW_LEAGUE' | 'FULL_REPLACE' = 'NEW_LEAGUE';

  // Sidebar UI state
  sidebarOpen: boolean = true;
  activeNav: string = 'Data Management';

  ngOnInit(){
    try {
      this.sidebarOpen = window.innerWidth >= 768; // collapse by default on small screens
      const path = (window.location?.pathname || '').toLowerCase();
      if (path.includes('fixtures-analysis-history')) this.activeNav = 'Fixture Analysis History';
      else if (path.includes('fixtures-analysis')) this.activeNav = 'Fixtures Analysis';
      else if (path.includes('fixtures')) this.activeNav = 'Fixtures';
      else if (path.includes('match-analysis')) this.activeNav = 'Match Analysis';
      else if (path.includes('form-guide')) this.activeNav = 'Form Guide';
      else if (path.includes('quick-insights')) this.activeNav = 'Quick Insights';
      else if (path.includes('league-table')) this.activeNav = 'League Table';
      else if (path.includes('team-search')) this.activeNav = 'Team Search';
      else if (path.includes('admin')) this.activeNav = 'Admin';
      else this.activeNav = 'Data Management';
    } catch { /* noop */ }
  }

  toggleSidebar(){
    this.sidebarOpen = !this.sidebarOpen;
  }

  onNavClick(path: string, key: string, event?: Event){
    if (event) event.preventDefault();
    this.activeNav = key;
    try {
      window.location.href = path;
    } catch {
      // ignore navigation failures
    }
  }

  loadPdfArchives(){
    this.archivesLoading = true;
    this.archivesError = '';
    const url = '/api/matches/analysis-pdfs?page=0&size=50';
    this.http.get<any>(url).subscribe({
      next: (res) => {
        this.archives = Array.isArray(res?.content) ? res.content : [];
        this.archivesLoading = false;
      },
      error: (err) => {
        this.archivesLoading = false;
        this.archivesError = (err?.error?.message) || 'Failed to load PDF archives.';
      }
    });
  }

  onCountryChange(){
    if (!this.rawCountry) {
      this.seasons = [];
    } else {
      // Populate seasons from 2015/2016 upwards to 2025/2026 when a country is selected
      this.seasons = this.generateSeasons(2015, 2025);
    }
    // Clear any previously selected season
    this.rawSeason = '';
    // Update leagues list based on selected country
    const leagues = COUNTRY_LEAGUES[this.rawCountry] || [];
    this.leaguesForCountry = [...leagues];
    // Reset selection and manual flag if leagues available
    this.leagueSelect = '';
    if (this.leaguesForCountry.length === 0) {
      // No preset leagues -> allow manual entry
      this.useManualLeague = true;
      this.rawLeagueName = '';
    } else {
      // Preset leagues available -> default to dropdown selection mode
      this.useManualLeague = false;
      this.rawLeagueName = '';
    }
  }

  onLeagueSelectChange(){
    if (!this.useManualLeague) {
      this.rawLeagueName = this.leagueSelect || '';
    }
  }

  onManualToggleChange(){
    if (this.useManualLeague) {
      // Switching to manual: clear auto value and wait for user input
      this.rawLeagueName = '';
    } else {
      // Switching off manual: sync from selected option
      this.rawLeagueName = this.leagueSelect || '';
    }
  }

  onLeagueManualChange(){
    // rawLeagueName is already two-way bound; keep hook for symmetry/future validations
  }

  onUploadRawText(){
    this.rawMessage = '';
    this.rawErrors = [];
    this.rawSuccess = false;
    if (!this.rawCountry.trim() || !this.rawLeagueName.trim() || !this.rawSeason.trim() || !this.rawText.trim()){
      this.rawMessage = 'Please fill Country or Competition, League, Season and paste Raw Text.';
      this.rawSuccess = false;
      return;
    }
    this.rawLoading = true;
    // Use FULL_REPLACE for archives so it's idempotent and creates teams if needed
    this.matchUpload.uploadUnifiedText('FULL_REPLACE', this.rawLeagueName.trim(), this.rawCountry.trim(), this.rawSeason.trim(), this.rawText, { allowSeasonAutoCreate: true, strict: this.rawStrict }).subscribe({
      next: (res: any) => {
        this.rawSuccess = !!res?.success;
        this.rawErrors = res?.errors || [];
        // Parse warnings into strings vs objects
        this.warnStrings = [];
        this.warnObjects = [];
        const ws: any[] = res?.warnings || [];
        for (const w of (ws || [])) {
          if (w && typeof w === 'object' && ('reason' in w || 'homeTeam' in w || 'awayTeam' in w)) {
            this.warnObjects.push(w);
          } else if (typeof w === 'string') {
            this.warnStrings.push(w);
          }
        }
        this.skipped = res?.skipped || [];
        // Enhanced feedback fields
        this.processedMatches = res?.processedMatches ?? 0;
        this.ignoredLines = res?.ignoredLines || [];
        const inserted = (res?.inserted !== undefined ? res.inserted : res?.insertedCount) ?? 0;
        const deleted = (res?.deleted !== undefined ? res.deleted : res?.deletedCount) ?? 0;
        if (this.rawSuccess){
          const skippedCount = (this.skipped?.length || 0);
          let msg = `Processed. Inserted ${inserted}, deleted ${deleted}.`;
          msg += skippedCount === 0 ? ' No valid matches were skipped.' : ` Skipped ${skippedCount} match(es).`;
          this.rawMessage = msg;
          // clear text on success
          this.rawText = '';
        } else {
          // Friendly summary for strict out-of-window errors when present
          try {
            const errs: string[] = this.rawErrors || [];
            const out = errs.filter(e => /out-of-window|outside season window/i.test(e)).length;
            this.rawMessage = out > 0 ? `${out} matches rejected because they are outside season window (strict mode).` : (res?.message || 'Upload failed.');
          } catch {
            this.rawMessage = res?.message || 'Upload failed.';
          }
        }
        this.rawLoading = false;
      },
      error: (err) => {
        this.rawSuccess = false;
        this.rawMessage = err?.error?.message || 'Server error during upload';
        this.rawErrors = err?.error?.errors || [];
        this.rawLoading = false;
      }
    });
  }
}
