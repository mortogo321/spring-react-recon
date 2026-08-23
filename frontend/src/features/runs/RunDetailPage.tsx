import { useMemo, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import LinearProgress from '@mui/material/LinearProgress';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { BarChart } from '@mui/x-charts/BarChart';

import { useRunBreakdownQuery, useRunOperationMutation, useRunQuery, useRunStepsQuery } from '../../api/reconApi';
import { useAppSelector } from '../../app/hooks';
import { CardSkeleton, EmptyState, ErrorPanel } from '../../components/Feedback';
import { KpiCard } from '../../components/KpiCard';
import { RunStatusChip } from '../../components/StatusChip';
import { canOperateRuns, selectUser } from '../auth/authSlice';
import { formatDuration, formatInstant, formatMoney, formatPercent, humanise } from '../../format';
import { RUN_IN_FLIGHT, type StepView } from '../../api/types';
import { MONOSPACE } from '../../theme';

type Operation = 'stop' | 'restart' | 'recover' | 'abandon';

/** Why each operation exists, in the words an operator would use at 3am. */
const OPERATION_HELP: Record<Operation, string> = {
  stop: 'Ask the job to stop at the next chunk boundary. The completed chunks stay committed.',
  restart: 'Resume a failed or stopped run from its last committed restart point.',
  recover: 'Clear a run left RUNNING by a JVM that died. Nothing is reprocessed.',
  abandon: 'Give up on this attempt so the same business date can be launched again.',
};

/**
 * One run, in enough detail to answer "what did the job actually do".
 *
 * The step table is the part that earns its place: the reconcile step is partitioned by merchant,
 * so a run that looks slow or lossy in aggregate resolves into one shard with a skip count. Read,
 * filtered and written are shown side by side because their difference is the whole story — 101
 * read, 4 filtered, 97 written means four rows were excluded on purpose, not lost.
 */
export default function RunDetailPage() {
  const { runId } = useParams();
  const id = Number(runId);
  const user = useAppSelector(selectUser);
  const [pendingOperation, setPendingOperation] = useState<Operation | null>(null);

  const run = useRunQuery(id, { skip: !Number.isFinite(id) });
  const inFlight = run.data ? RUN_IN_FLIGHT.includes(run.data.status) : false;
  const pollingInterval = inFlight ? 2_000 : 0;

  const breakdown = useRunBreakdownQuery(id, { skip: !Number.isFinite(id), pollingInterval });
  const steps = useRunStepsQuery(id, { skip: !Number.isFinite(id), pollingInterval });
  useRunQuery(id, { skip: !Number.isFinite(id), pollingInterval });

  const [operate, operation] = useRunOperationMutation();

  const partitions = useMemo(() => {
    const rows = steps.data ?? [];
    // The worker steps are the ones named after their shard; the three orchestration steps are
    // interesting individually, the partitions are interesting as a distribution.
    return rows.filter((step) => step.name.includes(':'));
  }, [steps.data]);

  if (!Number.isFinite(id)) return <ErrorPanel error={{ status: 400, data: { title: 'Not a run id' } }} />;
  if (run.isLoading) return <CardSkeleton height={260} />;
  if (run.error) return <ErrorPanel error={run.error} onRetry={run.refetch} />;
  if (!run.data) return <EmptyState title="Run not found" />;

  const data = run.data;
  const clean = data.exceptionKeys === 0;

  async function invoke(op: Operation) {
    setPendingOperation(op);
    try {
      await operate({ id, operation: op }).unwrap();
    } catch {
      // Surfaced from the mutation's error state under the action row.
    } finally {
      setPendingOperation(null);
    }
  }

  return (
    <Stack spacing={2.5}>
      <Box>
        <Breadcrumbs sx={{ mb: 0.5 }}>
          <Link component={RouterLink} to="/runs" variant="body2">
            Runs
          </Link>
          <Typography variant="body2" color="text.primary">
            {data.businessDate}
          </Typography>
        </Breadcrumbs>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap" }}>
          <Typography variant="h5" sx={{ fontFamily: MONOSPACE }}>
            {data.runKey}
          </Typography>
          <RunStatusChip status={data.status} />
          <Chip size="small" variant="outlined" label={`profile: ${data.toleranceProfile}`} />
          {data.jobExecutionId !== undefined && (
            <Chip size="small" variant="outlined" label={`execution #${data.jobExecutionId}`} />
          )}
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Triggered by {data.triggeredBy ?? 'unknown'} · started {formatInstant(data.startedAt)} · took{' '}
          {formatDuration(data.startedAt, data.finishedAt)}
        </Typography>
      </Box>

      {inFlight && <LinearProgress />}

      {data.failureReason && (
        <Alert severity="error">
          <Typography variant="body2">{data.failureReason}</Typography>
        </Alert>
      )}

      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
        {(['stop', 'restart', 'recover', 'abandon'] as const).map((op) => {
          const allowed = canOperateRuns(user) && isOperationSensible(op, data.status, data.restartable);
          return (
            <Tooltip key={op} title={canOperateRuns(user) ? OPERATION_HELP[op] : 'Requires the administrator role'}>
              <span>
                <Button
                  size="small"
                  variant="outlined"
                  color={op === 'abandon' ? 'error' : 'primary'}
                  disabled={!allowed}
                  loading={operation.isLoading && pendingOperation === op}
                  onClick={() => invoke(op)}
                >
                  {humanise(op)}
                </Button>
              </span>
            </Tooltip>
          );
        })}
      </Stack>
      {operation.error && <ErrorPanel error={operation.error} />}
      {operation.data && (
        <Alert severity={operation.data.accepted ? 'success' : 'warning'}>{operation.data.detail}</Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Match rate"
            value={formatPercent(data.matchRate)}
            caption={`${data.matchedKeys.toLocaleString()} of ${(data.matchedKeys + data.exceptionKeys).toLocaleString()} keys`}
            tone={clean ? 'good' : 'warn'}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Exposure"
            value={formatMoney(data.exposure)}
            help="Absolute value of every break this run raised."
            tone={clean ? 'good' : 'bad'}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Matched amount"
            value={formatMoney(data.matchedAmount)}
            help="Settled value that agreed with the ledger, tolerance included."
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Rows in scope"
            value={`${data.settlementRows.toLocaleString()} / ${data.ledgerRows.toLocaleString()}`}
            help="Settlement rows against ledger postings. Excluded rows are counted separately."
            caption={`${data.excludedRows.toLocaleString()} excluded (reversals, chargebacks)`}
          />
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Card sx={{ height: '100%' }}>
            <CardHeader
              title="Breaks by class"
              titleTypographyProps={{ variant: 'subtitle1' }}
              action={
                <Link component={RouterLink} to={`/exceptions?runId=${id}`} variant="body2" sx={{ mr: 2 }}>
                  Work the queue
                </Link>
              }
            />
            <CardContent sx={{ pt: 0 }}>
              {breakdown.isLoading ? (
                <CardSkeleton height={240} />
              ) : (breakdown.data?.byStatus.length ?? 0) === 0 ? (
                <EmptyState title="Reconciled clean" hint="Every key agreed within tolerance." />
              ) : (
                <>
                  <BarChart
                    height={240}
                    layout="horizontal"
                    yAxis={[
                      {
                        data: (breakdown.data?.byStatus ?? []).map((row) => humanise(row.name)),
                        scaleType: 'band',
                        width: 150,
                      },
                    ]}
                    series={[{ data: (breakdown.data?.byStatus ?? []).map((row) => row.amount), label: 'Exposure' }]}
                    margin={{ top: 8, right: 12, bottom: 8, left: 8 }}
                  />
                  <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap", mt: 1 }}>
                    {(breakdown.data?.bySeverity ?? []).map((row) => (
                      <Chip
                        key={row.name}
                        size="small"
                        variant="outlined"
                        color={row.name === 'CRITICAL' ? 'error' : row.name === 'WARNING' ? 'warning' : 'default'}
                        label={`${humanise(row.name)}: ${row.count}`}
                      />
                    ))}
                  </Stack>
                </>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 7 }}>
          <Card sx={{ height: '100%' }}>
            <CardHeader
              title="Job steps"
              subheader={
                partitions.length > 0
                  ? `${partitions.length} merchant partitions, executed by the worker pool`
                  : 'Orchestration steps'
              }
              titleTypographyProps={{ variant: 'subtitle1' }}
            />
            <CardContent sx={{ pt: 0, overflowX: 'auto' }}>
              {steps.isLoading ? (
                <CardSkeleton height={240} />
              ) : (steps.data?.length ?? 0) === 0 ? (
                <EmptyState
                  title="No step metadata"
                  hint="Spring Batch records steps once the job has started."
                />
              ) : (
                <Table size="small" sx={{ minWidth: 620 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Step</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Read</TableCell>
                      <TableCell align="right">Filtered</TableCell>
                      <TableCell align="right">Written</TableCell>
                      <TableCell align="right">Skipped</TableCell>
                      <TableCell align="right">Commits</TableCell>
                      <TableCell align="right">Took</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(steps.data ?? []).map((step) => (
                      <TableRow key={step.stepExecutionId} hover>
                        <TableCell sx={{ fontFamily: MONOSPACE, fontSize: 12 }}>{shortStepName(step)}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            variant="outlined"
                            color={stepColour(step.status)}
                            label={humanise(step.status)}
                          />
                        </TableCell>
                        <TableCell align="right">{step.readCount.toLocaleString()}</TableCell>
                        <TableCell align="right">{step.filterCount.toLocaleString()}</TableCell>
                        <TableCell align="right">{step.writeCount.toLocaleString()}</TableCell>
                        <TableCell align="right" sx={{ color: step.skipCount > 0 ? 'warning.main' : undefined }}>
                          {step.skipCount.toLocaleString()}
                        </TableCell>
                        <TableCell align="right">{step.commitCount.toLocaleString()}</TableCell>
                        <TableCell align="right">{formatDuration(step.startTime, step.endTime)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}

/** `reconcileMerchantStep:merchant-2-M-1003` reads better as `merchant-2-M-1003`. */
function shortStepName(step: StepView): string {
  const [, partition] = step.name.split(':');
  return partition ?? step.name;
}

function stepColour(status: string): 'default' | 'success' | 'error' | 'warning' | 'info' {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'error';
    case 'STOPPED':
    case 'ABANDONED':
      return 'warning';
    case 'STARTED':
    case 'STARTING':
      return 'info';
    default:
      return 'default';
  }
}

/**
 * Which operations make sense for the state the run is in. The API enforces the real rules; this
 * only stops the console from offering a button that is certain to fail — restarting a completed
 * run, or stopping one that already finished.
 */
function isOperationSensible(operation: Operation, status: string, restartable: boolean): boolean {
  switch (operation) {
    case 'stop':
      return status === 'RUNNING' || status === 'PENDING';
    case 'restart':
      return restartable;
    case 'recover':
      return status === 'RUNNING';
    case 'abandon':
      return status !== 'COMPLETED_CLEAN' && status !== 'COMPLETED_WITH_BREAKS';
    default:
      return false;
  }
}
