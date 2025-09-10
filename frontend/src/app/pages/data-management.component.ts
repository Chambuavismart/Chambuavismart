import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatchUploadComponent } from './match-upload.component';

@Component({
  selector: 'app-data-management',
  standalone: true,
  imports: [RouterLink, MatchUploadComponent],
  template: `
    <div class="container">
      <h1>Data Management</h1>
      <p>Manage data uploads and updates for your leagues.</p>

      <!-- Embed Match Upload directly so options are visible here -->
      <app-match-upload></app-match-upload>

      <!-- Still keep links to dedicated routes for deep linking -->
      <p style="margin-top:12px"><a routerLink="/data-management/match-upload">Open Match Upload in its own page →</a></p>
      <p style="margin-top:8px"><a routerLink="/data-management/archives">Archives – Uploads (Raw Text Only) →</a></p>
    </div>
  `
})
export class DataManagementComponent {}
