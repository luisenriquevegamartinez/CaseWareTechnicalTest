package com.caseware.templateupdates.port;

import com.caseware.templateupdates.domain.TemplateId;
import com.caseware.templateupdates.domain.TemplateVersion;

/**
 * Compares two versions of a product template.
 *
 * <p>The exercise grants a quick and reliable way to diff any two versions, so this port takes
 * an arbitrary range rather than only consecutive versions. Its production implementation is
 * expected to be cache-backed: the result depends solely on
 * {@code (templateId, fromVersion, toVersion)} and never on the engagement or the firm, because
 * product templates are shared across all customers. Hundreds of engagements per firm collapse
 * to a handful of distinct ranges, so the expensive work is done once for everyone.
 *
 * <p>Implementations must not throw for an absent or failing diff. Returning
 * {@link DiffLookup.Computing} or {@link DiffLookup.Unavailable} keeps a degraded summary from
 * failing the whole list, which matters when one response covers hundreds of engagements.
 */
public interface TemplateDiffSource {

    DiffLookup diff(TemplateId templateId, TemplateVersion fromVersion, TemplateVersion toVersion);
}
