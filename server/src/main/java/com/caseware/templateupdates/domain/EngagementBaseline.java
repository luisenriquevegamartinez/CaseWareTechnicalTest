package com.caseware.templateupdates.domain;

import java.util.Objects;

/**
 * What the read model knows about an engagement, without ever opening it.
 *
 * <p>Loading an engagement file takes roughly a minute, and the template version it was
 * created from lives inside that file. This record is the handful of bytes extracted from it
 * when the engagement system emits an event — created, update applied, update declined — so
 * that no user-facing read ever has to pay that minute.
 *
 * <p>Per the exercise's constraints there is no prior apply/decline history, so the recorded
 * version is simply the baseline the next diff is taken from.
 */
public record EngagementBaseline(
        EngagementId engagementId,
        String engagementName,
        TemplateId templateId,
        TemplateVersion baselineVersion) {

    public EngagementBaseline {
        Objects.requireNonNull(engagementId, "engagementId must not be null");
        Objects.requireNonNull(engagementName, "engagementName must not be null");
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(baselineVersion, "baselineVersion must not be null");
    }
}
