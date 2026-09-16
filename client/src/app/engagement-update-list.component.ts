import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { EngagementUpdateListItem } from '@contract/api-contract';

/**
 * The list view: which engagement files have something waiting.
 *
 * <p>Purely presentational — it receives rows and emits a selection, and holds no state of its
 * own. Every status the contract can return is given its own branch, because each one means a
 * different thing to a practitioner and collapsing them would make the interface lie. In
 * particular an engagement whose summary is still being prepared is shown as having an update,
 * because it does; and an engagement whose baseline is not yet known says so rather than
 * appearing reassuringly up to date.
 */
@Component({
  selector: 'cw-engagement-update-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul>
      @for (item of items(); track item.engagementId) {
        <li>
          <button type="button" (click)="selected.emit(item.engagementId)">
            {{ item.engagementName }}
          </button>
          <span> — {{ item.templateDisplayName }}: </span>

          @switch (item.status) {
            @case ('UP_TO_DATE') {
              <span data-testid="status">Up to date (version {{ item.currentVersion }})</span>
            }
            @case ('UPDATE_AVAILABLE') {
              <span data-testid="status">
                <strong>Update available</strong>
                — {{ versionsBehindLabel(item.versionsBehind) }}
              </span>
            }
            @case ('SUMMARY_PENDING') {
              <span data-testid="status">
                <strong>Update available</strong>
                — {{ versionsBehindLabel(item.versionsBehind) }}. Preparing the summary…
              </span>
            }
            @case ('SUMMARY_UNAVAILABLE') {
              <span data-testid="status">
                <strong>Update available</strong>
                — {{ versionsBehindLabel(item.versionsBehind) }}. Summary could not be prepared.
              </span>
            }
            @case ('DECISION_IN_PROGRESS') {
              <span data-testid="status">
                {{ item.decision === 'APPLY' ? 'Applying update…' : 'Declining update…' }}
              </span>
            }
            @case ('UNKNOWN') {
              <span data-testid="status">Checking for updates…</span>
            }
          }
        </li>
      }
    </ul>
  `,
})
export class EngagementUpdateListComponent {
  readonly items = input.required<readonly EngagementUpdateListItem[]>();
  readonly selected = output<string>();

  versionsBehindLabel(versionsBehind: number): string {
    return versionsBehind === 1
      ? '1 version published since yours'
      : `${versionsBehind} versions published since yours`;
  }
}
