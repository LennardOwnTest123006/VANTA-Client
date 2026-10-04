import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeAll, describe, expect, it } from 'vitest';
import { type DocPage, loadDocs } from '../../lib/docs';
import { DocsSearch } from './DocsSearch';

function LocationProbe() {
  const { pathname, hash } = useLocation();
  return <span data-testid="location">{`${pathname}${hash}`}</span>;
}

let pages: readonly DocPage[];
beforeAll(async () => {
  pages = await loadDocs();
});

function renderSearch(shortcut = false) {
  return render(
    <MemoryRouter initialEntries={['/documentation']}>
      <Routes>
        <Route path="*" element={<DocsSearch pages={pages} shortcut={shortcut} />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('DocsSearch', () => {
  it('finds documentation for "fps" and highlights the matches', async () => {
    const user = userEvent.setup();
    renderSearch();
    const input = screen.getByRole('combobox', { name: 'Search the documentation' });
    await user.type(input, 'fps');
    const options = await screen.findAllByRole('option', {}, { timeout: 5000 });
    expect(options.length).toBeGreaterThan(1);
    expect(input).toHaveAttribute('aria-expanded', 'true');
    expect(document.querySelectorAll('mark').length).toBeGreaterThan(0);
    expect(screen.getByRole('status')).toHaveTextContent(/results? for fps/);
  });

  it('navigates to the selected result with the keyboard and clears', async () => {
    const user = userEvent.setup();
    renderSearch();
    const input = screen.getByRole('combobox', { name: 'Search the documentation' });
    await user.type(input, 'profile');
    await screen.findAllByRole('option', {}, { timeout: 5000 });
    await user.keyboard('{ArrowDown}{ArrowUp}{Enter}');
    expect(screen.getByTestId('location').textContent).toMatch(/^\/documentation\/profiles/);
    expect(input).toHaveValue('');
  });

  it('reports no matches honestly', async () => {
    const user = userEvent.setup();
    renderSearch();
    await user.type(screen.getByRole('combobox'), 'qzxvwk');
    expect(
      await screen.findByText(/No documentation page matches/, {}, { timeout: 5000 }),
    ).toBeInTheDocument();
  });

  it('focuses on "/" unless the user is typing elsewhere', async () => {
    const user = userEvent.setup();
    const { container } = renderSearch(true);
    const other = document.createElement('input');
    container.appendChild(other);
    other.focus();
    await user.keyboard('/');
    expect(other).toHaveFocus();
    other.blur();
    document.body.focus();
    await user.keyboard('/');
    expect(screen.getByRole('combobox')).toHaveFocus();
    expect(screen.getByRole('combobox')).toHaveValue('');
  });
});
