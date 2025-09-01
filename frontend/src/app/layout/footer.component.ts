import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: true,
  template: `
    <footer>
      ChambuaViSmart © {{ year }}
    </footer>
  `
})
export class FooterComponent {
  year = new Date().getFullYear();
}
