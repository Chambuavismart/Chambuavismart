import { HttpEvent, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

// Dev-only mock for selected endpoints to avoid proxy ECONNREFUSED noise when backend is down.
// Enable by setting window.__USE_MOCK__ = true in index.html dev.

function isMockEnabled(): boolean {
  const w = window as any;
  return !!(w && w.__USE_MOCK__);
}

function handleGlobalLeaders(req: HttpRequest<any>): HttpResponse<any> {
  const url = new URL(req.url, window.location.origin);
  const category = url.searchParams.get('category') || 'btts';
  const limit = Number(url.searchParams.get('limit') || 5);
  const items = Array.from({ length: limit }).map((_, i) => ({
    teamId: i + 1,
    teamName: `Mock Team ${i + 1}`,
    teamSlug: `mock-team-${i + 1}`,
    teamLogoUrl: '',
    statPct: Math.max(35, 80 - i * 5),
    matchesPlayed: 10 + i,
    statCount: 8 - i,
    category,
    rank: i + 1
  }));
  return new HttpResponse({ status: 200, body: items });
}

export const mockApiInterceptor: HttpInterceptorFn = (req, next): Observable<HttpEvent<any>> => {
  if (!isMockEnabled()) {
    return next(req);
  }

  // Only intercept GET calls to /api/global-leaders
  if (req.method === 'GET' && req.url.startsWith('/api/global-leaders')) {
    return of(handleGlobalLeaders(req));
  }

  // Pass through others
  return next(req);
};
