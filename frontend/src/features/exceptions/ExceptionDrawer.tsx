import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import CloseIcon from '@mui/icons-material/Close';

import {
  useAssignExceptionMutation,
  useCommentOnExceptionMutation,
  useDecideExceptionMutation,
  useExceptionQuery,
  useSubmitExceptionMutation,
} from '../../api/reconApi';
import { useAppSelector } from '../../app/hooks';
import { CardSkeleton, ErrorPanel, Loading } from '../../components/Feedback';
import { BreakStatusChip, SeverityChip, StateChip } from '../../components/StatusChip';
import { canDecide, canInvestigate, canSubmit, selectUser } from '../auth/authSlice';
import { formatInstant, formatMoney, humanise } from '../../format';
import { DECISION_STATES, type ExceptionState } from '../../api/types';
import { MONOSPACE } from '../../theme';

interface ExceptionDrawerProps {
  exceptionId: number | null;
  onClose: () => void;
}

const DECISION_LABELS: Partial<Record<ExceptionState, string>> = {
  RESOLVED: 'Approve resolution',
  WRITTEN_OFF: 'Write off',
  REJECTED: 'Send back',
};

/**
 * One break, and the maker-checker workflow over it.
 *
 * The buttons are driven by `allowedTransitions`, which the server computes from the state machine
 * — not by a copy of that machine kept here. Two things follow: the console cannot offer an illegal
 * transition even if the enum grows a state, and the one rule it deliberately does not model is
 * self-approval. The console has no way to know whether the current user submitted this break, so
 * the approve button stays enabled and a 403 with its explanation is shown if they try. Better a
 * clear refusal from the authority that owns the rule than a greyed-out button with no reason.
 */
export function ExceptionDrawer({ exceptionId, onClose }: ExceptionDrawerProps) {
  const user = useAppSelector(selectUser);
  const open = exceptionId !== null;
  const { data, isLoading, error, refetch } = useExceptionQuery(exceptionId ?? 0, { skip: !open });

  const [assign, assignState] = useAssignExceptionMutation();
  const [comment, commentState] = useCommentOnExceptionMutation();
  const [submit, submitState] = useSubmitExceptionMutation();
  const [decide, decideState] = useDecideExceptionMutation();

  const [note, setNote] = useState('');
  const [commentBody, setCommentBody] = useState('');
  const [decision, setDecision] = useState<ExceptionState>('RESOLVED');

  const busy =
    assignState.isLoading || commentState.isLoading || submitState.isLoading || decideState.isLoading;
  const actionError = assignState.error ?? submitState.error ?? decideState.error ?? commentState.error;

  const row = data?.exception;
  const allowed = new Set(row?.allowedTransitions ?? []);

  async function run(action: () => Promise<unknown>, clear?: () => void) {
    try {
      await action();
      clear?.();
    } catch {
      // Shown in the error panel below the actions.
    }
  }

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      slotProps={{ paper: { sx: { width: { xs: '100%', sm: 480 }, p: 2.5 } } }}
    >
      <Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between", mb: 1 }}>
        <Typography variant="h6">Break {exceptionId}</Typography>
        <IconButton onClick={onClose} aria-label="Close break detail">
          <CloseIcon />
        </IconButton>
      </Stack>

      {isLoading && <CardSkeleton height={280} />}
      {error && <ErrorPanel error={error} onRetry={refetch} />}

      {row && (
        <Stack spacing={2} sx={{ overflowY: 'auto' }}>
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
            <BreakStatusChip status={row.status} />
            <SeverityChip severity={row.severity} />
            <StateChip state={row.state} />
          </Stack>

          <Typography variant="body2">{row.detail}</Typography>

          <Box>
            <Field label="Merchant" value={row.merchantId} mono />
            <Field label="Reference" value={row.externalRef} mono />
            <Field label="Settlement" value={formatMoney(row.settlementAmount)} mono />
            <Field label="Ledger" value={formatMoney(row.ledgerAmount)} mono />
            <Field label="Exposure" value={formatMoney(row.exposure)} mono emphasis />
            <Field label="Assigned to" value={row.assignedTo ?? 'nobody'} />
            {row.submittedBy && <Field label="Submitted by" value={row.submittedBy} />}
            {data.resolutionNote && <Field label="Proposed" value={data.resolutionNote} />}
            {data.decidedBy && (
              <Field label="Decided by" value={`${data.decidedBy} · ${formatInstant(data.decidedAt)}`} />
            )}
            <Field label="Last updated" value={formatInstant(row.updatedAt)} />
          </Box>

          <Divider />

          {/* --- maker-checker actions ------------------------------------------------- */}
          <Typography variant="subtitle2">Workflow</Typography>

          {allowed.has('INVESTIGATING') && canInvestigate(user) && (
            <Button
              variant="outlined"
              disabled={busy}
              loading={assignState.isLoading}
              onClick={() => run(() => assign({ id: row.id, assignee: user?.username ?? '' }).unwrap())}
            >
              Take ownership
            </Button>
          )}

          {allowed.has('PENDING_APPROVAL') && canSubmit(user) && (
            <Stack spacing={1}>
              <TextField
                label="What did you find?"
                value={note}
                onChange={(event) => setNote(event.target.value)}
                multiline
                minRows={2}
                fullWidth
                required
                helperText="Recorded against the break and visible to the approver."
              />
              <Button
                variant="contained"
                disabled={busy || note.trim().length === 0}
                loading={submitState.isLoading}
                onClick={() => run(() => submit({ id: row.id, note }).unwrap(), () => setNote(''))}
              >
                Submit for approval
              </Button>
            </Stack>
          )}

          {row.state === 'PENDING_APPROVAL' && (
            <Stack spacing={1}>
              <Tooltip
                title={canDecide(user) ? '' : 'Deciding a break requires the approver role'}
                placement="top"
              >
                <TextField
                  select
                  label="Decision"
                  value={decision}
                  onChange={(event) => setDecision(event.target.value as ExceptionState)}
                  disabled={!canDecide(user)}
                  fullWidth
                >
                  {DECISION_STATES.filter((state) => allowed.has(state)).map((state) => (
                    <MenuItem key={state} value={state}>
                      {DECISION_LABELS[state] ?? humanise(state)}
                    </MenuItem>
                  ))}
                </TextField>
              </Tooltip>
              <TextField
                label="Note (optional)"
                value={commentBody}
                onChange={(event) => setCommentBody(event.target.value)}
                disabled={!canDecide(user)}
                fullWidth
              />
              <Button
                variant="contained"
                color={decision === 'REJECTED' ? 'warning' : 'primary'}
                disabled={busy || !canDecide(user)}
                loading={decideState.isLoading}
                onClick={() =>
                  run(
                    () =>
                      decide({
                        id: row.id,
                        decision,
                        ...(commentBody.trim() ? { note: commentBody } : {}),
                      }).unwrap(),
                    () => setCommentBody(''),
                  )
                }
              >
                {DECISION_LABELS[decision] ?? humanise(decision)}
              </Button>
              <Typography variant="caption" color="text.secondary">
                The submitter cannot approve their own work, whatever roles they hold.
              </Typography>
            </Stack>
          )}

          {actionError && <ErrorPanel error={actionError} />}

          <Divider />

          {/* --- audit trail ------------------------------------------------------------ */}
          <Typography variant="subtitle2">Investigation trail</Typography>
          <Stack spacing={1}>
            <CommentComposer
              disabled={busy}
              loading={commentState.isLoading}
              onSubmit={(body) => run(() => comment({ id: row.id, body }).unwrap())}
            />
            {data.comments.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                Nothing recorded yet.
              </Typography>
            ) : (
              data.comments
                .slice()
                .reverse()
                .map((entry) => (
                  <Box key={entry.id} sx={{ borderLeft: 2, borderColor: 'divider', pl: 1.5, py: 0.5 }}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                      <Chip size="small" label={entry.author} />
                      <Typography variant="caption" color="text.secondary">
                        {formatInstant(entry.createdAt)}
                      </Typography>
                    </Stack>
                    <Typography variant="body2" sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>
                      {entry.body}
                    </Typography>
                  </Box>
                ))
            )}
          </Stack>

          {busy && <Loading label="Saving" />}
          {!canInvestigate(user) && !canDecide(user) && (
            <Alert severity="info">Your roles allow you to read this queue, not to work it.</Alert>
          )}
        </Stack>
      )}
    </Drawer>
  );
}

function Field({
  label,
  value,
  mono,
  emphasis,
}: {
  label: string;
  value: string;
  mono?: boolean;
  emphasis?: boolean;
}) {
  return (
    <Stack direction="row" spacing={1} sx={{ justifyContent: "space-between", py: 0.35 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography
        variant="body2"
        sx={{
          fontFamily: mono ? MONOSPACE : undefined,
          fontWeight: emphasis ? 700 : undefined,
          textAlign: 'right',
        }}
      >
        {value}
      </Typography>
    </Stack>
  );
}

/** Kept local and self-contained so typing a note does not re-render the whole drawer. */
function CommentComposer({
  disabled,
  loading,
  onSubmit,
}: {
  disabled: boolean;
  loading: boolean;
  onSubmit: (body: string) => void;
}) {
  const [body, setBody] = useState('');
  return (
    <Stack direction="row" spacing={1}>
      <TextField
        label="Add a note"
        value={body}
        onChange={(event) => setBody(event.target.value)}
        fullWidth
        size="small"
      />
      <Button
        variant="outlined"
        disabled={disabled || body.trim().length === 0}
        loading={loading}
        onClick={() => {
          onSubmit(body);
          setBody('');
        }}
      >
        Add
      </Button>
    </Stack>
  );
}
