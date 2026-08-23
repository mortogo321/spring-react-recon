import { useState } from 'react';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';

import { useLaunchRunMutation, useToleranceProfilesQuery } from '../../api/reconApi';
import { ErrorPanel } from '../../components/Feedback';
import { todayIso } from '../../format';

interface LaunchRunDialogProps {
  open: boolean;
  onClose: () => void;
  onLaunched: (runId: number) => void;
}

/** What each profile means in money terms, so nobody picks "relaxed" by accident. */
const PROFILE_HINTS: Record<string, string> = {
  strict: 'No allowance at all — every satang has to agree.',
  default: 'Allows the greater of 0.50 or 10 bps. Covers ordinary fee rounding.',
  relaxed: 'Allows the greater of 50.00 or 100 bps. Use only for a known scheme issue.',
};

/**
 * Launching a run is the one destructive-ish action in the console: it reads the legacy Oracle feed
 * and writes a run plus its breaks. The dialog therefore states the two things that decide what
 * the job will do — which business date, and which tolerance profile — and nothing else.
 */
export function LaunchRunDialog({ open, onClose, onLaunched }: LaunchRunDialogProps) {
  const { data: profiles } = useToleranceProfilesQuery();
  const [launch, { isLoading, error, reset }] = useLaunchRunMutation();
  const [businessDate, setBusinessDate] = useState(todayIso());
  const [profile, setProfile] = useState('default');

  function close() {
    reset();
    onClose();
  }

  async function submit() {
    try {
      const response = await launch({ businessDate, toleranceProfile: profile }).unwrap();
      onLaunched(response.runId);
      close();
    } catch {
      // The dialog stays open showing the problem detail — a 409 from the idempotency filter or
      // a 400 for an unknown profile both need to be read, not dismissed.
    }
  }

  return (
    <Dialog open={open} onClose={close} maxWidth="xs" fullWidth>
      <DialogTitle>Launch reconciliation</DialogTitle>
      <DialogContent>
        <DialogContentText variant="body2" sx={{ mb: 2 }}>
          Reads the acquirer settlement feed for the chosen date and reconciles it against the
          ledger. Re-running a date that already completed is a no-op.
        </DialogContentText>
        <Stack spacing={2}>
          <TextField
            label="Business date"
            type="date"
            value={businessDate}
            onChange={(event) => setBusinessDate(event.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            fullWidth
          />
          <TextField
            select
            label="Tolerance profile"
            value={profile}
            onChange={(event) => setProfile(event.target.value)}
            helperText={PROFILE_HINTS[profile] ?? 'Configured server-side.'}
            fullWidth
          >
            {(profiles ?? ['default']).map((name) => (
              <MenuItem key={name} value={name}>
                {name}
              </MenuItem>
            ))}
          </TextField>
          {error && <ErrorPanel error={error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={close}>Cancel</Button>
        <Button onClick={submit} variant="contained" loading={isLoading}>
          Launch
        </Button>
      </DialogActions>
    </Dialog>
  );
}
