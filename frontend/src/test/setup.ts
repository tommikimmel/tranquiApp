import '@testing-library/jest-dom/vitest'

// jsdom (as wired up by vitest-environment-jsdom here) doesn't ship a working
// window.localStorage/sessionStorage — accessing it resolves to `undefined` rather than
// throwing, which is easy to miss until a component's `localStorage.setItem(...)` blows up
// with "Cannot read properties of undefined". Several components under test (LoginPage,
// CheckoutFlow, App) read/write `tranqui_user` via the bare global, so provide a minimal
// in-memory Storage polyfill on both `window` and the bare global before any test runs.
class MemoryStorage implements Storage {
  private store = new Map<string, string>()

  get length() {
    return this.store.size
  }

  clear(): void {
    this.store.clear()
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? this.store.get(key)! : null
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null
  }

  removeItem(key: string): void {
    this.store.delete(key)
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value))
  }
}

function installStoragePolyfill(name: 'localStorage' | 'sessionStorage') {
  if (typeof window !== 'undefined' && !window[name]) {
    Object.defineProperty(window, name, { value: new MemoryStorage(), configurable: true, writable: true })
  }
  if (typeof globalThis !== 'undefined' && !(globalThis as any)[name]) {
    Object.defineProperty(globalThis, name, { value: (window as any)[name] ?? new MemoryStorage(), configurable: true, writable: true })
  }
}

installStoragePolyfill('localStorage')
installStoragePolyfill('sessionStorage')
