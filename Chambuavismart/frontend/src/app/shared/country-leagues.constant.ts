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
  "Kenya": ["FKF Premier League", "Kenya Premier League", "National Super League", "FKF Division One", "FKF Cup"],
  "Spain": ["La Liga", "Segunda División", "Copa del Rey", "Supercopa de España"],
  "Italy": [
    "Serie A",
    "Serie B",
    "Serie C – Group A",
    "Serie C – Group B",
    "Serie C – Group C",
    "Coppa Italia",
    "Supercoppa Italiana",
    "Primavera 1",
    "Primavera 2",
    "Serie A Women",
    "Serie B Women"
  ],
  "Germany": [
    "Bundesliga",
    "2. Bundesliga",
    "3. Liga",
    "Regionalliga Südwest",
    "Oberliga Schleswig-Holstein",
    "Oberliga NOFV-Nord",
    "Oberliga NOFV-Sud",
    "Oberliga Hamburg",
    "Oberliga Westfalen",
    "DFB-Pokal",
    "DFL-Supercup",
    "Frauen-Bundesliga",
    "2. Frauen-Bundesliga",
    "DFB Youth League"
  ],
  "France": ["Ligue 1", "Ligue 2", "National", "Coupe de France", "Trophée des Champions"],
  "Portugal": ["Primeira Liga", "Liga Portugal 2", "Taça de Portugal", "Supertaça Cândido de Oliveira"],
  "Netherlands": ["Eredivisie", "Eerste Divisie", "Tweede Divisie", "KNVB Cup", "Johan Cruyff Shield"],
  "Scotland": ["Premiership", "Championship", "League One", "League Two", "Scottish Cup", "Scottish League Cup"],
  "Sweden": [
    "Allsvenskan",
    "Superettan",
    "Division 1 – Södra"
  ],
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
  ],
  "India": [
    "I-League",
    "I-League 2",
    "I-League 3",
    "Calcutta Premier Division",
    "Santosh Trophy",
    "IWL Women"
  ],
  "Ukraine": [
    "Premier League",
    "Persha Liga",
    "Druha Liga",
    "U19 League",
    "Championship Women"
  ],
  "Vietnam": [
    "V.League 1",
    "V.League 2"
  ],
  "Turkey": [
    "Super Lig",
    "1. Lig",
    "2. Lig White Group",
    "2. Lig Red Group",
    "2. Lig Play Offs",
    "3. Lig Group 1",
    "3. Lig Group 2",
    "3. Lig Group 3",
    "3. Lig Group 4",
    "3. Lig Play Offs",
    "Turkish Cup",
    "Super Cup",
    "Super Lig Women",
    "Women’s First League",
    "Women’s Second League",
    "Women’s Third League",
    "U19 Elit A Ligi",
    "U19 Elit B Ligi",
    "Turkey U19 (National)",
    "Turkey U19 Women (National)"
  ],
  "Singapore": [
    "Singapore Premier League",
    "Singapore Cup",
    "Singapore Community Shield",
    "Singapore Football League 1 (SFL 1)",
    "Singapore Football League 2 (SFL 2)",
    "Island Wide League (IWL)",
    "Women’s Premier League (Singapore)",
    "Women’s National League (Singapore)"
  ],
  "Malaysia": [
    "Malaysia Super League",
    "A1 Semi-Pro League",
    "A2 Amateur League",
    "Malaysia Cup",
    "Malaysia FA Cup",
    "MFL Challenge Cup",
    "MFL Cup",
    "Malaysia Charity Shield / Supercup",
    "Piala Presiden",
    "Piala Belia"
  ],
  "Kazakhstan": [
    "Premier League",
    "First League"
  ],
  "Armenia": [
    "Premier League",
    "First League"
  ],
  "Malawi": [
    "Super League"
  ],
  "North Macedonia": [
    "1.MFL",
    "2.MFL"
  ],
  "Croatia": [
    "HNL",
    "Prva NL",
    "3. NL",
    "1. HNL Women"
  ],
  "Egypt": [
    "Egyptian Premier League",
    "Egyptian Second Division A",
    "Egyptian Women’s Premier League"
  ],
  "Romania": [
    "Superliga",
    "Liga 2",
    "Liga 3",
    "Liga 4",
    "Romania Cup",
    "Super Cup",
    "Liga 1 Women",
    "Liga 2 Women",
    "Romania Women",
    "Romania Cup Women",
    "Liga Elitelor U19",
    "Liga Elitelor U17",
    "Cupa Romaniei U19"
  ],
  "Poland": [
    "Ekstraklasa",
    "I liga",
    "II liga",
    "III liga",
    "III liga – Group I",
    "III liga – Group II",
    "III liga – Group III",
    "III liga – Group IV",
    "Central Youth League",
    "Ekstraliga (Women)",
    "I liga (Women)"
  ],
  "Cyprus": [
    "First Division",
    "Second Division",
    "Third Division",
    "Fourth Division",
    "Protathlima Entaxis STOK",
    "Cypriot First Division Women"
  ],
  "Ireland": [
    "Premier Division",
    "First Division",
    "League of Ireland Women’s Premier Division",
    "Ireland U19 Championship"
  ],
  "Japan": [
    "J1 League",
    "J2 League",
    "J3 League",
    "Japan Football League",
    "WE League",
    "Nadeshiko League Division 1",
    "Nadeshiko League Division 2",
    "Prince League U18 Premier League"
  ],
  "Iran": [
    "Persian Gulf Pro League",
    "Azadegan League",
    "Kowsar Women Football League"
  ],
  "Algeria": [
    "Ligue 1",
    "Ligue 2"
  ],
  "Belgium": [
    "Jupiler Pro League",
    "Challenger Pro League",
    "National Division 1 - ACFF",
    "Super League Women",
    "1st National Women"
  ],
  "Belarus": [
    "Vysshaya Liga",
    "Pershaya Liga"
  ],
  "Bolivia": [
    "Division Profesional",
    "Copa Simon Bolivar"
  ],
  "Ecuador": [
    "Liga Pro",
    "Serie B"
  ],
  "Lithuania": [
    "A Lyga",
    "1 Lyga"
  ],
  "Moldova": [
    "Super Liga",
    "Liga 1"
  ],
  "Montenegro": [
    "Druga Liga"
  ],
  "South Africa": [
    "Betway Premiership",
    "Motsepe Foundation Championship"
  ],
  "Tanzania": [
    "Ligi Kuu Bara"
  ],
  "Kosovo": [
    "Superliga",
    "Liga e Pare"
  ],
  "Oman": [
    "Professional League"
  ],
  "Serbia": [
    "Super Liga",
    "Prva Liga",
    "Srpska Liga - West",
    "Srpska Liga - Belgrade",
    "Srpska Liga - East"
  ],
  "Saudi Arabia": [
    "Saudi Professional League",
    "Division 1"
  ],
  "China": [
    "Super League",
    "Jia League",
    "Yi League"
  ],
  "Austria": [
    "Bundesliga",
    "2. Liga"
  ],
  "Denmark": [
    "Superliga",
    "1st Division",
    "2nd Division",
    "3rd Division"
  ],
  "Slovakia": [
    "Fortuna Liga",
    "2. Liga"
  ],
  "Switzerland": [
    "Super League",
    "Challenge League"
  ],
  "Bahrain": [
    "Premier League"
  ]
});
