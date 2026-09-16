import type {
  ChangeSummary,
  EngagementUpdateListItem,
  EngagementUpdateListResponse,
  PendingUpdateResponse,
} from '@contract/api-contract';

/**
 * Sample server responses, standing in for the API.
 *
 * <p>The change descriptions below are the exact strings the Java renderer produces for these
 * version ranges — see PendingUpdateCalculatorTest. They are not invented client-side copy.
 * That is the whole point of rendering the summary on the server: there is one wording of a
 * methodology change, versioned by `rendererVersion`, and the client displays it rather than
 * composing its own.
 */

const PROJECTION_AS_OF = '2026-09-16T09:58:30Z';
const SERVED_AT = '2026-09-16T10:00:00Z';

const freshness = {
  projectionAsOf: PROJECTION_AS_OF,
  servedAt: SERVED_AT,
  degraded: false,
};

/** AUDIT-CA v4 to v5 — a single accumulated step. */
const auditCaV4ToV5: ChangeSummary = {
  templateId: 'AUDIT-CA',
  fromVersion: 4,
  toVersion: 5,
  generatedAt: '2026-08-18T13:05:02Z',
  rendererVersion: '1.0.0',
  headline: { totalChanges: 3, added: 1, modified: 2, removed: 0, notable: 1 },
  groups: [
    {
      sectionKey: 'planning',
      sectionLabel: 'Planning',
      changes: [
        {
          id: 'chg-1',
          kind: 'MODIFIED',
          elementType: 'question',
          description: 'The wording of question 3 was updated.',
          valueChange: {
            before: 'Has management identified significant estimates?',
            after:
              'Has management identified significant accounting estimates and related estimation uncertainty?',
          },
          sourcePath: '/sections/planning/questions/3/label',
          significance: 'NORMAL',
        },
      ],
    },
    {
      sectionKey: 'materiality',
      sectionLabel: 'Materiality',
      changes: [
        {
          id: 'chg-2',
          kind: 'MODIFIED',
          elementType: 'guidance setting',
          description: 'Threshold in Materiality changed from 4.5% to 4.0%.',
          valueChange: { before: '4.5%', after: '4.0%' },
          sourcePath: '/sections/materiality/guidance/thresholdPercent',
          significance: 'NOTABLE',
        },
      ],
    },
    {
      sectionKey: 'completion',
      sectionLabel: 'Completion',
      changes: [
        {
          id: 'chg-3',
          kind: 'ADDED',
          elementType: 'checklist',
          description:
            'A new checklist was added: “Subsequent events review”. It has 3 items.',
          sourcePath: '/sections/completion/checklists/subsequent-events',
          significance: 'NORMAL',
        },
      ],
    },
  ],
};

/**
 * REVIEW-CA v6 to v8 — two updates accumulated, summarised as one net change.
 *
 * <p>Note what is absent: the analytics tolerance reads 0.15 to 0.1, with no mention of the
 * 0.12 it passed through in v7. This engagement has never held 0.12 and never will.
 */
const reviewCaV6ToV8: ChangeSummary = {
  templateId: 'REVIEW-CA',
  fromVersion: 6,
  toVersion: 8,
  generatedAt: '2026-08-25T13:05:10Z',
  rendererVersion: '1.0.0',
  headline: { totalChanges: 5, added: 2, modified: 2, removed: 1, notable: 1 },
  groups: [
    {
      sectionKey: 'metadata',
      sectionLabel: 'Template details',
      changes: [
        {
          id: 'chg-1',
          kind: 'MODIFIED',
          elementType: 'template detail',
          description:
            'The template name changed from “Canadian Review Engagement” to “Canadian Review Engagement 2026”.',
          valueChange: {
            before: 'Canadian Review Engagement',
            after: 'Canadian Review Engagement 2026',
          },
          sourcePath: '/metadata/displayName',
          significance: 'NORMAL',
        },
      ],
    },
    {
      sectionKey: 'inquiries',
      sectionLabel: 'Inquiries',
      changes: [
        {
          id: 'chg-2',
          kind: 'ADDED',
          elementType: 'question',
          description:
            'A new question was added: “Describe any events after the reporting date that may require adjustment or disclosure.”',
          sourcePath: '/sections/inquiries/questions/12',
          significance: 'NORMAL',
        },
        {
          id: 'chg-4',
          kind: 'REMOVED',
          elementType: 'question',
          description: 'Help text was removed from question 4.',
          sourcePath: '/sections/inquiries/questions/4/helpText',
          significance: 'NORMAL',
        },
      ],
    },
    {
      sectionKey: 'analytics',
      sectionLabel: 'Analytics',
      changes: [
        {
          id: 'chg-3',
          kind: 'MODIFIED',
          elementType: 'procedure',
          description: 'Tolerance for procedure 2 changed from 0.15 to 0.1.',
          valueChange: { before: '0.15', after: '0.1' },
          sourcePath: '/sections/analytics/procedures/2/tolerance',
          significance: 'NOTABLE',
        },
      ],
    },
    {
      sectionKey: 'completion',
      sectionLabel: 'Completion',
      changes: [
        {
          id: 'chg-5',
          kind: 'ADDED',
          elementType: 'checklist',
          description:
            'A new checklist was added: “Going concern evaluation”. It has 3 items.',
          sourcePath: '/sections/completion/checklists/going-concern',
          significance: 'NORMAL',
        },
      ],
    },
  ],
};

/** The list view, covering every state the badge has to handle. */
export const LIST_FIXTURE: EngagementUpdateListResponse = {
  freshness,
  items: [
    {
      engagementId: 'ENG-1001',
      engagementName: 'Northstar Manufacturing 2026',
      templateId: 'AUDIT-CA',
      templateDisplayName: 'Canadian Audit Engagement',
      status: 'UP_TO_DATE',
      currentVersion: 5,
    },
    {
      engagementId: 'ENG-1002',
      engagementName: 'Maple Ridge Foods 2026',
      templateId: 'AUDIT-CA',
      templateDisplayName: 'Canadian Audit Engagement',
      status: 'UPDATE_AVAILABLE',
      baselineVersion: 4,
      latestVersion: 5,
      versionsBehind: 1,
      latestPublishedAt: '2026-08-18T13:00:00Z',
    },
    {
      engagementId: 'ENG-1007',
      engagementName: 'Bluewater Hospitality 2026',
      templateId: 'REVIEW-CA',
      templateDisplayName: 'Canadian Review Engagement',
      status: 'UPDATE_AVAILABLE',
      baselineVersion: 6,
      latestVersion: 8,
      versionsBehind: 2,
      latestPublishedAt: '2026-08-25T13:00:00Z',
    },
    {
      engagementId: 'ENG-1010',
      engagementName: 'Greenfield Health Services 2026',
      templateId: 'RISK-CA',
      templateDisplayName: 'Canadian Risk Assessment',
      status: 'SUMMARY_PENDING',
      baselineVersion: 11,
      latestVersion: 12,
      versionsBehind: 1,
      latestPublishedAt: '2026-08-11T13:00:00Z',
    },
    {
      engagementId: 'ENG-1011',
      engagementName: 'Stonebridge Construction 2026',
      templateId: 'RISK-CA',
      templateDisplayName: 'Canadian Risk Assessment',
      status: 'SUMMARY_UNAVAILABLE',
      baselineVersion: 10,
      latestVersion: 12,
      versionsBehind: 2,
      latestPublishedAt: '2026-08-11T13:00:00Z',
    },
    {
      engagementId: 'ENG-1012',
      engagementName: 'Prairie Star Investments 2026',
      templateId: 'RISK-CA',
      templateDisplayName: 'Canadian Risk Assessment',
      status: 'UNKNOWN',
    },
  ] satisfies EngagementUpdateListItem[],
};

/** Detail responses, keyed by engagement. */
export const DETAIL_FIXTURE: Record<string, PendingUpdateResponse> = {
  'ENG-1001': {
    status: 'UP_TO_DATE',
    freshness,
    engagementId: 'ENG-1001',
    currentVersion: 5,
  },
  'ENG-1002': {
    status: 'UPDATE_AVAILABLE',
    freshness,
    engagementId: 'ENG-1002',
    baselineVersion: 4,
    latestVersion: 5,
    accumulatedVersions: [{ version: 5, publishedAt: '2026-08-18T13:00:00Z' }],
    summary: auditCaV4ToV5,
    decisionToken: 'dt_audit-ca_4_5_eng-1002',
  },
  'ENG-1007': {
    status: 'UPDATE_AVAILABLE',
    freshness,
    engagementId: 'ENG-1007',
    baselineVersion: 6,
    latestVersion: 8,
    accumulatedVersions: [
      { version: 7, publishedAt: '2026-07-21T13:00:00Z' },
      { version: 8, publishedAt: '2026-08-25T13:00:00Z' },
    ],
    summary: reviewCaV6ToV8,
    decisionToken: 'dt_review-ca_6_8_eng-1007',
  },
  'ENG-1010': {
    status: 'SUMMARY_PENDING',
    freshness,
    engagementId: 'ENG-1010',
    baselineVersion: 11,
    latestVersion: 12,
    accumulatedVersions: [{ version: 12, publishedAt: '2026-08-11T13:00:00Z' }],
    retryAfterSeconds: 5,
  },
  'ENG-1011': {
    status: 'SUMMARY_UNAVAILABLE',
    freshness,
    engagementId: 'ENG-1011',
    baselineVersion: 10,
    latestVersion: 12,
    accumulatedVersions: [
      { version: 11, publishedAt: '2026-06-30T13:00:00Z' },
      { version: 12, publishedAt: '2026-08-11T13:00:00Z' },
    ],
    reason: 'DIFF_SOURCE_UNAVAILABLE',
    incidentId: 'inc-7f21c9',
  },
  'ENG-1012': {
    status: 'UNKNOWN',
    freshness,
    engagementId: 'ENG-1012',
  },
};
