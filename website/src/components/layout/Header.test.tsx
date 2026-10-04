import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderWithRouter } from '../../test/render';
import { Header } from './Header';

const openMenu = async () => {
  const toggle = screen.getByRole('button', { name: 'Open menu' });
  await userEvent.click(toggle);
  return screen.getByRole('dialog', { name: 'Site navigation' });
};

describe('Header', () => {
  it('renders the brand link, the primary navigation and the download call-to-action', () => {
    renderWithRouter(<Header />);
    expect(screen.getByRole('link', { name: /VANTA Client — home/ })).toHaveAttribute('href', '/');
    const nav = screen.getAllByRole('navigation', { name: 'Primary' })[0]!;
    expect(within(nav).getByRole('link', { name: 'Features' })).toHaveAttribute(
      'href',
      '/features',
    );
    expect(within(nav).getByRole('link', { name: 'Performance' })).toHaveAttribute(
      'href',
      '/performance',
    );
    expect(within(nav).getByRole('link', { name: 'Documentation' })).toHaveAttribute(
      'href',
      '/documentation',
    );
    expect(within(nav).queryByRole('link', { name: 'News' })).toBeNull();
    expect(screen.getAllByRole('link', { name: 'Download' })[0]).toHaveAttribute(
      'href',
      '/download',
    );
  });

  it('marks the active route', () => {
    renderWithRouter(<Header />, '/features');
    const nav = screen.getAllByRole('navigation', { name: 'Primary' })[0]!;
    expect(within(nav).getByRole('link', { name: 'Features' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByRole('link', { name: 'Performance' })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('opens the mobile menu, moves focus inside, and closes it with Escape', async () => {
    renderWithRouter(<Header />);
    const toggle = screen.getByRole('button', { name: 'Open menu' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('dialog')).toBeNull();

    const dialog = await openMenu();
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    expect(dialog.contains(document.activeElement)).toBe(true);
    expect(document.body.style.overflow).toBe('hidden');

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByRole('button', { name: 'Open menu' })).toHaveFocus();
    expect(document.body.style.overflow).toBe('');
  });

  it('traps Tab focus inside the open menu', async () => {
    renderWithRouter(<Header />);
    const dialog = await openMenu();
    const focusables = within(dialog).getAllByRole('link');
    const first = focusables[0]!;
    const last = focusables[focusables.length - 1]!;

    last.focus();
    await userEvent.tab();
    expect(first).toHaveFocus();

    await userEvent.tab({ shift: true });
    expect(last).toHaveFocus();
  });

  it('closes the menu when a navigation link is chosen', async () => {
    renderWithRouter(<Header />);
    const dialog = await openMenu();
    await userEvent.click(within(dialog).getByRole('link', { name: 'Features' }));
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});
