package com.caseware.templateupdates.port;

import com.caseware.templateupdates.domain.TemplateDiff;

import java.util.Objects;

/**
 * The result of asking for a diff.
 *
 * <p>Three outcomes, because three genuinely different things can be true, and the user needs
 * to be told a different thing in each case. This type is the reason the API contract has
 * {@code UPDATE_AVAILABLE}, {@code SUMMARY_PENDING} and {@code SUMMARY_UNAVAILABLE} as separate
 * states rather than a summary field that is sometimes absent: a caller that collapses them has
 * to invent an explanation for the user, and will get it wrong.
 *
 * <p>Note that none of the three is "no update exists". Whether an engagement is behind is
 * decided before this port is called, by comparing the recorded baseline against the template's
 * publication history. A failure here degrades the summary, never the badge.
 */
public sealed interface DiffLookup {

    /** The diff is ready. */
    record Available(TemplateDiff diff) implements DiffLookup {
        public Available {
            Objects.requireNonNull(diff, "diff must not be null");
        }
    }

    /**
     * The diff for this version range is being computed. Expected shortly after a publish,
     * before the shared cache has been warmed for every range still in use.
     */
    record Computing() implements DiffLookup {
    }

    /**
     * The diff could not be produced. The user is still told truthfully that an update exists;
     * they are simply not shown a summary the system cannot stand behind.
     */
    record Unavailable(Reason reason) implements DiffLookup {
        public Unavailable {
            Objects.requireNonNull(reason, "reason must not be null");
        }

        public enum Reason {
            /** The template comparison service could not be reached or failed. */
            DIFF_SOURCE_UNAVAILABLE,
            /** The diff was retrieved but could not be turned into a summary. */
            RENDERING_FAILED
        }
    }
}
