export const runWhenIdle = (fn, options = {}) => {
  if (typeof fn !== 'function') return null
  const timeout = Number.isFinite(options.timeout) ? options.timeout : 1200
  if (typeof window === 'undefined') {
    fn()
    return null
  }
  if (typeof window.requestIdleCallback === 'function') {
    return window.requestIdleCallback(() => fn(), { timeout })
  }
  return window.setTimeout(fn, 0)
}
