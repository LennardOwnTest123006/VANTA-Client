import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Reveal } from './Reveal';

describe('Reveal', () => {
  it('renders visible content when IntersectionObserver is unavailable (jsdom)', () => {
    render(
      <Reveal as="section" delay={120}>
        Hello
      </Reveal>,
    );
    const node = screen.getByText('Hello');
    expect(node.tagName).toBe('SECTION');
    expect(node).toHaveClass('reveal', 'is-visible');
    expect(node.style.getPropertyValue('--reveal-delay')).toBe('120ms');
  });
});
