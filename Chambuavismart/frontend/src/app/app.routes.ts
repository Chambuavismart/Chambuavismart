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
  { path: 'quick-insights', canActivate: [authGuard], loadComponent: () => import('./pages/quick-insights.component').then(m => m.QuickInsightsComponent), title: 'Quick Insights' },
  { path: 'match-analysis', canActivate: [authGuard], loadComponent: () => import('./pages/match-analysis.component').then(m => m.MatchAnalysisComponent), title: 'Match Analysis' },
  { path: 'fixture-predictions', canActivate: [authGuard], loadComponent: () => import('./pages/fixture-predictions.component').then(m => m.FixturePredictionsComponent), title: 'Fixture Predictions' },
  { path: 'analyzed-fixtures', canActivate: [authGuard], loadComponent: () => import('./pages/analyzed-fixtures.component').then(m => m.AnalyzedFixturesComponent), title: 'Analyzed Fixtures' },
  { path: 'analysis-pdfs', canActivate: [authGuard], loadComponent: () => import('./pages/analysis-pdfs.component').then(m => m.AnalysisPdfsComponent), title: 'Fixture Analysis History' },
  { path: 'data-management', canActivate: [authGuard], loadComponent: () => import('./pages/data-management.component').then(m => m.DataManagementComponent), title: 'Data Management' },
  { path: 'data-management/match-upload', canActivate: [authGuard], loadComponent: () => import('./pages/match-upload.component').then(m => m.MatchUploadComponent), title: 'Match Upload' },
  { path: 'data-management/archives', canActivate: [authGuard], loadComponent: () => import('./pages/archives-upload.component').then(m => m.ArchivesUploadComponent), title: 'Archives' },
  { path: 'btts-over25', canActivate: [authGuard], loadComponent: () => import('./pages/btts-over25.component').then(m => m.BttsOver25Component), title: 'BTTS & Over 2.5' },
  { path: 'wekelea-baskets', canActivate: [authGuard], loadComponent: () => import('./pages/wekelea-baskets.component').then(m => m.WekeleaBasketsComponent), title: 'Wekelea Baskets' },
  { path: 'team-search', canActivate: [authGuard], loadComponent: () => import('./pages/team-search.component').then(m => m.TeamSearchComponent), title: 'Team Search' },
  { path: 'played-matches-summary', canActivate: [authGuard], loadComponent: () => import('./pages/played-matches-summary.component').then(m => m.PlayedMatchesSummaryComponent), title: 'Fixtures Analysis' },
  { path: 'hot-picks-today', canActivate: [authGuard], loadComponent: () => import('./pages/hot-picks-today.component').then(m => m.HotPicksTodayComponent), title: 'Hot Picks Today' },
  { path: 'fixtures', canActivate: [authGuard], component: FixturesComponent, title: 'Fixtures' },
  { path: 'teams', canActivate: [authGuard], component: TeamsComponent, title: 'Teams' },
  { path: 'matchup', canActivate: [authGuard], component: MatchupAnalyzerComponent, title: 'Matchup Analyzer' },
  { path: 'league', canActivate: [authGuard], component: LeagueTableComponent, title: 'League Table' },
  { path: 'league/:leagueId', canActivate: [authGuard], component: LeagueTableComponent, title: 'League Table' },
  { path: 'xg', canActivate: [authGuard], component: XgHistoryComponent, title: 'xG History' },
  { path: 'advice', canActivate: [authGuard], component: AdviceComponent, title: 'Advice' },
  { path: 'history', canActivate: [authGuard], component: MatchHistoryComponent, title: 'Match History' },
  { path: 'form-guide', canActivate: [authGuard], loadComponent: () => import('./pages/form-guide.component').then(m => m.FormGuideComponent), title: 'Form Guide' },
  { path: 'admin', canActivate: [authGuard], component: AdminUploadComponent, title: 'Admin Upload' },
  { path: 'today-color-report', canActivate: [authGuard], loadComponent: () => import('./pages/today-color-report.component').then(m => m.TodayColorReportComponent), title: "Today's Colours Report" },
  { path: 'streak-insights', canActivate: [authGuard], loadComponent: () => import('./pages/streak-insights.component').then(m => m.StreakInsightsComponent), title: 'Streak Insights' },
  { path: 'team-outcome-distribution', canActivate: [authGuard], loadComponent: () => import('./pages/team-outcome-distribution.component').then(m => m.TeamOutcomeDistributionComponent), title: 'Team Outcome distribution' },
  { path: 'analysis', canActivate: [authGuard], loadComponent: () => import('./pages/analysis-hub.component').then(m => m.AnalysisHubComponent), title: 'Analysis' },
  { path: 'over-1-5', canActivate: [authGuard], loadComponent: () => import('./pages/over-one-five.component').then(m => m.OverOneFiveComponent), title: 'Over 1.5' },
  { path: 'print-today', canActivate: [authGuard], loadComponent: () => import('./pages/print-today.component').then(m => m.PrintTodayComponent), title: 'Print Today' },
  { path: '**', canActivate: [authGuard], component: NotFoundComponent, title: 'Not Found' }
];
