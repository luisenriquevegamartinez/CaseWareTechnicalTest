package com.caseware.templateupdates.domain;

import com.caseware.templateupdates.domain.summary.ChangeSummary;
import com.caseware.templateupdates.domain.summary.ChangeSummaryRenderer;
import com.caseware.templateupdates.port.DiffLookup;
import com.caseware.templateupdates.port.EngagementBaselineProjection;
import com.caseware.templateupdates.port.TemplateCatalog;
import com.caseware.templateupdates.port.TemplateDiffSource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides an engagement's pending template-update state.
 *
 * <p>The load-bearing property of this class is what it never does: it never opens an engagement
 * file. Opening one costs roughly a minute, and a firm has hundreds, so asking the engagement
 * store at read time would make the feature impossible rather than merely slow. Instead the
 * baseline arrives through a read model fed by engagement events, and the latest version comes
 * from a small shared catalog.
 *
 * <p>Being behind is therefore <em>derived</em>, not stored: no flag is written into every
 * tenant's database when a template is published. Publishing touches no customer data at all. A
 * materialised flag would be a second copy of a fact that is already cheap to compute, and every
 * copy is something that can drift, fail halfway across tenants, and start lying.
 */
public final class PendingUpdateCalculator {

    private final EngagementBaselineProjection projection;
    private final TemplateCatalog catalog;
    private final TemplateDiffSource diffSource;
    private final ChangeSummaryRenderer renderer;

    public PendingUpdateCalculator(
            EngagementBaselineProjection projection,
            TemplateCatalog catalog,
            TemplateDiffSource diffSource,
            ChangeSummaryRenderer renderer) {
        this.projection = Objects.requireNonNull(projection, "projection must not be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.diffSource = Objects.requireNonNull(diffSource, "diffSource must not be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer must not be null");
    }

    /** Evaluates one engagement, resolving its baseline from the read model. */
    public PendingUpdateState evaluate(EngagementId engagementId) {
        Objects.requireNonNull(engagementId, "engagementId must not be null");
        return projection.findBaseline(engagementId)
                .map(this::evaluate)
                .orElseGet(() -> new PendingUpdateState.Unknown(
                        engagementId, PendingUpdateState.Unknown.Cause.NO_BASELINE_RECORDED));
    }

    /**
     * Evaluates every engagement in the caller's firm.
     *
     * <p>This is the list view, and it is the reason the design looks the way it does: hundreds of
     * engagements resolve to a projection read, a cached catalog lookup each, and a diff request
     * per distinct version range — not per engagement. Two engagements sitting on AUDIT-CA v3 ask
     * for the same range and are served the same cached summary.
     */
    public List<PendingUpdateState> evaluateFirm() {
        return projection.findAllForFirm().stream().map(this::evaluate).toList();
    }

    public PendingUpdateState evaluate(EngagementBaseline baseline) {
        Objects.requireNonNull(baseline, "baseline must not be null");

        Optional<ProductTemplate> template = catalog.find(baseline.templateId());
        if (template.isEmpty()) {
            return new PendingUpdateState.Unknown(
                    baseline.engagementId(), PendingUpdateState.Unknown.Cause.TEMPLATE_NOT_IN_CATALOG);
        }

        List<PublishedVersion> accumulated =
                template.get().versionsPublishedAfter(baseline.baselineVersion());

        // Empty covers both the ordinary case, where the engagement is on the latest version, and
        // the case where its baseline is ahead of what the catalog knows. The projection and the
        // catalog are cached independently, so a briefly stale catalog must not report a pending
        // update that would move the engagement backwards.
        if (accumulated.isEmpty()) {
            return new PendingUpdateState.UpToDate(baseline.engagementId(), baseline.baselineVersion());
        }

        TemplateVersion latest = template.get().latestVersion();
        DiffLookup lookup = diffSource.diff(baseline.templateId(), baseline.baselineVersion(), latest);

        return switch (lookup) {
            case DiffLookup.Available available ->
                    summarise(baseline, accumulated, available.diff());
            case DiffLookup.Computing ignored ->
                    new PendingUpdateState.SummaryPending(
                            baseline.engagementId(), baseline.baselineVersion(), accumulated);
            case DiffLookup.Unavailable unavailable ->
                    new PendingUpdateState.SummaryUnavailable(
                            baseline.engagementId(), baseline.baselineVersion(), accumulated,
                            unavailable.reason());
        };
    }

    private PendingUpdateState summarise(
            EngagementBaseline baseline, List<PublishedVersion> accumulated, TemplateDiff diff) {
        try {
            ChangeSummary summary = renderer.render(diff);
            return new PendingUpdateState.UpdateAvailable(
                    baseline.engagementId(), baseline.baselineVersion(), accumulated, summary);
        } catch (RuntimeException failure) {
            // A bulkhead, not a swallowed error. One template whose structure the renderer cannot
            // describe must not fail a list covering hundreds of engagements, and the user is still
            // told truthfully that an update is pending. The domain reports the reason; the
            // incident id the contract carries is attached at the API boundary, which is where
            // correlation identifiers belong.
            return new PendingUpdateState.SummaryUnavailable(
                    baseline.engagementId(), baseline.baselineVersion(), accumulated,
                    DiffLookup.Unavailable.Reason.RENDERING_FAILED);
        }
    }
}
