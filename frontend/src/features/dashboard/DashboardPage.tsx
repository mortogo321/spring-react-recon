import { useMemo } from 'react';
import { Link as RouterLink } from 'react-router';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Grid from '@mui/material/Grid';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { BarChart } from '@mui/x-charts/BarChart';
import { LineChart } from '@mui/x-charts/LineChart';

import { useDashboardQuery } from '../../api/reconApi';
import { CardSkeleton, EmptyState, ErrorPanel } from '../../components/Feedback';
import { KpiCard } from '../../components/KpiCard';
import { RunStatusChip } from '../../components/StatusChip';
import { formatMoney, formatPercent, humanise } from '../../format';

const TREND_DAYS = 14;

/**
 * The screen someone opens at 08:00 to answer one question: is yesterday's settlement clean, and
 * if not, how much money is sitting in breaks?
 *
 * Exposure leads because it is the number with a currency attached. Match rate is the quality
 * signal, but a 99% match rate hiding a single six-figure missing posting is the situation this
 * layout is designed to make impossible to miss.
 */
export default function DashboardPage() {
  const { data, isLoading, error, refetch } = useDashboardQuery(
    { trendDays: TREND_DAYS },
    // The dashboard is the screen most likely to be left open; a poll keeps it honest without a
    // websocket, and 30s is well inside the time it takes anyone to act on a break.
    { pollingInterval: 30_000, refetchOnMountOrArgChange: true },
  );

  const trend = useMemo(() => {
    const points = data?.trend ?? [];
    return {
      dates: points.map((point) => point.businessDate.slice(5)),
      rates: points.map((point) => point.matchRate ?? null),
      breaks: points.map((point) => point.exceptionKeys),
    };
  }, [data]);

  if (isLoading) {
    return (
      <Grid container spacing={2}>
        {Array.from({ length: 4 }).map((_, index) => (
          <Grid key={index} size={{ xs: 12, sm: 6, md: 3 }}>
            <CardSkeleton />
          </Grid>
        ))}
        <Grid size={{ xs: 12, md: 8 }}>
          <CardSkeleton height={320} />
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <CardSkeleton height={320} />
        </Grid>
      </Grid>
    );
  }

  if (error) return <ErrorPanel error={error} onRetry={refetch} />;
  if (!data) return <EmptyState title="No reconciliation data yet" hint="Launch a run to populate the console." />;

  const { kpis } = data;
  const hasRuns = data.recentRuns.length > 0;

  return (
    <Stack spacing={2.5}>
      <Box>
        <Typography variant="h5">Overview</Typography>
        <Typography variant="body2" color="text.secondary">
          {kpis.latestBusinessDate
            ? `Latest business date ${kpis.latestBusinessDate}`
            : 'No run has completed yet'}
        </Typography>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Open exposure"
            value={formatMoney(kpis.exposure)}
            help="Absolute value of every unresolved break. This is the amount someone has to account for."
            tone={kpis.hasCriticalBreaks ? 'bad' : 'default'}
            caption={kpis.hasCriticalBreaks ? 'Includes critical breaks' : 'No critical breaks'}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Match rate"
            value={formatPercent(kpis.matchRate)}
            help="Keys matched, including those inside the tolerance allowance, over all keys in scope."
            tone={matchRateTone(kpis.matchRate)}
            caption={`${kpis.settlementRows.toLocaleString()} settlement rows`}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Open breaks"
            value={kpis.openExceptions.toLocaleString()}
            help="Breaks not yet resolved, written off or rejected."
            tone={kpis.openExceptions > 0 ? 'warn' : 'good'}
            caption={`${kpis.exceptionKeys.toLocaleString()} raised in the latest run`}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <KpiCard
            label="Runs on file"
            value={data.recentRuns.length.toLocaleString()}
            help="Reconciliation runs the console can show, newest first."
            caption={hasRuns ? `Latest ${humanise(data.recentRuns[0]?.status ?? '')}` : 'None yet'}
          />
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardHeader
              title="Match rate and breaks"
              subheader={`Last ${TREND_DAYS} business dates`}
              titleTypographyProps={{ variant: 'subtitle1' }}
            />
            <CardContent sx={{ pt: 0 }}>
              {trend.dates.length === 0 ? (
                <EmptyState title="Not enough history to plot" />
              ) : (
                <LineChart
                  height={280}
                  xAxis={[{ data: trend.dates, scaleType: 'point', label: 'Business date' }]}
                  yAxis={[
                    { id: 'rate', width: 52, min: 0, max: 100, label: '%' },
                    { id: 'breaks', position: 'right', width: 44, label: 'Breaks' },
                  ]}
                  series={[
                    { data: trend.rates, label: 'Match rate %', yAxisId: 'rate', curve: 'monotoneX', showMark: true },
                    { data: trend.breaks, label: 'Breaks', yAxisId: 'breaks', curve: 'monotoneX', showMark: true },
                  ]}
                  margin={{ top: 12, right: 8, bottom: 8, left: 8 }}
                />
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardHeader
              title="Breaks by class"
              subheader="Latest run, by exposure"
              titleTypographyProps={{ variant: 'subtitle1' }}
            />
            <CardContent sx={{ pt: 0 }}>
              {data.exceptionsByStatus.length === 0 ? (
                <EmptyState title="Nothing to explain" hint="The latest run reconciled cleanly." />
              ) : (
                <BarChart
                  height={280}
                  layout="horizontal"
                  yAxis={[{ data: data.exceptionsByStatus.map((row) => humanise(row.name)), scaleType: 'band', width: 148 }]}
                  series={[{ data: data.exceptionsByStatus.map((row) => row.amount), label: 'Exposure' }]}
                  margin={{ top: 12, right: 12, bottom: 8, left: 8 }}
                />
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={12}>
          <Card>
            <CardHeader
              title="Recent runs"
              titleTypographyProps={{ variant: 'subtitle1' }}
              action={
                <Link component={RouterLink} to="/runs" variant="body2" sx={{ mr: 2 }}>
                  All runs
                </Link>
              }
            />
            <CardContent sx={{ pt: 0, overflowX: 'auto' }}>
              {!hasRuns ? (
                <EmptyState title="No runs yet" hint="Launch one from the Runs page." />
              ) : (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Business date</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Profile</TableCell>
                      <TableCell align="right">Match rate</TableCell>
                      <TableCell align="right">Breaks</TableCell>
                      <TableCell align="right">Exposure</TableCell>
                      <TableCell>Triggered by</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.recentRuns.slice(0, 8).map((run) => (
                      <TableRow key={run.id} hover>
                        <TableCell>
                          <Link component={RouterLink} to={`/runs/${run.id}`}>
                            {run.businessDate}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <RunStatusChip status={run.status} />
                        </TableCell>
                        <TableCell>{run.toleranceProfile}</TableCell>
                        <TableCell align="right">{formatPercent(run.matchRate)}</TableCell>
                        <TableCell align="right">{run.exceptionKeys.toLocaleString()}</TableCell>
                        <TableCell align="right">{formatMoney(run.exposure)}</TableCell>
                        <TableCell>{run.triggeredBy ?? '—'}</TableCell>
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

function matchRateTone(rate: number | undefined): 'default' | 'good' | 'warn' | 'bad' {
  if (rate === undefined) return 'default';
  if (rate >= 99.5) return 'good';
  if (rate >= 95) return 'warn';
  return 'bad';
}
