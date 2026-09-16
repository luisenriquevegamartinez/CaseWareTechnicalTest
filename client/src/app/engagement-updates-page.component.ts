import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import type { DecisionType } from '@contract/api-contract';
import { ChangeSummaryPanelComponent } from './change-summary-panel.component';
import { EngagementUpdateListComponent } from './engagement-update-list.component';
import { PendingUpdateStore } from './pending-update.store';

/**
 * Container for the pending-updates view.
 *
 * <p>The only component that knows the store exists. The list and the panel below it receive
 * data and emit intent, which keeps them trivially testable and means the transport can change —
 * polling, server-sent events, a websocket — without touching either of them.
 */
@Component({
  selector: 'cw-engagement-updates-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EngagementUpdateListComponent, ChangeSummaryPanelComponent],
  template: `
    <h1>Template updates</h1>

    <p data-testid="pending-count">
      {{ store.pendingCount() }} of {{ store.rows().length }} engagements have a pending update.
    </p>

    @if (store.stalenessIsWorthShowing()) {
      <p data-testid="staleness">
        This list was last updated {{ store.stalenessSeconds() }} seconds ago and may not yet show
        very recent changes.
      </p>
    }

    <cw-engagement-update-list [items]="store.rows()" (selected)="store.select($event)" />

    <cw-change-summary-panel [detail]="store.selected()" (decided)="onDecided($event)" />
  `,
})
export class EngagementUpdatesPageComponent implements OnInit {
  protected readonly store = inject(PendingUpdateStore);

  ngOnInit(): void {
    this.store.load();
  }

  protected onDecided(decision: DecisionType): void {
    if (decision === 'APPLY') {
      this.store.apply();
    } else {
      this.store.decline();
    }
  }
}
