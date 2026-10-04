import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithRouter } from '../../test/render';
import { Card } from './Card';

describe('Card', () => {
  it('renders title, body and a decorative icon', () => {
    render(
      <Card icon={<svg data-testid="icon" />} title="Performance" eyebrow="Module">
        Live FPS and frame time.
      </Card>,
    );
    expect(screen.getByRole('heading', { level: 3, name: 'Performance' })).toBeInTheDocument();
    expect(screen.getByText('Live FPS and frame time.')).toBeInTheDocument();
    expect(screen.getByText('Module')).toHaveClass('eyebrow');
    expect(screen.getByTestId('icon').parentElement).toHaveAttribute('aria-hidden', 'true');
  });

  it('is static by default and interactive when asked', () => {
    const { rerender } = render(<Card title="Static" />);
    expect(screen.getByRole('heading').closest('.surface-card')).not.toHaveClass(
      'surface-card-interactive',
    );
    rerender(<Card title="Hover" interactive />);
    expect(screen.getByRole('heading').closest('.surface-card')).toHaveClass(
      'surface-card-interactive',
    );
  });

  it('links the title when `to` is given and becomes an article', () => {
    renderWithRouter(<Card title="HUD" to="/features#hud" />);
    const link = screen.getByRole('link', { name: 'HUD' });
    expect(link).toHaveAttribute('href', '/features#hud');
    expect(link.closest('article')).not.toBeNull();
    expect(link.closest('.surface-card')).toHaveClass('surface-card-interactive');
  });

  it('respects a custom heading level and element', () => {
    render(<Card title="Level two" headingLevel={2} as="li" />);
    expect(screen.getByRole('heading', { level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('listitem')).toBeInTheDocument();
  });
});
