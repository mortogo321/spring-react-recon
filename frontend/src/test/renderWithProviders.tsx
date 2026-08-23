import type { ReactElement, ReactNode } from 'react';
import { Provider } from 'react-redux';
import { RouterProvider, createMemoryRouter } from 'react-router';
import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { render, type RenderOptions } from '@testing-library/react';

import { makeStore, type RootState } from '../app/store';
import { theme } from '../theme';
import type { UserInfo } from '../api/types';

interface Options extends Omit<RenderOptions, 'wrapper'> {
  /** Seeded auth state, so a test can render as an operator or as an approver. */
  user?: UserInfo;
  /** Initial URL — the exception queue reads its whole filter state from the query string. */
  route?: string;
  preloadedState?: Partial<RootState>;
}

/**
 * Renders a component inside the providers it actually needs: the real store with the real API
 * middleware, the real theme, and a memory router.
 *
 * Deliberately not a mocked store. The interesting behaviour of these components is what they do
 * with a query's lifecycle — loading, then data, then an invalidation — and a mocked store tests
 * none of it. `fetch` is what gets stubbed instead, at the boundary the app does not own.
 */
export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { user, route = '/', preloadedState, ...rest } = options;

  const authState = user
    ? { auth: { token: 'test-token', user, expiresAt: Date.now() + 3_600_000 } }
    : {};
  const store = makeStore({ ...authState, ...preloadedState } as Partial<RootState>);

  function Wrapper({ children }: { children: ReactNode }) {
    const router = createMemoryRouter([{ path: '*', element: <>{children}</> }], {
      initialEntries: [route],
    });
    return (
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <RouterProvider router={router} />
        </ThemeProvider>
      </Provider>
    );
  }

  return { store, ...render(ui, { wrapper: Wrapper, ...rest }) };
}

/** Minimal fetch stub: maps a URL fragment to the JSON the API would answer. */
export function stubFetch(routes: Record<string, unknown>, status = 200) {
  const calls: string[] = [];
  const impl = ((input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    calls.push(url);
    const match = Object.entries(routes).find(([fragment]) => url.includes(fragment));
    if (!match) {
      return Promise.resolve(
        new Response(JSON.stringify({ title: 'Not stubbed', detail: url }), {
          status: 404,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      );
    }
    return Promise.resolve(
      new Response(JSON.stringify(match[1]), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  }) as typeof fetch;
  globalThis.fetch = impl;
  return calls;
}
