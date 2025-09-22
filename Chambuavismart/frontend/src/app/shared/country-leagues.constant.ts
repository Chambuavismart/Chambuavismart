// Static mapping of Country -> Soccer Leagues
// This is a minimal seed; extend as needed. Later can be replaced by backend-configurable source.
export const COUNTRY_LEAGUES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  "England": [
    "Premier League",
    "Championship",
    "League One",
    "League Two",
    "FA Cup",
    "EFL Cup",
    "Community Shield",
    "National League",
    "National League North",
    "National League South",
    "NPL Premier Division",
    "Southern League Premier Central",
    "Southern League Premier South",
    "Isthmian League Premier Division"
  ],
  "Kenya": ["FKF Premier League", "National Super League", "FKF Division One", "FKF Cup"],
  "Spain": ["La Liga", "Segunda División", "Copa del Rey", "Supercopa de España"],
  "Italy": ["Serie A", "Serie B", "Coppa Italia", "Supercoppa Italiana"],
  "Germany": ["Bundesliga", "2. Bundesliga", "DFB-Pokal", "DFL-Supercup"],
  "France": ["Ligue 1", "Ligue 2", "Coupe de France", "Trophée des Champions"],
  "Portugal": ["Primeira Liga", "Liga Portugal 2", "Taça de Portugal", "Supertaça Cândido de Oliveira"],
  "Netherlands": ["Eredivisie", "Eerste Divisie", "Tweede Divisie", "KNVB Cup", "Johan Cruyff Shield"],
  "Scotland": ["Premiership", "Championship", "League One", "League Two", "Scottish Cup", "Scottish League Cup"],
  "United States": ["MLS", "US Open Cup"],
  "Brazil": ["Campeonato Brasileiro Série A", "Série B", "Copa do Brasil"],
  "Argentina": ["Liga Profesional", "Primera Nacional", "Copa Argentina"],
  "Czechia": [
    "Chance Liga",
    "ChNL",
    "3.CFL-Group A",
    "3.CFL-Group B",
    "3.MSFL",
    "4.Liga-Group A",
    "4.Liga-Group B",
    "4.Liga-Group C",
    "4.Liga-Group D",
    "4.Liga-Group E",
    "4.Liga-Group F",
    "U19 League",
    "1.Liga Women",
    "2.Liga Women"
  ],
  "South Korea": [
    "K League 1",
    "K League 2",
    "K 3 League",
    "Korean Cup",
    "WK League Women"
  ]
});
