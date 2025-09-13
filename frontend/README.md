# ChambuaViSmart Frontend

Angular app scaffold with routing and pages.

- Run dev server: npm install && npm start
- Build: npm run build

Proxy: /api -> http://localhost:8082

Developing without the backend
- If the backend (port 8082) is not running, you can enable a small mock for the Global Leaders widgets to avoid proxy errors and let the UI load.
- Open src/index.html and uncomment: `window.__USE_MOCK__ = true;`
- This will intercept GET /api/global-leaders in the browser and return mock data. Remove or set to false when backend is running.

Routes:
- Home (/)
- Fixtures (/fixtures)
- Teams (/teams)
- Matchup Analyzer (/matchup)
- League Table (/league)
- xG History (/xg)
- Advice (/advice)
- Match History (/history)
- Admin Upload (/admin)
