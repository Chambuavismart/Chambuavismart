import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-analysis-pdfs',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    :host { display:block; }
    .container { max-width: 1100px; margin: 0 auto; padding: 16px; }

    /* Hero header with subtle pitch pattern */
    .hero {
      position: relative; overflow: hidden; border-radius: 14px;
      background: linear-gradient(135deg, #e8f7ee 0%, #dff5ff 100%);
      border: 1px solid #bfe3cf; padding: 18px 18px 18px 18px;
    }
    .hero:before {
      content: ""; position: absolute; inset: 0; opacity: 0.12; pointer-events: none;
      background-image: repeating-linear-gradient(0deg, #7ad18f 0, #7ad18f 1px, transparent 1px, transparent 18px);
    }
    .hero h1 { margin: 0 0 4px 0; font-size: 22px; font-weight: 800; color: #0b3b1f; letter-spacing: .3px; }
    .hero p { margin: 0; color: #0f5132; font-size: 13px; }

    .badge {
      display:inline-flex; align-items:center; gap:6px; font-weight:600; font-size:12px;
      padding:4px 8px; border-radius:999px; color:#08341b; background:#d9fbe5; border:1px solid #b9efcc;
    }
    .badge svg { width:16px; height:16px; }

    /* Card */
    .card { background:#ffffff; border:1px solid #dbe8df; border-radius: 12px; padding: 12px; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
    .card-header { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:8px; }

    .btn {
      appearance: none; border: 0; border-radius: 10px; padding: 8px 12px; cursor: pointer; font-weight: 700;
      background: linear-gradient(180deg, #19b562, #0d8a46); color: #04110a; box-shadow: inset 0 1px 0 rgba(255,255,255,.4), 0 1px 2px rgba(0,0,0,.1);
    }
    .btn[disabled] { filter: grayscale(1); opacity: .6; cursor: default; }

    /* Table */
    table { width:100%; border-collapse: separate; border-spacing: 0; }
    thead th { position: sticky; top: 0; background: #0b1220; color: #cfe0f4; text-align: left; padding: 8px 10px; font-weight: 700; font-size: 12px; letter-spacing: .3px; }
    tbody td { padding: 8px 10px; border-bottom: 1px solid #e6f0ea; font-size: 13px; color:#0b1723; }
    tbody tr:nth-child(odd) { background: #fbfdfc; }
    tbody tr:hover { background: #eefbf3; }

    .fixture { display:flex; align-items:center; gap:6px; flex-wrap:wrap; }
    .team-pill { display:inline-flex; align-items:center; gap:6px; padding: 4px 8px; border-radius: 999px; color: #052012; font-weight: 700; border: 1px solid rgba(0,0,0,.06); }
    .vs { font-weight: 800; color: #0f5132; }

    .actions { display:flex; align-items:center; gap: 8px; }
    .link-btn { display:inline-flex; align-items:center; gap:6px; text-decoration:none; font-weight:700; padding:6px 10px; border-radius:10px; }
    .view { background:#e6f0ff; color:#0f2d6a; border:1px solid #c7dcff; }
    .download { background:#e8f8ef; color:#0f5132; border:1px solid #c3ecd2; }
    .link-btn:hover { filter:saturate(1.2) brightness(1.03); }

    .when { white-space: nowrap; color:#0b3b1f; font-weight:600; }
    .filename { color:#0b1723; max-width: 520px; overflow:hidden; text-overflow: ellipsis; white-space: nowrap; }
    .size { text-align:right; font-variant-numeric: tabular-nums; color:#08341b; font-weight:600; }

    .empty { color:#4b5563; font-size: 13px; padding: 6px 0; }
    .error { color:#b91c1c; font-size: 13px; margin: 4px 0; }
    .loader { height: 6px; background: linear-gradient(90deg, #19b562 0%, #0ea5e9 50%, #19b562 100%); background-size: 200% 100%; animation: slide 1.2s linear infinite; border-radius: 999px; }
    @keyframes slide { to { background-position: 200% 0; } }
  `],
  template: `
    <div class="container">
      <div class="hero mb-4">
        <div class="badge" title="All your generated PDFs, in one place">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9"></circle>
            <path d="M8 12l2.5 2.5L16 9"></path>
          </svg>
          Fixture Analysis History
        </div>
        <h1>Bring your match insights back anytime</h1>
        <p>All generated fixture analysis PDFs are archived here. View inline or download — football-themed and ready to share.</p>
      </div>

      <div class="card">
        <div class="card-header">
          <div style="display:flex; align-items:center; gap:8px;">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#19b562" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/></svg>
            <h2 style="margin:0; font-size:18px; font-weight:800; color:#0b3b1f;">Generated PDFs</h2>
          </div>
          <button class="btn" type="button" (click)="load()" [disabled]="loading">{{ loading ? 'Loading…' : 'Refresh' }}</button>
        </div>
        <div *ngIf="loading" class="loader" aria-label="Loading"></div>
        <div *ngIf="error" class="error">{{ error }}</div>
        <div *ngIf="!loading && items.length === 0" class="empty">No PDFs archived yet. Generate an analysis PDF to see it here.</div>

        <div class="overflow-auto" *ngIf="items.length">
          <table>
            <thead>
              <tr>
                <th style="width:180px;">When</th>
                <th>Fixture</th>
                <th>Filename</th>
                <th style="width:120px; text-align:right;">Size</th>
                <th style="width:180px;">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let a of items">
                <td class="when">{{ a.generatedAt | date:'yyyy-MM-dd HH:mm:ss' }}</td>
                <td>
                  <div class="fixture">
                    <span class="team-pill" [ngStyle]="{ background: tint(colorFor(a.homeTeam), .12), borderColor: tint(colorFor(a.homeTeam), .3), color: colorForText(a.homeTeam) }">{{ a.homeTeam }}</span>
                    <span class="vs">vs</span>
                    <span class="team-pill" [ngStyle]="{ background: tint(colorFor(a.awayTeam), .12), borderColor: tint(colorFor(a.awayTeam), .3), color: colorForText(a.awayTeam) }">{{ a.awayTeam }}</span>
                  </div>
                </td>
                <td class="filename" [title]="a.filename">{{ a.filename }}</td>
                <td class="size">{{ formatBytes(a.sizeBytes) }}</td>
                <td>
                  <div class="actions">
                    <a class="link-btn view" [href]="api('/api/matches/analysis-pdfs/' + a.id + '/inline')" target="_blank" title="Open inline in a new tab" rel="noopener">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 3h6v6"/><path d="M10 14L21 3"/><path d="M5 7v14h14"/></svg>
                      View
                    </a>
                    <a class="link-btn download" [href]="api('/api/matches/analysis-pdfs/' + a.id)" title="Download PDF">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/></svg>
                      Download
                    </a>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class AnalysisPdfsComponent implements OnInit {
  private http = inject(HttpClient);
  items: any[] = [];
  loading = false;
  error = '';

  ngOnInit(): void { this.load(); }

  load(){
    this.loading = true; this.error = '';
    this.http.get<any>(this.api('/api/matches/analysis-pdfs?page=0&size=50')).subscribe({
      next: res => { this.items = Array.isArray(res?.content) ? res.content : []; this.loading = false; },
      error: err => { this.loading = false; this.error = (err?.error?.message) || 'Failed to load PDF archives.'; }
    });
  }

  api(path: string): string { return path; }

  // --- UI helpers for formatting and theming ---
  formatBytes(bytes: number): string {
    if (!bytes && bytes !== 0) return '';
    const thresh = 1024; if (Math.abs(bytes) < thresh) return bytes + ' B';
    const units = ['KB','MB','GB','TB']; let u = -1; do { bytes /= thresh; ++u; } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(bytes < 10 ? 1 : 0) + ' ' + units[u];
  }

  private teamColorMap: Record<string, string> = {
    'liverpool': '#C8102E',
    'everton': '#003399',
    'crystal palace': '#1B458F',
    'brighton': '#0057B8',
    'tottenham': '#132257'
  };
  colorFor(name: string | null | undefined): string {
    if (!name) return '#228B22';
    const key = name.toLowerCase();
    return this.teamColorMap[key] || '#228B22';
  }
  colorForText(name: string | null | undefined): string {
    // Darken for contrast
    const c = this.colorFor(name);
    return this.mix('#000000', c, 0.3);
  }
  tint(hex: string, amount: number): string {
    // mix hex with white by amount (0..1)
    return this.mix('#ffffff', hex, amount);
  }
  mix(hex1: string, hex2: string, amount: number): string {
    const a = this.hexToRgb(hex1), b = this.hexToRgb(hex2); const t = Math.max(0, Math.min(1, amount));
    const r = Math.round(a.r + (b.r - a.r) * t);
    const g = Math.round(a.g + (b.g - a.g) * t);
    const bl = Math.round(a.b + (b.b - a.b) * t);
    return `rgb(${r}, ${g}, ${bl})`;
  }
  hexToRgb(hex: string){
    const s = hex.replace('#','');
    const n = parseInt(s.length === 3 ? s.split('').map(ch => ch+ch).join('') : s, 16);
    return { r: (n>>16)&255, g: (n>>8)&255, b: n&255 };
  }
}
