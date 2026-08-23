import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate, Outlet, useRouteError } from 'react-router';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Container from '@mui/material/Container';
import Typography from '@mui/material/Typography';

import { AppShell } from './components/AppShell';
import { Loading } from './components/Feedback';
import { useAppSelector } from './app/hooks';
import { selectIsAuthenticated } from './features/auth/authSlice';
import { LoginPage } from './features/auth/LoginPage';

/**
 * The three heavy pages are split out. The data grid and the charts together are the largest part
 * of the bundle, and the login screen has no business downloading either of them.
 */
const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage'));
const RunsPage = lazy(() => import('./features/runs/RunsPage'));
const RunDetailPage = lazy(() => import('./features/runs/RunDetailPage'));
const ExceptionsPage = lazy(() => import('./features/exceptions/ExceptionsPage'));
const MerchantsPage = lazy(() => import('./features/merchants/MerchantsPage'));

function RequireAuth() {
  const authenticated = useAppSelector(selectIsAuthenticated);
  // replace, so that signing out does not leave the protected URL in the back stack.
  return authenticated ? <Outlet /> : <Navigate to="/login" replace />;
}

function RouteError() {
  const error = useRouteError();
  const message = error instanceof Error ? error.message : 'Unexpected error';
  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Alert severity="error">
        <AlertTitle>This page failed to render</AlertTitle>
        <Typography variant="body2">{message}</Typography>
      </Alert>
    </Container>
  );
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        errorElement: <RouteError />,
        children: [
          {
            element: (
              <Suspense fallback={<Loading label="Loading view" />}>
                <Outlet />
              </Suspense>
            ),
            children: [
              { index: true, element: <DashboardPage /> },
              { path: 'runs', element: <RunsPage /> },
              { path: 'runs/:runId', element: <RunDetailPage /> },
              { path: 'exceptions', element: <ExceptionsPage /> },
              { path: 'merchants', element: <MerchantsPage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
