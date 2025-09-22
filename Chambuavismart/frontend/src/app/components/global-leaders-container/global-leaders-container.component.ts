import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GlobalLeadersWidgetComponent } from '../global-leaders-widget/global-leaders-widget.component';

@Component({
  selector: 'app-global-leaders-container',
  standalone: true,
  imports: [CommonModule, GlobalLeadersWidgetComponent],
  templateUrl: './global-leaders-container.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GlobalLeadersContainerComponent {
  @Input() side: 'left' | 'right' = 'right';

  // Expose Math to the template for expressions like Math.min(...)
  public readonly Math = Math;

  scope: 'overall'|'home'|'away' = 'overall';
  lastN: number = 0; // 0 = all
  minMatches: number = 5;

  setScope(s: 'overall'|'home'|'away') { this.scope = s; }
  setLastN(n: number) { this.lastN = n; }
}
