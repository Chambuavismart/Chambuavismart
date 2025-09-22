import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

export interface QuickInsightItem {
  fixtureId: number;
  leagueId: number;
  league: string;
  home: string;
  away: string;
  kickoff: string; // ISO instant
  trigger?: string;
  triggers?: string[];
}

export interface QuickInsightsResponse {
  highInterest: QuickInsightItem[];
  topPicks: QuickInsightItem[];
}

@Injectable({ providedIn: 'root' })
export class InsightsService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/insights`;

  getQuickInsights(): Observable<QuickInsightsResponse> {
    return this.http.get<QuickInsightsResponse>(`${this.baseUrl}/quick`);
  }
}
