import { createApi, fetchBaseQuery, type BaseQueryFn, type FetchArgs, type FetchBaseQueryError } from '@reduxjs/toolkit/query/react';

import { signedOut } from '../features/auth/authSlice';
import type { RootState } from '../app/store';
import type {
  Dashboard,
  ExceptionDetail,
  ExceptionQuery,
  ExceptionRow,
  ExceptionState,
  JobOperationResponse,
  LaunchRequest,
  LaunchResponse,
  MerchantQuery,
  MerchantView,
  PagedResult,
  RunBreakdown,
  RunView,
  StepView,
} from './types';

const rawBaseQuery = fetchBaseQuery({
  baseUrl: '/api',
  prepareHeaders: (headers, { getState }) => {
    const token = (getState() as RootState).auth.token;
    if (token) headers.set('Authorization', `Bearer ${token}`);
    // Every request carries one, so a support conversation can start from a screenshot: the id
    // the console generates is the id in the API's logs and in the problem detail it returns.
    headers.set('X-Correlation-Id', correlationId());
    return headers;
  },
});

/**
 * The one place a 401 is handled. Without this each panel would render its own "unauthorised"
 * state and the user would be left on a shell that no longer works — an expired token has to end
 * the session, not degrade it.
 */
const baseQuery: BaseQueryFn<string | FetchArgs, unknown, FetchBaseQueryError> = async (args, api, extra) => {
  const result = await rawBaseQuery(args, api, extra);
  if (result.error?.status === 401 && (api.getState() as RootState).auth.token !== null) {
    api.dispatch(signedOut());
  }
  return result;
};

function correlationId(): string {
  return crypto.randomUUID();
}

/**
 * A launch is the one request in this console that must not be repeated by a double click: it
 * starts a job against two production databases. The key is generated per attempt and sent as
 * Idempotency-Key, which the API's filter uses to answer 409 to the second click rather than
 * starting a second run. Regenerated on each mutation call so a *deliberate* relaunch still works.
 */
function idempotencyKey(): string {
  return crypto.randomUUID();
}

/** Drops undefined and empty values so the URL carries only filters the user actually set. */
function queryParams(query: object): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      // The API binds List<Enum> from repeated params, not a comma-joined string.
      for (const item of value) params.append(key, String(item));
    } else {
      params.set(key, String(value));
    }
  }
  return params;
}

export const reconApi = createApi({
  reducerPath: 'reconApi',
  baseQuery,
  // Every tag here is invalidated by something. 'Run' and 'Exception' are deliberately coarse:
  // a decision on one break changes the run's open count and the dashboard's KPI strip, so
  // per-id invalidation would leave two panels stale and looking like a bug.
  tagTypes: ['Run', 'RunList', 'Exception', 'ExceptionList', 'Dashboard', 'Merchant'],
  // A console left open on a wall display should not show yesterday's numbers.
  refetchOnReconnect: true,
  endpoints: (build) => ({
    login: build.mutation<import('./types').LoginResponse, { username: string; password: string }>({
      query: (body) => ({ url: '/auth/login', method: 'POST', body }),
    }),

    me: build.query<import('./types').UserInfo, void>({
      query: () => '/auth/me',
    }),

    dashboard: build.query<Dashboard, { trendDays?: number } | void>({
      query: (args) => `/dashboard?${queryParams({ trendDays: args?.trendDays ?? 14 })}`,
      providesTags: ['Dashboard'],
    }),

    runs: build.query<RunView[], { limit?: number } | void>({
      query: (args) => `/runs?${queryParams({ limit: args?.limit ?? 25 })}`,
      providesTags: ['RunList'],
    }),

    run: build.query<RunView, number>({
      query: (id) => `/runs/${id}`,
      providesTags: (_result, _error, id) => [{ type: 'Run', id }],
    }),

    runBreakdown: build.query<RunBreakdown, number>({
      query: (id) => `/runs/${id}/breakdown`,
      providesTags: (_result, _error, id) => [{ type: 'Run', id }],
    }),

    runSteps: build.query<StepView[], number>({
      query: (id) => `/runs/${id}/steps`,
      providesTags: (_result, _error, id) => [{ type: 'Run', id }],
    }),

    toleranceProfiles: build.query<string[], void>({
      query: () => '/runs/profiles',
      // Configuration, not data: it changes on a deployment, not during a shift.
      keepUnusedDataFor: 3600,
    }),

    launchRun: build.mutation<LaunchResponse, LaunchRequest>({
      query: (body) => ({
        url: '/runs',
        method: 'POST',
        body,
        headers: { 'Idempotency-Key': idempotencyKey() },
      }),
      invalidatesTags: ['RunList', 'Dashboard'],
    }),

    runOperation: build.mutation<JobOperationResponse, { id: number; operation: 'stop' | 'restart' | 'recover' | 'abandon' }>({
      query: ({ id, operation }) => ({ url: `/runs/${id}/${operation}`, method: 'POST' }),
      invalidatesTags: (_result, _error, { id }) => [{ type: 'Run', id }, 'RunList', 'Dashboard'],
    }),

    exceptions: build.query<PagedResult<ExceptionRow>, ExceptionQuery>({
      query: (query) => `/exceptions?${queryParams(query)}`,
      providesTags: ['ExceptionList'],
    }),

    exception: build.query<ExceptionDetail, number>({
      query: (id) => `/exceptions/${id}`,
      providesTags: (_result, _error, id) => [{ type: 'Exception', id }],
    }),

    assignException: build.mutation<ExceptionRow, { id: number; assignee: string }>({
      query: ({ id, assignee }) => ({ url: `/exceptions/${id}/assign`, method: 'POST', body: { assignee } }),
      invalidatesTags: (_result, _error, { id }) => [{ type: 'Exception', id }, 'ExceptionList', 'Dashboard'],
    }),

    bulkAssign: build.mutation<{ updated: number }, { ids: number[]; assignee: string }>({
      query: (body) => ({ url: '/exceptions/bulk-assign', method: 'POST', body }),
      invalidatesTags: ['ExceptionList', 'Exception', 'Dashboard'],
    }),

    commentOnException: build.mutation<ExceptionDetail, { id: number; body: string }>({
      query: ({ id, body }) => ({ url: `/exceptions/${id}/comments`, method: 'POST', body: { body } }),
      invalidatesTags: (_result, _error, { id }) => [{ type: 'Exception', id }],
    }),

    submitException: build.mutation<ExceptionRow, { id: number; note: string }>({
      query: ({ id, note }) => ({ url: `/exceptions/${id}/submit`, method: 'POST', body: { note } }),
      invalidatesTags: (_result, _error, { id }) => [{ type: 'Exception', id }, 'ExceptionList', 'Dashboard'],
    }),

    decideException: build.mutation<ExceptionRow, { id: number; decision: ExceptionState; note?: string }>({
      query: ({ id, decision, note }) => ({ url: `/exceptions/${id}/decision`, method: 'POST', body: { decision, note } }),
      invalidatesTags: (_result, _error, { id }) => [{ type: 'Exception', id }, 'ExceptionList', 'Dashboard'],
    }),

    merchants: build.query<MerchantView[], MerchantQuery | void>({
      query: (args) => `/merchants?${queryParams({ ...(args ?? {}), limit: args?.limit ?? 50 })}`,
      providesTags: ['Merchant'],
    }),

    merchant: build.query<MerchantView, string>({
      query: (merchantId) => `/merchants/${merchantId}`,
      providesTags: (_result, _error, id) => [{ type: 'Merchant', id }],
    }),

    evictMerchantCache: build.mutation<void, void>({
      query: () => ({ url: '/merchants/cache', method: 'DELETE' }),
      invalidatesTags: ['Merchant'],
    }),
  }),
});

export const {
  useLoginMutation,
  useMeQuery,
  useDashboardQuery,
  useRunsQuery,
  useRunQuery,
  useRunBreakdownQuery,
  useRunStepsQuery,
  useToleranceProfilesQuery,
  useLaunchRunMutation,
  useRunOperationMutation,
  useExceptionsQuery,
  useExceptionQuery,
  useAssignExceptionMutation,
  useBulkAssignMutation,
  useCommentOnExceptionMutation,
  useSubmitExceptionMutation,
  useDecideExceptionMutation,
  useMerchantsQuery,
  useMerchantQuery,
  useEvictMerchantCacheMutation,
} = reconApi;
