import { Eye, GripVertical, Move, RotateCcw } from 'lucide-react';
import { useId } from 'react';
import { Badge } from '../ui/Badge';
import { Container } from '../ui/Container';
import { cn } from '../../lib/cn';

/*
 * A stylised illustration of the HUD editor built from HTML elements. It is deliberately labelled
 * as an illustration: no screenshots exist before the first release, and the website never fakes one.
 * The sample values are illustrative.
 */

const widgets = [
  { name: 'FPS', on: true, selected: true },
  { name: 'Coordinates', on: true },
  { name: 'Ping', on: true },
  { name: 'Clock', on: true },
  { name: 'Keystrokes', on: true },
  { name: 'Potion effects', on: true },
  { name: 'Armor', on: false },
  { name: 'Memory', on: false },
  { name: 'Biome', on: false },
] as const;

function Toggle({ on }: { on: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'relative inline-flex h-4 w-7 shrink-0 rounded-pill border transition-colors',
        on ? 'border-accent-violet bg-accent-violet/80' : 'border-border-strong bg-surface-3',
      )}
    >
      <span
        className={cn(
          'absolute top-0.5 size-2.5 rounded-full bg-white transition-transform',
          on ? 'translate-x-3.5' : 'translate-x-0.5',
        )}
      />
    </span>
  );
}

function Chip({
  label,
  value,
  className,
  selected = false,
}: {
  label: string;
  value: string;
  className?: string;
  selected?: boolean;
}) {
  return (
    <div
      className={cn(
        'absolute flex items-center gap-2 rounded-sm border bg-bg-void/75 px-2 py-1 font-mono text-[11px] leading-4 backdrop-blur-sm',
        selected
          ? 'border-accent-violet-hover text-text-primary shadow-[0_0_0_3px_rgba(124,92,255,0.18)]'
          : 'border-border-strong/60 text-text-secondary',
        className,
      )}
    >
      <span className="text-text-muted">{label}</span>
      <span className="text-text-primary">{value}</span>
      {selected ? (
        <>
          <span className="absolute -top-1 -left-1 size-2 rounded-[1px] border border-accent-violet-hover bg-bg-base" />
          <span className="absolute -top-1 -right-1 size-2 rounded-[1px] border border-accent-violet-hover bg-bg-base" />
          <span className="absolute -bottom-1 -left-1 size-2 rounded-[1px] border border-accent-violet-hover bg-bg-base" />
          <span className="absolute -right-1 -bottom-1 size-2 rounded-[1px] border border-accent-violet-hover bg-bg-base" />
        </>
      ) : null}
    </div>
  );
}

function Key({ label, pressed = false }: { label: string; pressed?: boolean }) {
  return (
    <span
      className={cn(
        'inline-flex size-6 items-center justify-center rounded-[3px] border font-mono text-[10px]',
        pressed
          ? 'border-accent-violet-hover bg-accent-violet/30 text-text-primary'
          : 'border-border-strong/70 bg-bg-void/70 text-text-secondary',
      )}
    >
      {label}
    </span>
  );
}

function Slider({ label, value, percent }: { label: string; value: string; percent: number }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between text-[11px]">
        <span className="text-text-secondary">{label}</span>
        <span className="font-mono text-text-primary">{value}</span>
      </div>
      <div className="relative h-1 rounded-pill bg-surface-3">
        <div
          className="absolute inset-y-0 left-0 rounded-pill bg-gradient-accent"
          style={{ width: `${percent}%` }}
        />
        <span
          className="absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border border-accent-violet-hover bg-bg-base"
          style={{ left: `${percent}%` }}
        />
      </div>
    </div>
  );
}

/** Product preview block: window chrome, widget sidebar, properties and an editor canvas. */
export function InterfacePreview() {
  const captionId = useId();
  return (
    <section
      aria-labelledby={`${captionId}-title`}
      className="relative overflow-hidden border-t border-border-subtle py-16 sm:py-20 lg:py-24"
    >
      <div
        className="absolute inset-0 bg-blockgrid mask-fade-radial opacity-30"
        aria-hidden="true"
      />
      <Container className="relative">
        <div className="mx-auto mb-10 max-w-2xl text-center sm:mb-14">
          <p className="eyebrow mb-3">Interface preview</p>
          <h2
            id={`${captionId}-title`}
            className="font-display text-3xl leading-[1.08] font-semibold tracking-display text-text-primary sm:text-4xl"
          >
            Arrange your HUD on a live canvas.
          </h2>
          <p className="mt-4 text-base leading-relaxed text-text-secondary sm:text-lg">
            Drag widgets into place, snap them to anchors and tune scale, opacity and colours — the
            editor shows exactly what you will see in game.
          </p>
        </div>

        <figure aria-describedby={captionId} className="mx-auto max-w-5xl">
          <div className="relative rounded-2xl border border-border-strong bg-surface-1 shadow-lg ring-1 ring-white/[0.03]">
            {/* Title bar */}
            <div className="flex items-center justify-between gap-4 border-b border-border-subtle px-4 py-3">
              <div className="flex items-center gap-3">
                <span className="flex gap-1.5" aria-hidden="true">
                  <span className="size-2.5 rounded-full bg-surface-4" />
                  <span className="size-2.5 rounded-full bg-surface-4" />
                  <span className="size-2.5 rounded-full bg-surface-4" />
                </span>
                <span className="font-display text-xs font-semibold tracking-wordmark text-text-secondary uppercase">
                  VANTA · HUD Editor
                </span>
              </div>
              <Badge tone="violet">Stylised illustration</Badge>
            </div>

            <div className="grid md:grid-cols-[220px_minmax(0,1fr)]">
              {/* Sidebar */}
              <aside
                className="hidden flex-col gap-5 border-r border-border-subtle p-4 md:flex"
                aria-hidden="true"
              >
                <div>
                  <p className="mb-2 text-[10px] font-semibold tracking-label text-text-muted uppercase">
                    Widgets
                  </p>
                  <ul className="flex flex-col gap-1">
                    {widgets.map((widget) => (
                      <li
                        key={widget.name}
                        className={cn(
                          'flex items-center gap-2 rounded-md px-2 py-1.5 text-xs',
                          'selected' in widget && widget.selected
                            ? 'bg-surface-3 text-text-primary'
                            : 'text-text-secondary',
                        )}
                      >
                        <GripVertical className="size-3.5 text-text-muted" />
                        <span className="flex-1">{widget.name}</span>
                        <Toggle on={widget.on} />
                      </li>
                    ))}
                  </ul>
                </div>
                <div className="flex flex-col gap-3 border-t border-border-subtle pt-4">
                  <p className="text-[10px] font-semibold tracking-label text-text-muted uppercase">
                    Selected · FPS
                  </p>
                  <Slider label="Scale" value="100%" percent={50} />
                  <Slider label="Opacity" value="90%" percent={90} />
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-text-secondary">Text colour</span>
                    <span className="flex items-center gap-1.5 font-mono text-text-primary">
                      <span className="size-3 rounded-[2px] border border-border-strong bg-text-primary" />
                      #F5F5F7
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-text-secondary">Anchor</span>
                    <span className="font-mono text-text-primary">top-left</span>
                  </div>
                  <div className="mt-1 flex gap-2">
                    <span className="inline-flex h-7 flex-1 items-center justify-center gap-1.5 rounded-sm border border-border-strong bg-surface-2 text-[11px] text-text-secondary">
                      <RotateCcw className="size-3" /> Reset
                    </span>
                    <span className="inline-flex h-7 flex-1 items-center justify-center gap-1.5 rounded-sm bg-gradient-accent text-[11px] font-semibold text-white">
                      Save
                    </span>
                  </div>
                </div>
              </aside>

              {/* Canvas */}
              <div
                className="relative aspect-[16/10] overflow-hidden rounded-b-2xl bg-[radial-gradient(70%_60%_at_50%_100%,rgba(124,92,255,0.14),transparent_70%),linear-gradient(180deg,#0E0E15,#07070A)] md:aspect-auto md:min-h-[420px] md:rounded-bl-none"
                aria-hidden="true"
              >
                <div className="absolute inset-0 bg-isogrid opacity-40" />
                {/* anchors */}
                {[
                  'top-3 left-3',
                  'top-3 left-1/2 -translate-x-1/2',
                  'top-3 right-3',
                  'top-1/2 left-3 -translate-y-1/2',
                  'top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2',
                  'top-1/2 right-3 -translate-y-1/2',
                  'bottom-3 left-3',
                  'bottom-3 left-1/2 -translate-x-1/2',
                  'right-3 bottom-3',
                ].map((position) => (
                  <span
                    key={position}
                    className={cn('absolute size-1.5 rounded-full bg-accent-violet/50', position)}
                  />
                ))}

                <Chip label="FPS" value="144" selected className="top-6 left-6" />
                <Chip
                  label="XYZ"
                  value="128 / 64 / -512"
                  className="top-6 left-1/2 -translate-x-1/2"
                />
                <Chip label="PING" value="23 ms" className="top-6 right-6" />
                <Chip label="CLOCK" value="12:04" className="top-16 right-6" />
                <div className="absolute bottom-6 left-6 flex flex-col items-center gap-1">
                  <Key label="W" pressed />
                  <div className="flex gap-1">
                    <Key label="A" />
                    <Key label="S" />
                    <Key label="D" pressed />
                  </div>
                  <div className="mt-1 flex gap-1">
                    <span className="inline-flex h-5 w-[52px] items-center justify-center rounded-[3px] border border-border-strong/70 bg-bg-void/70 font-mono text-[9px] text-text-secondary">
                      SPACE
                    </span>
                  </div>
                </div>
                <div className="absolute right-6 bottom-6 flex flex-col gap-1.5 text-[11px]">
                  <div className="flex items-center gap-2 rounded-sm border border-border-strong/60 bg-bg-void/75 px-2 py-1 font-mono">
                    <span className="size-2 rounded-full bg-accent-blue" />
                    <span className="text-text-secondary">Speed II</span>
                    <span className="text-text-primary">2:14</span>
                  </div>
                  <div className="flex items-center gap-2 rounded-sm border border-border-strong/60 bg-bg-void/75 px-2 py-1 font-mono">
                    <span className="size-2 rounded-full bg-warning" />
                    <span className="text-text-secondary">Haste</span>
                    <span className="text-text-primary">0:48</span>
                  </div>
                </div>

                {/* cursor hint */}
                <div className="absolute top-[38%] left-1/2 flex -translate-x-1/2 items-center gap-2 rounded-pill border border-border-strong/70 bg-bg-void/80 px-3 py-1.5 text-[11px] text-text-secondary">
                  <Move className="size-3.5 text-accent-violet-hover" />
                  Drag a widget · hold Shift to snap
                </div>
                <div className="absolute right-6 bottom-24 hidden items-center gap-1.5 text-[10px] text-text-muted sm:flex">
                  <Eye className="size-3" /> Preview matches GUI scale 2
                </div>
              </div>
            </div>
          </div>
          <figcaption
            id={captionId}
            className="mt-4 text-center text-xs leading-relaxed text-text-muted"
          >
            Interface preview — a stylised illustration of the HUD editor built from HTML, not a
            screenshot. The values are examples. Real screenshots are on the Screenshots page.
          </figcaption>
        </figure>
      </Container>
    </section>
  );
}
