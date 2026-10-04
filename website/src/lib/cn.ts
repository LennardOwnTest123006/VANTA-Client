/** Class name fragments: strings are kept, every falsy or non-string value is dropped. */
export type ClassValue = string | number | bigint | boolean | null | undefined;

/** Joins class names, skipping falsy values. Tiny replacement for `clsx`. */
export function cn(...parts: readonly ClassValue[]): string {
  return parts.filter((part): part is string => typeof part === 'string' && part !== '').join(' ');
}
