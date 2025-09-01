import { Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { FixturesComponent } from './pages/fixtures.component';
import { TeamsComponent } from './pages/teams.component';
import { MatchupAnalyzerComponent } from './pages/matchup-analyzer.component';
import { LeagueTableComponent } from './pages/league-table.component';
import { XgHistoryComponent } from './pages/xg-history.component';
import { AdviceComponent } from './pages/advice.component';
import { MatchHistoryComponent } from './pages/match-history.component';
import { AdminUploadComponent } from './pages/admin-upload.component';
import { NotFoundComponent } from './pages/not-found.component';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: 'auth/login', loadComponent: () => import('./auth/login.component').then(m => m.LoginComponent), title: 'Login' },
  { path: '', component: HomeComponent, canActivate: [authGuard], title: 'Home' },
  { path: 'match-analysis', canActivate: [authGuard], loadComponent: () => import('./pages/match-analysis.component').then(m => m.MatchAnalysisComponent), title: 'Match Analysis' },
  { path: 'fixture-predictions', canActivate: [authGuard], loadComponent: () => import('./pages/fixture-predictions.component').then(m => m.FixturePredictionsComponent), title: 'Fixture Predictions' },
  { path: 'analyzed-fixtures', canActivate: [authGuard], loadComponent: () => import('./pages/analyzed-fixtures.component').then(m => m.AnalyzedFixturesComponent), title: 'Analyzed Fixtures' },
  { path: 'data-management', canActivate: [authGuard], loadComponent: () => import('./pages/data-management.component').then(m => m.DataManagementComponent), title: 'Data Management' },
  { path: 'btts-over25', canActivate: [authGuard], loadComponent: () => import('./pages/btts-over25.component').then(m => m.BttsOver25Component), title: 'BTTS & Over 2.5' },
  { path: 'wekelea-baskets', canActivate: [authGuard], loadComponent: () => import('./pages/wekelea-baskets.component').then(m => m.WekeleaBasketsComponent), title: 'Wekelea Baskets' },
  { path: 'team-search', canActivate: [authGuard], loadComponent: () => import('./pages/team-search.component').then(m => m.TeamSearchComponent), title: 'Team Search' },
  { path: 'fixtures', canActivate: [authGuard], component: FixturesComponent, title: 'Fixtures' },
  { path: 'teams', canActivate: [authGuard], component: TeamsComponent, title: 'Teams' },
  { path: 'matchup', canActivate: [authGuard], component: MatchupAnalyzerComponent, title: 'Matchup Analyzer' },
  { path: 'league', canActivate: [authGuard], component: LeagueTableComponent, title: 'League Table' },
  { path: 'xg', canActivate: [authGuard], component: XgHistoryComponent, title: 'xG History' },
  { path: 'advice', canActivate: [authGuard], component: AdviceComponent, title: 'Advice' },
  { path: 'history', canActivate: [authGuard], component: MatchHistoryComponent, title: 'Match History' },
  { path: 'admin', canActivate: [authGuard], component: AdminUploadComponent, title: 'Admin Upload' },
  { path: '**', canActivate: [authGuard], component: NotFoundComponent, title: 'Not Found' }
];
