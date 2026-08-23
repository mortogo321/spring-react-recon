import { useMemo, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import LinearProgress from '@mui/material/LinearProgress';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import RefreshIcon from '@mui/icons-material/Refresh';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';

import { useRunsQuery } from '../../api/reconApi';
import { useAppSelector } from '../../app/hooks';
import { ErrorPanel } from '../../components/Feedback';
import { RunStatusChip } from '../../components/StatusChip';
import { canOperateRuns, selectUser } from '../auth/authSlice';
import { formatDuration, formatMoney, formatInstant, formatPercent } from '../../format';
import { RUN_IN_FLIGHT, type RunView } from '../../api/types';
import { LaunchRunDialog } from './LaunchRunDialog';
import { MONOSPACE } from '../../theme';

/**
 * The operations view of the job. One row per run, newest first.
 *
 * The grid polls only while something is in flight. A console that polls a finished table every
 * two seconds costs a query per user per tick for no new information; one that never polls makes
 * an operator sit on F5 while a job runs. The condition below is the whole difference.
 */
export default function RunsPage() {
  const user = useAppSelector(selectUser);
  const navigate = useNavigate();
  const [launchOpen, setLaunchOpen] = useState(false);
  const { data, isLoading, isFetching, error, refetch } = useRunsQuery({ limit: 50 });

  const anyInFlight = useMemo(
    () => (data ?? []).some((run) => RUN_IN_FLIGHT.includes(run.status)),
    [data],
  );
  // A second hook call with the polling interval attached: RTK Query dedupes to one subscription
  // per cache key, so this shares the data above and only changes the poll behaviour.
  useRunsQuery({ limit: 50 }, { pollingInterval: anyInFlight ? 2_000 : 0 });

  const columns = useMemo<GridColDef<RunView>[]>(
    () => [
      {
        field: 'businessDate',
        headerName: 'Business date',
        width: 130,
        renderCell: (params) => (
          <Link component={RouterLink} to={`/runs/${params.row.id}`} sx={{ fontFamily: MONOSPACE }}>
            {params.value as string}
          </Link>
        ),
      },
      {
        field: 'status',
        headerName: 'Status',
        width: 190,
        renderCell: (params) => <RunStatusChip status={params.row.status} />,
      },
      { field: 'toleranceProfile', headerName: 'Profile', width: 96 },
      {
        field: 'matchRate',
        headerName: 'Match rate',
        width: 110,
        align: 'right',
        headerAlign: 'right',
        valueFormatter: (value: number | undefined) => formatPercent(value),
      },
      {
        field: 'settlementRows',
        headerName: 'Settled',
        width: 90,
        align: 'right',
        headerAlign: 'right',
      },
      { field: 'ledgerRows', headerName: 'Ledger', width: 90, align: 'right', headerAlign: 'right' },
      {
        field: 'excludedRows',
        headerName: 'Excluded',
        width: 96,
        align: 'right',
        headerAlign: 'right',
        description: 'Reversals and chargebacks, out of scope by design.',
      },
      {
        field: 'exceptionKeys',
        headerName: 'Breaks',
        width: 88,
        align: 'right',
        headerAlign: 'right',
      },
      {
        field: 'exposure',
        headerName: 'Exposure',
        width: 150,
        align: 'right',
        headerAlign: 'right',
        sortComparator: compareMoney,
        renderCell: (params) => (
          <Box sx={{ fontFamily: MONOSPACE }}>{formatMoney(params.row.exposure)}</Box>
        ),
      },
      {
        field: 'duration',
        headerName: 'Duration',
        width: 96,
        align: 'right',
        headerAlign: 'right',
        sortable: false,
        valueGetter: (_value, row) => formatDuration(row.startedAt, row.finishedAt),
      },
      {
        field: 'startedAt',
        headerName: 'Started',
        width: 190,
        valueFormatter: (value: string | undefined) => formatInstant(value),
      },
      { field: 'triggeredBy', headerName: 'By', width: 110 },
    ],
    [],
  );

  if (error) return <ErrorPanel error={error} onRetry={refetch} />;

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} sx={{ alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h5">Runs</Typography>
          <Typography variant="body2" color="text.secondary">
            Every reconciliation attempt, with the numbers it produced.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button startIcon={<RefreshIcon />} onClick={() => refetch()} loading={isFetching && !anyInFlight}>
            Refresh
          </Button>
          <Tooltip title={canOperateRuns(user) ? '' : 'Launching a run requires the administrator role'}>
            <span>
              <Button
                variant="contained"
                startIcon={<PlayArrowIcon />}
                disabled={!canOperateRuns(user)}
                onClick={() => setLaunchOpen(true)}
              >
                Launch run
              </Button>
            </span>
          </Tooltip>
        </Stack>
      </Stack>

      {anyInFlight && (
        <Alert severity="info" icon={false} sx={{ py: 0.5 }}>
          <Stack spacing={0.5}>
            <Typography variant="body2">A run is in progress — this table is refreshing itself.</Typography>
            <LinearProgress />
          </Stack>
        </Alert>
      )}

      <Card>
        <DataGrid
          rows={data ?? []}
          columns={columns}
          loading={isLoading}
          getRowId={(row) => row.id}
          disableRowSelectionOnClick
          onRowDoubleClick={(params) => navigate(`/runs/${params.id}`)}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[10, 25, 50]}
          density="compact"
          sx={{ border: 0, minHeight: 320 }}
        />
      </Card>

      <LaunchRunDialog
        open={launchOpen}
        onClose={() => setLaunchOpen(false)}
        onLaunched={(runId) => navigate(`/runs/${runId}`)}
      />
    </Stack>
  );
}

/**
 * Money sorts by value, not by string. "9.00 THB" would otherwise sort above "10,000.00 THB",
 * which on an exposure column is the kind of wrong that gets acted on.
 */
function compareMoney(a: unknown, b: unknown): number {
  const left = Number((a as { amount?: string } | undefined)?.amount ?? 0);
  const right = Number((b as { amount?: string } | undefined)?.amount ?? 0);
  return left - right;
}
