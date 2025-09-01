import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class MatchUploadService {
  private baseUrl = '/api/matches/upload';

  constructor(private http: HttpClient) {}

  uploadCsv(leagueName: string, country: string, season: string, file: File, fullReplace = true): Observable<any> {
    const form = new FormData();
    form.append('leagueName', leagueName);
    form.append('country', country);
    form.append('season', season);
    form.append('fullReplace', String(fullReplace));
    form.append('file', file);
    return this.http.post(`${this.baseUrl}/csv`, form);
  }

  uploadText(leagueName: string, country: string, season: string, text: string, fullReplace = true): Observable<any> {
    const body = { leagueName, country, season, text, fullReplace };
    return this.http.post(`${this.baseUrl}/text`, body);
  }
}
