import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithRouter } from '../test/render';
import NotFoundPage from './NotFoundPage';

describe('NotFoundPage', () => {
  it('names the missing path and offers the main routes', () => {
    renderWithRouter(<NotFoundPage />, '/does-not-exist');
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'This page does not exist.',
    );
    expect(screen.getByText('/does-not-exist')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the home page' })).toHaveAttribute(
      'href',
      '/',
    );
    expect(screen.getByRole('navigation', { name: 'Suggested pages' })).toBeInTheDocument();
    expect(document.title).toBe('Page not found — VANTA Client');
  });
});
