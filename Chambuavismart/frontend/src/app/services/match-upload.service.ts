import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type UploadType = 'NEW_LEAGUE' | 'FULL_REPLACE' | 'INCREMENTAL' | 'FIXTURE';

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
    form.append('strict', 'true');
    form.append('dryRun', 'false');
    form.append('allowSeasonAutoCreate', 'false');
    if (seasonId !== undefined && seasonId !== null) form.append('seasonId', String(seasonId));
    form.append('file', file);
    return this.http.post(`${this.legacyBaseUrl}/csv`, form);
  }

  legacyUploadText(leagueName: string, country: string, season: string, text: string, fullReplace = true, incrementalUpdate = false, seasonId?: number | null): Observable<any> {
    const body: any = { leagueName, country, season, text, fullReplace, incrementalUpdate, strict: true, dryRun: false, allowSeasonAutoCreate: false };
    if (seasonId !== undefined && seasonId !== null) body.seasonId = seasonId;
    return this.http.post(`${this.legacyBaseUrl}/text`, body);
  }

  // Unified multipart (CSV)
  uploadUnifiedCsv(uploadType: UploadType, leagueName: string, country: string, season: string, file: File, options?: { seasonId?: number | null; leagueId?: number | null; autoDetectSeason?: boolean; strict?: boolean; dryRun?: boolean; allowSeasonAutoCreate?: boolean }): Observable<any> {
    const form = new FormData();
    form.append('uploadType', uploadType);
    if (options?.leagueId !== undefined && options?.leagueId !== null) form.append('leagueId', String(options.leagueId));
    if (options?.seasonId !== undefined && options?.seasonId !== null) form.append('seasonId', String(options.seasonId));
    // Implicit flags for NEW_LEAGUE: allowSeasonAutoCreate=true, strict=true, dryRun=false
    const isNewLeague = uploadType === 'NEW_LEAGUE';
    form.append('autoDetectSeason', String(!!options?.autoDetectSeason));
    form.append('strict', String(isNewLeague ? true : (options?.strict ?? true)));
    form.append('dryRun', String(isNewLeague ? false : (options?.dryRun ?? false)));
    form.append('allowSeasonAutoCreate', String(isNewLeague ? true : (options?.allowSeasonAutoCreate ?? false)));
    form.append('leagueName', leagueName);
    form.append('country', country);
    form.append('season', season);
    form.append('file', file);
    return this.http.post(this.unifiedBaseUrl, form);
  }

  // Unified JSON (raw text)
  uploadUnifiedText(uploadType: UploadType, leagueName: string, country: string, season: string, text: string, options?: { seasonId?: number | null; leagueId?: number | null; autoDetectSeason?: boolean; strict?: boolean; dryRun?: boolean; allowSeasonAutoCreate?: boolean }): Observable<any> {
    const isNewLeague = uploadType === 'NEW_LEAGUE';
    const body: any = {
      uploadType,
      leagueId: options?.leagueId ?? null,
      seasonId: options?.seasonId ?? null,
      autoDetectSeason: !!options?.autoDetectSeason,
      strict: isNewLeague ? true : (options?.strict ?? true),
      dryRun: isNewLeague ? false : (options?.dryRun ?? false),
      allowSeasonAutoCreate: isNewLeague ? true : (options?.allowSeasonAutoCreate ?? false),
      leagueName,
      country,
      season,
      text
    };
    return this.http.post(this.unifiedBaseUrl, body);
  }
}
