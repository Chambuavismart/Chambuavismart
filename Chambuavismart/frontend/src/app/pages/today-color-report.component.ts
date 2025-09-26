import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, NgFor, NgIf, DatePipe, NgStyle } from '@angular/common';
import { RouterModule } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
import { FixturesService, LeagueFixturesResponse, FixtureDTO } from '../services/fixtures.service';
import { AnalysisColorCacheService } from '../services/analysis-color-cache.service';

interface FlatFixtureItem {
  leagueId: number;
  leagueName: string;
  leagueCountry?: string;
  fixture: FixtureDTO;
}

interface GroupedBucket {
  key: string; // internal key
  label: string; // shown label
  items: FlatFixtureItem[];
}

interface DateOption {
  iso: string;
  label: string;
  pendingCount: number; // non-finished fixtures
}

@Component({
  selector: 'app-today-color-report',
  standalone: true,
  imports: [CommonModule, RouterModule, NgFor, NgIf, DatePipe, NgStyle],
  templateUrl: './today-color-report.component.html',
  styleUrls: ['./today-color-report.component.css']
})
export class TodayColorReportComponent implements OnInit {
  private fixturesApi = inject(FixturesService);
  private colorCache = inject(AnalysisColorCacheService);
  private sanitizer = inject(DomSanitizer);

  todayIso: string = '';
  selectedIso: string = '';
  loading: boolean = false;
  groups: GroupedBucket[] = [];
  totalCount = 0; // total pending (non-finished) fixtures for selected date

  dateOptions: DateOption[] = [];
  optionsLoading = false;

  // Keep a flat list so we can rebuild groups when cache updates
  private flatData: FlatFixtureItem[] = [];

  // Event handlers to react to color cache updates
  private onColorsUpdated = (_ev?: any) => {
    try { this.rebuildGroups(); } catch {}
  };
  private onStorageUpdated = (ev: StorageEvent) => {
    try {
      const k = ev?.key || '';
      if (k === 'fixturesAnalysis.teamColors.v1' || k === 'fixturesAnalysis.teamColors.version') {
        this.rebuildGroups();
      }
    } catch {}
  };

  // Safely convert input to Date or null to avoid NG02100 in DatePipe
  toSafeDate(input: any): Date | null {
    if (!input) return null;
    try {
      const d = new Date(input);
      return isNaN(d.getTime()) ? null : d;
    } catch {
      return null;
    }
  }

  // Robust resolver for team names (mirrors Home logic)
  private resolveTeamNameRaw(f: any, side: 'home' | 'away'): string {
    const keys = side === 'home'
      ? ['homeTeam','home_team','home','home_name','homeTeamName']
      : ['awayTeam','away_team','away','away_name','awayTeamName'];
    for (const k of keys) {
      const v = f && typeof f[k] === 'string' ? (f[k] as string).trim() : '';
      if (v) return v;
    }
    const nested = side === 'home'
      ? (f?.home || f?.home_team || f?.teams?.home || f?.teamHome)
      : (f?.away || f?.away_team || f?.teams?.away || f?.teamAway);
    if (nested) {
      if (typeof nested === 'string' && nested.trim()) return nested.trim();
      if (typeof nested.name === 'string' && nested.name.trim()) return nested.name.trim();
      if (typeof nested.team === 'string' && nested.team.trim()) return nested.team.trim();
      if (typeof nested.title === 'string' && nested.title.trim()) return nested.title.trim();
    }
    return '';
  }
  private normalizeTeamName(f: any, side: 'home' | 'away'): string {
    const raw = this.resolveTeamNameRaw(f, side);
    return typeof raw === 'string' ? raw.trim() : '';
  }

  private rebuildGroups(): void {
    try {
      const data = Array.isArray(this.flatData) ? this.flatData.slice() : [];
      this.groups = this.buildGroups(data);
    } catch {
      this.groups = [];
    }
  }

  ngOnDestroy(): void {
    try { window.removeEventListener('fixtures:colors-updated', this.onColorsUpdated as any); } catch {}
    try { window.removeEventListener('storage', this.onStorageUpdated as any); } catch {}
  }

  ngOnInit(): void {
    this.todayIso = this.computeTodayIsoNairobi();
    this.selectedIso = this.todayIso;
    this.preloadDateOptions();
    // Listen for cache updates from analysis modal and other tabs
    try { window.addEventListener('fixtures:colors-updated', this.onColorsUpdated as any); } catch {}
    try { window.addEventListener('storage', this.onStorageUpdated as any); } catch {}
    this.load(this.selectedIso);
  }

  private computeTodayIsoNairobi(): string {
    // Derive YYYY-MM-DD for Africa/Nairobi regardless of user's local TZ
    const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Nairobi', year: 'numeric', month: '2-digit', day: '2-digit' });
    return fmt.format(new Date()); // en-CA gives YYYY-MM-DD
  }

  private lastNDaysIso(n: number): string[] {
    const out: string[] = [];
    const tz = 'Africa/Nairobi';
    for (let i = 0; i <= n; i++) {
      const d = new Date();
      // shift by i days using local then format in Nairobi to get date component
      d.setDate(d.getDate() - i);
      const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' });
      out.push(fmt.format(d));
    }
    return out;
  }

  private labelFor(iso: string): string {
    // Provide human-friendly label relative to today
    if (iso === this.todayIso) return 'Today';
    const today = new Date(this.todayIso);
    const dt = new Date(iso);
    const diff = Math.round((today.getTime() - dt.getTime()) / 86400000);
    if (diff === 1) return 'Yesterday';
    const opts: Intl.DateTimeFormatOptions = { weekday: 'short', month: 'short', day: 'numeric' };
    return new Intl.DateTimeFormat('en-US', opts).format(dt);
  }

  preloadDateOptions(): void {
    this.optionsLoading = true;
    const days = this.lastNDaysIso(7);
    this.dateOptions = days.map(iso => ({ iso, label: this.labelFor(iso), pendingCount: 0 }));
    // Load counts sequentially to be gentle; small number of requests (<=8)
    const next = (idx: number) => {
      if (idx >= this.dateOptions.length) { this.optionsLoading = false; return; }
      const iso = this.dateOptions[idx].iso;
      this.fixturesApi.getFixturesByDate(iso).subscribe({
        next: (byLeague) => {
          let count = 0;
          for (const lg of byLeague || []) {
            for (const f of lg.fixtures || []) {
              if ((f.status as any) !== 'FINISHED') count++;
            }
          }
          this.dateOptions[idx].pendingCount = count;
          next(idx + 1);
        },
        error: _ => { this.dateOptions[idx].pendingCount = 0; next(idx + 1); }
      });
    };
    next(0);
  }

  changeDate(iso: string): void {
    if (this.selectedIso === iso) return;
    this.selectedIso = iso;
    this.load(iso);
  }

  private load(iso: string): void {
    this.loading = true;
    this.groups = [];
    this.totalCount = 0;
    this.flatData = [];
    this.fixturesApi.getFixturesByDate(iso).subscribe({
      next: (byLeague: LeagueFixturesResponse[]) => {
        const flat: FlatFixtureItem[] = [];
        for (const lg of byLeague || []) {
          const fixturesAll = (lg.fixtures || []) as any[];
          const fixtures = (iso === this.todayIso ? fixturesAll.filter(f => (f.status as any) !== 'FINISHED') : fixturesAll)
            .slice()
            .sort((a, b) => {
              const t1 = new Date(a.dateTime).getTime();
              const t2 = new Date(b.dateTime).getTime();
              return t1 - t2;
            });
          for (const f of fixtures) {
            // Normalize team names to ensure cache key alignment
            try {
              (f as any).homeTeam = this.normalizeTeamName(f, 'home') || f.homeTeam;
              (f as any).awayTeam = this.normalizeTeamName(f, 'away') || f.awayTeam;
            } catch {}
            flat.push({ leagueId: lg.leagueId, leagueName: lg.leagueName, leagueCountry: lg.leagueCountry, fixture: f });
          }
        }
        this.totalCount = flat.length;
        this.flatData = flat;
        this.groups = this.buildGroups(flat);
      },
      error: _ => {
        this.groups = [];
        this.totalCount = 0;
        this.flatData = [];
      },
      complete: () => { this.loading = false; }
    });
  }

  // Generate and download a real PDF of the report (no print dialog)
  async downloadPdf(): Promise<void> {
    const root = document.querySelector('div.report-wrap') as HTMLElement | null;
    if (!root) { try { window.print(); } catch {} return; }
    // Dynamically load html2canvas and jsPDF from CDN (no extra project deps)
    const loadScript = (src: string) => new Promise<void>((resolve, reject) => {
      const s = document.createElement('script');
      s.src = src; s.async = true; s.onload = () => resolve(); s.onerror = () => reject(new Error('Failed to load ' + src));
      document.head.appendChild(s);
    });
    try {
      const h2cUrl = 'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js';
      const jspdfUrl = 'https://cdn.jsdelivr.net/npm/jspdf@2.5.1/dist/jspdf.umd.min.js';
      if (!(window as any).html2canvas) await loadScript(h2cUrl);
      if (!(window as any).jspdf) await loadScript(jspdfUrl);
      const html2canvas = (window as any).html2canvas as (el: HTMLElement, opts?: any) => Promise<HTMLCanvasElement>;
      const { jsPDF } = (window as any).jspdf || {};
      if (!html2canvas || !jsPDF) throw new Error('PDF libs unavailable');

      // Optional: add a temporary class to hide controls during capture
      root.classList.add('exporting');
      const canvas = await html2canvas(root, { backgroundColor: '#0f1320', scale: 2, useCORS: true });
      root.classList.remove('exporting');

      const imgData = canvas.toDataURL('image/png');
      const pdf = new jsPDF({ orientation: 'p', unit: 'pt', format: 'a4' });
      const pageWidth = pdf.internal.pageSize.getWidth();
      const pageHeight = pdf.internal.pageSize.getHeight();
      const imgWidth = pageWidth;
      const ratio = imgWidth / canvas.width; // px -> pt ratio

      // First-page header
      const margin = 24;
      pdf.setFontSize(14);
      pdf.setTextColor(150, 150, 150);
      // @ts-ignore align option exists in UMD build
      pdf.text('Powered by Chambuavismart', pageWidth - margin, 28, { align: 'right' });

      // Paginate the tall image into A4 pages
      let remainingPx = canvas.height;
      let startPx = 0;
      let pageIndex = 0;
      while (remainingPx > 0) {
        const availablePt = pageHeight - (pageIndex === 0 ? 40 : 0); // leave space for header on page 1
        const slicePx = Math.max(0, Math.floor(availablePt / ratio));
        if (slicePx <= 0) break;
        const pageCanvas = document.createElement('canvas');
        const ctx = pageCanvas.getContext('2d');
        pageCanvas.width = canvas.width;
        pageCanvas.height = slicePx;
        if (!ctx) break;
        ctx.clearRect(0, 0, pageCanvas.width, pageCanvas.height);
        ctx.drawImage(canvas, 0, -startPx);
        const pageImg = pageCanvas.toDataURL('image/png');
        const targetY = pageIndex === 0 ? 40 : 0;
        const targetH = pageIndex === 0 ? availablePt : pageHeight;
        pdf.addImage(pageImg, 'PNG', 0, targetY, imgWidth, targetH, undefined, 'FAST');
        remainingPx -= slicePx;
        startPx += slicePx;
        pageIndex++;
        if (remainingPx > 0) {
          pdf.addPage();
          pdf.setFontSize(14);
          pdf.setTextColor(150, 150, 150);
          // @ts-ignore align option exists in UMD build
          pdf.text('Powered by Chambuavismart', pageWidth - margin, 28, { align: 'right' });
        }
      }

      const iso = this.selectedIso || this.todayIso || new Date().toISOString().slice(0,10);
      const name = `Fixture_Analysis_Colours_Report_${iso}.pdf`;
      pdf.save(name);
    } catch (e) {
      console.warn('[ColorReport] PDF generation failed, falling back to print()', e);
      try { window.print(); } catch {}
    }
  }

  private canonicalColor(c: string | null | undefined): string | null {
    if (!c) return null;
    const s = c.trim().toLowerCase();
    if (!s) return null;
    // Named keywords quick map
    const contains = (k: string) => s.includes(k);
    if (contains('red')) return 'Red';
    if (contains('green')) return 'Green';
    if (contains('orange')) return 'Orange';
    if (contains('yellow') || contains('gold')) return 'Yellow';
    if (contains('blue')) return 'Blue';
    if (contains('purple') || contains('violet')) return 'Purple';
    if (contains('pink')) return 'Pink';
    if (contains('teal') || contains('cyan') || contains('aqua')) return 'Teal';
    if (contains('brown')) return 'Brown';
    if (contains('black')) return 'Black';
    if (contains('white')) return 'White';
    if (contains('grey') || contains('gray') || contains('silver')) return 'Gray';
    // Hex or rgb mapping
    const rgb = this.parseColorToRgb(s);
    if (rgb) {
      const [r, g, b] = rgb;
      // simple nearest primary/secondary heuristic
      const max = Math.max(r, g, b);
      const min = Math.min(r, g, b);
      if (max === 0) return 'Black';
      if (min > 240) return 'White';
      const isGray = Math.abs(r - g) < 15 && Math.abs(g - b) < 15 && Math.abs(r - b) < 15;
      if (isGray) return 'Gray';
      // hue-like rules
      if (r > 200 && g < 100 && b < 100) return 'Red';
      if (g > 200 && r < 100 && b < 100) return 'Green';
      if (b > 200 && r < 100 && g < 100) return 'Blue';
      if (r > 200 && g > 150 && b < 80) return 'Yellow';
      if (r > 200 && g > 100 && g < 200 && b < 60) return 'Orange';
      if (r > 150 && b > 150 && g < 120) return 'Purple';
      if (r > 200 && g < 140 && b > 140) return 'Pink';
      if (g > 150 && b > 150 && r < 120) return 'Teal';
      if (r > 120 && g < 100 && b < 60) return 'Brown';
      // fallback choose the dominant channel
      if (r >= g && r >= b) return 'Red';
      if (g >= r && g >= b) return 'Green';
      return 'Blue';
    }
    return 'Other';
  }

  private parseColorToRgb(s: string): [number, number, number] | null {
    // Try keywords quickly for common CSS names for contrast logic
    const kw: Record<string, [number, number, number]> = {
      red: [255,0,0], green: [0,128,0], blue: [0,0,255], orange: [255,165,0], yellow: [255,255,0], purple: [128,0,128], pink: [255,192,203], teal: [0,128,128], brown: [165,42,42], black: [0,0,0], white: [255,255,255], gray: [128,128,128], grey: [128,128,128]
    };
    const kwv = kw[s as keyof typeof kw];
    if (kwv) return kwv;
    // #rgb, #rrggbb
    if (s.startsWith('#')) {
      const hex = s.substring(1);
      if (hex.length === 3) {
        const r = parseInt(hex[0] + hex[0], 16);
        const g = parseInt(hex[1] + hex[1], 16);
        const b = parseInt(hex[2] + hex[2], 16);
        return [r, g, b];
      }
      if (hex.length === 6) {
        const r = parseInt(hex.slice(0, 2), 16);
        const g = parseInt(hex.slice(2, 4), 16);
        const b = parseInt(hex.slice(4, 6), 16);
        return [r, g, b];
      }
    }
    // rgb/rgba
    const m = s.match(/rgba?\((\d+)[ ,]+(\d+)[ ,]+(\d+)/);
    if (m) {
      return [parseInt(m[1], 10), parseInt(m[2], 10), parseInt(m[3], 10)];
    }
    return null;
  }

  // Compute readable text color for a given background
  private textOn(bg: string): string {
    try {
      const rgb = this.parseColorToRgb(bg.trim().toLowerCase());
      if (!rgb) return '#fff';
      const [r,g,b] = rgb;
      const luminance = 0.299*r + 0.587*g + 0.114*b;
      return luminance > 186 ? '#000' : '#fff';
    } catch { return '#fff'; }
  }

  teamPillStyle(team: string | null | undefined, leagueId: number): any {
    const name = (team || '').toString().trim();
    const c = name ? this.colorCache.getTeamColor(name, leagueId) : null;
    if (!c) return {};
    const fg = this.textOn(c);
    const style: any = { 'background-color': c, color: fg, 'border': '1px solid rgba(255,255,255,0.12)' };
    if (name && this.colorCache.isDoubleGreen(name, leagueId)) {
      // Make the double-green ring VERY visible across the report too
      style.boxShadow = 'inset 0 0 0 3px #16a34a, inset 0 0 0 7px rgba(22,163,74,0.65), 0 0 0 2px #16a34a, 0 0 10px 3px rgba(22,163,74,0.75)';
    }
    return style;
  }

  // Prefix icons/HTML when the team has special flags: big shining orange D for draw-heavy and 🔥 for doubleGreen
  formatTeamLabelHtml(team: string | null | undefined, leagueId: number) {
    const name = (team || '').toString().trim();
    if (!name) return '';
    try {
      const hasD = this.colorCache.hasDrawHeavyD(name, leagueId);
      const hasFire = this.colorCache.isDoubleGreen(name, leagueId);
      const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      const parts: string[] = [];
      if (hasD) {
        parts.push('<span style="font-weight:800;color:#ff7a00;text-shadow:0 0 6px rgba(255,122,0,0.85), 0 0 12px rgba(255,122,0,0.6);font-size:1.05em;letter-spacing:0.5px;">D</span>');
      }
      if (hasFire) parts.push('🔥');
      parts.push(esc(name));
      return this.sanitizer.bypassSecurityTrustHtml(parts.join(' '));
    } catch { return name; }
  }

  private bucketLabelFor(c1: string | null, c2: string | null): { key: string, label: string } {
    const a = c1 || 'Uncolored';
    const b = c2 || 'Uncolored';
    if (a === 'Uncolored' && b === 'Uncolored') return { key: 'uncolored', label: 'Uncolored (no team colors set)' };
    const ca = this.canonicalColor(a === 'Uncolored' ? null : a) || 'Other';
    const cb = this.canonicalColor(b === 'Uncolored' ? null : b) || 'Other';

    if (ca === cb) {
      return { key: `same:${ca}`, label: `Both ${ca}` };
    }

    // Special highlight groups
    if ((ca === 'Orange' && cb !== 'Orange') || (cb === 'Orange' && ca !== 'Orange')) {
      return { key: 'orange-vs-other', label: 'Orange vs Other' };
    }
    // Standard pair: sort to make symmetric
    const pair = [ca, cb].sort().join(' vs ');
    const key = `pair:${[ca, cb].sort().join('|')}`;
    return { key, label: pair };
  }

  private buildGroups(items: FlatFixtureItem[]): GroupedBucket[] {
    const buckets = new Map<string, GroupedBucket>();
    for (const it of items) {
      const homeColor = this.colorCache.getTeamColor(it.fixture.homeTeam, it.leagueId);
      const awayColor = this.colorCache.getTeamColor(it.fixture.awayTeam, it.leagueId);
      const { key, label } = this.bucketLabelFor(homeColor, awayColor);
      let bucket = buckets.get(key);
      if (!bucket) {
        bucket = { key, label, items: [] };
        buckets.set(key, bucket);
      }
      bucket.items.push(it);
    }
    // sort fixtures by kickoff time within buckets
    for (const b of buckets.values()) {
      b.items.sort((a, c) => new Date(a.fixture.dateTime).getTime() - new Date(c.fixture.dateTime).getTime());
    }
    // order buckets: special ones first, then alphabetically by label
    const order: string[] = ['pair:Green|Red', 'orange-vs-other'];
    const arr = Array.from(buckets.values());
    arr.sort((x, y) => {
      const ix = order.indexOf(x.key);
      const iy = order.indexOf(y.key);
      if (ix !== -1 || iy !== -1) {
        return (ix === -1 ? 999 : ix) - (iy === -1 ? 999 : iy);
      }
      if (x.label.startsWith('Both') && !y.label.startsWith('Both')) return -1;
      if (!x.label.startsWith('Both') && y.label.startsWith('Both')) return 1;
      return x.label.localeCompare(y.label);
    });
    return arr;
  }
}
