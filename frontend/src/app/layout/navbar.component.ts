import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="topnav">
      <div class="nav-inner">
        <a class="brand" routerLink="/">
          <img src="assets/design/logo.png" alt="ChambuaViSmart" class="logo" />
          <span class="brand-text">ChambuaViSmart</span>
        </a>
        <button class="burger" (click)="open = !open" aria-label="Toggle navigation">
          <span></span><span></span><span></span>
        </button>
        <div class="links" [class.open]="open">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Home</a>
          <a routerLink="/match-analysis" routerLinkActive="active">Match Analysis</a>
          <a routerLink="/history" routerLinkActive="active">History</a>
          <a routerLink="/fixture-predictions" routerLinkActive="active">Fixture Predictions</a>
          <a routerLink="/analyzed-fixtures" routerLinkActive="active">Analyzed Fixtures</a>
          <a routerLink="/data-management" routerLinkActive="active">Data Management</a>
          <a routerLink="/btts-over25" routerLinkActive="active">BTTS & Over 2.5</a>
          <a routerLink="/advice" routerLinkActive="active">Advice</a>
          <a routerLink="/wekelea-baskets" routerLinkActive="active">Wekelea Baskets</a>
          <a routerLink="/team-search" routerLinkActive="active">Team Search</a>
        </div>
      </div>
    </nav>
  `,
  styles: [`
    :host { display:block; }
    .topnav { position: sticky; top: 0; z-index: 50; background: #0b1220; border-bottom: 1px solid #1f2937; color: #e6eef8; }
    .nav-inner { max-width: 1200px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; flex-wrap: wrap; gap: 8px 12px; }
    .brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: inherit; min-width: 0; }
    .logo { height: 28px; width: auto; display: block; }
    .brand-text { font-weight: 800; letter-spacing: .2px; }
    .burger { display: none; background: transparent; border: 0; cursor: pointer; padding: 6px; border-radius: 6px; }
    .burger span { display:block; width: 22px; height: 2px; background: #e6eef8; margin: 4px 0; }
    .links { display: flex; align-items: center; gap: 10px 12px; flex: 1 1 auto; justify-content: flex-end; flex-wrap: wrap; min-width: 0; }
    .links a { color: #cfe0f4; text-decoration: none; padding: 8px 10px; border-radius: 8px; transition: background .15s ease, color .15s ease; white-space: nowrap; }
    .links a:hover { background: #0f172a; color: #ffffff; }
    .links a.active { background: #19b562; color: #04110a; }

    @media (max-width: 900px) {
      .burger { display: inline-block; }
      .links { position: absolute; left: 0; right: 0; top: 100%; background: #0b1220; border-bottom: 1px solid #1f2937; display: none; flex-direction: column; align-items: stretch; padding: 8px; flex-wrap: nowrap; }
      .links.open { display: flex; }
      .links a { padding: 10px 12px; }
    }
  `]
})
export class NavbarComponent { open = false; }
