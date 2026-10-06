import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

export interface User {
  sub?: string;
  name?: string;
  email?: string;
  picture?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _user = signal<User | null>(null);

  /** Aktueller User oder null, wenn nicht eingeloggt */
  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);

  /** Fragt das Backend, wer eingeloggt ist (Session-Cookie). Beim App-Start aufrufen. */
  async loadUser(): Promise<void> {
    try {
      const user = await firstValueFrom(this.http.get<User>('/api/user'));
      this._user.set(user);
    } catch {
      this._user.set(null);
    }
  }

  /** Startet den Google-Login: echte Browser-Navigation zum Backend */
  login(): void {
    window.location.href = '/api/oauth2/authorization/google';
  }

  /** Beendet die Session im Backend und leitet auf die Landing-Page */
  logout(): void {
    this.http.post('/api/logout', {}).subscribe({
      next: () => this.afterLogout(),
      error: () => this.afterLogout(),
    });
  }

  private afterLogout(): void {
    this._user.set(null);
    this.router.navigate(['/']);
  }
}
