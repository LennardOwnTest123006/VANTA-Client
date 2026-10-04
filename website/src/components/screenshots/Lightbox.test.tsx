import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Lightbox } from './Lightbox';

const items = [
  { file: 'a.png', src: '/a.png', caption: 'Main menu', alt: 'Menu', capturedWith: 'client 1.0.0' },
  {
    file: 'b.png',
    src: '/b.png',
    caption: 'Settings',
    alt: 'Settings screen',
    capturedWith: undefined,
  },
];

describe('Lightbox', () => {
  it('renders nothing while closed', () => {
    render(<Lightbox items={items} index={undefined} onClose={vi.fn()} onIndexChange={vi.fn()} />);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('shows the image, caption and counter and reacts to the keyboard', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onIndexChange = vi.fn();
    render(<Lightbox items={items} index={0} onClose={onClose} onIndexChange={onIndexChange} />);
    const dialog = screen.getByRole('dialog', { name: 'Screenshot 1 of 2' });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Menu' })).toHaveAttribute('src', '/a.png');
    expect(screen.getByText('client 1.0.0')).toBeInTheDocument();
    expect(dialog.contains(document.activeElement)).toBe(true);

    await user.keyboard('{ArrowRight}');
    expect(onIndexChange).toHaveBeenLastCalledWith(1);
    await user.keyboard('{ArrowLeft}');
    expect(onIndexChange).toHaveBeenLastCalledWith(1); // wraps from 0 to the last item
    await user.keyboard('{End}');
    expect(onIndexChange).toHaveBeenLastCalledWith(1);
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('has previous/next/close buttons', async () => {
    const user = userEvent.setup();
    const onIndexChange = vi.fn();
    render(<Lightbox items={items} index={1} onClose={vi.fn()} onIndexChange={onIndexChange} />);
    await user.click(screen.getByRole('button', { name: 'Next screenshot' }));
    expect(onIndexChange).toHaveBeenCalledWith(0);
    await user.click(screen.getByRole('button', { name: 'Previous screenshot' }));
    expect(onIndexChange).toHaveBeenCalledWith(0);
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument();
  });
});
