import { useCallback, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import AssignmentIndIcon from '@mui/icons-material/AssignmentInd';
import ClearIcon from '@mui/icons-material/Clear';
import {
  DataGrid,
  type GridColDef,
  type GridPaginationModel,
  type GridRowSelectionModel,
  type GridSortModel,
} from '@mui/x-data-grid';

import { useBulkAssignMutation, useExceptionsQuery } from '../../api/reconApi';
import { useAppSelector } from '../../app/hooks';
import { ErrorPanel } from '../../components/Feedback';
import { BreakStatusChip, SeverityChip, StateChip } from '../../components/StatusChip';
import { canInvestigate, selectUser } from '../auth/authSlice';
import { formatInstant, formatMoney, humanise } from '../../format';
import type {
  ExceptionQuery,
  ExceptionRow,
  ExceptionSortBy,
  ExceptionState,
  MatchSeverity,
  MatchStatus,
} from '../../api/types';
import { MONOSPACE } from '../../theme';
import { ExceptionDrawer } from './ExceptionDrawer';

const STATUSES: MatchStatus[] = [
  'AMOUNT_MISMATCH',
  'CURRENCY_MISMATCH',
  'DUPLICATE_SETTLEMENT',
  'MISSING_IN_LEDGER',
  'MISSING_IN_SETTLEMENT',
];
const SEVERITIES: MatchSeverity[] = ['CRITICAL', 'WARNING', 'INFO'];
const STATES: ExceptionState[] = ['OPEN', 'INVESTIGATING', 'PENDING_APPROVAL', 'RESOLVED', 'WRITTEN_OFF', 'REJECTED'];

/** The grid's column ids mapped onto the sort fields the API whitelists. */
const SORT_FIELDS: Record<string, ExceptionSortBy> = {
  exposure: 'exposure',
  merchantId: 'merchant',
  externalRef: 'ref',
  status: 'status',
  severity: 'severity',
  state: 'state',
  updatedAt: 'updated',
};

/**
 * The break queue. This is where the work happens, so it is server-driven throughout:
 * pagination, sorting and filtering are all query parameters, never a client-side filter over a
 * page of rows. A run with 40,000 breaks is an ordinary Tuesday and the grid must not try to hold
 * it.
 *
 * The filter state lives in the URL. That is not tidiness — it is what makes "look at this one" a
 * link an analyst can paste to a colleague, and what makes the back button behave.
 */
export default function ExceptionsPage() {
  const user = useAppSelector(selectUser);
  const [params, setParams] = useSearchParams();
  const [selection, setSelection] = useState<GridRowSelectionModel>({ type: 'include', ids: new Set() });
  const [openId, setOpenId] = useState<number | null>(null);

  const query = useMemo<ExceptionQuery>(() => {
    const runId = params.get('runId');
    const status = params.getAll('status') as MatchStatus[];
    const severity = params.getAll('severity') as MatchSeverity[];
    const state = params.getAll('state') as ExceptionState[];
    return {
      ...(runId ? { runId: Number(runId) } : {}),
      ...(status.length ? { status } : {}),
      ...(severity.length ? { severity } : {}),
      ...(state.length ? { state } : {}),
      ...(params.get('q') ? { q: params.get('q') as string } : {}),
      ...(params.get('merchantId') ? { merchantId: params.get('merchantId') as string } : {}),
      ...(params.get('minExposure') ? { minExposure: params.get('minExposure') as string } : {}),
      page: Number(params.get('page') ?? 0),
      size: Number(params.get('size') ?? 25),
      sortBy: (params.get('sortBy') as ExceptionSortBy | null) ?? 'exposure',
      sortDir: (params.get('sortDir') as 'asc' | 'desc' | null) ?? 'desc',
    };
  }, [params]);

  const { data, isLoading, isFetching, error, refetch } = useExceptionsQuery(query, {
    refetchOnMountOrArgChange: true,
  });
  const [bulkAssign, bulkState] = useBulkAssignMutation();

  const update = useCallback(
    (mutate: (next: URLSearchParams) => void) => {
      const next = new URLSearchParams(params);
      mutate(next);
      setParams(next, { replace: true });
    },
    [params, setParams],
  );

  const setSingle = useCallback(
    (key: string, value: string | undefined) => {
      update((next) => {
        if (value === undefined || value === '') next.delete(key);
        else next.set(key, value);
        // Any filter change invalidates the page cursor; staying on page 7 of a narrower result
        // set is how a grid ends up showing "no rows" over data that exists.
        next.delete('page');
      });
    },
    [update],
  );

  const setMulti = useCallback(
    (key: string, values: string[]) => {
      update((next) => {
        next.delete(key);
        for (const value of values) next.append(key, value);
        next.delete('page');
      });
    },
    [update],
  );

  const columns = useMemo<GridColDef<ExceptionRow>[]>(
    () => [
      { field: 'id', headerName: 'Id', width: 70 },
      {
        field: 'merchantId',
        headerName: 'Merchant',
        width: 110,
        renderCell: (cell) => <Box sx={{ fontFamily: MONOSPACE }}>{cell.row.merchantId}</Box>,
      },
      {
        field: 'externalRef',
        headerName: 'Reference',
        width: 120,
        renderCell: (cell) => <Box sx={{ fontFamily: MONOSPACE }}>{cell.row.externalRef}</Box>,
      },
      {
        field: 'status',
        headerName: 'Break',
        width: 190,
        renderCell: (cell) => <BreakStatusChip status={cell.row.status} />,
      },
      {
        field: 'severity',
        headerName: 'Severity',
        width: 110,
        renderCell: (cell) => <SeverityChip severity={cell.row.severity} />,
      },
      {
        field: 'state',
        headerName: 'State',
        width: 150,
        renderCell: (cell) => <StateChip state={cell.row.state} />,
      },
      {
        field: 'exposure',
        headerName: 'Exposure',
        width: 150,
        align: 'right',
        headerAlign: 'right',
        renderCell: (cell) => (
          <Box sx={{ fontFamily: MONOSPACE, fontWeight: 600 }}>{formatMoney(cell.row.exposure)}</Box>
        ),
      },
      {
        field: 'assignedTo',
        headerName: 'Owner',
        width: 110,
        valueGetter: (_value, row) => row.assignedTo ?? '—',
      },
      {
        field: 'updatedAt',
        headerName: 'Updated',
        width: 190,
        valueFormatter: (value: string) => formatInstant(value),
      },
      {
        field: 'detail',
        headerName: 'Detail',
        flex: 1,
        minWidth: 260,
        sortable: false,
      },
    ],
    [],
  );

  const selectedIds = useMemo(
    () => (selection.type === 'include' ? [...selection.ids].map(Number) : []),
    [selection],
  );

  const activeFilters =
    (query.status?.length ?? 0) +
    (query.severity?.length ?? 0) +
    (query.state?.length ?? 0) +
    (query.q ? 1 : 0) +
    (query.runId ? 1 : 0) +
    (query.minExposure ? 1 : 0);

  if (error) return <ErrorPanel error={error} onRetry={refetch} />;

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h5">Exception queue</Typography>
        <Typography variant="body2" color="text.secondary">
          {data
            ? `${data.totalElements.toLocaleString()} break${data.totalElements === 1 ? '' : 's'} match the current filters`
            : 'Loading breaks'}
        </Typography>
      </Box>

      <Card sx={{ p: 1.5 }}>
        <Stack direction="row" spacing={1.5} useFlexGap sx={{ flexWrap: "wrap", alignItems: "center" }}>
          <TextField
            label="Search"
            placeholder="merchant, reference or detail"
            value={query.q ?? ''}
            onChange={(event) => setSingle('q', event.target.value)}
            sx={{ minWidth: 240 }}
          />
          <TextField
            label="Run"
            type="number"
            value={query.runId ?? ''}
            onChange={(event) => setSingle('runId', event.target.value)}
            sx={{ width: 110 }}
          />
          <TextField
            select
            label="Break class"
            value={query.status ?? []}
            slotProps={{ select: { multiple: true, renderValue: (v) => renderChips(v as string[]) } }}
            onChange={(event) => setMulti('status', toArray(event.target.value))}
            sx={{ minWidth: 200 }}
          >
            {STATUSES.map((status) => (
              <MenuItem key={status} value={status}>
                {humanise(status)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Severity"
            value={query.severity ?? []}
            slotProps={{ select: { multiple: true, renderValue: (v) => renderChips(v as string[]) } }}
            onChange={(event) => setMulti('severity', toArray(event.target.value))}
            sx={{ minWidth: 160 }}
          >
            {SEVERITIES.map((severity) => (
              <MenuItem key={severity} value={severity}>
                {humanise(severity)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="State"
            value={query.state ?? []}
            slotProps={{ select: { multiple: true, renderValue: (v) => renderChips(v as string[]) } }}
            onChange={(event) => setMulti('state', toArray(event.target.value))}
            sx={{ minWidth: 180 }}
          >
            {STATES.map((state) => (
              <MenuItem key={state} value={state}>
                {humanise(state)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Min exposure"
            type="number"
            value={query.minExposure ?? ''}
            onChange={(event) => setSingle('minExposure', event.target.value)}
            sx={{ width: 130 }}
          />
          {activeFilters > 0 && (
            <Button startIcon={<ClearIcon />} onClick={() => setParams(new URLSearchParams(), { replace: true })}>
              Clear
            </Button>
          )}
          <Box sx={{ flexGrow: 1 }} />
          <Tooltip
            title={
              canInvestigate(user)
                ? 'Assign every selected break to yourself in one statement'
                : 'Assigning requires the operator or approver role'
            }
          >
            <span>
              <Button
                variant="outlined"
                startIcon={<AssignmentIndIcon />}
                disabled={selectedIds.length === 0 || !canInvestigate(user)}
                loading={bulkState.isLoading}
                onClick={() =>
                  bulkAssign({ ids: selectedIds, assignee: user?.username ?? '' })
                    .unwrap()
                    .then(() => setSelection({ type: 'include', ids: new Set() }))
                    .catch(() => undefined)
                }
              >
                Assign {selectedIds.length > 0 ? `(${selectedIds.length})` : ''}
              </Button>
            </span>
          </Tooltip>
        </Stack>
        {bulkState.error && (
          <Box sx={{ mt: 1.5 }}>
            <ErrorPanel error={bulkState.error} />
          </Box>
        )}
      </Card>

      <Card>
        <DataGrid
          rows={data?.items ?? []}
          columns={columns}
          getRowId={(row) => row.id}
          loading={isLoading || isFetching}
          checkboxSelection
          disableRowSelectionOnClick
          rowSelectionModel={selection}
          onRowSelectionModelChange={setSelection}
          onRowClick={(clicked) => setOpenId(Number(clicked.id))}
          /* Server-side everything: the grid is a view over a query, not a copy of the table. */
          paginationMode="server"
          sortingMode="server"
          filterMode="server"
          rowCount={data?.totalElements ?? 0}
          paginationModel={{ page: query.page, pageSize: query.size }}
          onPaginationModelChange={(model: GridPaginationModel) =>
            update((next) => {
              next.set('page', String(model.page));
              next.set('size', String(model.pageSize));
            })
          }
          sortModel={[{ field: fieldOf(query.sortBy), sort: query.sortDir ?? 'desc' }]}
          onSortModelChange={(model: GridSortModel) => {
            const first = model[0];
            update((next) => {
              if (!first) {
                next.delete('sortBy');
                next.delete('sortDir');
                return;
              }
              next.set('sortBy', SORT_FIELDS[first.field] ?? 'exposure');
              next.set('sortDir', first.sort === 'asc' ? 'asc' : 'desc');
            });
          }}
          pageSizeOptions={[25, 50, 100]}
          density="compact"
          sx={{ border: 0, minHeight: 420, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Card>

      <ExceptionDrawer exceptionId={openId} onClose={() => setOpenId(null)} />
    </Stack>
  );
}

function renderChips(values: string[]) {
  if (values.length === 0) return <em>any</em>;
  return (
    <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
      {values.map((value) => (
        <Chip key={value} size="small" label={humanise(value)} />
      ))}
    </Stack>
  );
}

function toArray(value: unknown): string[] {
  return Array.isArray(value) ? (value as string[]) : typeof value === 'string' && value ? [value] : [];
}

/** Inverse of SORT_FIELDS, so the grid's sort arrow lands on the column the API is sorting by. */
function fieldOf(sortBy: ExceptionSortBy | undefined): string {
  const entry = Object.entries(SORT_FIELDS).find(([, api]) => api === (sortBy ?? 'exposure'));
  return entry?.[0] ?? 'exposure';
}
