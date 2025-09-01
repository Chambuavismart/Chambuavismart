import { Component } from '@angular/core';

@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent {
  today = new Date();

  get formattedDate(): string {
    const d = this.today;
    const day = d.getDate();
    const month = d.toLocaleString('en-GB', { month: 'short' });
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
  }

  stats = {
    matchesAnalyzed: 0,
    avgConfidence: 75,
    fixturesUploaded: 0
  };
}
