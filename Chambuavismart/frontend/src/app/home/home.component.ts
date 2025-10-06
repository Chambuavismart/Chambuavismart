import { Component, inject, OnInit, OnDestroy, NgZone, ChangeDetectorRef } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { NgFor, NgIf, DatePipe, NgClass, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FixturesService, LeagueFixturesResponse, SearchFixtureItemDTO } from '../services/fixtures.service';
import { MatchService } from '../services/match.service';
import { AnalysisColorCacheService } from '../services/analysis-color-cache.service';
import { GlobalLeadersContainerComponent } from '../components/global-leaders-container/global-leaders-container.component';
import { GlobalLeadersService, GlobalLeader } from '../services/global-leaders.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterModule, FormsModule, NgFor, NgIf, NgClass, NgStyle, DatePipe, GlobalLeadersContainerComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {
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
  // Sidebar UI state
  sidebarOpen: boolean = true;
  activeNav: string = 'Home';
  private fixturesApi = inject(FixturesService);
  private router = inject(Router);
  private leadersApi = inject(GlobalLeadersService);
  private route = inject(ActivatedRoute);
  private colorCache = inject(AnalysisColorCacheService);
    private matchApi = inject(MatchService);
    private _backfillSeen = new Set<string>();
  private zone = inject(NgZone);
  private cdr = inject(ChangeDetectorRef);
  private sanitizer = inject(DomSanitizer);

  // Color version poll state (fallback when events are missed)
  private _colorVersionPoll: any;
  private _lastColorVersion: string | null = null;

  today = new Date();
  showCalendar = false;
  calendarYear = this.today.getFullYear();
  calendarMonth = this.today.getMonth(); // 0-based
  availableDates = new Set<string>(); // 'YYYY-MM-DD'
  selectedDate: string | null = null;
  pastFixtures: LeagueFixturesResponse[] = [];
  loadingPast = false;
  season: string | undefined; // Optional: set if you want to constrain to a specific season

  // Yesterday section state
  yesterdayIso: string = '';
  yesterdayCollapsed: boolean = true;
  yesterdayLoading: boolean = false;
  yesterdayFlatData: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];

  // Additional past sections (2–7 days back)
  pastSections: { daysBack: number; iso: string; collapsed: boolean; loading: boolean; flatData: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] }[] = [];

  // Live clock label (Africa/Nairobi)
  liveNowLabel: string = '';
  private _clockTimer: any;

  // Hourly voice notification (Web Speech API) aligned to :20 each hour
  private _ttsAlignTimeout: any;
  private _ttsHourlyTimer: any;
  private _ttsInitialized: boolean = false;
  private readonly HOURLY_ANCHOR_MINUTE: number = 0;
  private readonly DAILY_FIRST_HOUR_24: number = 7;

  // Today's fixtures state
  todayIso: string = '';
  todayFixtures: LeagueFixturesResponse[] = [];
  // Stable, precomputed flat list for rendering (Phase 1)
  todayFlatData: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
  // When true, hide today's fixtures card to give more space to past fixtures
  todayClosed: boolean = false;

  // Analysis modal state
  analysisModalOpen: boolean = false;
  analysisUrl: SafeResourceUrl | null = null;
  
  // Search state for today's fixtures
  searchQuery: string = '';
  showSuggestions: boolean = false;
  highlightedIndex: number = -1;
  todayLoading = false;

  // When a past date is selected, hide today's fixtures section
  get showOnlySelectedDate(): boolean {
    return !!(this.selectedDate && this.todayIso && this.selectedDate < this.todayIso);
  }

  // Total count of today's fixtures across all leagues
  get todayCount(): number {
    return this.todayFlatData.length;
  }

  // Unified normalization for team names (single source of truth)
  normalizeTeamName(f: any, side: 'home' | 'away'): string {
    if (!f) return '';
    const primaryKeys = side === 'home'
      ? ['homeTeam','home_team','home','home_name','homeTeamName']
      : ['awayTeam','away_team','away','away_name','awayTeamName'];
    for (const k of primaryKeys) {
      const v = (f as any)[k];
      if (typeof v === 'string' && v.trim()) return v.trim();
    }
    // Nested common structures
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

  // Flattened, filtered, time-sorted list of today's fixtures across all leagues

  // Global search results populated from backend
  searchResults: SearchFixtureItemDTO[] = [];

  onSearchInput(): void {
    const q = this.searchQuery.trim().toLowerCase();
    this.highlightedIndex = -1;
    if (q.length < 3) {
      this.searchResults = [];
      this.showSuggestions = false;
      return;
    }
    // Client-side filter: only search among today's fixtures as requested
    const matches: SearchFixtureItemDTO[] = [];
    for (const item of this.todayFlatData) {
      const f: any = item.fixture || {};
      const home = (f.homeTeam || this.normalizeTeamName(f, 'home') || '').toString();
      const away = (f.awayTeam || this.normalizeTeamName(f, 'away') || '').toString();
      const h = home.toLowerCase();
      const a = away.toLowerCase();
      // Match if either team starts with the entered prefix
      if (h.startsWith(q) || a.startsWith(q)) {
        matches.push({
          leagueId: item.leagueId,
          leagueName: item.leagueName,
          leagueCountry: item.leagueCountry,
          fixture: f
        });
        if (matches.length >= 10) break; // cap suggestions
      }
    }
    this.searchResults = matches;
    this.showSuggestions = this.searchResults.length > 0;
  }

  onSearchFocus(): void {
    this.showSuggestions = this.searchQuery.trim().length >= 3 && this.searchResults.length > 0;
  }

  onSearchBlur(): void {
    // Slight delay to allow click event on suggestion to fire before closing
    setTimeout(() => { this.showSuggestions = false; }, 150);
  }

  onSearchKeydown(event: KeyboardEvent): void {
    const suggestions = this.searchResults;
    if (!suggestions.length) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.highlightedIndex = (this.highlightedIndex + 1) % suggestions.length;
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.highlightedIndex = (this.highlightedIndex <= 0 ? suggestions.length - 1 : this.highlightedIndex - 1);
    } else if (event.key === 'Enter') {
      if (this.highlightedIndex >= 0 && this.highlightedIndex < suggestions.length) {
        event.preventDefault();
        const sel = suggestions[this.highlightedIndex];
        this.selectSuggestion(sel);
      }
    } else if (event.key === 'Escape') {
      this.showSuggestions = false;
    }
  }

  selectSuggestion(item: SearchFixtureItemDTO): void {
    if (!item) return;
    this.goToAnalysis({ leagueId: item.leagueId!, leagueName: item.leagueName }, item.fixture);
    this.clearSearch();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.showSuggestions = false;
    this.highlightedIndex = -1;
  }

  // Leaders cache for highlighting
  private leaderTeams: Set<string> = new Set<string>();
  private leadersByTeam: Map<string, GlobalLeader[]> = new Map<string, GlobalLeader[]>();

  private onFixturesRefresh = (_e?: any) => {
    // refresh today's fixtures and selected date view (if any)
    this.loadToday();
    this.loadYesterday();
    // Reload additional past sections
    for (const sec of this.pastSections) this.loadPast(sec);
    if (this.selectedDate) {
      // Re-fetch for the currently selected date
      this.selectDate(this.selectedDate);
    }
  }

  ngOnInit(): void {
    try {
      this.sidebarOpen = window.innerWidth >= 768;
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
      else this.activeNav = 'Home';
    } catch {}
    this.todayIso = this.toIsoLocal(this.today);
    this.yesterdayIso = this.computeYesterdayIsoInNairobi();
    // Initialize additional past sections for days back 2..7
    this.pastSections = [];
    for (let d = 2; d <= 7; d++) {
      this.pastSections.push({ daysBack: d, iso: this.computePastIsoInNairobi(d), collapsed: true, loading: false, flatData: [] });
    }
    this.loadLeaders();
    this.loadToday();
    this.loadYesterday();
    for (const sec of this.pastSections) this.loadPast(sec);

    // Trigger a legacy backfill shortly after initial data loads
    setTimeout(() => this.backfillVisibleTeams(), 1200);

    // Start live clock updating each second without triggering excessive change detection
    this.startLiveClock();

    // Check if a date is provided in the URL (from global navbar calendar)
    const qpDate = this.route.snapshot.queryParamMap.get('date');
    if (qpDate && /^\d{4}-\d{2}-\d{2}$/.test(qpDate)) {
      // Defer selection slightly to let initial state settle
      setTimeout(() => this.selectDate(qpDate), 0);
    }
    window.addEventListener('fixtures:refresh', this.onFixturesRefresh as EventListener);
    window.addEventListener('fixtures:colors-updated', this._onColorsUpdated as EventListener);
    // Listen for cross-iframe messages from the analysis modal and storage changes
    window.addEventListener('message', this._onMessageFromChild as EventListener);
    window.addEventListener('storage', this._onStorageUpdated as EventListener);

    // Fallback: poll version key to detect color cache changes when events are missed
    try {
      this._lastColorVersion = localStorage.getItem('fixturesAnalysis.teamColors.version');
    } catch { this._lastColorVersion = null; }
    this.zone.runOutsideAngular(() => {
      this._colorVersionPoll = setInterval(() => {
        let v: string | null = null;
        try { v = localStorage.getItem('fixturesAnalysis.teamColors.version'); } catch { v = null; }
        if (v && v !== this._lastColorVersion) {
          this._lastColorVersion = v;
          // Re-enter Angular to update view and sorting
          this.zone.run(() => this._onColorsUpdated(new Event('poll:fixtures:colors-updated')));
        }
      }, 1500);
    });
  }

  toggleSidebar(){ this.sidebarOpen = !this.sidebarOpen; }

  setActive(key: string){ this.activeNav = key; }

  get todayLabel(): string {
    try {
      const opts: Intl.DateTimeFormatOptions = { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: true, timeZoneName: 'short' };
      return new Intl.DateTimeFormat('en-GB', { ...opts, timeZone: 'Africa/Nairobi' }).format(new Date());
    } catch {
      return this.formattedDate;
    }
  }

  private formatNowInNairobi(withSeconds: boolean = true): string {
    try {
      const opts: Intl.DateTimeFormatOptions = {
        weekday: 'short', day: '2-digit', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit', hour12: true, timeZoneName: 'short'
      };
      if (withSeconds) (opts as any).second = '2-digit';
      return new Intl.DateTimeFormat('en-GB', { ...opts, timeZone: 'Africa/Nairobi' }).format(new Date());
    } catch {
      return this.todayLabel; // fallback
    }
  }

  private startLiveClock(): void {
    // Initialize immediately
    this.liveNowLabel = this.formatNowInNairobi(true);
    // Run timer outside Angular to avoid triggering CD on every tick
    this.zone.runOutsideAngular(() => {
      this._clockTimer = setInterval(() => {
        const label = this.formatNowInNairobi(true);
        // Re-enter Angular to update the bound property and trigger view update
        this.zone.run(() => {
          this.liveNowLabel = label;
          try { this.cdr.markForCheck(); } catch {}
        });
      }, 1000);
    });
  }

  // ========== Hourly Voice Notification (Web Speech API) ==========
  private setupHourlyKickoffAnnouncements(): void {
    if (this._ttsInitialized) return; // avoid duplicate setup
    // Only initialize if we have at least one upcoming fixture today
    const nextFx = this.getNextUpcomingFixture();
    if (!nextFx) return;

    this._ttsInitialized = true;
    // Align to the first tick: if before 07:00, wait until 07:00; otherwise align to the next top-of-hour, then tick every hour
    const ms = this.msUntilFirstHourlyTickWith7AMStart();
    // eslint-disable-next-line no-console
    console.debug('[HomeComponent] TTS hourly announcement (top-of-hour) will start in', Math.round(ms/1000), 'seconds');
    this._ttsAlignTimeout = setTimeout(() => {
      this.announceNextKickoff();
      this._ttsHourlyTimer = setInterval(() => this.announceNextKickoff(), 60 * 60 * 1000);
    }, ms);
  }

  private computeYesterdayIsoInNairobi(): string {
    return this.computePastIsoInNairobi(1);
  }

  private computePastIsoInNairobi(daysBack: number): string {
    try {
      const tz = 'Africa/Nairobi';
      const now = new Date();
      const ymd = this.getYMDInTz(new Date(now.getTime() - daysBack*24*60*60*1000), tz);
      const y = ymd.y.toString().padStart(4, '0');
      const m = ymd.m.toString().padStart(2, '0');
      const d = ymd.d.toString().padStart(2, '0');
      return `${y}-${m}-${d}`;
    } catch {
      const d = new Date(); d.setDate(d.getDate() - daysBack); return this.toIsoLocal(d);
    }
  }

  private loadYesterday(): void {
    this.yesterdayLoading = true;
    this.yesterdayFlatData = [];
    const tz = 'Africa/Nairobi';
    this.fixturesApi.getFixturesByDate(this.yesterdayIso, this.season).subscribe({
      next: (res) => {
        const byLeague = Array.isArray(res) ? res.filter(Boolean) : [];
        const seen = new Set<number>();
        const flat: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
        for (const lg of byLeague) {
          const fx = lg?.fixtures || [];
          for (const f of fx) {
            if (!f) continue;
            // Include both finished and non-finished fixtures for past days
            // const status = this.computeStatus(f as any);
            // if (status === 'FINISHED') continue;
            const home = this.normalizeTeamName(f, 'home');
            const away = this.normalizeTeamName(f, 'away');
            (f as any).homeTeam = home;
            (f as any).awayTeam = away;
            if (!home || !away) continue;
            // Ensure fixture is on yesterday in EAT
            if (!this.isSameDayInTzRef((f as any).dateTime, tz, this.yesterdayIso)) continue;
            const id: number | undefined = typeof (f as any).id === 'number' ? (f as any).id : undefined;
            if (id != null) {
              if (seen.has(id)) continue;
              seen.add(id);
            }
            // Preformat display date
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
        // Keep same ordering logic as today (unanalysed first, then by kickoff)
        flat.sort((a, b) => this.compareFixturesForToday(a, b));
        this.yesterdayFlatData = flat;
        this.yesterdayLoading = false;
      },
      error: _err => {
        this.yesterdayFlatData = [];
        this.yesterdayLoading = false;
      }
    });
  }

  private loadPast(section: { daysBack: number; iso: string; collapsed: boolean; loading: boolean; flatData: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] }): void {
    if (!section || !section.iso) return;
    section.loading = true;
    section.flatData = [];
    const tz = 'Africa/Nairobi';
    this.fixturesApi.getFixturesByDate(section.iso, this.season).subscribe({
      next: (res) => {
        const byLeague = Array.isArray(res) ? res.filter(Boolean) : [];
        const seen = new Set<number>();
        const flat: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
        for (const lg of byLeague) {
          const fx = lg?.fixtures || [];
          for (const f of fx) {
            if (!f) continue;
            // Include both finished and non-finished fixtures for past days
            // const status = this.computeStatus(f as any);
            // if (status === 'FINISHED') continue;
            const home = this.normalizeTeamName(f, 'home');
            const away = this.normalizeTeamName(f, 'away');
            (f as any).homeTeam = home;
            (f as any).awayTeam = away;
            if (!home || !away) continue;
            if (!this.isSameDayInTzRef((f as any).dateTime, tz, section.iso)) continue;
            const id: number | undefined = typeof (f as any).id === 'number' ? (f as any).id : undefined;
            if (id != null) {
              if (seen.has(id)) continue;
              seen.add(id);
            }
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
        flat.sort((a, b) => this.compareFixturesForToday(a, b));
        section.flatData = flat;
        section.loading = false;
      },
      error: _err => {
        section.flatData = [];
        section.loading = false;
      }
    });
  }

  get yesterdayCount(): number { return this.yesterdayFlatData.length; }

  // trackBy function for past sections (days-back cards)
  trackByPastSection(index: number, sec: { iso?: string }): any {
    return sec?.iso || index;
  }

  // Toggle handlers: when opening a past section, close today's fixtures
  toggleYesterday(): void {
    this.yesterdayCollapsed = !this.yesterdayCollapsed;
    if (!this.yesterdayCollapsed) {
      this.todayClosed = true;
      // When opening yesterday section, consider yesterday as the selected date for printing
      this.selectedDate = this.yesterdayIso;
    }
  }

  togglePast(section: { daysBack: number; iso: string; collapsed: boolean; loading: boolean; flatData: any[] }): void {
    if (!section) return;
    section.collapsed = !section.collapsed;
    if (!section.collapsed) {
      this.todayClosed = true;
      // When opening a past section, use its date for printing
      this.selectedDate = section.iso;
    }
  }

  // Bring back Today's fixtures and collapse past sections
  showToday(): void {
    this.todayClosed = false;
    this.yesterdayCollapsed = true;
    if (Array.isArray(this.pastSections)) {
      for (const sec of this.pastSections) {
        sec.collapsed = true;
      }
    }
    // Reset selection so Print uses today's date by default
    this.selectedDate = null;
  }

  private msUntilNextAnchorMinute(anchorMinute: number): number {
    const now = new Date();
    const target = new Date(now.getTime());
    if (now.getMinutes() < anchorMinute || (now.getMinutes() === anchorMinute && now.getSeconds() === 0)) {
      target.setMinutes(anchorMinute, 0, 0);
    } else {
      // Next hour at anchor minute
      target.setHours(now.getHours() + 1, anchorMinute, 0, 0);
    }
    return Math.max(0, target.getTime() - now.getTime());
  }

  // Compute delay until the first hourly tick with a 07:00 start-of-day rule
  // If current time is before 07:00, wait until 07:00. Otherwise align to the next top-of-hour.
  private msUntilFirstHourlyTickWith7AMStart(): number {
    const now = new Date();
    const h = now.getHours();
    const m = now.getMinutes();
    const s = now.getSeconds();
    if (h < this.DAILY_FIRST_HOUR_24) {
      const target = new Date(now.getTime());
      target.setHours(this.DAILY_FIRST_HOUR_24, 0, 0, 0);
      return Math.max(0, target.getTime() - now.getTime());
    }
    // Align to next top-of-hour
    if (m === 0 && s === 0) return 0;
    const target = new Date(now.getTime());
    target.setMinutes(0, 0, 0);
    if (now.getTime() >= target.getTime()) {
      target.setHours(target.getHours() + 1, 0, 0, 0);
    }
    return Math.max(0, target.getTime() - now.getTime());
  }

  private getNextUpcomingFixture(): { item: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }, kickoff: Date } | null {
    if (!Array.isArray(this.todayFlatData) || this.todayFlatData.length === 0) return null;
    const now = new Date();
    let best: { item: any; kickoff: Date } | null = null;
    for (const item of this.todayFlatData) {
      const dtRaw = (item.fixture as any)?.dateTime;
      if (!dtRaw) continue;
      const d = new Date(dtRaw);
      if (isNaN(d.getTime())) continue;
      if (d.getTime() <= now.getTime()) continue; // only upcoming
      if (!best || d.getTime() < best.kickoff.getTime()) {
        best = { item, kickoff: d };
      }
    }
    return best;
  }

  private announceNextKickoff(): void {
    try {
      const target = this.getNextUpcomingFixture();
      if (!target) return; // Nothing upcoming – skip this slot
      const now = new Date();
      const diffMs = Math.max(0, target.kickoff.getTime() - now.getTime());
      const totalMinutes = Math.floor(diffMs / 60000);
      const hours = Math.floor(totalMinutes / 60);
      const minutes = totalMinutes % 60;

      const home = target.item.fixture?.homeTeam || 'Unknown Home';
      const away = target.item.fixture?.awayTeam || 'Unknown Away';
      const league = target.item.leagueName || 'Unknown League';
      const country = target.item.leagueCountry || 'Unknown Country';

      const hPart = hours === 1 ? '1 hour' : `${hours} hours`;
      const mPart = minutes === 1 ? '1 minute' : `${minutes} minutes`;
      const message = `It is exactly ${hPart}, ${mPart} until the next kick off which is a match between ${home} versus ${away}. The league is ${league} from the country known as ${country}. Thank you for choosing Chambuavismart. Stay tuned for more updates and analysis.`;
      this.speak(message);
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('[HomeComponent] TTS announce error', e);
    }
  }

  private pickFemaleCalmVoice(): SpeechSynthesisVoice | null {
    try {
      const synth = (window as any).speechSynthesis as SpeechSynthesis | undefined;
      if (!synth || typeof synth.getVoices !== 'function') return null;
      const voices = synth.getVoices() || [];
      if (!voices.length) return null;

      const isEnglish = (v: SpeechSynthesisVoice) => (v.lang || '').toLowerCase().startsWith('en');
      const isEnGb = (v: SpeechSynthesisVoice) => (v.lang || '').toLowerCase().startsWith('en-gb');
      const isEnUs = (v: SpeechSynthesisVoice) => (v.lang || '').toLowerCase().startsWith('en-us');

      // Common female-indicative names/URIs across engines (Microsoft, Google, Amazon-like)
      const femaleHints = /female|zira|hazel|jenny|samantha|victoria|karen|serena|susan|mia|olivia|joanna|salli|kimberly|kendra|amy|emma|google uk english female/i;
      const maleNames = /male|daniel|alex|david|mark|ryan|george|guy|microsoft david|microsoft mark|google uk english male/i;

      // 1) Prefer explicit female-indicated names in en-GB (often calmer UK timbre)
      let pick = voices.find(v => isEnGb(v) && femaleHints.test((v.name || '') + ' ' + ((v as any).voiceURI || '')));
      if (pick) return pick;
      // 2) Prefer explicit female-indicated names in en-US
      pick = voices.find(v => isEnUs(v) && femaleHints.test((v.name || '') + ' ' + ((v as any).voiceURI || '')));
      if (pick) return pick;
      // 3) Any English voice explicitly female
      pick = voices.find(v => isEnglish(v) && femaleHints.test((v.name || '') + ' ' + ((v as any).voiceURI || '')));
      if (pick) return pick;
      // 4) English voices that are not obviously male by name; prefer en-GB then en-US then any en
      pick = voices.find(v => isEnGb(v) && !maleNames.test((v.name || '') + ' ' + ((v as any).voiceURI || '')));
      if (pick) return pick;
      pick = voices.find(v => isEnUs(v) && !maleNames.test((v.name || '') + ' ' + ((v as any).voiceURI || '')));
      if (pick) return pick;
      pick = voices.find(v => isEnglish(v) && !maleNames.test((v.name || '') + ' ' + ((v as any).voiceURI || '')));
      if (pick) return pick;
      // 5) Fallback to first available
      return voices[0] || null;
    } catch { return null; }
  }

  private speak(text: string): void {
    try {
      const synth = (window as any).speechSynthesis as SpeechSynthesis | undefined;
      if (!synth || typeof (window as any).SpeechSynthesisUtterance !== 'function') return;
      const utter = new (window as any).SpeechSynthesisUtterance(text) as SpeechSynthesisUtterance;
      const voice = this.pickFemaleCalmVoice();
      if (voice) utter.voice = voice;
      utter.rate = 0.9; // slower, calm pacing as requested
      utter.pitch = 0.95; // slightly deeper female tone (but not as deep as male)
      utter.volume = 1;

      // In some browsers, voices load asynchronously
      const trySpeak = () => {
        try { synth.speak(utter); } catch {}
      };
      if (synth.getVoices().length === 0) {
        const onVoices = () => {
          trySpeak();
          try { synth.removeEventListener('voiceschanged', onVoices as any); } catch {}
        };
        try { synth.addEventListener('voiceschanged', onVoices as any); } catch {}
        // Also attempt after a short delay
        setTimeout(trySpeak, 500);
      } else {
        trySpeak();
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('[HomeComponent] speech synthesis unavailable', e);
    }
  }

  // Stops any ongoing or queued speech. Returns true if something was speaking/pending.
  private stopSpeaking(): boolean {
    try {
      const synth = (window as any).speechSynthesis as SpeechSynthesis | undefined;
      if (!synth) return false;
      if ((synth as any).speaking || (synth as any).pending) {
        try { synth.cancel(); } catch {}
        return true;
      }
      return false;
    } catch { return false; }
  }

  onNotifyMe(): void {
    try {
      // If something is currently being announced (scheduled or manual), stop it instead of starting a new one
      if (this.stopSpeaking()) return;
      const target = this.getNextUpcomingFixture();
      if (!target) {
        this.speak('Thank you for reaching out to Chambuavismart. There is no upcoming kickoff available right now.');
        return;
      }
      const now = new Date();
      const diffMs = Math.max(0, target.kickoff.getTime() - now.getTime());
      const totalMinutes = Math.floor(diffMs / 60000);
      const hours = Math.floor(totalMinutes / 60);
      const minutes = totalMinutes % 60;

      const home = target.item.fixture?.homeTeam || 'Unknown Home';
      const away = target.item.fixture?.awayTeam || 'Unknown Away';
      const league = target.item.leagueName || 'Unknown League';
      const country = target.item.leagueCountry || 'Unknown Country';

      // Format kickoff time in Africa/Nairobi local time as hh:mm a
      let localTime = '';
      try {
        localTime = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: true, timeZone: 'Africa/Nairobi' }).format(target.kickoff);
      } catch { localTime = (target.kickoff.toLocaleTimeString && target.kickoff.toLocaleTimeString()) || ''; }

      const hPart = hours === 1 ? '1 hour' : `${hours} hours`;
      const mPart = minutes === 1 ? '1 minute' : `${minutes} minutes`;

      const msg = `Thank you for reaching out to Chambuavismart. It is now exactly ${hPart}, ${mPart} to the start of the match between ${home} an ${away}, which is going to be played today in ${country}, ${league} league at ${localTime} local time. . Thank you very much for choosing chambuavismart. stay tuned for more match updates.`;
      this.speak(msg);
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('[HomeComponent] onNotifyMe error', e);
    }
  }

  ngOnDestroy(): void {
    window.removeEventListener('fixtures:refresh', this.onFixturesRefresh as EventListener);
    window.removeEventListener('fixtures:colors-updated', this._onColorsUpdated as EventListener);
    window.removeEventListener('message', this._onMessageFromChild as EventListener);
    window.removeEventListener('storage', this._onStorageUpdated as EventListener);
    if (this._clockTimer) { try { clearInterval(this._clockTimer); } catch {} this._clockTimer = null; }
    if (this._ttsAlignTimeout) { try { clearTimeout(this._ttsAlignTimeout); } catch {} this._ttsAlignTimeout = null; }
    if (this._ttsHourlyTimer) { try { clearInterval(this._ttsHourlyTimer); } catch {} this._ttsHourlyTimer = null; }
    if (this._colorVersionPoll) { try { clearInterval(this._colorVersionPoll); } catch {} this._colorVersionPoll = null; }
  }

  private isDev(): boolean {
    try {
      const host = (window && window.location && window.location.hostname) ? window.location.hostname : '';
      return host === 'localhost' || host === '127.0.0.1' || host.endsWith('.local');
    } catch { return false; }
  }

  private loadToday(): void {
    this.todayLoading = true;
    this.todayFixtures = [];
    this.todayFlatData = [];
    // eslint-disable-next-line no-console
    console.debug('[HomeComponent] fetching TODAY fixtures for', this.todayIso);
    this.fixturesApi.getFixturesByDate(this.todayIso, this.season).subscribe({
      next: res => {
        this.todayFixtures = Array.isArray(res) ? res.filter(Boolean) : [];
        // Build stable flat list once (normalize, filter, sort)
        const tz = 'Africa/Nairobi';
        const seen = new Set<number>();
        const flat: { leagueId: number; leagueName: string; leagueCountry?: string; fixture: any; }[] = [];
        for (const lg of this.todayFixtures) {
          const fx = lg?.fixtures || [];
          for (const f of fx) {
            if (!f) continue;
            const home = this.normalizeTeamName(f, 'home');
            const away = this.normalizeTeamName(f, 'away');
            // Attach normalized names directly to fixture for consistent template binding
            (f as any).homeTeam = home;
            (f as any).awayTeam = away;
            if (!home || !away) continue; // exclude invalid entries
            if (!this.isSameDayInTz((f as any).dateTime, tz)) continue; // keep only today's fixtures in EAT
            const id: number | undefined = typeof (f as any).id === 'number' ? (f as any).id : undefined;
            if (id != null) {
              if (seen.has(id)) continue;
              seen.add(id);
            }
            // Preformat date for display to minimize DatePipe usage
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
        // Sort with priority: fixtures without analysis colors first, then by kickoff time ascending
        flat.sort((a, b) => this.compareFixturesForToday(a, b));
        this.todayFlatData = flat;
        // Initialize hourly voice announcements once we know today's upcoming fixtures
        try { this.setupHourlyKickoffAnnouncements(); } catch {}
        if (this.isDev()) {
          try {
            // eslint-disable-next-line no-console
            console.log('[HomeComponent] Normalized today fixtures:', {
              totalLeagues: this.todayFixtures.length,
              total: this.todayFlatData.length,
              sample: this.todayFlatData.slice(0, 5).map(x => ({ home: x.fixture.homeTeam, away: x.fixture.awayTeam, dt: x.fixture.formattedDate || x.fixture.dateTime }))
            });
          } catch {}
        }
        const allValid = this.todayFlatData.every(it => !!(it.fixture.homeTeam && it.fixture.awayTeam));
        if (!allValid) {
          // eslint-disable-next-line no-console
          console.warn('[HomeComponent] Some fixtures missing names after normalization');
        }
        this.todayLoading = false;
      },
      error: err => {
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] TODAY fixtures load error', err);
        this.todayFixtures = []; this.todayFlatData = []; this.todayLoading = false;
      }
    });
  }

  get formattedDate(): string {
    const d = this.today;
    const day = d.getDate();
    const month = d.toLocaleString('en-GB', { month: 'short' });
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
  }

  get calendarMonthLabel(): string {
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    const month = d.toLocaleString('en-GB', { month: 'long' });
    return `${month} ${this.calendarYear}`;
  }

  private toIsoLocal(d: Date): string {
    const y = d.getFullYear();
    const m = (d.getMonth() + 1).toString().padStart(2, '0');
    const day = d.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private parseIsoLocal(iso: string): Date {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, (m ?? 1) - 1, d ?? 1);
  }

  get daysGrid(): { day: number; inMonth: boolean; iso: string; hasFixtures: boolean; isPast: boolean; }[] {
    const first = new Date(this.calendarYear, this.calendarMonth, 1);
    const startDay = first.getDay() === 0 ? 6 : first.getDay() - 1; // Monday=0
    const daysInMonth = new Date(this.calendarYear, this.calendarMonth + 1, 0).getDate();
    const prevMonthDays = new Date(this.calendarYear, this.calendarMonth, 0).getDate();

    const grid: { day: number; inMonth: boolean; iso: string; hasFixtures: boolean; isPast: boolean; }[] = [];

    // previous month fillers
    for (let i = startDay - 1; i >= 0; i--) {
      const day = prevMonthDays - i;
      const d = new Date(this.calendarYear, this.calendarMonth - 1, day);
      const iso = this.toIsoLocal(d);
      grid.push({ day, inMonth: false, iso, hasFixtures: this.availableDates.has(iso), isPast: d <= this.today });
    }

    // current month
    for (let day = 1; day <= daysInMonth; day++) {
      const d = new Date(this.calendarYear, this.calendarMonth, day);
      const iso = this.toIsoLocal(d);
      grid.push({ day, inMonth: true, iso, hasFixtures: this.availableDates.has(iso), isPast: d <= this.today });
    }

    // next month fillers to complete weeks
    while (grid.length % 7 !== 0) {
      const last = grid[grid.length - 1];
      const lastDate = this.parseIsoLocal(last.iso);
      const nextDate = new Date(lastDate.getFullYear(), lastDate.getMonth(), lastDate.getDate() + 1);
      const iso = this.toIsoLocal(nextDate);
      grid.push({ day: nextDate.getDate(), inMonth: false, iso, hasFixtures: this.availableDates.has(iso), isPast: nextDate <= this.today });
    }

    return grid;
  }

  stats = {
    matchesAnalyzed: 0,
    avgConfidence: 75,
    fixturesUploaded: 0
  };

  toggleCalendar() {
    this.showCalendar = !this.showCalendar;
    if (this.showCalendar) {
      this.fetchAvailableDates();
    }
  }

  // Load leaders for highlighting logic
  private loadLeaders() {
    const categories = ['btts','over15','over25','wins','draws'];
    const calls = categories.map(c => this.leadersApi.getLeaders(c, 5, 5, 'overall', 0));
    forkJoin(calls).subscribe((lists: GlobalLeader[][]) => {
      const names = new Set<string>();
      const byTeam = new Map<string, GlobalLeader[]>();
      for (const list of lists) {
        for (const l of list) {
          if (!l?.teamName) continue;
          const key = l.teamName.toLowerCase();
          names.add(key);
          const arr = byTeam.get(key) || [];
          arr.push(l);
          byTeam.set(key, arr);
        }
      }
      this.leaderTeams = names;
      this.leadersByTeam = byTeam;
    }, _err => {
      this.leaderTeams = new Set<string>();
      this.leadersByTeam = new Map<string, GlobalLeader[]>();
    });
  }

  prevMonth() {
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    d.setMonth(d.getMonth() - 1);
    this.calendarYear = d.getFullYear();
    this.calendarMonth = d.getMonth();
    this.fetchAvailableDates();
  }

  nextMonth() {
    const d = new Date(this.calendarYear, this.calendarMonth, 1);
    d.setMonth(d.getMonth() + 1);
    this.calendarYear = d.getFullYear();
    this.calendarMonth = d.getMonth();
    this.fetchAvailableDates();
  }

  fetchAvailableDates() {
    const year = this.calendarYear;
    const month = this.calendarMonth + 1; // 1-based
    this.fixturesApi.getAvailableDates(year, month, this.season).subscribe(ds => {
      this.availableDates = new Set((ds ?? []).filter(Boolean));
    });
  }

  selectDate(iso: string) {
    // Always allow selecting any date; backend will return fixtures or an empty list.
    this.selectedDate = iso;
    this.loadingPast = true;
    this.pastFixtures = [];
    // eslint-disable-next-line no-console
    console.debug('[HomeComponent] fetching fixtures for date', iso);
    const hasDot = this.availableDates?.has?.(iso) === true;
    const handleSuccess = (res: any, attemptedRefresh: boolean) => {
      this.pastFixtures = Array.isArray(res) ? res.filter(Boolean) : [];
      // eslint-disable-next-line no-console
      console.debug('[HomeComponent] fixtures loaded:', this.pastFixtures.length, '(refresh:', attemptedRefresh, ')');
      if (hasDot && (!this.pastFixtures || this.pastFixtures.length === 0) && !attemptedRefresh) {
        // Retry once with refresh=true if calendar indicated fixtures should exist
        // eslint-disable-next-line no-console
        console.debug('[HomeComponent] empty result but calendar shows availability; retrying with refresh=true');
        this.fixturesApi.getFixturesByDate(iso, this.season, true).subscribe({
          next: r2 => { handleSuccess(r2, true); },
          error: e2 => { handleError(e2, true); }
        });
        return;
      }
      this.loadingPast = false;
    };
    const handleError = (err: any, attemptedRefresh: boolean) => {
      // eslint-disable-next-line no-console
      console.debug('[HomeComponent] fixtures load error', err, '(refresh:', attemptedRefresh, ')');
      this.pastFixtures = [];
      this.loadingPast = false;
    };
    this.fixturesApi.getFixturesByDate(iso, this.season).subscribe({
      next: res => handleSuccess(res, false),
      error: err => handleError(err, false)
    });
    this.showCalendar = false;
  }

  // Derive display status using current time with 24h overdue rule
  statusLabel(f: { dateTime: string; homeScore: number | null; awayScore: number | null; status?: any }): string {
    const s = this.computeStatus(f);
    switch (s) {
      case 'FINISHED': return 'Finished';
      case 'RESULTS_MISSING': return 'Results Missing';
      case 'AWAITING': return 'Awaiting Results';
      default: return 'Upcoming';
    }
  }

  computeStatus(f: { dateTime: string; homeScore: number | null; awayScore: number | null }): 'UPCOMING' | 'AWAITING' | 'RESULTS_MISSING' | 'FINISHED' {
    if (this.hasResults(f)) return 'FINISHED';
    const fixtureMs = this.toUtcMillis(f?.dateTime);
    if (isNaN(fixtureMs)) return 'UPCOMING';
    const nowMs = Date.now();
    if (fixtureMs > nowMs) return 'UPCOMING';
    const overdueMs = 24 * 60 * 60 * 1000;
    return (fixtureMs < (nowMs - overdueMs)) ? 'RESULTS_MISSING' : 'AWAITING';
  }

  private hasResults(f: { homeScore: number | null; awayScore: number | null }): boolean {
    return f?.homeScore != null && f?.awayScore != null;
  }

  private toUtcMillis(iso: string | undefined): number {
    if (!iso) return NaN;
    const hasTZ = /[zZ]|[+-]\d{2}:\d{2}$/.test(iso);
    const s = hasTZ ? iso : iso + 'Z';
    const t = Date.parse(s);
    if (!isNaN(t)) return t;
    // fallback
    const d = new Date(iso);
    return d.getTime();
  }

  // Determine if a fixture has been analysed based on presence of team colors in cache
  private hasAnalysisColors(item: { leagueId?: number; fixture: any }): boolean {
    try {
      const leagueId = item?.leagueId ?? undefined;
      const f = item?.fixture || {};
      const home = (f.homeTeam || this.normalizeTeamName(f, 'home') || '').toString().trim();
      const away = (f.awayTeam || this.normalizeTeamName(f, 'away') || '').toString().trim();
      if (!home && !away) return false;
      const c1 = home ? this.colorCache.getTeamColor(home, leagueId) : null;
      const c2 = away ? this.colorCache.getTeamColor(away, leagueId) : null;
      return !!(c1 || c2);
    } catch { return false; }
  }

  // Comparator: unanalysed fixtures first; within each group, order by kickoff time ascending
  private compareFixturesForToday = (a: { leagueId?: number; fixture: any }, b: { leagueId?: number; fixture: any }): number => {
    const aAnalysed = this.hasAnalysisColors(a) ? 1 : 0;
    const bAnalysed = this.hasAnalysisColors(b) ? 1 : 0;
    if (aAnalysed !== bAnalysed) return aAnalysed - bAnalysed; // 0 (unanalysed) comes before 1 (analysed)
    const ta = this.toUtcMillis(a.fixture?.dateTime);
    const tb = this.toUtcMillis(b.fixture?.dateTime);
    if (isNaN(ta) && isNaN(tb)) return 0;
    if (isNaN(ta)) return 1;
    if (isNaN(tb)) return -1;
    return ta - tb;
  }

  private resortTodayFlatData(): void {
    if (!Array.isArray(this.todayFlatData) || this.todayFlatData.length === 0) return;
    this.todayFlatData = this.todayFlatData.slice().sort(this.compareFixturesForToday);
  }

  // Returns {y,m,d} of given Date in a given IANA timezone using Intl API
  private getYMDInTz(date: Date, timeZone: string): { y: number; m: number; d: number } {
    const fmt = new Intl.DateTimeFormat('en-GB', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
    const parts = fmt.formatToParts(date);
    const y = Number(parts.find(p => p.type === 'year')?.value);
    const m = Number(parts.find(p => p.type === 'month')?.value);
    const d = Number(parts.find(p => p.type === 'day')?.value);
    return { y, m, d };
  }

  // Check if an ISO string occurs on the same calendar day as now in a specific time zone
  private isSameDayInTz(iso: string | undefined, timeZone: string): boolean {
    if (!iso) return false;
    const dt = new Date(iso);
    if (isNaN(dt.getTime())) return false;
    const todayYMD = this.getYMDInTz(new Date(), timeZone);
    const thatYMD = this.getYMDInTz(dt, timeZone);
    return todayYMD.y === thatYMD.y && todayYMD.m === thatYMD.m && todayYMD.d === thatYMD.d;
  }

  // Compare a fixture date to a specific reference calendar day in the given timezone
  private isSameDayInTzRef(iso: string | undefined, timeZone: string, refIso: string): boolean {
    if (!iso || !refIso) return false;
    const dt = new Date(iso);
    if (isNaN(dt.getTime())) return false;
    const ref = this.parseIsoLocal(refIso);
    const thatYMD = this.getYMDInTz(dt, timeZone);
    const refYMD = this.getYMDInTz(ref, timeZone);
    return thatYMD.y === refYMD.y && thatYMD.m === refYMD.m && thatYMD.d === refYMD.d;
  }

  // Leader helpers
  private isFixtureToday(f: { dateTime: string }): boolean {
    const d = f?.dateTime ? new Date(f.dateTime) : null;
    if (!d || isNaN(d.getTime())) return false;
    const now = new Date();
    return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
  }

  involvesLeader(f: { homeTeam: string; awayTeam: string }): boolean {
    const home = f?.homeTeam?.toLowerCase?.();
    const away = f?.awayTeam?.toLowerCase?.();
    if (!home || !away) return false;
    return this.leaderTeams.has(home) || this.leaderTeams.has(away);
  }

  leaderTooltip(f: any): string | null {
    if (!f) return null;
    const homeKey = f.homeTeam?.toLowerCase?.();
    const awayKey = f.awayTeam?.toLowerCase?.();
    const parts: string[] = [];
    const fmt = (cat: string) => {
      switch (cat) {
        case 'btts': return 'BTTS';
        case 'over15': return 'Over 1.5 Goals';
        case 'over25': return 'Over 2.5 Goals';
        case 'wins': return 'Wins';
        case 'draws': return 'Draws';
        default: return cat;
      }
    };
    const build = (teamLabel: string, key: string | undefined) => {
      if (!key) return;
      const entries = this.leadersByTeam.get(key) || [];
      if (!entries.length) return;
      const details = entries
        .sort((a: any, b: any) => (b.statPct - a.statPct))
        .map((e: any) => `${fmt(e.category)}: ${Math.round(e.statPct)}% (${e.statCount}/${e.matchesPlayed})`)
        .join('; ');
      parts.push(`${teamLabel} leads in ${details}`);
    };

    build(f.homeTeam, homeKey);
    if (awayKey !== homeKey) {
      build(f.awayTeam, awayKey);
    }

    return parts.length ? parts.join(' • ') : null;
  }

  // New: robust display resolver for team names at render-time
  displayTeamName(f: any, side: 'home' | 'away'): string {
    if (!f) return '';
    const keys = side === 'home'
      ? ['homeTeam','home_team','home','home_name','homeTeamName']
      : ['awayTeam','away_team','away','away_name','awayTeamName'];
    for (const k of keys) {
      const v = (f as any)[k];
      if (typeof v === 'string' && v.trim()) return v.trim();
    }
    // Common nested structures
    const nested = side === 'home'
      ? ((f as any).home || (f as any).home_team || (f as any).teams?.home || (f as any).teamHome)
      : ((f as any).away || (f as any).away_team || (f as any).teams?.away || (f as any).teamAway);
    if (nested) {
      if (typeof nested === 'string' && nested.trim()) return nested.trim();
      if (typeof nested.name === 'string' && nested.name.trim()) return nested.name.trim();
      if (typeof nested.team === 'string' && nested.team.trim()) return nested.team.trim();
      if (typeof nested.title === 'string' && nested.title.trim()) return nested.title.trim();
    }
    // Fallback to normalized fields if present
    const fallback = side === 'home' ? f.homeTeam : f.awayTeam;
    return typeof fallback === 'string' ? (fallback || '').trim() : '';
  }

  // Style for colored team pills based on Fixtures Analysis cache
  teamPillStyle(teamName: string | null | undefined, leagueId?: number | null): {[k: string]: string} {
    const name = (teamName || '').toString().trim();
    if (!name) return {};
    try {
      const colorRaw = this.colorCache.getTeamColor(name, leagueId ?? undefined);
      if (!colorRaw) return {};
      const s = colorRaw.toString().toLowerCase();
      let bg: string | null = null;
      if (s.includes('green') || s.includes('#16a34a') || s.includes('16,160,16')) bg = '#16a34a';
      else if (s.includes('red') || s.includes('#ef4444') || s.includes('204, 43, 59')) bg = '#ef4444';
      else if (s.includes('orange') || s.includes('#f59e0b') || s.includes('255, 165, 0')) bg = '#f59e0b';
      else return {};
      const style: {[k: string]: string} = {
        background: bg,
        color: '#ffffff',
        padding: '2px 6px',
        borderRadius: '6px',
        border: '1px solid rgba(255,255,255,0.15)'
      };
      if (this.colorCache.isDoubleGreen(name, leagueId ?? undefined)) {
        style.boxShadow = 'inset 0 0 0 3px #16a34a, inset 0 0 0 7px rgba(22,163,74,0.65), 0 0 0 2px #16a34a, 0 0 10px 3px rgba(22,163,74,0.75)';
      }
      return style;
    } catch { return {}; }
  }

  // New: decorate team label with special prefixes: glowing orange D for draw-heavy & fire for doubleGreen
  formatTeamLabel(teamName: string | null | undefined, leagueId?: number | null): string {
    const name = (teamName || '').toString().trim();
    if (!name) return '';
    try {
      const hasD = this.colorCache.hasDrawHeavyD(name, leagueId ?? undefined);
      const hasFire = this.colorCache.isDoubleGreen(name, leagueId ?? undefined);
      const fire = hasFire ? '🔥 ' : '';
      const d = hasD ? 'D ' : '';
      return (d + fire + name).trim();
    } catch { return name; }
  }

  // HTML version with styling for the big shining orange D and appending streak count when applicable
  formatTeamLabelHtml(teamName: string | null | undefined, leagueId?: number | null) {
    const name = (teamName || '').toString().trim();
    if (!name) return '';
    try {
      const lid = leagueId ?? undefined;
      const hasD = this.colorCache.hasDrawHeavyD(name, lid);
      const hasFire = this.colorCache.isDoubleGreen(name, lid);
      const streak = this.colorCache.getTeamStreakCount(name, lid);
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

  // Kick off legacy backfill for visible teams: enrich colors with longest streak counts
  private backfillVisibleTeams(): void {
    try {
      const collect: { name: string; leagueId?: number | null }[] = [];
      const push = (name: any, leagueId?: number | null) => {
        const n = (name || '').toString().trim();
        if (!n) return;
        const key = (leagueId != null ? `${n.toLowerCase()}#${leagueId}` : n.toLowerCase());
        if (this._backfillSeen.has(key)) return;
        const color = this.colorCache.getTeamColor(n, leagueId ?? undefined);
        if (!color) return;
        if (this.colorCache.isIndigoColor(color)) return; // skip fallback
        const sc = this.colorCache.getTeamStreakCount(n, leagueId ?? undefined);
        if (sc && sc > 0) { this._backfillSeen.add(key); return; }
        collect.push({ name: n, leagueId });
      };
      // Today
      for (const it of this.todayFlatData || []) {
        push(it?.fixture?.homeTeam, it?.leagueId);
        push(it?.fixture?.awayTeam, it?.leagueId);
      }
      // Yesterday
      for (const it of this.yesterdayFlatData || []) {
        push(it?.fixture?.homeTeam, it?.leagueId);
        push(it?.fixture?.awayTeam, it?.leagueId);
      }
      // Past sections
      for (const sec of this.pastSections || []) {
        for (const it of sec?.flatData || []) {
          push(it?.fixture?.homeTeam, it?.leagueId);
          push(it?.fixture?.awayTeam, it?.leagueId);
        }
      }
      // De-dupe and backfill sequentially to avoid bursts
      const runNext = () => {
        if (!collect.length) return;
        const { name, leagueId } = collect.shift()!;
        const key = (leagueId != null ? `${name.toLowerCase()}#${leagueId}` : name.toLowerCase());
        if (!this.colorCache.markBackfillInFlight(name, leagueId ?? undefined)) { runNext(); return; }
        this.matchApi.getResultsBreakdownByTeamName(name).subscribe({
          next: (bd) => {
            const cnt = (bd?.longestStreakCount ?? 0) as number;
            const t = (bd?.longestStreakType || '').toString().toUpperCase();
            if (cnt && cnt > 0) {
              this.colorCache.setTeamStreakCount(name, cnt, leagueId ?? undefined);
            } else {
              this.colorCache.setTeamStreakCount(name, null, leagueId ?? undefined);
            }
            // Always set color based on streak type regardless of count threshold
            if (t === 'W') {
              this.colorCache.setTeamColor(name, '#16a34a', leagueId ?? undefined);
            } else if (t === 'L') {
              this.colorCache.setTeamColor(name, '#ef4444', leagueId ?? undefined);
            } else if (t === 'D') {
              this.colorCache.setTeamColor(name, '#f59e0b', leagueId ?? undefined);
            } else {
              // No streak type: clear any previous color to avoid stale/legacy colors
              this.colorCache.removeTeamColor(name, leagueId ?? undefined);
            }
          },
          error: _ => {},
          complete: () => {
            this._backfillSeen.add(key);
            this.colorCache.clearBackfillInFlight(name, leagueId ?? undefined);
            // schedule next with slight delay to yield UI
            setTimeout(runNext, 100);
          }
        });
      };
      // Start a few in parallel but small
      const par = Math.min(collect.length, 3);
      for (let i = 0; i < par; i++) setTimeout(runNext, 0);
    } catch {}
  }

  // React to color updates from Fixtures Analysis tab
  private _onColorsUpdated = (_ev: Event) => {
    try {
      this.zone.run(() => {
        // Resort based on updated analysis colors and refresh view
        this.resortTodayFlatData();
        // Attempt legacy backfill for any colored teams missing streak counts
        this.backfillVisibleTeams();
        try { this.cdr.detectChanges(); } catch {}
      });
    } catch { /* no-op */ }
  };

  // Handle postMessage from iframe (played-matches-summary modal)
  private _onMessageFromChild = (ev: MessageEvent) => {
    try {
      const data: any = ev?.data;
      if (!data) return;
      // Accept either structured type or legacy string channel
      const type = typeof data === 'string' ? data : data.type;
      if (type === 'fixtures:colors-updated') {
        this._onColorsUpdated(ev as any);
      }
    } catch { /* ignore */ }
  };

  // Handle localStorage updates from child frame (storage event only fires on other documents)
  private _onStorageUpdated = (ev: StorageEvent) => {
    try {
      if (!ev) return;
      const k = ev.key || '';
      if (k === 'fixturesAnalysis.teamColors.v1' || k === 'fixturesAnalysis.teamColors.version') {
        this._onColorsUpdated(ev as any);
      }
    } catch { /* ignore */ }
  };
 
  // Open Played Matches Summary (Fixture Analysis) in a modal overlay instead of navigating away
   // Keeps user on Home and preserves scroll/expanded sections
   goToAnalysis(league: { leagueId?: number; leagueName?: string } | null, f: any) {
     if (!f) return;
     const leagueId = league?.leagueId ?? null;
     const h2hHome = encodeURIComponent((f?.homeTeam || this.normalizeTeamName(f, 'home') || ''));
     const h2hAway = encodeURIComponent((f?.awayTeam || this.normalizeTeamName(f, 'away') || ''));
     const qp = `${h2hHome && h2hAway ? `?h2hHome=${h2hHome}&h2hAway=${h2hAway}` : ''}${leagueId ? (h2hHome || h2hAway ? `&leagueId=${leagueId}` : `?leagueId=${leagueId}`) : ''}`;
     const url = `/played-matches-summary${qp}`;
     try {
       this.analysisUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
       this.analysisModalOpen = true;
       // Optional: prevent background scroll
       document.body.style.overflow = 'hidden';
     } catch {
       // Fallback to navigation if sanitizer fails
       this.router.navigate(['/played-matches-summary'], {
         queryParams: {
           h2hHome: (f?.homeTeam || this.normalizeTeamName(f, 'home') || ''),
           h2hAway: (f?.awayTeam || this.normalizeTeamName(f, 'away') || ''),
           ...(leagueId ? { leagueId } : {})
         }
       });
     }
   }

   closeAnalysisModal() {
     this.analysisModalOpen = false;
     this.analysisUrl = null;
     try { document.body.style.overflow = ''; } catch {}
   }

  // trackBy to stabilize DOM and performance
  trackByFixtureId(index: number, item: { fixture: any }): string | number {
    const f = item?.fixture as any;
    return (f && (typeof f.id === 'number' || typeof f.id === 'string')) ? f.id : `${f?.homeTeam || ''}-${f?.awayTeam || ''}-${f?.dateTime || index}`;
  }
}
