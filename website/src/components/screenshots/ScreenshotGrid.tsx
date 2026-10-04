import { Maximize2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import { type Screenshot } from '../../lib/screenshots';
import { Lightbox } from './Lightbox';

export interface ScreenshotGridProps {
  readonly items: readonly Screenshot[];
}

/** Responsive grid of real screenshots; every tile opens the lightbox at that image. */
export function ScreenshotGrid({ items }: ScreenshotGridProps) {
  const [openIndex, setOpenIndex] = useState<number | undefined>(undefined);
  const close = useCallback(() => {
    setOpenIndex(undefined);
  }, []);
  return (
    <>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="Screenshots">
        {items.map((item, index) => (
          <li key={item.file} className="flex">
            <figure className="surface-card surface-card-interactive group flex w-full flex-col overflow-hidden">
              <button
                type="button"
                onClick={() => {
                  setOpenIndex(index);
                }}
                aria-label={`Open ${item.caption} in the viewer`}
                className="relative block aspect-video w-full overflow-hidden bg-bg-void outline-none after:absolute after:inset-0 after:rounded-t-xl focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-border-focus"
              >
                <img
                  src={item.src}
                  alt={item.alt}
                  loading="lazy"
                  decoding="async"
                  className="size-full object-cover transition-transform duration-(--vanta-duration-slow) motion-safe:group-hover:scale-[1.02]"
                />
                <span
                  aria-hidden="true"
                  className="absolute right-3 bottom-3 inline-flex size-8 items-center justify-center rounded-md border border-border-strong bg-bg-void/80 text-text-primary opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"
                >
                  <Maximize2 className="size-4" />
                </span>
              </button>
              <figcaption className="flex flex-col gap-1 p-4">
                <span className="text-sm font-medium text-text-primary">{item.caption}</span>
                {item.capturedWith ? (
                  <span className="font-mono text-[11px] text-text-muted">{item.capturedWith}</span>
                ) : null}
              </figcaption>
            </figure>
          </li>
        ))}
      </ul>
      <Lightbox items={items} index={openIndex} onClose={close} onIndexChange={setOpenIndex} />
    </>
  );
}
