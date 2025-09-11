import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <!-- Spacer to prevent content from being hidden behind the fixed bottom nav -->
    <div class="bottomnav-spacer" aria-hidden="true"></div>

    <nav class="bottomnav" role="navigation" aria-label="Global bottom navigation">
      <div class="links">
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Home</a>
        <a routerLink="/fixtures" routerLinkActive="active">Fixtures</a>
        <a routerLink="/match-analysis" routerLinkActive="active">Match Analysis</a>
        <a routerLink="/form-guide" routerLinkActive="active">Form Guide</a>
        <a routerLink="/league" routerLinkActive="active">League Table</a>
        <a routerLink="/history" routerLinkActive="active">History</a>
        <a routerLink="/fixture-predictions" routerLinkActive="active">Fixture Predictions</a>
        <a routerLink="/analyzed-fixtures" routerLinkActive="active">Analyzed Fixtures</a>
        <a routerLink="/data-management" routerLinkActive="active">Data Management</a>
        <a routerLink="/btts-over25" routerLinkActive="active">BTTS & Over 2.5</a>
        <a routerLink="/advice" routerLinkActive="active">Advice</a>
        <a routerLink="/wekelea-baskets" routerLinkActive="active">Wekelea Baskets</a>
        <a routerLink="/team-search" routerLinkActive="active">Team Search</a>
        <a routerLink="/played-matches-summary" routerLinkActive="active">Played Matches</a>
        <a routerLink="/admin" routerLinkActive="active">Admin</a>
      </div>
      <div class="copyright">ChambuaViSmart © {{ year }}</div>
    </nav>
  `,
  styles: [`
    :host { display: block; }
    .bottomnav-spacer { height: 64px; }
    .bottomnav { position: fixed; left: 0; right: 0; bottom: 0; z-index: 40; background: #0b1220; border-top: 1px solid #1f2937; color: #e6eef8; }
    .bottomnav .links { display: flex; flex-wrap: wrap; gap: 8px 10px; justify-content: center; padding: 8px 12px; }
    .bottomnav .links a { color: #cfe0f4; text-decoration: none; padding: 6px 8px; border-radius: 8px; transition: background .15s ease, color .15s ease; white-space: nowrap; }
    .bottomnav .links a:hover { background: #0f172a; color: #ffffff; }
    .bottomnav .links a.active { background: #19b562; color: #04110a; }
    .bottomnav .copyright { text-align: center; font-size: 12px; padding: 4px 0 8px; color: #9fb5cf; }

    @media (max-width: 900px) {
      .bottomnav-spacer { height: 80px; }
      .bottomnav .links { padding: 10px 8px; }
      .bottomnav .links a { padding: 8px 10px; }
    }
  `]
})
export class FooterComponent {
  year = new Date().getFullYear();
}
