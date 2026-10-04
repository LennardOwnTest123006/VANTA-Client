import { presetOptions, presets } from '../../config/presets';
import { cn } from '../../lib/cn';

export interface PresetTableProps {
  readonly className?: string;
  /** Shows the `options.txt` key under each option name. */
  readonly showKeys?: boolean;
  readonly caption?: string;
}

/** Comparison table of the four presets and the vanilla video option values each one sets. */
export function PresetTable({ className, showKeys = false, caption }: PresetTableProps) {
  return (
    <div
      className={cn(
        'overflow-x-auto rounded-xl border border-border-subtle bg-surface-1',
        className,
      )}
    >
      <table className="w-full min-w-[640px] border-collapse text-left text-sm">
        <caption className="sr-only">
          {caption ??
            'Vanilla video option values set by the LOW, BALANCED, HIGH and ULTRA presets'}
        </caption>
        <thead>
          <tr className="border-b border-border-subtle bg-surface-2/70">
            <th
              scope="col"
              className="px-4 py-3 text-[11px] font-semibold tracking-label text-text-muted uppercase"
            >
              Vanilla option
            </th>
            {presets.map((preset) => (
              <th
                key={preset.id}
                scope="col"
                className="px-4 py-3 font-display text-xs font-semibold tracking-label text-text-primary uppercase"
              >
                {preset.id}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {presetOptions.map((row, index) => (
            <tr
              key={row.key}
              className={cn(
                'border-b border-border-subtle last:border-0',
                index % 2 === 1 && 'bg-surface-2/30',
              )}
            >
              <th scope="row" className="px-4 py-3 font-medium text-text-primary">
                {row.option}
                {showKeys ? (
                  <span className="mt-0.5 block font-mono text-[11px] font-normal text-text-muted">
                    {row.key}
                  </span>
                ) : null}
              </th>
              {presets.map((preset) => (
                <td
                  key={preset.id}
                  className="px-4 py-3 font-mono text-[13px] text-text-secondary tabular-nums"
                >
                  {row.values[preset.id]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
