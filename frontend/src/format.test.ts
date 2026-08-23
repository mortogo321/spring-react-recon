import { describe, expect, it } from 'vitest';

import { formatDuration, formatInstant, formatMoney, formatPercent, humanise, todayIso } from './format';

/**
 * These are the tests that stop a rounding bug reaching a screen an accountant reads. The
 * formatter takes a decimal *string* on purpose, so the cases below are mostly about proving it
 * never becomes a double on the way through.
 */
describe('formatMoney', () => {
  it('groups thousands and keeps exactly two decimal places', () => {
    expect(formatMoney({ amount: '104643.42', currency: 'THB' })).toBe('104,643.42 THB');
    expect(formatMoney({ amount: '1234567.80', currency: 'THB' })).toBe('1,234,567.80 THB');
    expect(formatMoney({ amount: '0.50', currency: 'THB' })).toBe('0.50 THB');
  });

  it('pads a short or missing fraction rather than showing a bare integer', () => {
    expect(formatMoney({ amount: '25', currency: 'USD' })).toBe('25.00 USD');
    expect(formatMoney({ amount: '25.4', currency: 'USD' })).toBe('25.40 USD');
  });

  it('keeps precision a double would destroy', () => {
    // 0.1 + 0.2 in binary floating point is 0.30000000000000004. The string path is immune.
    expect(formatMoney({ amount: '0.30', currency: 'THB' })).toBe('0.30 THB');
    // Beyond 2^53: any implementation that went through Number() would lose the last digits.
    expect(formatMoney({ amount: '12345678901234567.89', currency: 'THB' })).toBe(
      '12,345,678,901,234,567.89 THB',
    );
  });

  it('keeps the sign in front of the grouped amount', () => {
    expect(formatMoney({ amount: '-2205.00', currency: 'THB' })).toBe('-2,205.00 THB');
  });

  it('renders a dash for an absent amount, because a break can have only one side', () => {
    expect(formatMoney(undefined)).toBe('—');
  });

  it('can omit the currency for a column that already has it in the header', () => {
    expect(formatMoney({ amount: '10.00', currency: 'THB' }, { withCurrency: false })).toBe('10.00');
  });
});

describe('formatPercent', () => {
  it('shows two decimals so 86.73 and 86.734 are distinguishable', () => {
    expect(formatPercent(86.73)).toBe('86.73%');
    expect(formatPercent(100)).toBe('100.00%');
  });

  it('does not invent a zero for a run that has not computed a rate', () => {
    expect(formatPercent(undefined)).toBe('—');
  });
});

describe('formatDuration', () => {
  it('scales the unit to the magnitude', () => {
    expect(formatDuration('2026-08-20T10:00:00Z', '2026-08-20T10:00:00.196Z')).toBe('196 ms');
    expect(formatDuration('2026-08-20T10:00:00Z', '2026-08-20T10:00:04.500Z')).toBe('4.5 s');
    expect(formatDuration('2026-08-20T10:00:00Z', '2026-08-20T10:02:30Z')).toBe('2m 30s');
  });

  it('returns a dash while a run is still going rather than a negative number', () => {
    expect(formatDuration('2026-08-20T10:00:00Z', undefined)).toBe('—');
    expect(formatDuration('2026-08-20T10:00:05Z', '2026-08-20T10:00:00Z')).toBe('—');
  });
});

describe('formatInstant', () => {
  it('passes an unparseable value through instead of rendering "Invalid Date"', () => {
    expect(formatInstant('not-a-date')).toBe('not-a-date');
    expect(formatInstant(undefined)).toBe('—');
  });
});

describe('humanise', () => {
  it('turns the API enum vocabulary into something readable', () => {
    expect(humanise('MISSING_IN_LEDGER')).toBe('Missing In Ledger');
    expect(humanise('COMPLETED_WITH_BREAKS')).toBe('Completed With Breaks');
    expect(humanise('OPEN')).toBe('Open');
  });
});

describe('todayIso', () => {
  it('is a LocalDate the API can bind, in the browser timezone', () => {
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
