import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home.component';
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
