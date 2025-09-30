import { Component, OnInit, inject } from '@angular/core';
import { NgFor, NgIf, NgStyle, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FixturesService, LeagueFixturesResponse } from '../services/fixtures.service';
import { AnalysisColorCacheService } from '../services/analysis-color-cache.service';

@Component({
  selector: 'app-hot-picks-today',
  standalone: true,
  imports: [NgFor, NgIf, NgStyle, DatePipe, RouterLink],
  template: `
    <section class="container">
      <div class="header">
        <h1>Hot Picks Today</h1>
        <div class="sub">Fixtures where one team has a strong green streak and the opponent has a strong red streak (as in Fixtures Analysis).</div>
        <div class="actions">
          <button class="btn" (click)="downloadPdf()" title="Download this list as PDF">Download as PDF</button>
          <a class="btn outline" routerLink="/" title="Back to Home">Home</a>
        </div>
      </div>

      <div class="muted" *ngIf="loading">Loading today\'s fixtures…</div>
      <div class="muted" *ngIf="!loading && items.length===0">No hot picks found for today (based on current analysis colors).</div>

      <div class="list">
        <div class="fixture" *ngFor="let it of items; trackBy: trackById">
          <div class="row1">
            <span class="league"><i class="fa fa-flag"></i> {{ it.leagueCountry || 'Unknown Country' }} — {{ it.leagueName || 'Unknown League' }}</span>
            <span class="dt">{{ it.fixture.formattedDate || '' }}</span>
          </div>
          <div class="row2">
            <span class="team name" [ngStyle]="pillStyle(it.fixture.homeTeam, it.leagueId)">{{ it.fixture.homeTeam }}</span>
            <span class="vs">vs</span>
            <span class="team name" [ngStyle]="pillStyle(it.fixture.awayTeam, it.leagueId)">{{ it.fixture.awayTeam }}</span>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    :host { display:block; color:#e0e0e0; background:#0a0a0a; font-family: Inter, Roboto, Arial, sans-serif; }
    .container { max-width: 1100px; margin: 0 auto; padding: 16px; }
    .header { display:flex; align-items:center; gap:12px; }
    .header h1 { margin:0; font-size:22px; font-weight:800; color:#fff; }
    .sub { color:#9aa0a6; }
    .actions { margin-left:auto; display:flex; gap:8px; }
    .btn { background:#2563eb; color:#fff; border:1px solid #1d4ed8; padding:8px 10px; border-radius:8px; cursor:pointer; font-weight:700; }
    .btn.outline { background: transparent; color:#9cc2ff; border-color:#123e73; }
    .muted { color:#9aa0a6; margin-top:10px; }
    .list { margin-top:14px; display:grid; grid-template-columns: 1fr; gap:10px; }
    .fixture { background:#1e293b; border:1px solid #334155; border-radius:8px; padding:10px; }
    .row1 { display:flex; justify-content:space-between; color:#c9d1d9; font-size:12px; }
    .row2 { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-top:6px; font-weight:800; }
    .team.name { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .vs { color:#9aa0a6; }
    .row2 .vs { display:flex; flex-direction:column; align-items:center; justify-content:center; line-height:1; }
    /* Tiny watermark under vs only during export */
    .exporting .row2 .vs::after { content:'Powered by Chambuavismart'; font-size:10px; color:#94a3b8; opacity:0.65; margin-top:2px; letter-spacing:0.2px; }

    /* Hide non-essential elements during client-side PDF export */
    .exporting .sub, .exporting .actions { display:none !important; }
    .exporting .list { margin-top: 120px !important; }

    /* Print styles to keep colors and layout for PDF */
    @media print {
      body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      .btn, .actions { display:none !important; }
      .container { padding:0; }
      .fixture { break-inside: avoid; }
    }
  `]
})
export class HotPicksTodayComponent implements OnInit {
  private api = inject(FixturesService);
  private cache = inject(AnalysisColorCacheService);

  loading = false;
  items: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
  todayIso = '';

  ngOnInit(): void {
    const today = new Date();
    this.todayIso = this.toIsoLocal(today);
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.getFixturesByDate(this.todayIso).subscribe({
      next: res => {
        const flat: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
        const tz = 'Africa/Nairobi';
        for (const lg of (res || [])) {
          const fx = lg?.fixtures || [];
          for (const f of fx) {
            if (!f) continue;
            const home = this.normalizeTeamName(f as any, 'home');
            const away = this.normalizeTeamName(f as any, 'away');
            if (!home || !away) continue;
            if (!this.isSameDayInTz(f.dateTime, tz)) continue;
            (f as any).homeTeam = home; (f as any).awayTeam = away;
            // Preformat kickoff date in EAT to avoid DatePipe NG02100
            let formattedDate: string | null = null;
            try {
              const d = new Date((f as any).dateTime);
              if (!isNaN(d.getTime())) {
                formattedDate = new Intl.DateTimeFormat('en-GB', {
                  weekday: 'short', day: '2-digit', month: 'short', year: 'numeric',
                  hour: '2-digit', minute: '2-digit', hour12: true, timeZone: tz, timeZoneName: 'short'
                }).format(d);
              }
            } catch { formattedDate = null; }
            (f as any).formattedDate = formattedDate;
            flat.push({ leagueId: lg.leagueId, leagueName: lg.leagueName, leagueCountry: lg.leagueCountry, fixture: f });
          }
        }
        // Filter where exactly one is green and the other is red
        this.items = flat.filter(it => !!(it.fixture?.homeTeam && it.fixture?.awayTeam) && this.isHotPick(it.fixture.homeTeam, it.fixture.awayTeam, it.leagueId));
        // Sort by kickoff time
        this.items.sort((a,b)=> this.toUtcMillis(a.fixture?.dateTime) - this.toUtcMillis(b.fixture?.dateTime));
        try { console.log('[HotPicks] Today', { total: flat.length, picks: this.items.length, sample: this.items.slice(0,5).map(x=>({home:x.fixture.homeTeam, away:x.fixture.awayTeam, leagueId:x.leagueId, hColor:this.cache.getTeamColor(x.fixture.homeTeam, x.leagueId), aColor:this.cache.getTeamColor(x.fixture.awayTeam, x.leagueId)}))}); } catch {}
        this.loading = false;
      },
      error: _ => { this.items = []; this.loading = false; }
    });
  }

  isHotPick(home: string, away: string, leagueId?: number): boolean {
    const h = this.cache.getTeamColor(home, leagueId ?? undefined) || '';
    const a = this.cache.getTeamColor(away, leagueId ?? undefined) || '';
    const hc = this.colorCategory(h);
    const ac = this.colorCategory(a);
    return (hc === 'green' && ac === 'red') || (hc === 'red' && ac === 'green');
  }

  colorCategory(color: string | null): 'green'|'red'|'orange'|'none' {
    const c = (color || '').toLowerCase();
    if (!c) return 'none';
    // Recognize new canonical hexes and legacy strings
    if (c.includes('#16a34a') || c.includes('16,160,16') || c.includes('#10a010') || c.includes('28a745') || c.includes('green')) return 'green';
    if (c.includes('#ef4444') || c.includes('204, 43, 59') || c.includes('cc2b3b') || c.includes('red')) return 'red';
    if (c.includes('#f59e0b') || c.includes('255, 165, 0') || c.includes('ffa500') || c.includes('orange')) return 'orange';
    return 'none';
  }

  pillStyle(team: string, leagueId?: number | null): {[k:string]: string} {
    const color = this.cache.getTeamColor(team, leagueId ?? undefined);
    if (!color) return {};
    return { background: color, color: '#fff', padding: '2px 6px', borderRadius: '6px' };
  }

  toSafeDate(input: any): Date | null { try { const d = new Date(input); return isNaN(d.getTime()) ? null : d; } catch { return null; } }
  toIsoLocal(d: Date): string { const y=d.getFullYear(); const m=('0'+(d.getMonth()+1)).slice(-2); const da=('0'+d.getDate()).slice(-2); return `${y}-${m}-${da}`; }
  toUtcMillis(iso: string | undefined): number { if (!iso) return NaN; const hasTZ = /[zZ]|[+-]\d{2}:\d{2}$/.test(iso); const s = hasTZ ? iso : iso + 'Z'; const t = Date.parse(s); return isNaN(t) ? new Date(iso).getTime() : t; }
  // Returns {y,m,d} in a given IANA timezone
  private getYMDInTz(date: Date, timeZone: string): { y: number; m: number; d: number } {
    const fmt = new Intl.DateTimeFormat('en-GB', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' });
    const parts = fmt.formatToParts(date);
    const y = Number(parts.find(p => p.type==='year')?.value);
    const m = Number(parts.find(p => p.type==='month')?.value);
    const d = Number(parts.find(p => p.type==='day')?.value);
    return { y, m, d };
  }
  private isSameDayInTz(iso: string | undefined, timeZone: string): boolean {
    if (!iso) return false; const dt = new Date(iso); if (isNaN(dt.getTime())) return false; const todayYMD = this.getYMDInTz(new Date(), timeZone); const thatYMD = this.getYMDInTz(dt, timeZone); return todayYMD.y===thatYMD.y && todayYMD.m===thatYMD.m && todayYMD.d===thatYMD.d;
  }

  trackById(i: number, it: {fixture:any}): any { const f=it?.fixture; return (f && (f.id!=null)) ? f.id : `${f?.homeTeam}-${f?.awayTeam}-${f?.dateTime}-${i}`; }

  async downloadPdf(): Promise<void> {
    const section = document.querySelector('section.container') as HTMLElement | null;
    if (!section) { try { window.print(); } catch {}; return; }
    // Dynamically load html2canvas and jsPDF from CDN
    const loadScript = (src: string) => new Promise<void>((resolve, reject) => {
      const s = document.createElement('script'); s.src = src; s.async = true; s.onload = () => resolve(); s.onerror = () => reject(new Error('Failed to load ' + src)); document.head.appendChild(s);
    });
    try {
      const h2cUrl = 'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js';
      const jspdfUrl = 'https://cdn.jsdelivr.net/npm/jspdf@2.5.1/dist/jspdf.umd.min.js';
      if (!(window as any).html2canvas) await loadScript(h2cUrl);
      if (!(window as any).jspdf) await loadScript(jspdfUrl);
      const html2canvas = (window as any).html2canvas as (el: HTMLElement, opts?: any) => Promise<HTMLCanvasElement>;
      const { jsPDF } = (window as any).jspdf || {};
      if (!html2canvas || !jsPDF) throw new Error('PDF libs unavailable');
      // Hide non-essential elements during export
      section.classList.add('exporting');
      const canvas = await html2canvas(section, { backgroundColor: '#0b1220', scale: 2, useCORS: true });
      section.classList.remove('exporting');
      const imgData = canvas.toDataURL('image/png');
      const pdf = new jsPDF({ orientation: 'p', unit: 'pt', format: 'a4' });
      const pageWidth = pdf.internal.pageSize.getWidth();
      const pageHeight = pdf.internal.pageSize.getHeight();
      const imgWidth = pageWidth;
      const ratio = imgWidth / canvas.width; // px -> pt scaling ratio
      const imgHeight = canvas.height * ratio;

      // Draw page title (top-right) and instruction pill (top-left) on the first page BEFORE placing content
      const margin = 24;
      pdf.setFontSize(14);
      pdf.setTextColor(150, 150, 150);
      // @ts-ignore align option supported in UMD build
      pdf.text('Powered by Chambuavismart', pageWidth - margin, 28, { align: 'right' });

      // Instruction pill box (with auto-wrapping)
      const instrLines = [
        "1. Get today's Hot Picks in PDF form",
        '2. Look at the odds assigned by the bookies.',
        '3. If the odds favour the team we have assigned the green colour, then proceed and bet on this team to win.',
        '3. If you are Risk-Averse, instead of picking a direct win, give the team double chance (Win or Draw).',
        '4. NB. Placing multibets is risky. Consider placing multibets of very few teams if you prefer multibets over single bets.',
        '5. Good luck from Chambuavismart team.'
      ];
      const lineH = 14; const boxPad = 10;
      const boxX = margin; const boxY = 40; const boxW = pageWidth - margin * 2;
      const maxTextW = boxW - boxPad * 2;
      pdf.setFontSize(12);
      // Split lines to fit inside the pill
      const wrapped: string[] = [];
      for (const ln of instrLines) {
        // @ts-ignore splitTextToSize exists in UMD build
        const segs = (pdf as any).splitTextToSize(ln, maxTextW) as string[];
        wrapped.push(...segs);
      }
      const boxH = boxPad * 2 + wrapped.length * lineH;
      pdf.setFillColor(11, 47, 85); // pill background
      pdf.setDrawColor(18, 62, 115); // pill border
      // @ts-ignore roundedRect is available in UMD build
      pdf.roundedRect(boxX, boxY, boxW, boxH, 8, 8, 'FD');
      pdf.setTextColor(220, 235, 255);
      let ty = boxY + boxPad + 12;
      for (const ln of wrapped) { pdf.text(ln, boxX + boxPad, ty); ty += lineH; }

      // Compute top offset for content so it starts AFTER the instructions pill
      const contentYOffset = boxY + boxH + 24; // ensure clear gap below pill

      // Paginate the captured section image so its first slice starts at contentYOffset
      let remainingPx = canvas.height; // how many canvas pixels remain to draw
      let startPx = 0; // current top pixel in the source canvas for slicing
      let pageIndex = 0;
      while (remainingPx > 0) {
        const availablePt = pageIndex === 0 ? (pageHeight - contentYOffset) : pageHeight;
        const slicePx = Math.max(0, Math.floor(availablePt / ratio));
        if (slicePx <= 0) break; // no vertical room to place content
        const pageCanvas = document.createElement('canvas');
        const ctx = pageCanvas.getContext('2d');
        pageCanvas.width = canvas.width;
        pageCanvas.height = slicePx; // in px
        if (!ctx) break;
        ctx.clearRect(0, 0, pageCanvas.width, pageCanvas.height);
        // Draw a slice of the source canvas into pageCanvas starting at startPx
        ctx.drawImage(canvas, 0, -startPx);
        const pageImg = pageCanvas.toDataURL('image/png');
        const targetY = pageIndex === 0 ? contentYOffset : 0;
        const targetH = pageIndex === 0 ? availablePt : pageHeight;
        pdf.addImage(pageImg, 'PNG', 0, targetY, imgWidth, targetH, undefined, 'FAST');
        // advance
        remainingPx -= slicePx;
        startPx += slicePx;
        pageIndex++;
        if (remainingPx > 0) {
          pdf.addPage();
          // Re-draw the title on subsequent pages (top-right)
          pdf.setFontSize(14);
          pdf.setTextColor(150, 150, 150);
          // @ts-ignore align option supported in UMD build
          pdf.text('Powered by Chambuavismart', pageWidth - margin, 28, { align: 'right' });
        }
      }

      // Ensure the title exists on all pages (in case of single-page export we already drew it once)
      const pages = pdf.getNumberOfPages();
      for (let p = 1; p <= pages; p++) {
        pdf.setPage(p);
        pdf.setFontSize(14);
        pdf.setTextColor(150, 150, 150);
        // @ts-ignore align option supported in UMD build
        pdf.text('Powered by Chambuavismart', pageWidth - margin, 28, { align: 'right' });
        if (p === 1) {
          // Outline the pill again to be safe (already drawn), but no need to redraw text
          pdf.setDrawColor(18, 62, 115);
          // @ts-ignore roundedRect is available in UMD build
          pdf.roundedRect(boxX, boxY, boxW, boxH, 8, 8);
        }
      }
      const today = new Date();
      const pad2 = (n: number) => (n < 10 ? '0' + n : '' + n);
      const name = `Hot_Picks_Today_${today.getFullYear()}-${pad2(today.getMonth()+1)}-${pad2(today.getDate())}.pdf`;
      pdf.save(name);
    } catch (e) {
      console.warn('[HotPicks] PDF generation failed, falling back to print()', e);
      try { window.print(); } catch {}
    }
  }

  private normalizeTeamName(f: any, side: 'home'|'away'): string {
    if (!f) return '';
    const keys = side === 'home'
      ? ['homeTeam','home_team','home','home_name','homeTeamName']
      : ['awayTeam','away_team','away','away_name','awayTeamName'];
    for (const k of keys) {
      const v = (f as any)[k];
      if (typeof v === 'string' && v.trim()) return v.trim();
    }
    const nested = side === 'home'
      ? ((f as any).home || (f as any).home_team || (f as any).teams?.home || (f as any).teamHome)
      : ((f as any).away || (f as any).away_team || (f as any).teams?.away || (f as any).teamAway);
    if (nested) {
      if (typeof nested === 'string' && nested.trim()) return nested.trim();
      if (typeof (nested as any).name === 'string' && (nested as any).name.trim()) return (nested as any).name.trim();
      if (typeof (nested as any).team === 'string' && (nested as any).team.trim()) return (nested as any).team.trim();
      if (typeof (nested as any).title === 'string' && (nested as any).title.trim()) return (nested as any).title.trim();
      if (typeof (nested as any).shortName === 'string' && (nested as any).shortName.trim()) return (nested as any).shortName.trim();
    }
    return '';
  }
}
