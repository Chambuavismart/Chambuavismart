import { Component } from '@angular/core';

@Component({
  selector: 'app-not-found',
  standalone: true,
  template: `
    <h1>404 - Page Not Found</h1>
    <p>The page you are looking for does not exist.</p>
  `
})
export class NotFoundComponent {}
