import { PendingUpdateStore, isActionable } from './pending-update.store';

/**
 * Two focused tests, covering the two things about this screen that could be got wrong in a way
 * a reviewer would not notice by looking at it.
 *
 * <p>The store has no constructor dependencies, so these run without TestBed: the behaviour worth
 * proving is state and narrowing, not rendering.
 */
describe('PendingUpdateStore', () => {
  let store: PendingUpdateStore;

  beforeEach(() => {
    store = new PendingUpdateStore();
    store.load();
  });

  it('presents accumulated updates as their net effect and carries the reviewed token', () => {
    // ENG-1007 sits on REVIEW-CA v6 with v7 and v8 both published since.
    store.select('ENG-1007');
    const update = store.selectedActionable();

    expect(update).not.toBeNull();
    expect(update!.baselineVersion).toBe(6);
    expect(update!.latestVersion).toBe(8);
    expect(update!.accumulatedVersions.map((published) => published.version)).toEqual([7, 8]);

    // The analytics tolerance passed through 0.12 in v7, but this engagement has never held that
    // value and never will. The practitioner is shown where it is now and where it would land.
    const descriptions = update!.summary.groups.flatMap((group) =>
      group.changes.map((change) => change.description),
    );
    expect(descriptions).toContain('Tolerance for procedure 2 changed from 0.15 to 0.1.');
    expect(descriptions.some((text) => text.includes('0.12'))).toBe(false);

    store.apply();

    // The decision carries the token of the summary that was actually read, so a version
    // published while the user was reading is rejected by the server rather than applied unseen.
    expect(store.decisions()).toEqual([
      {
        decision: 'APPLY',
        decisionToken: 'dt_review-ca_6_8_eng-1007',
        requestId: 'req-ENG-1007-8',
      },
    ]);

    // Applying loads the engagement file, which takes about a minute, so the row has to show work
    // in flight rather than a completed decision.
    expect(store.selected()?.status).toBe('DECISION_IN_PROGRESS');
    expect(store.selectedActionable()).toBeNull();
  });

  it('offers no decision on an engagement whose summary could not be prepared', () => {
    // ENG-1011 is genuinely behind — the badge must keep saying so — but its summary failed.
    store.select('ENG-1011');
    const detail = store.selected();

    expect(detail?.status).toBe('SUMMARY_UNAVAILABLE');
    expect(isActionable(detail!)).toBe(false);
    expect(store.selectedActionable()).toBeNull();

    store.apply();

    // Nothing was submitted and nothing moved. A practitioner cannot accept changes they were
    // never shown, and that is enforced by the shape of the state rather than by a disabled button.
    expect(store.decisions()).toEqual([]);
    expect(store.selected()?.status).toBe('SUMMARY_UNAVAILABLE');
  });
});
