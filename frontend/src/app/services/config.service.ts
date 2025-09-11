import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { getApiBase } from './api-base';

export interface FlagsDto { predictiveH2HPhase1Enabled: boolean; }

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private http = inject(HttpClient);
  private baseUrl = `${getApiBase()}/config`;

  getFlags(): Observable<FlagsDto> {
    return this.http.get<FlagsDto>(`${this.baseUrl}/flags`);
  }
}
