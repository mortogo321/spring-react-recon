import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import FormControlLabel from '@mui/material/FormControlLabel';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import { DataGrid, type GridColDef } from '@mui/x-data-grid';

import { useEvictMerchantCacheMutation, useMerchantsQuery } from '../../api/reconApi';
import { useAppSelector } from '../../app/hooks';
import { ErrorPanel } from '../../components/Feedback';
import { canOperateRuns, selectUser } from '../auth/authSlice';
import type { MerchantView } from '../../api/types';
import { MONOSPACE } from '../../theme';

/**
 * The merchant master, read straight from the legacy Oracle side through MyBatis and a
 * read-through cache.
 *
 * It earns a page for one reason: when a break says `M-1003`, the next question is always "who is
 * that, and what currency do they settle in" — a currency mismatch on a merchant whose master
 * record says USD is a configuration problem, not a reconciliation one. The link into the queue
 * turns that answer into the next action.
 */
export default function MerchantsPage() {
  const user = useAppSelector(selectUser);
  const [name, setName] = useState('');
  const [activeOnly, setActiveOnly] = useState(true);
  const { data, isLoading, isFetching, error, refetch } = useMerchantsQuery({
    ...(name ? { name } : {}),
    activeOnly,
    limit: 200,
  });
  const [evict, evictState] = useEvictMerchantCacheMutation();

  const columns = useMemo<GridColDef<MerchantView>[]>(
    () => [
      {
        field: 'merchantId',
        headerName: 'Merchant',
        width: 120,
        renderCell: (cell) => <Box sx={{ fontFamily: MONOSPACE }}>{cell.row.merchantId}</Box>,
      },
      { field: 'legalName', headerName: 'Legal name', flex: 1, minWidth: 220 },
      { field: 'mcc', headerName: 'MCC', width: 90, description: 'Merchant category code' },
      { field: 'settlementCurrency', headerName: 'Currency', width: 100 },
      { field: 'acquirerId', headerName: 'Acquirer', width: 110 },
      { field: 'onboardedOn', headerName: 'Onboarded', width: 120 },
      {
        field: 'active',
        headerName: 'Status',
        width: 100,
        renderCell: (cell) =>
          cell.row.active ? (
            <Chip size="small" color="success" variant="outlined" label="Active" />
          ) : (
            <Chip size="small" variant="outlined" label="Closed" />
          ),
      },
      {
        field: 'breaks',
        headerName: 'Breaks',
        width: 110,
        sortable: false,
        renderCell: (cell) => (
          <Link component={RouterLink} to={`/exceptions?merchantId=${cell.row.merchantId}`} variant="body2">
            Open queue
          </Link>
        ),
      },
    ],
    [],
  );

  if (error) return <ErrorPanel error={error} onRetry={refetch} />;

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h5">Merchants</Typography>
        <Typography variant="body2" color="text.secondary">
          Read from the legacy master through MyBatis, cached read-through.
        </Typography>
      </Box>

      <Card sx={{ p: 1.5 }}>
        <Stack direction="row" spacing={2} useFlexGap sx={{ alignItems: "center", flexWrap: "wrap" }}>
          <TextField
            label="Legal name contains"
            value={name}
            onChange={(event) => setName(event.target.value)}
            sx={{ minWidth: 260 }}
          />
          <FormControlLabel
            control={<Switch checked={activeOnly} onChange={(event) => setActiveOnly(event.target.checked)} />}
            label="Active only"
          />
          <Box sx={{ flexGrow: 1 }} />
          <Tooltip
            title={
              canOperateRuns(user)
                ? 'Drop the read-through cache after a master-data refresh on the legacy side'
                : 'Evicting the cache requires the administrator role'
            }
          >
            <span>
              <Button
                startIcon={<DeleteSweepIcon />}
                disabled={!canOperateRuns(user)}
                loading={evictState.isLoading}
                onClick={() => void evict().unwrap().catch(() => undefined)}
              >
                Evict cache
              </Button>
            </span>
          </Tooltip>
        </Stack>
        {evictState.error && (
          <Box sx={{ mt: 1.5 }}>
            <ErrorPanel error={evictState.error} />
          </Box>
        )}
      </Card>

      <Card>
        <DataGrid
          rows={data ?? []}
          columns={columns}
          getRowId={(row) => row.merchantId}
          loading={isLoading || isFetching}
          disableRowSelectionOnClick
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[25, 50, 100]}
          density="compact"
          sx={{ border: 0, minHeight: 400 }}
        />
      </Card>
    </Stack>
  );
}
