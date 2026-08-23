import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

import type { FetchBaseQueryError } from '@reduxjs/toolkit/query';
import type { SerializedError } from '@reduxjs/toolkit';

import type { ProblemDetail } from '../api/types';

/**
 * Exactly what an RTK Query hook hands back, plus the hand-rolled shape a component passes when
 * it wants to render a problem it produced itself (a malformed route parameter, say).
 */
export type QueryError =
  | FetchBaseQueryError
  | SerializedError
  | { status?: number | string; data?: unknown; error?: string }
  | undefined;

/**
 * The API answers every failure as an RFC 9457 problem detail, so this is the only error renderer
 * in the console. It shows the correlation id when there is one: that id is in the API's logs, and
 * quoting it turns "the console broke" into a one-line log query.
 */
export function problemOf(error: QueryError): ProblemDetail {
  if (!error) return {};
  const shape = error as { status?: number | string; data?: unknown; error?: string; message?: string };
  if (shape.data && typeof shape.data === 'object') return shape.data as ProblemDetail;
  if (typeof shape.status === 'string' || shape.status === undefined) {
    // FETCH_ERROR / PARSING_ERROR, or a SerializedError: the request never reached the API, or
    // what came back could not be parsed as a problem detail.
    return {
      title: 'Cannot reach the API',
      detail: shape.error ?? shape.message ?? String(shape.status ?? 'unknown error'),
    };
  }
  return { title: 'Request failed', status: shape.status };
}

export function ErrorPanel({ error, onRetry }: { error: QueryError; onRetry?: () => void }) {
  const problem = problemOf(error);
  const fieldErrors = Object.entries(problem.errors ?? {});
  return (
    <Alert
      severity="error"
      {...(onRetry
        ? {
            action: (
              <Typography
                component="button"
                variant="button"
                onClick={onRetry}
                sx={{ background: 'none', border: 0, cursor: 'pointer', color: 'inherit' }}
              >
                Retry
              </Typography>
            ),
          }
        : {})}
    >
      <AlertTitle>{problem.title ?? 'Something went wrong'}</AlertTitle>
      {problem.detail && <Typography variant="body2">{problem.detail}</Typography>}
      {fieldErrors.length > 0 && (
        <Stack component="ul" sx={{ m: 0, pl: 2 }}>
          {fieldErrors.map(([field, message]) => (
            <li key={field}>
              <Typography variant="body2">
                <strong>{field}</strong>: {message}
              </Typography>
            </li>
          ))}
        </Stack>
      )}
      {problem.correlationId && (
        <Typography variant="caption" color="text.secondary">
          Correlation id {problem.correlationId}
        </Typography>
      )}
    </Alert>
  );
}

export function Loading({ label = 'Loading' }: { label?: string }) {
  return (
    <Stack direction="row" spacing={1.5} sx={{ alignItems: "center", py: 3 }} role="status" aria-live="polite">
      <CircularProgress size={18} />
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    </Stack>
  );
}

/** Placeholder sized like the content it replaces, so nothing jumps when the data lands. */
export function CardSkeleton({ height = 120 }: { height?: number }) {
  return <Skeleton variant="rounded" height={height} animation="wave" />;
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <Box sx={{ py: 6, textAlign: 'center' }}>
      <Typography variant="subtitle1" color="text.secondary">
        {title}
      </Typography>
      {hint && (
        <Typography variant="body2" color="text.disabled" sx={{ mt: 0.5 }}>
          {hint}
        </Typography>
      )}
    </Box>
  );
}
