package com.caseware.templateupdates.port;

import com.caseware.templateupdates.domain.EngagementBaseline;
import com.caseware.templateupdates.domain.EngagementId;

import java.util.List;
import java.util.Optional;

/**
 * The read model holding each engagement's recorded template version.
 *
 * <p>Populated from engagement lifecycle events — created, update applied, update declined —
 * so that answering "which of my files have pending updates?" never loads an engagement, which
 * would cost roughly a minute each.
 *
 * <p>Eventually consistent by construction. {@link Optional#empty()} is a real answer, not an
 * error: on an existing product the projection starts empty while engagements already exist,
 * and backfilling costs one slow load per file. During that window the honest response is that
 * the state is unknown, rather than "up to date", which would hide a pending update.
 */
public interface EngagementBaselineProjection {

    Optional<EngagementBaseline> findBaseline(EngagementId engagementId);

    /** Every engagement visible to the caller's firm. Scope comes from the caller's tenant. */
    List<EngagementBaseline> findAllForFirm();
}
