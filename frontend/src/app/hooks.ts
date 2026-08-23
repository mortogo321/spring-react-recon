import { useDispatch, useSelector } from 'react-redux';

import type { AppDispatch, RootState } from './store';

/**
 * Pre-typed hooks. Importing the raw react-redux hooks works, but every call site then has to
 * restate the state and dispatch types, and one of them eventually gets it wrong.
 */
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
