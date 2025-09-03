import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type UploadType = 'NEW_LEAGUE' | 'FULL_REPLACE' | 'INCREMENTAL' | 'FIXTURE' | 'HISTORICAL';

@Injectable({ providedIn: 'root' })
export class MatchUploadService {
  private legacyBaseUrl = '/api/matches/upload';
  private unifiedBaseUrl = '/api/uploads/matches';

  constructor(private http: HttpClient) {}

  // Legacy endpoints retained for fallback (not used by updated component by default)
  legacyUploadCsv(leagueName: string, country: string, season: string, file: File, fullReplace = true, incrementalUpdate = false, seasonId?: number | null): Observable<any> {
    const form = new FormData();
    form.append('leagueName', leagueName);
    form.append('country', country);
    form.append('season', season);
    form.append('fullReplace', String(fullReplace));
    form.append('incrementalUpdate', String(incrementalUpdate));
    if (seasonId) form.append('seasonId', String(seasonId));
    form.append('file', file);
    return this.http.post(`${this.legacyBaseUrl}/csv`, form);
  }

  legacyUploadText(leagueName: string, country: string, season: string, text: string, fullReplace = true, incrementalUpdate = false, seasonId?: number | null): Observable<any> {
    const body: any = { leagueName, country, season, text, fullReplace, incrementalUpdate };
    if (seasonId) body.seasonId = seasonId;
    return this.http.post(`${this.legacyBaseUrl}/text`, body);
  }

  // Unified multipart (CSV)
  uploadUnifiedCsv(uploadType: UploadType, leagueName: string, country: string, season: string, file: File, options?: { seasonId?: number | null; leagueId?: number | null; autoDetectSeason?: boolean }): Observable<any> {
    const form = new FormData();
    form.append('uploadType', uploadType);
    if (options?.leagueId) form.append('leagueId', String(options.leagueId));
    if (options?.seasonId) form.append('seasonId', String(options.seasonId));
    form.append('autoDetectSeason', String(!!options?.autoDetectSeason));
    form.append('leagueName', leagueName);
    form.append('country', country);
    form.append('season', season);
    form.append('file', file);
    return this.http.post(this.unifiedBaseUrl, form);
  }

  // Unified JSON (raw text)
  uploadUnifiedText(uploadType: UploadType, leagueName: string, country: string, season: string, text: string, options?: { seasonId?: number | null; leagueId?: number | null; autoDetectSeason?: boolean }): Observable<any> {
    const body: any = {
      uploadType,
      leagueId: options?.leagueId ?? null,
      seasonId: options?.seasonId ?? null,
      autoDetectSeason: !!options?.autoDetectSeason,
      leagueName,
      country,
      season,
      text
    };
    return this.http.post(this.unifiedBaseUrl, body);
  }
}
