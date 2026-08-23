import { configureStore, combineSlices } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';

import { reconApi } from '../api/reconApi';
import authSlice from '../features/auth/authSlice';

const rootReducer = combineSlices(authSlice, reconApi);

export function makeStore(preloadedState?: Partial<RootState>) {
  const store = configureStore({
    reducer: rootReducer,
    middleware: (getDefault) => getDefault().concat(reconApi.middleware),
    ...(preloadedState ? { preloadedState } : {}),
  });
  // refetchOnReconnect needs the browser events wired up; without this the flag is inert.
  setupListeners(store.dispatch);
  return store;
}

export const store = makeStore();

export type RootState = ReturnType<typeof rootReducer>;
export type AppStore = ReturnType<typeof makeStore>;
export type AppDispatch = AppStore['dispatch'];
