import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { renderWithRouter } from '../../test/render';
import { Button } from './Button';

describe('Button', () => {
  it('renders a native button and forwards clicks', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Apply</Button>);
    const button = screen.getByRole('button', { name: 'Apply' });
    expect(button).toHaveAttribute('type', 'button');
    await userEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('does not fire when disabled', async () => {
    const onClick = vi.fn();
    render(
      <Button onClick={onClick} disabled>
        Not yet
      </Button>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Not yet' }));
    expect(onClick).not.toHaveBeenCalled();
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('renders an internal router link for `to`', () => {
    renderWithRouter(
      <Button to="/download" variant="secondary">
        Download
      </Button>,
    );
    const link = screen.getByRole('link', { name: 'Download' });
    expect(link).toHaveAttribute('href', '/download');
    expect(link).not.toHaveAttribute('target');
  });

  it('opens external anchors in a new tab with a safe rel', () => {
    render(<Button href="https://github.com/example">GitHub</Button>);
    const link = screen.getByRole('link', { name: 'GitHub' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('keeps same-origin anchors in the same tab', () => {
    render(<Button href="/fonts/LICENSE-Inter.txt">License</Button>);
    expect(screen.getByRole('link', { name: 'License' })).not.toHaveAttribute('target');
  });

  it('applies variant, size and full width classes and hides decorative icons', () => {
    render(
      <Button variant="ghost" size="lg" fullWidth leadingIcon={<svg data-testid="icon" />}>
        Ghost
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Ghost' });
    expect(button.className).toContain('w-full');
    expect(button.className).toContain('h-12');
    expect(screen.getByTestId('icon').parentElement).toHaveAttribute('aria-hidden', 'true');
  });
});
