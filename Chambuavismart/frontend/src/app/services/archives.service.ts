import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ImportRunSummary {
  id: number;
  status: string;
  rowsSuccess: number;
  rowsFailed: number;
  rowsTotal: number;
  provider?: string;
  competitionCode?: string;
  startedAt?: string;
  finishedAt?: string;
  filename?: string;
  createdBy?: string;
}

export interface ImportError {
  id: number;
  rowNumber: number;
  errorMessage: string;
  rawData: string;
}

export interface PreviewRow {
  status: 'ok' | 'warn' | 'error';
  reason?: string;
  values: string[];
}

export interface ImportPreviewResponse {
  headers: string[];
  rows: PreviewRow[];
}

@Injectable({ providedIn: 'root' })
export class ArchivesService {
  constructor(private http: HttpClient) {}

  /**
   * Download the original file for an import run.
   */
  downloadImportFile(runId: number) {
    return this.http.get(`/api/archives/import/${runId}/file`, {
      responseType: 'blob',
      observe: 'response'
    });
  }

  /**
   * Upload a CSV archive file for processing.
   * @param file CSV file
   * @param params additional params (competitionCode, season, timezone, provider)
   */
  importCsv(file: File, params: Record<string, any>): Observable<ImportRunSummary> {
    const form = new FormData();
    form.append('file', file, file.name);
    Object.entries(params || {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null) form.append(k, String(v));
    });
    return this.http.post<ImportRunSummary>('/api/archives/import/csv', form);
  }

  /**
   * Preview an import to validate and inspect first rows.
   */
  previewCsv(file: File, params: Record<string, any>): Observable<ImportPreviewResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    Object.entries(params || {}).forEach(([k, v]) => {
      if (v !== undefined && v !== null) form.append(k, String(v));
    });
    return this.http.post<ImportPreviewResponse>('/api/archives/import/preview', form);
  }

  /**
   * Fetch paged list of import runs.
   */
  getImportRuns(page: number, size: number): Observable<ImportRunSummary[]> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ImportRunSummary[]>('/api/archives/import/runs', { params });
  }

  /**
   * Fetch errors for a specific run.
   */
  getImportRunErrors(runId: number): Observable<ImportError[]> {
    return this.http.get<ImportError[]>(`/api/archives/import/runs/${runId}/errors`);
  }
}
