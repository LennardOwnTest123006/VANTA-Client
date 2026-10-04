/** "Skip to content" link: visually hidden until focused, first element in the tab order. */
export function SkipLink({ target = '#main' }: { readonly target?: string }) {
  return (
    <a
      href={target}
      className="sr-only z-[100] rounded-md bg-accent-violet px-4 py-2 text-sm font-semibold text-white focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
    >
      Skip to content
    </a>
  );
}
