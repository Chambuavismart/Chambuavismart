// Helper to resolve API base URL at runtime.
// Priority:
// 1) window.__API_BASE_URL__ if defined by index.html or hosting env
// 2) default '/api' (works with Angular dev proxy and Nginx proxy in Docker)

export function getApiBase(): string {
  const w = window as any;
  const fromWindow = w && w.__API_BASE_URL__;
  if (typeof fromWindow === 'string' && fromWindow.trim().length > 0) {
    return fromWindow.replace(/\/$/, '');
  }
  return '/api';
}
