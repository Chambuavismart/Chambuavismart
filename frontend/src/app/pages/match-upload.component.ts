import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatchUploadService } from '../services/match-upload.service';
import { LeagueService, LeagueDto } from '../services/league.service';
import { HttpClient } from '@angular/common/http';

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

      <div class="card">
        <div class="grid">
          <label>League Name <input [(ngModel)]="leagueName" placeholder="e.g., Premier League"/></label>
          <label>Country <input [(ngModel)]="country" placeholder="e.g., England"/></label>
          <label>Season <input [(ngModel)]="season" placeholder="e.g., 2024/2025"/></label>
          <label><input type="checkbox" [(ngModel)]="fullReplace"/> Full replace existing matches</label>
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
        <div class="grid">
          <label>
            League
            <select [(ngModel)]="fixturesLeagueId" (ngModelChange)="onFixturesLeagueChange()">
              <option [ngValue]="null">Select a league...</option>
              <option *ngFor="let l of fixturesLeagues" [ngValue]="l.id">{{l.name}} ({{l.season}})</option>
            </select>
          </label>
          <label>
            Season
            <input [(ngModel)]="fixturesSeason" placeholder="e.g., 2024/2025"/>
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
        <button (click)="uploadFixtures()" [disabled]="!fixturesLeagueId || !fixturesSeason.trim() || !fixturesRawText.trim()">Upload Fixtures</button>
      </div>

      <div *ngIf="message" class="alert" [class.success]="success" [class.error]="!success">
        {{message}}
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
    input, textarea{width:100%;padding:6px;border:1px solid #d1d5db;border-radius:6px}
    button{padding:8px 16px;background:#10b981;color:#fff;border:none;border-radius:6px;cursor:pointer}
    .hint{color:#6b7280;font-size:12px}
    .alert{padding:10px;border-radius:6px;margin-top:12px}
    .alert.success{background:#ecfdf5;color:#065f46;border:1px solid #10b981}
    .alert.error{background:#fef2f2;color:#991b1b;border:1px solid #ef4444}
  `]
})
export class MatchUploadComponent {
  activeTab: 'csv' | 'text' | 'fixtures' = 'csv';
  leagueName = '';
  country = '';
  season = '';
  fullReplace = true;

  // Fixtures upload state
  fixturesLeagueId: number | null = null;
  fixturesSeason: string = '';
  fixturesRawText: string = '';
  fixturesLeagues: { id: number; name: string; season: string; }[] = [];

  file?: File;
  text = '';

  message = '';
  success = false;
  errors: string[] = [];

  constructor(private api: MatchUploadService, private leagueApi: LeagueService, private http: HttpClient) {
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
  }

  uploadCsv(){
    this.resetFeedback();
    if (!this.validateMeta()) return;
    if (!this.file) return;
    this.api.uploadCsv(this.leagueName, this.country, this.season, this.file, this.fullReplace).subscribe({
      next: res => this.handleResult(res),
      error: err => this.handleHttpError(err)
    });
  }

  uploadText(){
    this.resetFeedback();
    if (!this.validateMeta()) return;
    this.api.uploadText(this.leagueName, this.country, this.season, this.text, this.fullReplace).subscribe({
      next: res => this.handleResult(res),
      error: err => this.handleHttpError(err)
    });
  }

  uploadFixtures(){
    this.resetFeedback();
    if (!this.fixturesLeagueId || !this.fixturesSeason.trim() || !this.fixturesRawText.trim()){
      this.success = false; this.message = 'Please select a league, provide season and paste fixtures text.'; return;
    }
    const payload = {
      leagueId: this.fixturesLeagueId,
      season: this.fixturesSeason,
      fullReplace: this.fullReplace,
      rawText: this.fixturesRawText
    };
    this.http.post<any>('/api/fixtures/upload', payload).subscribe({
      next: res => {
        this.handleResult(res);
        if (res?.success){
          // Clear textarea on success
          this.fixturesRawText = '';
          // Show specific success message as toast-equivalent
          this.message = res.message || `Fixtures uploaded successfully.`;
        }
      },
      error: err => this.handleHttpError(err)
    });
  }

  private handleResult(res: any){
    this.success = !!res?.success;
    this.errors = res?.errors || [];
    this.message = this.success ? `Upload successful. Inserted ${res.inserted}, deleted ${res.deleted}.` : (res?.message || 'Upload failed.');
    // Clear raw text textarea on successful upload as requested
    if (this.success && this.activeTab === 'text') {
      this.text = '';
    }
  }
  private handleHttpError(err: any){
    this.success = false;
    this.message = err?.error?.message || 'Server error during upload';
    if (err?.error?.errors) this.errors = err.error.errors;
  }
  private resetFeedback(){ this.message=''; this.success=false; this.errors=[]; }
  private validateMeta(){
    if (!this.leagueName.trim() || !this.country.trim() || !this.season.trim()){
      this.success = false; this.message = 'Please provide league name, country and season.'; return false;
    }
    return true;
  }
}
