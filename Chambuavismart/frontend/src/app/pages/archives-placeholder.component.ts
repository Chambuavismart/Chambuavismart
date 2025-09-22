import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-archives-placeholder',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="container">
      <div class="rounded shadow p-4 bg-white">
        <h2 class="text-xl font-semibold mb-2">Archives (coming soon)</h2>
        <p class="text-gray-700 mb-4">A future area for uploading historical match archives and reviewing import runs.</p>
        <div class="flex items-center gap-3">
          <button class="btn btn-primary opacity-60 cursor-not-allowed" [disabled]="true" title="Backend not yet enabled">Upload CSV</button>
          <a class="text-blue-500 opacity-60 cursor-not-allowed pointer-events-none" title="Backend not yet enabled">Import run viewer</a>
        </div>
      </div>
    </div>
  `
})
export class ArchivesPlaceholderComponent {}
