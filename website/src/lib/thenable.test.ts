import { describe, expect, it } from 'vitest';
import { trackSettled } from './thenable';

describe('trackSettled', () => {
  it('records fulfilment', async () => {
    const tracked = trackSettled(Promise.resolve(42));
    expect(tracked.status).toBe('pending');
    await tracked;
    expect(tracked.status).toBe('fulfilled');
    expect(tracked.value).toBe(42);
    expect(trackSettled(tracked)).toBe(tracked);
  });
  it('records rejection', async () => {
    const error = new Error('nope');
    const tracked = trackSettled(Promise.reject(error));
    await expect(tracked).rejects.toBe(error);
    expect(tracked.status).toBe('rejected');
    expect(tracked.reason).toBe(error);
  });
});
