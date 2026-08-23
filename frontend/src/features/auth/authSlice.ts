import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import type { LoginResponse, ReconRole, UserInfo } from '../../api/types';

/**
 * Where the token lives, and why.
 *
 * sessionStorage, not localStorage: closing the tab ends the session, which is the behaviour a
 * finance operations team expects from a shared workstation. Not a cookie, because the API is a
 * stateless resource server with no CSRF machinery — a bearer token in a header cannot be sent by
 * a third-party page, which is the property that matters here.
 *
 * This is still a token readable by any script on the origin. The honest mitigation is that the
 * console serves no user-authored HTML and loads no third-party script, and the token expires in
 * an hour. A deployment with a real identity provider would move to a BFF holding an httpOnly
 * refresh cookie; that is a deployment decision, not a change to this reducer.
 */
const STORAGE_KEY = 'recon.session';

export interface AuthState {
  token: string | null;
  user: UserInfo | null;
  /** Epoch millis. Kept so the UI can warn before the API starts answering 401. */
  expiresAt: number | null;
}

interface StoredSession {
  token: string;
  user: UserInfo;
  expiresAt: number;
}

function readStoredSession(): AuthState {
  const empty: AuthState = { token: null, user: null, expiresAt: null };
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return empty;
    const parsed = JSON.parse(raw) as StoredSession;
    // An expired token would otherwise render the whole shell and then 401 every panel in it.
    if (!parsed.token || parsed.expiresAt <= Date.now()) return empty;
    return { token: parsed.token, user: parsed.user, expiresAt: parsed.expiresAt };
  } catch {
    // Private browsing, a storage quota error, or a payload from an older version of this app.
    return empty;
  }
}

function persist(state: AuthState): void {
  try {
    if (state.token && state.user && state.expiresAt) {
      const session: StoredSession = { token: state.token, user: state.user, expiresAt: state.expiresAt };
      window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      window.sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Losing persistence degrades to "log in again after a refresh", which is survivable.
  }
}

const authSlice = createSlice({
  name: 'auth',
  initialState: readStoredSession,
  reducers: {
    signedIn(state, action: PayloadAction<LoginResponse>) {
      state.token = action.payload.accessToken;
      state.user = action.payload.user;
      state.expiresAt = Date.now() + action.payload.expiresIn * 1000;
      persist(state);
    },
    /** Also dispatched by the base query on any 401, so an expired token cannot get stuck. */
    signedOut(state) {
      state.token = null;
      state.user = null;
      state.expiresAt = null;
      persist(state);
    },
  },
  selectors: {
    selectToken: (state) => state.token,
    selectUser: (state) => state.user,
    selectIsAuthenticated: (state) => state.token !== null,
  },
});

export const { signedIn, signedOut } = authSlice.actions;
export const { selectToken, selectUser, selectIsAuthenticated } = authSlice.selectors;
export default authSlice;

/**
 * Role checks live here rather than in components so that the console's idea of who may do what
 * has exactly one definition, and matches the @PreAuthorize expressions one for one:
 *
 *   launch/stop/restart a run   ADMIN
 *   assign / comment            OPERATOR or APPROVER
 *   submit for approval         OPERATOR   (the maker)
 *   approve / reject / write off APPROVER   (the checker)
 *
 * Hiding a button the API would refuse is a courtesy, not a control: the server decides. Which is
 * why self-approval is not represented here at all — the console cannot know whether the current
 * user is the submitter of a break it has not loaded, so it lets the API answer 403 and shows it.
 */
export function hasRole(user: UserInfo | null, role: ReconRole): boolean {
  return user?.roles.includes(role) ?? false;
}

export function canOperateRuns(user: UserInfo | null): boolean {
  return hasRole(user, 'ADMIN');
}

export function canInvestigate(user: UserInfo | null): boolean {
  return hasRole(user, 'OPERATOR') || hasRole(user, 'APPROVER');
}

export function canSubmit(user: UserInfo | null): boolean {
  return hasRole(user, 'OPERATOR');
}

export function canDecide(user: UserInfo | null): boolean {
  return hasRole(user, 'APPROVER');
}
