import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';

import { useLoginMutation } from '../../api/reconApi';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { ErrorPanel } from '../../components/Feedback';
import { selectIsAuthenticated, signedIn } from './authSlice';

/** The three demo identities, and what each one is allowed to do. */
const DEMO_USERS = [
  { username: 'operator', label: 'Ops Analyst', can: 'Investigate and propose' },
  { username: 'approver', label: 'Finance Approver', can: 'Approve or reject' },
  { username: 'admin', label: 'Administrator', can: 'Operate the job' },
] as const;

/**
 * Sign-in. Deliberately the only unauthenticated screen in the console.
 *
 * The demo identities are listed because segregation of duties is the thing worth demonstrating
 * here: signing in as `operator`, proposing a resolution, then signing in as `approver` to decide
 * it is the whole maker-checker story, and it is not discoverable without being told the names.
 */
export function LoginPage() {
  const authenticated = useAppSelector(selectIsAuthenticated);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [login, { isLoading, error }] = useLoginMutation();
  const [username, setUsername] = useState('operator');
  const [password, setPassword] = useState('operator');

  if (authenticated) return <Navigate to="/" replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    try {
      const response = await login({ username, password }).unwrap();
      dispatch(signedIn(response));
      navigate('/', { replace: true });
    } catch {
      // Rendered from the mutation's own error state below; nothing to add here.
    }
  }

  /** In the demo the password equals the username, so one click fills both fields. */
  function useDemoUser(name: string) {
    setUsername(name);
    setPassword(name);
  }

  return (
    <Box
      sx={{
        minHeight: '100dvh',
        display: 'grid',
        placeItems: 'center',
        bgcolor: 'background.default',
        p: 2,
      }}
    >
      <Card sx={{ width: '100%', maxWidth: 420 }}>
        <CardContent>
          <Typography variant="h5" gutterBottom>
            Reconciliation Console
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Sign in to review settlement breaks.
          </Typography>

          <Stack component="form" spacing={2} onSubmit={submit} noValidate>
            <TextField
              label="Username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              autoFocus
              required
              fullWidth
              slotProps={{ htmlInput: { 'aria-label': 'Username' } }}
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
              fullWidth
              slotProps={{ htmlInput: { 'aria-label': 'Password' } }}
            />
            {error && <ErrorPanel error={error} />}
            <Button type="submit" variant="contained" size="large" loading={isLoading} fullWidth>
              Sign in
            </Button>
          </Stack>

          <Divider sx={{ my: 2.5 }}>
            <Typography variant="caption" color="text.secondary">
              DEMO IDENTITIES
            </Typography>
          </Divider>
          <Stack spacing={1}>
            {DEMO_USERS.map((demo) => (
              <Stack key={demo.username} direction="row" spacing={1} sx={{ alignItems: "center" }}>
                <Chip
                  label={demo.username}
                  size="small"
                  onClick={() => useDemoUser(demo.username)}
                  sx={{ minWidth: 88, cursor: 'pointer' }}
                />
                <Typography variant="caption" color="text.secondary">
                  {demo.label} — {demo.can}
                </Typography>
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
