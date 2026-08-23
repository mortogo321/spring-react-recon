import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';

import { humanise } from '../format';
import type { ExceptionState, MatchSeverity, MatchStatus, RunStatus } from '../api/types';

type ChipColour = 'default' | 'primary' | 'secondary' | 'success' | 'error' | 'info' | 'warning';

/**
 * Colour is a second channel, never the only one: every chip carries its label, so the screen is
 * still readable in greyscale and to a colour-blind user. The mapping is centralised because the
 * same status appears on the dashboard, the run page and the grid, and three different opinions
 * about what "amber" means is how a console starts lying.
 */
const RUN_COLOURS: Record<RunStatus, ChipColour> = {
  PENDING: 'default',
  RUNNING: 'info',
  COMPLETED_CLEAN: 'success',
  COMPLETED_WITH_BREAKS: 'warning',
  FAILED: 'error',
  STOPPED: 'warning',
  ABANDONED: 'default',
};

const SEVERITY_COLOURS: Record<MatchSeverity, ChipColour> = {
  INFO: 'default',
  WARNING: 'warning',
  CRITICAL: 'error',
};

const STATE_COLOURS: Record<ExceptionState, ChipColour> = {
  OPEN: 'error',
  INVESTIGATING: 'info',
  PENDING_APPROVAL: 'warning',
  RESOLVED: 'success',
  WRITTEN_OFF: 'secondary',
  REJECTED: 'default',
};

/** What each break class actually means, in the words an analyst would use to explain it. */
const STATUS_EXPLANATIONS: Record<MatchStatus, string> = {
  MATCHED: 'Settlement and ledger agree exactly.',
  MATCHED_WITHIN_TOLERANCE: 'Amounts differ by less than the profile allowance — treated as matched.',
  AMOUNT_MISMATCH: 'Both sides posted, amounts differ by more than the allowance.',
  CURRENCY_MISMATCH: 'Same transaction settled in a different currency from the ledger posting.',
  DUPLICATE_SETTLEMENT: 'The acquirer delivered the same transaction twice.',
  MISSING_IN_LEDGER: 'Money settled with no corresponding ledger posting — the exposure is real.',
  MISSING_IN_SETTLEMENT: 'The ledger posted, the acquirer has not settled it yet.',
  EXCLUDED: 'Out of scope for this run: a reversal or chargeback.',
};

export function RunStatusChip({ status }: { status: RunStatus }) {
  return <Chip size="small" variant="filled" color={RUN_COLOURS[status]} label={humanise(status)} />;
}

export function SeverityChip({ severity }: { severity: MatchSeverity }) {
  return <Chip size="small" variant="outlined" color={SEVERITY_COLOURS[severity]} label={humanise(severity)} />;
}

export function StateChip({ state }: { state: ExceptionState }) {
  return <Chip size="small" variant="filled" color={STATE_COLOURS[state]} label={humanise(state)} />;
}

export function BreakStatusChip({ status }: { status: MatchStatus }) {
  return (
    <Tooltip title={STATUS_EXPLANATIONS[status]} enterDelay={400}>
      <Chip size="small" variant="outlined" label={humanise(status)} />
    </Tooltip>
  );
}
