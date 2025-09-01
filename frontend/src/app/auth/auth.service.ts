import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, catchError, map, tap, throwError } from 'rxjs';

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: number; // epoch millis
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly storageKey = 'chambua_token';
  private readonly expiryKey = 'chambua_token_expiry';

  private authed$ = new BehaviorSubject<boolean>(this.hasValidToken());

  isAuthenticated$ = this.authed$.asObservable();

  login(credentials: LoginCredentials): Observable<void> {
    return this.http.post<LoginResponse>('/api/auth/login', credentials).pipe(
      tap(res => {
        localStorage.setItem(this.storageKey, res.token);
        localStorage.setItem(this.expiryKey, String(res.expiresAt));
        this.authed$.next(true);
      }),
      map(() => void 0),
      catchError(err => {
        // Normalize error
        return throwError(() => err);
      })
    );
  }

  logout(redirect: boolean = true): void {
    localStorage.removeItem(this.storageKey);
    localStorage.removeItem(this.expiryKey);
    this.authed$.next(false);
    if (redirect) {
      this.router.navigate(['/auth/login']);
    }
  }

  getToken(): string | null {
    const token = localStorage.getItem(this.storageKey);
    const exp = localStorage.getItem(this.expiryKey);
    if (!token || !exp) return null;
    const expiresAt = Number(exp);
    if (isNaN(expiresAt) || Date.now() > expiresAt) {
      // token expired
      this.logout(false);
      return null;
    }
    return token;
  }

  isAuthenticated(): boolean {
    return this.hasValidToken();
  }

  private hasValidToken(): boolean {
    const token = localStorage.getItem(this.storageKey);
    const exp = localStorage.getItem(this.expiryKey);
    if (!token || !exp) return false;
    const expiresAt = Number(exp);
    return !isNaN(expiresAt) && Date.now() < expiresAt;
  }
}
