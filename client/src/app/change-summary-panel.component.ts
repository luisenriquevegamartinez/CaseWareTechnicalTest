import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { DecisionType, PendingUpdateResponse } from '@contract/api-contract';
import { isActionable } from './pending-update.store';

/**
 * The review panel: what would change, and the decision.
 *
 * <p>Presentational. It displays the sentences the server rendered rather than composing its own
 * — there is one wording of a methodology change, and it is versioned server-side so a firm can
 * be told later why a change was described the way it was.
 *
 * <p>The Apply and Decline controls exist only in the branch that carries a decisionToken. That
 * is not a convenience: a practitioner must not be able to accept an update whose changes they
 * could not read, and expressing it through the union means there is no disabled-button state to
 * get wrong, and no path where a missing token has to be handled at submit time.
 */
@Component({
  selector: 'cw-change-summary-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let update = detail();

    @if (update === null) {
      <p>Select an engagement to review its pending update.</p>
    } @else {
      @switch (update.status) {
        @case ('UP_TO_DATE') {
          <p data-testid="panel">
            This engagement is on the latest version ({{ update.currentVersion }}). There is
            nothing to review.
          </p>
        }

        @case ('UPDATE_AVAILABLE') {
          <section data-testid="panel">
            <h2>Pending update: version {{ update.baselineVersion }} to {{ update.latestVersion }}</h2>

            <p data-testid="accumulated">{{ accumulatedLabel() }}</p>

            <p data-testid="headline">
              {{ update.summary.headline.totalChanges }} changes:
              {{ update.summary.headline.added }} added,
              {{ update.summary.headline.modified }} changed,
              {{ update.summary.headline.removed }} removed.
              @if (update.summary.headline.notable > 0) {
                <strong>{{ update.summary.headline.notable }} need your attention.</strong>
              }
            </p>

            @for (group of update.summary.groups; track group.sectionKey) {
              <section>
                <h3>{{ group.sectionLabel }}</h3>
                <ul>
                  @for (change of group.changes; track change.id) {
                    <li data-testid="change">
                      @if (change.significance === 'NOTABLE') {
                        <strong>Needs attention:</strong>
                      }
                      {{ change.description }}
                      @if (change.valueChange; as value) {
                        <dl>
                          <dt>Currently</dt>
                          <dd data-testid="before">{{ value.before }}</dd>
                          <dt>After the update</dt>
                          <dd data-testid="after">{{ value.after }}</dd>
                        </dl>
                      }
                    </li>
                  }
                </ul>
              </section>
            }

            <p>
              <button type="button" data-testid="apply" (click)="decided.emit('APPLY')">
                Apply update
              </button>
              <button type="button" data-testid="decline" (click)="decided.emit('DECLINE')">
                Decline update
              </button>
            </p>
          </section>
        }

        @case ('SUMMARY_PENDING') {
          <section data-testid="panel">
            <h2>Pending update: version {{ update.baselineVersion }} to {{ update.latestVersion }}</h2>
            <p data-testid="accumulated">{{ accumulatedLabel() }}</p>
            <p>
              We are preparing a summary of these changes. This page will refresh in
              {{ update.retryAfterSeconds }} seconds.
            </p>
            <!-- No decision controls: the summary has not been read, so it cannot be decided on. -->
          </section>
        }

        @case ('SUMMARY_UNAVAILABLE') {
          <section data-testid="panel">
            <h2>Pending update: version {{ update.baselineVersion }} to {{ update.latestVersion }}</h2>
            <p data-testid="accumulated">{{ accumulatedLabel() }}</p>
            <p>
              We could not prepare a summary of these changes, so there is nothing to review yet.
              The update itself is unaffected. Quote reference {{ update.incidentId }} if you
              contact support.
            </p>
          </section>
        }

        @case ('DECISION_IN_PROGRESS') {
          <section data-testid="panel">
            <p>
              {{ update.decision === 'APPLY' ? 'Applying' : 'Declining' }} the update to version
              {{ update.targetVersion }}. This usually takes about a minute.
            </p>
          </section>
        }

        @case ('UNKNOWN') {
          <section data-testid="panel">
            <p>
              We are still checking this engagement for pending updates. Its status will appear
              shortly.
            </p>
          </section>
        }
      }
    }
  `,
})
export class ChangeSummaryPanelComponent {
  readonly detail = input.required<PendingUpdateResponse | null>();
  readonly decided = output<DecisionType>();

  /**
   * How much has piled up, in a form that answers "is this one tweak or a season of them?".
   *
   * <p>Deliberately worded as versions published *since* the engagement's own, not as versions
   * skipped: the newest of them is where the engagement would land, so calling it skipped would
   * be wrong.
   */
  protected readonly accumulatedLabel = computed(() => {
    const update = this.detail();
    if (update === null || !('accumulatedVersions' in update)) {
      return '';
    }
    const versions = update.accumulatedVersions;
    const formatted = versions
      .map((published) => `v${published.version} (${formatDate(published.publishedAt)})`)
      .join(', ');
    return versions.length === 1
      ? `1 version published since yours: ${formatted}.`
      : `${versions.length} versions published since yours: ${formatted}.`;
  });

  /** Exposed so the container can decide whether to offer bulk actions elsewhere. */
  protected readonly canDecide = computed(() => {
    const update = this.detail();
    return update !== null && isActionable(update);
  });
}

function formatDate(instant: string): string {
  return new Date(instant).toLocaleDateString('en-CA', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
}
