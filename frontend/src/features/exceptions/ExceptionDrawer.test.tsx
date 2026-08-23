import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';

import { renderWithProviders, stubFetch } from '../../test/renderWithProviders';
import { ExceptionDrawer } from './ExceptionDrawer';
import type { ExceptionDetail, UserInfo } from '../../api/types';

const OPERATOR: UserInfo = { username: 'operator', displayName: 'Ops Analyst', roles: ['OPERATOR'] };
const APPROVER: UserInfo = { username: 'approver', displayName: 'Finance Approver', roles: ['APPROVER'] };

function detail(overrides: Partial<ExceptionDetail['exception']> = {}): ExceptionDetail {
  return {
    exception: {
      id: 10,
      runId: 1,
      merchantId: 'M-1003',
      externalRef: 'CM-0004',
      status: 'MISSING_IN_LEDGER',
      severity: 'CRITICAL',
      state: 'OPEN',
      settlementAmount: { amount: '5413.52', currency: 'THB' },
      exposure: { amount: '5413.52', currency: 'THB' },
      detail: 'Acquirer settled THB 5413.52 (txn TXN-CM-0004) with no ledger posting',
      updatedAt: '2026-08-20T18:30:18Z',
      version: 0,
      allowedTransitions: ['PENDING_APPROVAL', 'INVESTIGATING'],
      ...overrides,
    },
    comments: [],
  };
}

/**
 * The drawer is where segregation of duties becomes visible, so these tests assert on what each
 * role is offered rather than on markup. Every action shown here is one the API would also allow;
 * a button that appears for the wrong role is a bug worth failing a build over.
 */
describe('ExceptionDrawer', () => {
  it('shows the break with its exposure formatted from the decimal string', async () => {
    stubFetch({ '/api/exceptions/10': detail() });
    renderWithProviders(<ExceptionDrawer exceptionId={10} onClose={() => undefined} />, { user: OPERATOR });

    // Twice: once as the settled amount, once as the exposure. For a missing ledger posting the
    // whole settled value *is* the exposure, and showing both is what makes that legible.
    expect(await screen.findAllByText('5,413.52 THB')).toHaveLength(2);
    expect(screen.getByText(/no ledger posting/)).toBeInTheDocument();
    expect(screen.getByText('Missing In Ledger')).toBeInTheDocument();
    expect(screen.getByText('Critical')).toBeInTheDocument();
  });

  it('offers the maker steps to an operator', async () => {
    stubFetch({ '/api/exceptions/10': detail() });
    renderWithProviders(<ExceptionDrawer exceptionId={10} onClose={() => undefined} />, { user: OPERATOR });

    expect(await screen.findByRole('button', { name: /take ownership/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /submit for approval/i })).toBeInTheDocument();
  });

  it('does not offer an operator the submit step once the break is already awaiting a decision', async () => {
    stubFetch({
      '/api/exceptions/10': detail({ state: 'PENDING_APPROVAL', allowedTransitions: ['RESOLVED', 'WRITTEN_OFF', 'REJECTED'] }),
    });
    renderWithProviders(<ExceptionDrawer exceptionId={10} onClose={() => undefined} />, { user: OPERATOR });

    await screen.findByText('Pending Approval');
    expect(screen.queryByRole('button', { name: /submit for approval/i })).not.toBeInTheDocument();
    // The decision control is rendered so the operator can see what is pending, but is disabled.
    expect(screen.getByRole('button', { name: /approve resolution/i })).toBeDisabled();
  });

  it('lets an approver decide a submitted break, and says why they may not approve their own', async () => {
    stubFetch({
      '/api/exceptions/10': detail({
        state: 'PENDING_APPROVAL',
        submittedBy: 'operator',
        allowedTransitions: ['RESOLVED', 'WRITTEN_OFF', 'REJECTED'],
      }),
    });
    renderWithProviders(<ExceptionDrawer exceptionId={10} onClose={() => undefined} />, { user: APPROVER });

    await waitFor(() => expect(screen.getByRole('button', { name: /approve resolution/i })).toBeEnabled());
    expect(screen.getByText(/cannot approve their own work/i)).toBeInTheDocument();
    // An approver is not a maker: no submit step, whatever the state.
    expect(screen.queryByRole('button', { name: /submit for approval/i })).not.toBeInTheDocument();
  });

  it('renders the API problem detail when the break cannot be loaded', async () => {
    stubFetch({}, 500);
    renderWithProviders(<ExceptionDrawer exceptionId={99} onClose={() => undefined} />, { user: OPERATOR });

    expect(await screen.findByText(/not stubbed/i)).toBeInTheDocument();
  });
});
