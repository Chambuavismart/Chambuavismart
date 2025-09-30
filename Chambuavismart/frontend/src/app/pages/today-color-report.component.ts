import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, NgFor, NgIf, DatePipe, NgStyle } from '@angular/common';
import { RouterModule } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
import { FixturesService, LeagueFixturesResponse, FixtureDTO } from '../services/fixtures.service';
import { MatchService } from '../services/match.service';
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
  private matchApi = inject(MatchService);
  private _backfillSeen = new Set<string>();

  todayIso: string = '';
  selectedIso: string = '';
  loading: boolean = false;
  groups: GroupedBucket[] = [];
  totalCount = 0; // total pending (non-finished) fixtures for selected date

  // Sorting state
  sortMode: 'time' | 'streakType' | 'strongest' | 'sameColorFirst' = 'time';
  sortOptions: { key: 'time' | 'streakType' | 'strongest' | 'sameColorFirst'; label: string }[] = [
    { key: 'time', label: 'By kickoff time (default)' },
    { key: 'streakType', label: 'By streak type: Wins → Draws → Losses' },
    { key: 'strongest', label: 'By strongest streak (max count desc)' },
    { key: 'sameColorFirst', label: 'Same-color clashes first' }
  ];

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
      // Attempt legacy backfill after grouping
      this.backfillVisibleTeams();
    } catch {
      this.groups = [];
    }
  }

  // Enrich legacy color entries with longest streak counts for currently visible teams
  private backfillVisibleTeams(): void {
    try {
      const collect: { name: string; leagueId: number }[] = [];
      const push = (n: any, leagueId: number) => {
        const name = (n || '').toString().trim();
        if (!name) return;
        const key = `${name.toLowerCase()}#${leagueId}`;
        if (this._backfillSeen.has(key)) return;
        const color = this.colorCache.getTeamColor(name, leagueId);
        if (!color) return;
        if (this.colorCache.isIndigoColor(color)) return;
        const sc = this.colorCache.getTeamStreakCount(name, leagueId);
        if (sc && sc > 0) { this._backfillSeen.add(key); return; }
        collect.push({ name, leagueId });
      };
      for (const it of this.flatData || []) {
        push(it.fixture?.homeTeam, it.leagueId);
        push(it.fixture?.awayTeam, it.leagueId);
      }
      const runNext = () => {
        if (!collect.length) return;
        const { name, leagueId } = collect.shift()!;
        if (!this.colorCache.markBackfillInFlight(name, leagueId)) { runNext(); return; }
        this.matchApi.getResultsBreakdownByTeamName(name).subscribe({
          next: (bd) => {
            const cnt = (bd?.longestStreakCount ?? 0) as number;
            const t = (bd?.longestStreakType || '').toString().toUpperCase();
            if (cnt && cnt > 0) {
              this.colorCache.setTeamStreakCount(name, cnt, leagueId);
            } else {
              this.colorCache.setTeamStreakCount(name, null, leagueId);
            }
            // Always set color based on streak type regardless of count threshold
            if (t === 'W') {
              this.colorCache.setTeamColor(name, '#16a34a', leagueId);
            } else if (t === 'L') {
              this.colorCache.setTeamColor(name, '#ef4444', leagueId);
            } else if (t === 'D') {
              this.colorCache.setTeamColor(name, '#f59e0b', leagueId);
            } else {
              this.colorCache.removeTeamColor(name, leagueId);
            }
          },
          error: _ => {},
          complete: () => {
            this._backfillSeen.add(`${name.toLowerCase()}#${leagueId}`);
            this.colorCache.clearBackfillInFlight(name, leagueId);
            setTimeout(runNext, 100);
          }
        });
      };
      const par = Math.min(collect.length, 3);
      for (let i = 0; i < par; i++) setTimeout(runNext, 0);
    } catch {}
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
        // Kick a backfill shortly after initial grouping
        setTimeout(() => this.backfillVisibleTeams(), 500);
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
    if (contains('orange') || contains('amber')) return 'Orange';
    if (contains('yellow') || contains('gold')) return 'Yellow';
    if (contains('blue')) return 'Blue';
    if (contains('purple') || contains('violet')) return 'Purple';
    if (contains('pink')) return 'Pink';
    if (contains('teal') || contains('cyan') || contains('aqua')) return 'Teal';
    if (contains('brown')) return 'Brown';
    if (contains('black')) return 'Black';
    if (contains('white')) return 'White';
    if (contains('grey') || contains('gray') || contains('silver')) return 'Gray';

    // Explicit hex shortcuts for known orange variants before numeric heuristics
    if (s.startsWith('#')) {
      if (s === '#f59e0b' || s === '#ffa500') return 'Orange';
    }

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
      // Evaluate Orange before Yellow to avoid classifying #f59e0b as Yellow
      if (r > 200 && g > 100 && g < 200 && b < 60) return 'Orange';
      if (r > 200 && g > 150 && b < 80) return 'Yellow';
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

  // Helpers to resolve color type and streak counts from cache
  private teamColorType(team: string | null | undefined, leagueId: number): 'W' | 'D' | 'L' | null {
    const name = (team || '').toString().trim();
    if (!name) return null;
    const cRaw = this.colorCache.getTeamColor(name, leagueId);
    if (!cRaw) return null;
    const canon = (this.canonicalColor(cRaw) || '').toLowerCase();
    if (canon === 'green') return 'W';
    if (canon === 'orange') return 'D';
    if (canon === 'red') return 'L';
    return null;
  }
  private teamStreakCount(team: string | null | undefined, leagueId: number): number {
    const name = (team || '').toString().trim();
    if (!name) return 0;
    const cnt = this.colorCache.getTeamStreakCount(name, leagueId);
    return typeof cnt === 'number' && cnt > 0 ? cnt : 0;
  }

  changeSort(mode: 'time' | 'streakType' | 'strongest' | 'sameColorFirst'): void {
    if (this.sortMode === mode) return;
    this.sortMode = mode;
    this.rebuildGroups();
  }

  private parseColorToRgb(s: string): [number, number, number] | null {
    // Try keywords quickly for common CSS names for contrast logic
    const kw: Record<string, [number, number, number]> = {
      red: [255,0,0], green: [0,128,0], blue: [0,0,255], orange: [255,165,0], amber: [255,191,0], yellow: [255,255,0], purple: [128,0,128], pink: [255,192,203], teal: [0,128,128], brown: [165,42,42], black: [0,0,0], white: [255,255,255], gray: [128,128,128], grey: [128,128,128]
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
    const cRaw = name ? this.colorCache.getTeamColor(name, leagueId) : null;
    if (!cRaw) return {};
    const canon = (this.canonicalColor(cRaw) || '').toLowerCase();
    let bg: string | null = null;
    if (canon === 'green') bg = '#16a34a';
    else if (canon === 'red') bg = '#ef4444';
    else if (canon === 'orange') bg = '#f59e0b';
    else return {};
    const style: any = { 'background-color': bg, color: '#fff', 'border': '1px solid rgba(255,255,255,0.12)' };
    if (name && this.colorCache.isDoubleGreen(name, leagueId)) {
      style.boxShadow = 'inset 0 0 0 3px #16a34a, inset 0 0 0 7px rgba(22,163,74,0.65), 0 0 0 2px #16a34a, 0 0 10px 3px rgba(22,163,74,0.75)';
    }
    return style;
  }

  // Prefix icons/HTML when the team has special flags: big shining orange D for draw-heavy and 🔥 for doubleGreen.
  // Also append longest streak count when the color is derived from a long streak.
  formatTeamLabelHtml(team: string | null | undefined, leagueId: number) {
    const name = (team || '').toString().trim();
    if (!name) return '';
    try {
      const hasD = this.colorCache.hasDrawHeavyD(name, leagueId);
      const hasFire = this.colorCache.isDoubleGreen(name, leagueId);
      const streak = this.colorCache.getTeamStreakCount(name, leagueId);
      const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      const parts: string[] = [];
      if (hasD) {
        parts.push('<span style="font-weight:800;color:#ff7a00;text-shadow:0 0 6px rgba(255,122,0,0.85), 0 0 12px rgba(255,122,0,0.6);font-size:1.05em;letter-spacing:0.5px;">D</span>');
      }
      if (hasFire) parts.push('🔥');
      const label = streak && streak > 0 ? `${esc(name)} <span style='opacity:0.95;font-weight:700'>( ${streak} )</span>` : esc(name);
      parts.push(label);
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
    const data = (items || []).slice();

    const byKickoff = (a: FlatFixtureItem, b: FlatFixtureItem) => {
      const ta = new Date(a.fixture.dateTime).getTime();
      const tb = new Date(b.fixture.dateTime).getTime();
      if (isNaN(ta) && isNaN(tb)) return 0;
      if (isNaN(ta)) return 1;
      if (isNaN(tb)) return -1;
      return ta - tb;
    };

    const byStreakType = (a: FlatFixtureItem, b: FlatFixtureItem) => {
      const catIndex = (it: FlatFixtureItem) => {
        const ht = this.teamColorType(it.fixture.homeTeam, it.leagueId);
        const at = this.teamColorType(it.fixture.awayTeam, it.leagueId);
        const hc = this.teamStreakCount(it.fixture.homeTeam, it.leagueId);
        const ac = this.teamStreakCount(it.fixture.awayTeam, it.leagueId);
        const winMax = Math.max(ht === 'W' ? hc : 0, at === 'W' ? ac : 0);
        const drawMax = Math.max(ht === 'D' ? hc : 0, at === 'D' ? ac : 0);
        const lossMax = Math.max(ht === 'L' ? hc : 0, at === 'L' ? ac : 0);
        let cat = 3; // default uncolored
        let keyVal = 0;
        if (winMax > 0 || drawMax > 0 || lossMax > 0) {
          if (winMax >= drawMax && winMax >= lossMax) { cat = 0; keyVal = winMax; }
          else if (drawMax >= winMax && drawMax >= lossMax) { cat = 1; keyVal = drawMax; }
          else { cat = 2; keyVal = lossMax; }
        }
        return { cat, keyVal };
      };
      const xa = catIndex(a);
      const xb = catIndex(b);
      if (xa.cat !== xb.cat) return xa.cat - xb.cat; // W(0) -> D(1) -> L(2) -> Uncolored(3)
      if (xa.keyVal !== xb.keyVal) return xb.keyVal - xa.keyVal; // desc by streak length
      return byKickoff(a,b);
    };

    const byStrongest = (a: FlatFixtureItem, b: FlatFixtureItem) => {
      const stats = (it: FlatFixtureItem) => {
        const ht = this.teamColorType(it.fixture.homeTeam, it.leagueId);
        const at = this.teamColorType(it.fixture.awayTeam, it.leagueId);
        const hc = this.teamStreakCount(it.fixture.homeTeam, it.leagueId);
        const ac = this.teamStreakCount(it.fixture.awayTeam, it.leagueId);
        const winMax = Math.max(ht === 'W' ? hc : 0, at === 'W' ? ac : 0);
        const drawMax = Math.max(ht === 'D' ? hc : 0, at === 'D' ? ac : 0);
        const lossMax = Math.max(ht === 'L' ? hc : 0, at === 'L' ? ac : 0);
        const maxCnt = Math.max(winMax, drawMax, lossMax);
        let typePri = 3; // W(0) D(1) L(2), 3 for none
        if (maxCnt > 0) {
          if (maxCnt === winMax) typePri = 0; else if (maxCnt === drawMax) typePri = 1; else typePri = 2;
        }
        return { maxCnt, typePri };
      };
      const sa = stats(a);
      const sb = stats(b);
      if (sa.maxCnt !== sb.maxCnt) return sb.maxCnt - sa.maxCnt; // desc by max streak
      if (sa.typePri !== sb.typePri) return sa.typePri - sb.typePri; // prefer W over D over L
      return byKickoff(a,b);
    };

    const bySameColorFirst = (a: FlatFixtureItem, b: FlatFixtureItem) => {
      const metrics = (it: FlatFixtureItem) => {
        const ht = this.teamColorType(it.fixture.homeTeam, it.leagueId);
        const at = this.teamColorType(it.fixture.awayTeam, it.leagueId);
        const same = !!(ht && at && ht === at);
        const sumCnt = (ht ? this.teamStreakCount(it.fixture.homeTeam, it.leagueId) : 0)
                      + (at ? this.teamStreakCount(it.fixture.awayTeam, it.leagueId) : 0);
        return { same, sumCnt };
      };
      const ma = metrics(a);
      const mb = metrics(b);
      if (ma.same !== mb.same) return ma.same ? -1 : 1; // same-color first
      if (ma.sumCnt !== mb.sumCnt) return mb.sumCnt - ma.sumCnt; // desc by combined streak
      return byKickoff(a,b);
    };

    let sorted: FlatFixtureItem[];
    if (this.sortMode === 'streakType') sorted = data.sort(byStreakType);
    else if (this.sortMode === 'strongest') sorted = data.sort(byStrongest);
    else if (this.sortMode === 'sameColorFirst') sorted = data.sort(bySameColorFirst);
    else sorted = data.sort(byKickoff);

    const label = this.sortMode === 'time' ? 'All fixtures (by time)'
      : this.sortMode === 'streakType' ? 'All fixtures (Wins → Draws → Losses)'
      : this.sortMode === 'strongest' ? 'All fixtures (by strongest streak)'
      : 'All fixtures (same-color clashes first)';

    return [{ key: 'all', label, items: sorted }];
  }
}
