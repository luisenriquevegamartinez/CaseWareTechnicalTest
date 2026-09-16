package com.caseware.templateupdates.domain;

import com.caseware.templateupdates.domain.summary.ChangeSummary;
import com.caseware.templateupdates.port.DiffLookup;

import java.util.List;
import java.util.Objects;

/**
 * What the system can currently say about one engagement's pending template update.
 *
 * <p>A sealed hierarchy rather than a status enum beside a bag of nullable fields, so each
 * variant carries exactly what is knowable in that state and nothing more. There is no summary
 * field to read when there is no summary, and no baseline to read when the baseline is unknown.
 *
 * <p>Note what is absent: there is no {@code DecisionInProgress} variant here, although the API
 * contract has that state. Whether a decision is in flight is a fact about a submitted command,
 * not something derivable from a baseline and a publication history. It is layered on by the
 * caller. Keeping it out of this type is what stops this class from becoming a mirror of the
 * wire format.
 */
public sealed interface PendingUpdateState {

    EngagementId engagementId();

    /**
     * No baseline is recorded, so whether an update is pending cannot be determined.
     *
     * <p>Reached two ways: the projection has not yet seen this engagement — expected during
     * backfill on an existing product, where populating the read model costs one slow load per
     * file — or the engagement references a template missing from the catalog. In both cases
     * the honest answer is that we do not know, because reporting "up to date" would hide a
     * pending update behind a confident-looking badge.
     */
    record Unknown(EngagementId engagementId, Cause cause) implements PendingUpdateState {

        public Unknown {
            Objects.requireNonNull(engagementId, "engagementId must not be null");
            Objects.requireNonNull(cause, "cause must not be null");
        }

        public enum Cause {
            /** The read model holds no baseline for this engagement yet. */
            NO_BASELINE_RECORDED,
            /** The recorded template is not present in the catalog. */
            TEMPLATE_NOT_IN_CATALOG
        }
    }

    /** The engagement is on the latest published version. Nothing to decide. */
    record UpToDate(EngagementId engagementId, TemplateVersion currentVersion) implements PendingUpdateState {
        public UpToDate {
            Objects.requireNonNull(engagementId, "engagementId must not be null");
            Objects.requireNonNull(currentVersion, "currentVersion must not be null");
        }
    }

    /** Behind, with a summary ready to review. This is the only state a decision can be made from. */
    record UpdateAvailable(
            EngagementId engagementId,
            TemplateVersion baselineVersion,
            List<PublishedVersion> accumulatedVersions,
            ChangeSummary summary) implements PendingUpdateState, Behind {

        public UpdateAvailable {
            Objects.requireNonNull(engagementId, "engagementId must not be null");
            Objects.requireNonNull(baselineVersion, "baselineVersion must not be null");
            Objects.requireNonNull(summary, "summary must not be null");
            accumulatedVersions = List.copyOf(
                    Objects.requireNonNull(accumulatedVersions, "accumulatedVersions must not be null"));
        }
    }

    /** Behind, and the summary for this range is still being computed. */
    record SummaryPending(
            EngagementId engagementId,
            TemplateVersion baselineVersion,
            List<PublishedVersion> accumulatedVersions) implements PendingUpdateState, Behind {

        public SummaryPending {
            Objects.requireNonNull(engagementId, "engagementId must not be null");
            Objects.requireNonNull(baselineVersion, "baselineVersion must not be null");
            accumulatedVersions = List.copyOf(
                    Objects.requireNonNull(accumulatedVersions, "accumulatedVersions must not be null"));
        }
    }

    /** Behind, and the summary could not be produced. The pending badge is still correct. */
    record SummaryUnavailable(
            EngagementId engagementId,
            TemplateVersion baselineVersion,
            List<PublishedVersion> accumulatedVersions,
            DiffLookup.Unavailable.Reason reason) implements PendingUpdateState, Behind {

        public SummaryUnavailable {
            Objects.requireNonNull(engagementId, "engagementId must not be null");
            Objects.requireNonNull(baselineVersion, "baselineVersion must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            accumulatedVersions = List.copyOf(
                    Objects.requireNonNull(accumulatedVersions, "accumulatedVersions must not be null"));
        }
    }

    /**
     * Shared by the three states in which an update is known to be pending, whatever happened to
     * the summary. Lets a caller badge the row without caring why the summary is or is not there.
     */
    sealed interface Behind extends PendingUpdateState
            permits UpdateAvailable, SummaryPending, SummaryUnavailable {

        TemplateVersion baselineVersion();

        List<PublishedVersion> accumulatedVersions();

        /** Versions published after the baseline, up to and including the latest. */
        default int versionsBehind() {
            return accumulatedVersions().size();
        }

        default TemplateVersion latestVersion() {
            return accumulatedVersions().get(accumulatedVersions().size() - 1).version();
        }
    }
}
