// Static mapping of Country -> Soccer Leagues
// This is a minimal seed; extend as needed. Later can be replaced by backend-configurable source.
export const COUNTRY_LEAGUES: Readonly<Record<string, readonly string[]>> = Object.freeze({
  "England": ["Premier League", "Championship", "League One", "League Two", "FA Cup", "EFL Cup", "Community Shield"],
  "Kenya": ["FKF Premier League", "National Super League", "FKF Division One", "FKF Cup"],
  "Spain": ["La Liga", "Segunda División", "Copa del Rey", "Supercopa de España"],
  "Italy": ["Serie A", "Serie B", "Coppa Italia", "Supercoppa Italiana"],
  "Germany": ["Bundesliga", "2. Bundesliga", "DFB-Pokal", "DFL-Supercup"],
  "France": ["Ligue 1", "Ligue 2", "Coupe de France", "Trophée des Champions"],
  "Portugal": ["Primeira Liga", "Liga Portugal 2", "Taça de Portugal", "Supertaça Cândido de Oliveira"],
  "Netherlands": ["Eredivisie", "Eerste Divisie", "KNVB Cup", "Johan Cruyff Shield"],
  "Scotland": ["Premiership", "Championship", "League One", "League Two", "Scottish Cup", "Scottish League Cup"],
  "United States": ["MLS", "US Open Cup"],
  "Brazil": ["Campeonato Brasileiro Série A", "Série B", "Copa do Brasil"],
  "Argentina": ["Liga Profesional", "Primera Nacional", "Copa Argentina"],
});
