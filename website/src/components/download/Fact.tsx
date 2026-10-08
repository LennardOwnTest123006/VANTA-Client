import { type ReactNode } from 'react';

export interface FactProps {
  readonly label: string;
  readonly children: ReactNode;
}

/** One label/value pair of a download card's facts list (`<dl>`). */
export function Fact({ label, children }: FactProps) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-[11px] font-semibold tracking-label text-text-muted uppercase">
        {label}
      </dt>
      <dd className="text-sm text-text-primary">{children}</dd>
    </div>
  );
}
