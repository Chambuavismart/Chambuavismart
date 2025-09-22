import { Component, EventEmitter, Input, Output, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArchivesService, ImportError, ImportRunSummary } from '../services/archives.service';
import { ArchivesErrorsModalComponent } from './archives-errors-modal.component';

@Component({
  selector: 'app-archives-history',
  standalone: true,
  imports: [CommonModule, ArchivesErrorsModalComponent],
  template: `
    <div class="rounded shadow p-4 bg-white">
      <div class="flex items-center justify-between">
        <h2 class="text-xl font-semibold mb-2">Import History</h2>
        <button class="btn btn-ghost" (click)="reload()" [disabled]="loading()">Refresh</button>
      </div>

      <div *ngIf="error()" class="mt-2 text-red-700 bg-red-50 border border-red-300 rounded p-2" [hidden]="errorCode===404">{{ error() }}</div>

      <div *ngIf="errorCode===404 || (runs()?.length||0)===0" class="mt-2 p-6 text-center border border-gray-200 rounded bg-gray-50">
        <div class="text-gray-700 mb-2">No past uploads found.</div>
        <button class="btn btn-primary" (click)="reload()" [disabled]="loading()">Try Again</button>
      </div>

      <div class="overflow-auto mt-2" *ngIf="(runs()?.length||0) > 0">
        <table class="table w-full text-sm">
          <thead>
            <tr>
              <th>Run ID</th>
              <th>Competition</th>
              <th>Provider</th>
              <th>Rows</th>
              <th>Date</th>
              <th>Status</th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let run of runs()">
              <td>{{ run.id }}</td>
              <td>{{ run.competitionCode }}</td>
              <td>{{ run.provider }}</td>
              <td>
                <span [class]="(run.rowsFailed||0) > 0 ? 'text-red-700 font-semibold' : ''">
                  {{ run.rowsSuccess }}/{{ run.rowsTotal }}
                </span>
                <span *ngIf="(run.rowsFailed||0) > 0" class="badge bg-red-600 text-white ml-2">{{ run.rowsFailed }} failed</span>
              </td>
              <td>{{ run.startedAt || '-' }}</td>
              <td>
                <span [ngClass]="{
                  'badge bg-green-600 text-white': (run.status||'').toLowerCase()==='success' || (run.rowsFailed||0)===0,
                  'badge bg-yellow-500 text-white': (run.status||'').toLowerCase()==='pending',
                  'badge bg-red-600 text-white': (run.status||'').toLowerCase()==='failed' || (run.rowsFailed||0) > 0
                }">{{ run.status }}</span>
              </td>
              <td><button class="btn btn-sm" (click)="openErrors(run)">View Errors</button></td>
              <td><button class="btn btn-sm btn-ghost" (click)="download(run)">Download File</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <app-archives-errors-modal *ngIf="showModal()" [errors]="errors()" (close)="showModal.set(false)"></app-archives-errors-modal>

      <!-- Simple toast -->
      <div *ngIf="toast()" class="fixed bottom-4 right-4 bg-gray-900 text-white px-4 py-2 rounded shadow">{{ toast() }}</div>
    </div>
  `
})
export class ArchivesHistoryComponent implements OnInit {
  private svc = inject(ArchivesService);

  runs = signal<ImportRunSummary[]>([]);
  errors = signal<ImportError[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  errorCode: number | null = null;
  showModal = signal(false);
  toast = signal<string | null>(null);

  page = 0;
  size = 20;

  ngOnInit() {
    this.reload();
  }

  reload() {
    this.loading.set(true);
    this.error.set(null);
    this.errorCode = null;
    this.svc.getImportRuns(this.page, this.size).subscribe({
      next: (runs) => {
        this.runs.set(runs);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorCode = err?.status ?? null;
        if (this.errorCode === 404) {
          this.runs.set([]);
          this.error.set(null);
        } else {
          this.error.set(err?.error?.message || err?.message || 'Failed to load history');
        }
        this.loading.set(false);
      }
    });
  }

  openErrors(run: ImportRunSummary) {
    this.svc.getImportRunErrors(run.id).subscribe({
      next: (errs) => {
        this.errors.set(errs);
        this.showModal.set(true);
      },
      error: (err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load errors');
      }
    });
  }

  private showToast(msg: string) {
    this.toast.set(msg);
    setTimeout(() => this.toast.set(null), 3000);
  }

  download(run: ImportRunSummary) {
    this.svc.downloadImportFile(run.id).subscribe({
      next: (resp: any) => {
        const blob = resp.body as Blob;
        const cd = resp.headers?.get ? resp.headers.get('Content-Disposition') || resp.headers.get('content-disposition') : undefined;
        let filename = this.extractFilename(cd) || run.filename || `import-run-${run.id}`;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: (err: any) => {
        if (err?.status === 404) {
          this.showToast('File not found.');
        } else {
          this.error.set(err?.message || 'Failed to download file');
        }
      }
    });
  }

  private extractFilename(contentDisposition?: string | null): string | null {
    if (!contentDisposition) return null;
    // Examples: attachment; filename="file.csv"; filename*=UTF-8''file.csv
    const filenameStarMatch = /filename\*=([^']*)''([^;]+)/i.exec(contentDisposition);
    if (filenameStarMatch && filenameStarMatch[2]) {
      try {
        return decodeURIComponent(filenameStarMatch[2]);
      } catch {
        return filenameStarMatch[2];
      }
    }
    const filenameMatch = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(contentDisposition);
    if (filenameMatch) {
      return (filenameMatch[1] || filenameMatch[2] || '').trim();
    }
    return null;
  }
}
