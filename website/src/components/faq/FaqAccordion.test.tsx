import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderWithRouter } from '../../test/render';
import { FaqAccordion } from './FaqAccordion';

const items = [
  { id: 'is-it-free', question: 'Is it free?', answer: 'Yes, **completely**.' },
  { id: 'servers', question: 'Is VANTA allowed on servers?', answer: 'Server rules vary.' },
  { id: 'java', question: 'Why Java 21?', answer: 'Minecraft requires it.' },
];

describe('FaqAccordion', () => {
  it('renders collapsed disclosure buttons wired to their regions', () => {
    renderWithRouter(<FaqAccordion items={items} />);
    const button = screen.getByRole('button', { name: 'Is it free?' });
    expect(button).toHaveAttribute('aria-expanded', 'false');
    const region = document.getElementById(button.getAttribute('aria-controls')!)!;
    expect(region).toHaveAttribute('role', 'region');
    expect(region).toHaveAttribute('hidden');
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(3);
  });

  it('toggles with mouse and keyboard and moves focus with the arrow keys', async () => {
    const user = userEvent.setup();
    renderWithRouter(<FaqAccordion items={items} />);
    const first = screen.getByRole('button', { name: 'Is it free?' });
    await user.click(first);
    expect(first).toHaveAttribute('aria-expanded', 'true');
    const region = screen.getByRole('region', { name: 'Is it free?' });
    expect(await within(region).findByText('completely')).toBeInTheDocument();

    first.focus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('button', { name: 'Is VANTA allowed on servers?' })).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: 'Is VANTA allowed on servers?' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    await user.keyboard('{End}');
    expect(screen.getByRole('button', { name: 'Why Java 21?' })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(first).toHaveFocus();
    await user.keyboard(' ');
    expect(first).toHaveAttribute('aria-expanded', 'false');
  });

  it('opens the item named in the URL hash', () => {
    renderWithRouter(<FaqAccordion items={items} />, '/faq#servers');
    expect(screen.getByRole('button', { name: 'Is VANTA allowed on servers?' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });

  it('filters by question and answer text', () => {
    renderWithRouter(<FaqAccordion items={items} filter="rules" />);
    expect(screen.getAllByRole('button')).toHaveLength(1);
    expect(screen.getByRole('status')).toHaveTextContent('1 of 3 questions match');
    renderWithRouter(<FaqAccordion items={items} filter="zzz" />);
    expect(screen.getByText(/No question matches/)).toBeInTheDocument();
  });
});
