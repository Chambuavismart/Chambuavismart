import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatchUploadService } from '../services/match-upload.service';
import { COUNTRIES } from '../shared/countries.constant';
import { COUNTRY_LEAGUES } from '../shared/country-leagues.constant';

@Component({
  selector: 'app-archives-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <!-- Page header / intro -->
      <div class="rounded p-4 mb-4 bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-100">
        <h1 class="text-2xl font-semibold mb-1">Upload Historical Matches (Raw Text Only)</h1>
        <p class="text-gray-700 text-sm">Paste past season match data in the supported formats below.</p>
      </div>

      <div class="grid gap-4 md:grid-cols-2">
      <div class="rounded shadow p-4 bg-white">
        <!-- Raw Text Upload for Archives -->
        <div class="rounded p-0 bg-white">
          <h2 class="text-xl font-semibold mb-2">Raw Text Upload</h2>
          <div class="card mb-3">
            <div class="grid" style="grid-template-columns: 1fr 1fr; gap: 6px;">
              <label title="Create a brand new league with provided country and season.">
                <input type="radio" [(ngModel)]="rawType" value="NEW_LEAGUE" />
                New League Upload
              </label>
              <label title="Replace all existing matches for a selected league and season.">
                <input type="radio" [(ngModel)]="rawType" value="FULL_REPLACE" />
                Complete Replacement of Existing League
              </label>
            </div>
          </div>
          <div class="grid gap-3">
            <div class="grid gap-1">
              <label>League </label>
              <select class="input" [(ngModel)]="leagueSelect" [disabled]="!rawCountry" (ngModelChange)="onLeagueSelectChange()">
                <option value="">Select league...</option>
                <option *ngFor="let l of leaguesForCountry" [value]="l">{{ l }}</option>
              </select>
              <div class="text-xs text-gray-600" *ngIf="!rawCountry">Select a country first to choose a league.</div>
              <label class="flex items-center gap-2 mt-1">
                <input type="checkbox" [(ngModel)]="useManualLeague" (change)="onManualToggleChange()" />
                <span>Can't find your league? Enter manually</span>
              </label>
              <input *ngIf="useManualLeague" class="input" [(ngModel)]="rawLeagueName" (ngModelChange)="onLeagueManualChange()" placeholder="Type league name..." />
            </div>
            <div class="grid gap-1">
              <label>Country</label>
              <select class="input" [(ngModel)]="rawCountry" (ngModelChange)="onCountryChange()">
                <option value="">Select country...</option>
                <option *ngFor="let c of countries" [value]="c">{{ c }}</option>
              </select>
            </div>
            <div class="grid gap-1">
              <label>Season </label>
              <select class="input" [(ngModel)]="rawSeason">
                <option value="">Select season...</option>
                <option *ngFor="let s of seasons" [value]="s">{{ s }}</option>
              </select>
              <div class="text-xs text-gray-600">Creates a new league. All matches will be inserted under the provided season.</div>
            </div>
            <div class="grid gap-1">
              <label>Accepted formats:</label>
              <div class="text-gray-600 text-sm">A) Single-line: YYYY-MM-DD, Home Team - Away Team, X-Y</div>
              <div class="text-gray-600 text-sm">B) Vertical blocks (dates like dd.MM. HH:mm, year inferred from Season):</div>
              <pre class="text-xs" style="white-space: pre-wrap; background:#f8fafc; padding:8px; border-radius:6px; border:1px solid #e5e7eb;">
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
            </div>
            <div class="grid gap-1">
              <label>Raw Text</label>
              <textarea class="input" rows="14" style="min-height: 320px; resize: vertical;" [(ngModel)]="rawText" placeholder="Paste your rounds and matches here..."></textarea>
            </div>
            <div class="flex items-center gap-3 mt-2">
              <button class="btn btn-primary" [disabled]="rawLoading || !rawLeagueName?.trim() || !rawCountry?.trim() || !rawSeason?.trim() || !rawText?.trim()" type="button" (click)="onUploadRawText()">
                {{ rawLoading ? 'Uploading…' : 'Upload Text' }}
              </button>
              <button class="btn" type="button" (click)="rawText=''; rawMessage=''; rawErrors=[];">Clear</button>
            </div>
            <div *ngIf="rawMessage" class="mt-2" [ngClass]="{ 'text-green-700 bg-green-50 border border-green-300 rounded p-2': rawSuccess, 'text-red-700 bg-red-50 border border-red-300 rounded p-2': !rawSuccess }">{{ rawMessage }}</div>
            <div *ngIf="rawErrors?.length" class="mt-2 text-sm text-red-700 bg-red-50 border border-red-300 rounded p-2">
              <div>Errors:</div>
              <ul class="list-disc pl-5">
                <li *ngFor="let e of rawErrors">{{ e }}</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
      </div>
    </div>
  `
})
export class ArchivesUploadComponent {
  countries: readonly string[] = COUNTRIES;
  seasons: readonly string[] = [];
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
  // Upload type: incremental removed for archives
  rawType: 'NEW_LEAGUE' | 'FULL_REPLACE' = 'NEW_LEAGUE';

  onCountryChange(){
    if (!this.rawCountry) {
      this.seasons = [];
    } else {
      // Populate seasons from 2018/2019 upwards to 2025/2026 when a country is selected
      this.seasons = this.generateSeasons(2018, 2025);
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
      // Default to list usage
      if (this.useManualLeague) {
        this.rawLeagueName = '';
      }
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
      this.rawMessage = 'Please fill Country, League, Season and paste Raw Text.';
      this.rawSuccess = false;
      return;
    }
    this.rawLoading = true;
    // Use FULL_REPLACE for archives so it's idempotent and creates teams if needed
    this.matchUpload.uploadUnifiedText('FULL_REPLACE', this.rawLeagueName.trim(), this.rawCountry.trim(), this.rawSeason.trim(), this.rawText, { allowSeasonAutoCreate: true }).subscribe({
      next: (res: any) => {
        this.rawSuccess = !!res?.success;
        this.rawErrors = res?.errors || [];
        const inserted = (res?.inserted !== undefined ? res.inserted : res?.insertedCount) ?? 0;
        const deleted = (res?.deleted !== undefined ? res.deleted : res?.deletedCount) ?? 0;
        if (this.rawSuccess){
          this.rawMessage = `Processed. Inserted ${inserted}, deleted ${deleted}.`;
          // clear text on success
          this.rawText = '';
        } else {
          this.rawMessage = res?.message || 'Upload failed.';
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
