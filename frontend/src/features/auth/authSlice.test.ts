import { beforeEach, describe, expect, it } from 'vitest';

import authSlice, {
  canDecide,
  canInvestigate,
  canOperateRuns,
  canSubmit,
  hasRole,
  signedIn,
  signedOut,
} from './authSlice';
import type { LoginResponse, UserInfo } from '../../api/types';

const OPERATOR: UserInfo = { username: 'operator', displayName: 'Ops Analyst', roles: ['OPERATOR'] };
const APPROVER: UserInfo = { username: 'approver', displayName: 'Finance Approver', roles: ['APPROVER'] };
const ADMIN: UserInfo = {
  username: 'admin',
  displayName: 'Recon Administrator',
  roles: ['ADMIN', 'OPERATOR', 'APPROVER'],
};

function login(user: UserInfo, expiresIn = 3600): LoginResponse {
  return { accessToken: 'token-for-' + user.username, tokenType: 'Bearer', expiresIn, user };
}

describe('auth reducer', () => {
  beforeEach(() => window.sessionStorage.clear());

  it('stores the session and computes an absolute expiry from the relative one', () => {
    const before = Date.now();
    const state = authSlice.reducer(undefined, signedIn(login(OPERATOR)));
    expect(state.token).toBe('token-for-operator');
    expect(state.user).toEqual(OPERATOR);
    expect(state.expiresAt).toBeGreaterThanOrEqual(before + 3_600_000);
  });

  it('persists to sessionStorage so a refresh does not sign the analyst out mid-investigation', () => {
    authSlice.reducer(undefined, signedIn(login(OPERATOR)));
    expect(window.sessionStorage.getItem('recon.session')).toContain('token-for-operator');
  });

  it('clears both the state and the stored session on sign out', () => {
    const signedInState = authSlice.reducer(undefined, signedIn(login(ADMIN)));
    const state = authSlice.reducer(signedInState, signedOut());
    expect(state).toEqual({ token: null, user: null, expiresAt: null });
    expect(window.sessionStorage.getItem('recon.session')).toBeNull();
  });

  it('ignores a stored session that has already expired', () => {
    // Directly seeded: this is what a tab reopened an hour later actually finds.
    window.sessionStorage.setItem(
      'recon.session',
      JSON.stringify({ token: 'stale', user: OPERATOR, expiresAt: Date.now() - 1 }),
    );
    const state = authSlice.reducer(undefined, { type: '@@INIT' });
    expect(state.token).toBeNull();
  });

  it('ignores a stored session that is not valid JSON', () => {
    window.sessionStorage.setItem('recon.session', '{not json');
    const state = authSlice.reducer(undefined, { type: '@@INIT' });
    expect(state.token).toBeNull();
  });
});

/**
 * The role table exists so that the console's idea of who may do what has one definition. These
 * assertions are that definition, and they mirror the @PreAuthorize expressions on the API: the
 * point of the queue is that the person who proposes a write-off is not the person who approves it.
 */
describe('segregation of duties', () => {
  it('lets only an administrator operate the job', () => {
    expect(canOperateRuns(ADMIN)).toBe(true);
    expect(canOperateRuns(OPERATOR)).toBe(false);
    expect(canOperateRuns(APPROVER)).toBe(false);
  });

  it('lets the maker propose but not decide', () => {
    expect(canSubmit(OPERATOR)).toBe(true);
    expect(canDecide(OPERATOR)).toBe(false);
  });

  it('lets the checker decide but not propose', () => {
    expect(canDecide(APPROVER)).toBe(true);
    expect(canSubmit(APPROVER)).toBe(false);
  });

  it('lets either role pick a break up and work it', () => {
    expect(canInvestigate(OPERATOR)).toBe(true);
    expect(canInvestigate(APPROVER)).toBe(true);
  });

  it('grants nothing to a signed-out user', () => {
    expect(hasRole(null, 'ADMIN')).toBe(false);
    expect(canInvestigate(null)).toBe(false);
    expect(canSubmit(null)).toBe(false);
    expect(canDecide(null)).toBe(false);
    expect(canOperateRuns(null)).toBe(false);
  });
});
