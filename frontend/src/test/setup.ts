import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom implements neither of these, and MUI's responsive components and the data grid both ask.
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
}

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}

// crypto.randomUUID exists in Node 20+, but jsdom does not always expose it on window.
if (!globalThis.crypto?.randomUUID) {
  Object.defineProperty(globalThis.crypto ?? (globalThis.crypto = {} as Crypto), 'randomUUID', {
    value: () => '00000000-0000-4000-8000-000000000000',
    configurable: true,
  });
}

/**
 * jsdom supplies a document URL; Node's undici, which provides Request/Response, does not consult
 * it. fetchBaseQuery builds `new Request('/api/...')`, which undici then refuses to parse. In a
 * browser that relative URL resolves against the document — so resolving it here is restoring
 * browser behaviour the test environment is missing, not working around the application.
 */
const NativeRequest = globalThis.Request;
class DocumentRelativeRequest extends NativeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    const resolved =
      typeof input === 'string' && input.startsWith('/')
        ? new URL(input, window.location.origin).toString()
        : input;
    super(resolved, init);
  }
}
globalThis.Request = DocumentRelativeRequest as unknown as typeof Request;

afterEach(() => cleanup());
