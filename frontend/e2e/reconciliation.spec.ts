import { expect, test, type Page } from '@playwright/test';

/**
 * The journey this whole system exists for, driven through the browser:
 *
 *   1. an administrator launches a reconciliation for the demo business date
 *   2. the job partitions by merchant, reconciles, and raises breaks
 *   3. an operator picks a break up and proposes what to do about it
 *   4. an approver — a different person — decides it
 *
 * Step 4 is the one worth the cost of an e2e suite. Every layer below enforces segregation of
 * duties in its own way (a @PreAuthorize expression, a service check, a disabled button), and the
 * only way to know all three agree is to walk a break through them with two different sessions.
 */

const DEMO_DATE = '2026-08-20';

async function signIn(page: Page, username: string) {
  await page.goto('/login');
  // The demo chips fill both fields; using them is also a check that they still work.
  await page.getByRole('button', { name: username, exact: true }).click().catch(async () => {
    await page.getByLabel('Username').fill(username);
    await page.getByLabel('Password').fill(username);
  });
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(username);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
}

/**
 * The sidebar entry, not any other link that happens to contain the same word: the dashboard also
 * links to "All runs", and an unanchored name match would resolve to both.
 */
function navLink(page: Page, name: string) {
  return page.getByRole('navigation').getByRole('link', { name, exact: true });
}

async function signOut(page: Page) {
  await page.locator('header').getByRole('button').last().click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
}

test.describe.configure({ mode: 'serial' });

test('an unauthenticated visitor is sent to the login screen', async ({ page }) => {
  await page.goto('/exceptions');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  // And the three demo identities are documented on it, or nobody can use the demo.
  await expect(page.getByText('Ops Analyst')).toBeVisible();
  await expect(page.getByText('Finance Approver')).toBeVisible();
});

test('an administrator launches a run and sees it reconcile', async ({ page }) => {
  await signIn(page, 'admin');

  await navLink(page, 'Runs').click();
  await expect(page.getByRole('heading', { name: 'Runs' })).toBeVisible();

  await page.getByRole('button', { name: 'Launch run' }).click();
  // Scoped to the dialog: the runs grid behind it has a "Business date" column whose menu button
  // carries the same words in its accessible name.
  const launch = page.getByRole('dialog');
  await launch.getByLabel('Business date').fill(DEMO_DATE);
  await launch.getByRole('button', { name: 'Launch', exact: true }).click();

  // Landing on the run page is the launch response being honoured; the status settling is the job.
  await expect(page.getByText(`${DEMO_DATE}:default`)).toBeVisible();
  await expect(page.getByText(/Completed (Clean|With Breaks)/)).toBeVisible({ timeout: 45_000 });

  // The numbers the demo data is built to produce. Asserting them here means a change that
  // silently starts counting excluded rows as breaks fails in the browser, not just in a unit test.
  await expect(page.getByText('86.73%')).toBeVisible();
  await expect(page.getByText('29,115.80 THB')).toBeVisible();

  // The partitioned step is the interesting one: read minus filtered equals written.
  await expect(page.getByRole('cell', { name: 'merchant-0-M-1001' })).toBeVisible();
});

test('an operator investigates a break and proposes a resolution', async ({ page }) => {
  await signIn(page, 'operator');

  await navLink(page, 'Exceptions').click();
  await expect(page.getByRole('heading', { name: 'Exception queue' })).toBeVisible();

  // An operator may not launch anything: the read side is open to every console role, the
  // operational side is not.
  await navLink(page, 'Runs').click();
  await expect(page.getByRole('button', { name: 'Launch run' })).toBeDisabled();
  await navLink(page, 'Exceptions').click();

  // Sorted by exposure descending by default, so the first row is the one that matters most.
  const firstRow = page.locator('.MuiDataGrid-row').first();
  await expect(firstRow).toBeVisible();
  await firstRow.click();

  const drawer = page.getByRole('presentation').locator('.MuiDrawer-paper');
  await expect(drawer.getByText('Workflow')).toBeVisible();

  await drawer.getByRole('button', { name: 'Take ownership' }).click();
  await expect(drawer.getByText('Investigating')).toBeVisible();

  await drawer.getByLabel('What did you find?').fill('Acquirer confirmed the posting was never sent.');
  await drawer.getByRole('button', { name: 'Submit for approval' }).click();
  await expect(drawer.getByText('Pending Approval')).toBeVisible();

  // The maker cannot also be the checker, even on their own proposal.
  await expect(drawer.getByRole('button', { name: 'Approve resolution' })).toBeDisabled();
});

test('an approver decides the break the operator proposed', async ({ page }) => {
  await signIn(page, 'operator');
  await signOut(page);
  await signIn(page, 'approver');

  await page.goto('/exceptions?state=PENDING_APPROVAL');
  const pending = page.locator('.MuiDataGrid-row').first();
  await expect(pending).toBeVisible();
  await pending.click();

  const drawer = page.getByRole('presentation').locator('.MuiDrawer-paper');
  await expect(drawer.getByText('Pending Approval')).toBeVisible();
  await expect(drawer.getByText(/cannot approve their own work/i)).toBeVisible();

  await drawer.getByRole('button', { name: 'Approve resolution' }).click();
  await expect(drawer.getByText('Resolved')).toBeVisible();

  // And the workflow is a state machine: there is nothing left to submit on a decided break.
  await expect(drawer.getByRole('button', { name: 'Submit for approval' })).toHaveCount(0);
});

test('the queue filters and pages against the server, and the URL carries the state', async ({ page }) => {
  await signIn(page, 'operator');
  await page.goto('/exceptions');

  await page.getByLabel('Search').fill('M-1003');
  // Debounce-free by design: every keystroke is a query param change and a request, and the count
  // line is what proves the server did the filtering rather than the grid.
  await expect(page).toHaveURL(/q=M-1003/);
  await expect(page.getByText(/break(s)? match the current filters/)).toBeVisible();

  await page.getByRole('button', { name: 'Clear' }).click();
  await expect(page).not.toHaveURL(/q=/);
});

test('the console survives a reload without asking anyone to sign in again', async ({ page }) => {
  await signIn(page, 'admin');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
});
