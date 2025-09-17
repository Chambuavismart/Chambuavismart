import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';
import { MatchAnalysisResponse } from './match-analysis.service';

@Injectable({ providedIn: 'root' })
export class PersistedAnalysesService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/persisted-analyses`;

  getPersistedToday(): Observable<MatchAnalysisResponse[]> {
    return this.http.get<MatchAnalysisResponse[]>(`${this.baseUrl}/today`);
  }

  getPersistedTodayPdfUrl(): string {
    return `${this.baseUrl}/today/pdf`;
  }
}
