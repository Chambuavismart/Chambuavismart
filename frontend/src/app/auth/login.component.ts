import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
  <div class="min-h-[60vh] flex items-center justify-center p-4">
    <div class="w-full max-w-md bg-white/5 border border-gray-700 rounded-xl p-6 shadow">
      <h1 class="text-2xl font-bold mb-4 text-center text-white">Sign in</h1>
      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
        <div>
          <label class="block text-sm text-gray-300 mb-1">Email</label>
          <input type="email" formControlName="email" class="w-full px-3 py-2 rounded-md bg-gray-900 border border-gray-700 text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="you@example.com" />
          <div class="text-red-400 text-xs mt-1" *ngIf="submitted && form.controls.email.invalid">
            Please enter a valid email.
          </div>
        </div>
        <div>
          <label class="block text-sm text-gray-300 mb-1">Password</label>
          <input type="password" formControlName="password" class="w-full px-3 py-2 rounded-md bg-gray-900 border border-gray-700 text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" placeholder="••••••" />
          <div class="text-red-400 text-xs mt-1" *ngIf="submitted && form.controls.password.invalid">
            Password must be at least 6 characters.
          </div>
        </div>
        <button type="submit" class="w-full py-2 rounded-md bg-emerald-500 hover:bg-emerald-600 text-black font-semibold">Log in</button>
      </form>
      <div *ngIf="error" class="mt-3 text-sm text-red-400 text-center">{{ error }}</div>
      <div class="mt-4 text-center">
        <a routerLink="/" class="text-gray-300 hover:text-white text-sm">Back to Home</a>
      </div>
    </div>
  </div>
  `,
  styles: [`:host{display:block;background:#0b1220;}`]
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  error: string | null = null;
  submitted = false;

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  onSubmit() {
    this.submitted = true;
    this.error = null;
    if (this.form.invalid) return;
    const { email, password } = this.form.value;
    this.auth.login({ email: email!, password: password! }).subscribe({
      next: () => {
        this.router.navigateByUrl('/');
      },
      error: (err) => {
        this.error = err?.error?.message || 'Invalid credentials';
      }
    });
  }
}
