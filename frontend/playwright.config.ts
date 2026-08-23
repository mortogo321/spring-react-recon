import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end against the real thing: a real browser, the real console bundle, the real API on the
 * local profile with its two in-memory databases. Nothing is stubbed, which is the point — these
 * specs are the only place where "an operator proposes and an approver decides" is proved across
 * the whole stack rather than in one layer of it.
 *
 * The API is expected to be already running (CI starts it as a step, so its logs stay separate and
 * a boot failure is not reported as a test failure). Set E2E_SKIP_WEB_SERVER=1 to point at a
 * console you are already running yourself.
 */
const API_ORIGIN = process.env.API_ORIGIN ?? 'http://localhost:8080';
const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:4173';

export default defineConfig({
  testDir: './e2e',
  // The specs share one API and one demo business date, so they are ordered and serial by design:
  // parallel workers would race each other's workflow transitions on the same break.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: BASE_URL,
    // On first failure only: enough to see what the page looked like without gigabytes of video.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  ...(process.env.E2E_SKIP_WEB_SERVER
    ? {}
    : {
        webServer: {
          // `preview` serves the built bundle, so the specs exercise the artefact that ships
          // rather than the dev server's transformed modules.
          command: 'bun run build && bun run preview --port 4173 --strictPort',
          url: BASE_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 180_000,
          env: { API_ORIGIN },
        },
      }),
});
