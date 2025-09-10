import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBase } from './api-base';

@Injectable({ providedIn: 'root' })
export class MatchService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/matches`;

  getTotalPlayedMatches(): Observable<number> {
    return this.http.get<number>(`${this.baseUrl}/played/total`);
  }
}
