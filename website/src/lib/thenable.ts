/**
 * Promise instrumentation following React's thenable protocol: once settled, the promise carries
 * `status` and `value`/`reason`, so `use(promise)` returns synchronously instead of suspending again.
 * This keeps navigation between content pages instant and lets tests render preloaded content
 * without an async act scope.
 */
export type TrackedPromise<T> = Promise<T> & {
  status?: 'pending' | 'fulfilled' | 'rejected';
  value?: T;
  reason?: unknown;
};

/** Marks `promise` as pending and records its outcome when it settles. Returns the same promise. */
export function trackSettled<T>(promise: Promise<T>): TrackedPromise<T> {
  const tracked = promise as TrackedPromise<T>;
  if (tracked.status !== undefined) return tracked;
  tracked.status = 'pending';
  tracked.then(
    (value) => {
      tracked.status = 'fulfilled';
      tracked.value = value;
    },
    (reason: unknown) => {
      tracked.status = 'rejected';
      tracked.reason = reason;
    },
  );
  return tracked;
}
