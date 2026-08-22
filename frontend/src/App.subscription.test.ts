import { describe, expect, it } from 'vitest'
import { getDaysUntilSubscriptionExpiry, isSubscriptionAllowed } from './App'

// App.tsx itself is a 1200+ line root component wired to react-router hooks, WebSocket
// (stomp/sockjs) connections and ~15 API calls fired from nested useEffects — not something a
// unit test can exercise safely/cheaply. isSubscriptionAllowed/getDaysUntilSubscriptionExpiry
// are the two pieces of *pure* business logic in it (mirroring the backend's
// SubscriptionAccessFilter gate), extracted here specifically so they're testable in isolation.
describe('isSubscriptionAllowed', () => {
  it('allows an ACTIVE subscription whose currentPeriodEnd is still in the future', () => {
    const future = new Date(Date.now() + 10 * 86400000).toISOString()
    expect(isSubscriptionAllowed({ status: 'ACTIVE', currentPeriodEnd: future })).toBe(true)
  })

  it('blocks an ACTIVE subscription whose currentPeriodEnd already passed', () => {
    const past = new Date(Date.now() - 86400000).toISOString()
    expect(isSubscriptionAllowed({ status: 'ACTIVE', currentPeriodEnd: past })).toBe(false)
  })

  it('blocks a non-ACTIVE subscription even with a future currentPeriodEnd', () => {
    const future = new Date(Date.now() + 10 * 86400000).toISOString()
    expect(isSubscriptionAllowed({ status: 'PAST_DUE', currentPeriodEnd: future })).toBe(false)
    expect(isSubscriptionAllowed({ status: 'CANCELED', currentPeriodEnd: future })).toBe(false)
  })

  it('blocks when currentPeriodEnd is missing', () => {
    expect(isSubscriptionAllowed({ status: 'ACTIVE' })).toBe(false)
    expect(isSubscriptionAllowed({ status: 'ACTIVE', currentPeriodEnd: null })).toBe(false)
  })

  it('blocks null/undefined subscriptions (e.g. a professional who never subscribed)', () => {
    expect(isSubscriptionAllowed(null)).toBe(false)
    expect(isSubscriptionAllowed(undefined)).toBe(false)
  })
})

describe('getDaysUntilSubscriptionExpiry', () => {
  it('returns null when there is no subscription or no currentPeriodEnd', () => {
    expect(getDaysUntilSubscriptionExpiry(null)).toBeNull()
    expect(getDaysUntilSubscriptionExpiry({})).toBeNull()
  })

  it('rounds up to the nearest whole day remaining', () => {
    const in2point5Days = new Date(Date.now() + 2.5 * 86400000).toISOString()
    expect(getDaysUntilSubscriptionExpiry({ currentPeriodEnd: in2point5Days })).toBe(3)
  })

  it('returns a negative number once the period has already ended', () => {
    const yesterday = new Date(Date.now() - 1.5 * 86400000).toISOString()
    expect(getDaysUntilSubscriptionExpiry({ currentPeriodEnd: yesterday })).toBeLessThan(0)
  })
})
