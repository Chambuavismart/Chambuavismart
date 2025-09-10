import { Component, Input, ChangeDetectionStrategy, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule, NgFor, NgIf } from '@angular/common';
import { GlobalLeadersService, GlobalLeader } from '../../services/global-leaders.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-global-leaders-widget',
  standalone: true,
  imports: [CommonModule, NgFor, NgIf],
  templateUrl: './global-leaders-widget.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GlobalLeadersWidgetComponent implements OnChanges {
  @Input() category: string = 'btts';
  @Input() limit: number = 5;
  @Input() scope: 'overall'|'home'|'away' = 'overall';
  @Input() lastN: number = 0; // 0 = all
  @Input() minMatches: number = 3;

  leaders$!: Observable<GlobalLeader[]>;

  constructor(private svc: GlobalLeadersService) {}

  ngOnChanges(_changes: SimpleChanges) {
    this.leaders$ = this.svc.getLeaders(this.category, this.limit, this.minMatches, this.scope, this.lastN);
  }
}
