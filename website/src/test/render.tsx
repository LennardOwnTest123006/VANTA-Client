import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';

/** Renders a component inside a MemoryRouter starting at `route`. */
export function renderWithRouter(ui: ReactElement, route = '/', options?: RenderOptions) {
  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>, options);
}
