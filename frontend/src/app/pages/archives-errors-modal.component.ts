import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ImportError } from '../services/archives.service';

@Component({
  selector: 'app-archives-errors-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed inset-0 bg-black/40 flex items-start justify-center p-4" (click)="onBackdrop($event)">
      <div class="bg-white rounded shadow max-w-3xl w-full" (click)="$event.stopPropagation()">
        <div class="p-3 border-b flex items-center justify-between">
          <h3 class="font-semibold">Import Errors</h3>
          <button class="btn btn-ghost" (click)="close.emit()">Close</button>
        </div>
        <div class="p-3">
          <table class="table w-full text-sm">
            <thead>
              <tr>
                <th>#</th>
                <th>Row</th>
                <th>Error</th>
                <th>Raw Data</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let e of errors; let i = index">
                <td>{{ i + 1 }}</td>
                <td>{{ e.rowNumber }}</td>
                <td>{{ e.errorMessage }}</td>
                <td><pre class="whitespace-pre-wrap text-xs">{{ e.rawData }}</pre></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class ArchivesErrorsModalComponent {
  @Input() errors: ImportError[] = [];
  @Output() close = new EventEmitter<void>();

  onBackdrop(_: Event) {
    this.close.emit();
  }
}
