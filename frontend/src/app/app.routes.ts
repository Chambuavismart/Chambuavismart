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

export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'Home' },
    { path: 'match-analysis', loadComponent: () => import('./pages/match-analysis.component').then(m => m.MatchAnalysisComponent), title: 'Match Analysis' },
    { path: 'fixture-predictions', loadComponent: () => import('./pages/fixture-predictions.component').then(m => m.FixturePredictionsComponent), title: 'Fixture Predictions' },
    { path: 'analyzed-fixtures', loadComponent: () => import('./pages/analyzed-fixtures.component').then(m => m.AnalyzedFixturesComponent), title: 'Analyzed Fixtures' },
    { path: 'data-management', loadComponent: () => import('./pages/data-management.component').then(m => m.DataManagementComponent), title: 'Data Management' },
    { path: 'btts-over25', loadComponent: () => import('./pages/btts-over25.component').then(m => m.BttsOver25Component), title: 'BTTS & Over 2.5' },
    { path: 'wekelea-baskets', loadComponent: () => import('./pages/wekelea-baskets.component').then(m => m.WekeleaBasketsComponent), title: 'Wekelea Baskets' },
    { path: 'team-search', loadComponent: () => import('./pages/team-search.component').then(m => m.TeamSearchComponent), title: 'Team Search' },
  { path: 'fixtures', component: FixturesComponent, title: 'Fixtures' },
  { path: 'teams', component: TeamsComponent, title: 'Teams' },
  { path: 'matchup', component: MatchupAnalyzerComponent, title: 'Matchup Analyzer' },
  { path: 'league', component: LeagueTableComponent, title: 'League Table' },
  { path: 'xg', component: XgHistoryComponent, title: 'xG History' },
  { path: 'advice', component: AdviceComponent, title: 'Advice' },
  { path: 'history', component: MatchHistoryComponent, title: 'Match History' },
  { path: 'admin', component: AdminUploadComponent, title: 'Admin Upload' },
  { path: '**', component: NotFoundComponent, title: 'Not Found' }
];
