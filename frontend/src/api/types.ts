/**
 * The HTTP contract, hand-written rather than generated.
 *
 * Generating these from /v3/api-docs is the right call on a large API; on one this size the
 * hand-written version is worth more, because it is where the frontend states the assumptions it
 * is making about the backend. Two of those assumptions are load-bearing:
 *
 * - `amount` is a string. The API serialises money as a decimal string precisely so that
 *   104643.42 never becomes 104643.41999999999 on its way through a double, and the type is what
 *   stops someone reaching for `.toFixed()`.
 * - Optional fields are declared `?:` rather than `| null`, because the API is configured with
 *   `default-property-inclusion: non_null` — a null is absent from the payload, not present as
 *   null. Modelling it as `| null` would make `'x' in obj` checks lie.
 */

/** Decimal string plus ISO-4217 code. Never parsed into a number for display — see formatMoney. */
export interface Money {
  amount: string;
  currency: string;
}

export type RunStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED_CLEAN'
  | 'COMPLETED_WITH_BREAKS'
  | 'FAILED'
  | 'STOPPED'
  | 'ABANDONED';

/** In flight in the sense that matters to the console: keep polling, and disable the actions. */
export const RUN_IN_FLIGHT: readonly RunStatus[] = ['PENDING', 'RUNNING'];

export type MatchStatus =
  | 'MATCHED'
  | 'MATCHED_WITHIN_TOLERANCE'
  | 'AMOUNT_MISMATCH'
  | 'CURRENCY_MISMATCH'
  | 'DUPLICATE_SETTLEMENT'
  | 'MISSING_IN_LEDGER'
  | 'MISSING_IN_SETTLEMENT'
  | 'EXCLUDED';

export type MatchSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export type ExceptionState =
  | 'OPEN'
  | 'INVESTIGATING'
  | 'PENDING_APPROVAL'
  | 'RESOLVED'
  | 'WRITTEN_OFF'
  | 'REJECTED';

/** The two decisions an approver may take, as opposed to the states the workflow passes through. */
export const DECISION_STATES: readonly ExceptionState[] = ['RESOLVED', 'WRITTEN_OFF', 'REJECTED'];

export interface RunView {
  id: number;
  runKey: string;
  businessDate: string;
  status: RunStatus;
  toleranceProfile: string;
  triggeredBy?: string;
  jobExecutionId?: number;
  startedAt?: string;
  finishedAt?: string;
  settlementRows: number;
  ledgerRows: number;
  excludedRows: number;
  matchedKeys: number;
  exceptionKeys: number;
  matchRate?: number;
  matchedAmount?: Money;
  exposure?: Money;
  failureReason?: string;
  restartable: boolean;
}

export interface LaunchRequest {
  businessDate: string;
  toleranceProfile?: string;
}

export interface LaunchResponse {
  runId: number;
  jobExecutionId?: number;
  status: string;
  runKey: string;
}

export interface JobOperationResponse {
  operation: string;
  accepted: boolean;
  detail: string;
  jobExecutionId?: number;
}

export interface CountByName {
  name: string;
  count: number;
}

export interface AmountByName extends CountByName {
  amount: number;
}

export interface RunBreakdown {
  runId: number;
  byStatus: AmountByName[];
  bySeverity: CountByName[];
  byState: CountByName[];
  openCount: number;
}

export interface StepView {
  stepExecutionId: number;
  name: string;
  status: string;
  exitCode?: string;
  readCount: number;
  writeCount: number;
  filterCount: number;
  skipCount: number;
  commitCount: number;
  rollbackCount: number;
  startTime?: string;
  endTime?: string;
}

export interface ExceptionRow {
  id: number;
  runId: number;
  merchantId: string;
  externalRef: string;
  status: MatchStatus;
  severity: MatchSeverity;
  state: ExceptionState;
  settlementAmount?: Money;
  ledgerAmount?: Money;
  exposure?: Money;
  detail: string;
  assignedTo?: string;
  submittedBy?: string;
  updatedAt: string;
  version: number;
  /** What the workflow will accept next, decided server-side. The UI renders buttons from this. */
  allowedTransitions: ExceptionState[];
}

export interface CommentView {
  id: number;
  author: string;
  body: string;
  createdAt: string;
}

export interface ExceptionDetail {
  exception: ExceptionRow;
  resolutionNote?: string;
  submittedAt?: string;
  decidedBy?: string;
  decidedAt?: string;
  comments: CommentView[];
}

export interface PagedResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DashboardKpis {
  latestBusinessDate?: string;
  matchRate?: number;
  exceptionKeys: number;
  openExceptions: number;
  exposure?: Money;
  settlementRows: number;
  hasCriticalBreaks: boolean;
}

export interface TrendPoint {
  businessDate: string;
  matchRate?: number;
  exceptionKeys: number;
  settlementRows: number;
}

export interface Dashboard {
  kpis: DashboardKpis;
  trend: TrendPoint[];
  exceptionsByStatus: AmountByName[];
  exceptionsByState: CountByName[];
  recentRuns: RunView[];
}

export interface MerchantView {
  merchantId: string;
  legalName: string;
  mcc?: string;
  settlementCurrency: string;
  acquirerId: string;
  onboardedOn: string;
  active: boolean;
}

// As the API sends them. The `ROLE_` prefix Spring Security uses internally is stripped before
// the claim is written, so it never appears on the wire and must not be assumed here.
export type ReconRole = 'OPERATOR' | 'APPROVER' | 'ADMIN';

export interface UserInfo {
  username: string;
  displayName: string;
  roles: ReconRole[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

/** Sort fields the API whitelists. Anything else falls back to id, so the union is the contract. */
export type ExceptionSortBy = 'exposure' | 'merchant' | 'ref' | 'status' | 'severity' | 'state' | 'updated';

export interface ExceptionQuery {
  runId?: number | undefined;
  /** Repeated query params: the API takes lists for the three enum filters. */
  status?: MatchStatus[] | undefined;
  severity?: MatchSeverity[] | undefined;
  state?: ExceptionState[] | undefined;
  merchantId?: string | undefined;
  assignedTo?: string | undefined;
  minExposure?: string | undefined;
  /** Free text across merchant, reference and the detail line. */
  q?: string | undefined;
  page: number;
  size: number;
  sortBy?: ExceptionSortBy | undefined;
  sortDir?: 'asc' | 'desc' | undefined;
}

export interface MerchantQuery {
  name?: string | undefined;
  mcc?: string | undefined;
  acquirerId?: string | undefined;
  activeOnly?: boolean | undefined;
  limit?: number | undefined;
}

/**
 * RFC 9457 problem detail. The API answers every failure in this shape, including validation
 * errors, so the console has exactly one error renderer instead of one per endpoint.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  errors?: Record<string, string>;
}
