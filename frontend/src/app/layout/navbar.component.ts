import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav style="background:#222;color:#fff;padding:10px;">
      <a routerLink="/" routerLinkActive="active-link" [routerLinkActiveOptions]="{ exact: true }" style="color:#fff;">Home</a>
      <a routerLink="/fixtures" routerLinkActive="active-link" style="color:#fff;">Fixtures</a>
      <a routerLink="/league" routerLinkActive="active-link" style="color:#fff;">League</a>
      <a routerLink="/teams" routerLinkActive="active-link" style="color:#fff;">Teams</a>
      <a routerLink="/matchup" routerLinkActive="active-link" style="color:#fff;">Matchup</a>
      <a routerLink="/xg" routerLinkActive="active-link" style="color:#fff;">xG</a>
      <a routerLink="/advice" routerLinkActive="active-link" style="color:#fff;">Advice</a>
      <a routerLink="/history" routerLinkActive="active-link" style="color:#fff;">History</a>
      <a routerLink="/admin" routerLinkActive="active-link" style="color:#fff;">Admin</a>
    </nav>
  `
})
export class NavbarComponent {}
