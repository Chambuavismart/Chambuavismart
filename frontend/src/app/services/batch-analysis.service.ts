import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface BatchStartResponse { jobId: string }
export interface BatchStatus {
  jobId: string;
  date: string;
  status: 'PENDING'|'RUNNING'|'COMPLETED'|'FAILED';
  total: number; completed: number; failed: number; inProgress: number;
  startedAt?: string; finishedAt?: string; etaSeconds: number;
}

export interface FixtureAnalysisResult {
  fixtureId: number;
  leagueId: number;
  leagueName: string;
  leagueCountry?: string;
  kickoff?: string;
  homeTeam: string;
  awayTeam: string;
  success: boolean;
  error?: string;
  durationMs: number;
  cacheHit: boolean;
  payload?: any; // MatchAnalysisResponse
}

@Injectable({ providedIn: 'root' })
export class BatchAnalysisService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/fixture-analysis/batch`;

  start(date?: string, seasonId?: number, refresh: boolean = false, analysisMode?: 'fixture'|'match'): Observable<BatchStartResponse> {
    const params: any = {};
    if (date) params.date = date;
    if (seasonId != null) params.seasonId = seasonId;
    if (refresh) params.refresh = true;
    if (analysisMode) params.analysisMode = analysisMode.toUpperCase();
    const t0 = performance.now?.() ?? Date.now();
    // eslint-disable-next-line no-console
    console.debug('[BatchAnalysisService][start] params=', params);
    return this.http.post<BatchStartResponse>(`${this.baseUrl}`, null, { params }).pipe(
      // eslint-disable-next-line no-console
      (window as any).rxjs?.operators?.tap ? (window as any).rxjs.operators.tap((res:any)=>console.debug('[BatchAnalysisService][start][OK]', res, 'in', (performance.now?.() ?? Date.now()) - t0, 'ms')) : (src:any)=>src
    );
  }

  status(jobId: string): Observable<BatchStatus> {
    const t0 = performance.now?.() ?? Date.now();
    // eslint-disable-next-line no-console
    console.debug('[BatchAnalysisService][status] jobId=', jobId);
    return this.http.get<BatchStatus>(`${this.baseUrl}/${jobId}`).pipe(
      // eslint-disable-next-line no-console
      (window as any).rxjs?.operators?.tap ? (window as any).rxjs.operators.tap((res:any)=>console.debug('[BatchAnalysisService][status][OK]', res, 'in', (performance.now?.() ?? Date.now()) - t0, 'ms')) : (src:any)=>src
    );
  }

  results(jobId: string, page = 0, size = 50) {
    const t0 = performance.now?.() ?? Date.now();
    // eslint-disable-next-line no-console
    console.debug('[BatchAnalysisService][results] jobId=', jobId, 'page=', page, 'size=', size);
    return this.http.get<any>(`${this.baseUrl}/${jobId}/results`, { params: { page, size } }).pipe(
      // eslint-disable-next-line no-console
      (window as any).rxjs?.operators?.tap ? (window as any).rxjs.operators.tap((res:any)=>console.debug('[BatchAnalysisService][results][OK]', res, 'in', (performance.now?.() ?? Date.now()) - t0, 'ms')) : (src:any)=>src
    );
  }

  consolidatedPdf(jobId: string) { return `${this.baseUrl}/${jobId}/pdf`; }
  zip(jobId: string) { return `${this.baseUrl}/${jobId}/zip`; }
}
