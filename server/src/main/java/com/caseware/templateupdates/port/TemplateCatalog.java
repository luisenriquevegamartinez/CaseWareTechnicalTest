package com.caseware.templateupdates.port;

import com.caseware.templateupdates.domain.ProductTemplate;
import com.caseware.templateupdates.domain.TemplateId;

import java.util.Optional;

/**
 * Publication history of the product templates, shared across all customer firms.
 *
 * <p>Small and slow-changing — a handful of templates, updated roughly weekly — so an
 * implementation is expected to serve from memory. That is what allows "is this engagement
 * behind?" to be an ordering comparison at read time instead of a flag written into every
 * tenant's database whenever a template is published.
 */
public interface TemplateCatalog {

    Optional<ProductTemplate> find(TemplateId templateId);
}
