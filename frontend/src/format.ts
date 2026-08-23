import type { Money } from './api/types';

/**
 * Money is formatted from its decimal string, never from a number.
 *
 * Intl.NumberFormat accepts a string only via the `formatNumber`-on-string path in newer engines,
 * so the amount is split and grouped by hand. That is deliberate: passing it through Number()
 * would silently round 12345678901234567.89, and a reconciliation console displaying a rounded
 * variance is worse than one displaying nothing.
 */
export function formatMoney(money: Money | undefined, options: { withCurrency?: boolean } = {}): string {
  if (!money) return '—';
  const negative = money.amount.startsWith('-');
  const digits = negative ? money.amount.slice(1) : money.amount;
  const [whole = '0', fraction = ''] = digits.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const padded = (fraction + '00').slice(0, 2);
  const sign = negative ? '-' : '';
  const suffix = options.withCurrency === false ? '' : ` ${money.currency}`;
  return `${sign}${grouped}.${padded}${suffix}`;
}

/** Percentages arrive as a number because they are a ratio, not an amount of money. */
export function formatPercent(value: number | undefined, fractionDigits = 2): string {
  if (value === undefined) return '—';
  return `${value.toFixed(fractionDigits)}%`;
}

export function formatInstant(iso: string | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date);
}

export function formatDuration(from: string | undefined, to: string | undefined): string {
  if (!from || !to) return '—';
  const ms = new Date(to).getTime() - new Date(from).getTime();
  if (!Number.isFinite(ms) || ms < 0) return '—';
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`;
  const minutes = Math.floor(ms / 60_000);
  const seconds = Math.round((ms % 60_000) / 1000);
  return `${minutes}m ${seconds}s`;
}

/** SCREAMING_SNAKE enums are the API's vocabulary, not something to show an analyst as-is. */
export function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/** Today in the browser's timezone, as the API's LocalDate wants it. */
export function todayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}
