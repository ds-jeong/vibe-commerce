let pending = 0;
let hideTimer = null;
const listeners = new Set();
const MIN_VISIBLE_MS = 400;

function isActive() {
  return pending > 0;
}

function notify() {
  const active = isActive();
  listeners.forEach((listener) => listener(active));
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('vc-loading-bar', { detail: { active } }));
  }
}

function start() {
  pending += 1;
  if (hideTimer) {
    clearTimeout(hideTimer);
    hideTimer = null;
  }
  notify();
}

function stop() {
  pending = Math.max(0, pending - 1);
  if (pending > 0) {
    notify();
    return;
  }
  hideTimer = setTimeout(() => {
    hideTimer = null;
    notify();
  }, MIN_VISIBLE_MS);
}

export function subscribeLoadingBar(listener) {
  listeners.add(listener);
  listener(isActive());
  return () => listeners.delete(listener);
}

export function installLoadingFetch(rewriteUrl) {
  if (typeof window === 'undefined' || window.__vcLoadingFetchInstalled) {
    return;
  }
  window.__vcLoadingFetchInstalled = true;
  const originalFetch = window.fetch.bind(window);
  window.fetch = (input, init) => {
    const nextInput = typeof rewriteUrl === 'function' ? rewriteUrl(input) : input;
    start();
    return originalFetch(nextInput, init).finally(stop);
  };
}
