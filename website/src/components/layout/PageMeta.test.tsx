import { act, render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { absoluteUrl } from '../../lib/meta';
import { renderWithRouter } from '../../test/render';
import { PageMeta } from './PageMeta';

const canonical = () =>
  Array.from(document.head.querySelectorAll('link[rel="canonical"]')).map((link) =>
    link.getAttribute('href'),
  );
const ogUrl = () =>
  Array.from(document.head.querySelectorAll('meta[property="og:url"]')).map((meta) =>
    meta.getAttribute('content'),
  );

describe('PageMeta', () => {
  it('sets the title, description and social tags of the page', () => {
    renderWithRouter(<PageMeta title="Download" description="Get VANTA." />, '/download');
    expect(document.title).toBe('Download — VANTA Client');
    expect(document.head.querySelector('meta[name="description"]')).toHaveAttribute(
      'content',
      'Get VANTA.',
    );
    expect(document.head.querySelector('meta[property="og:image"]')).toHaveAttribute(
      'content',
      absoluteUrl('/icon-512.png'),
    );
    expect(canonical()).toEqual([absoluteUrl('/download')]);
    expect(ogUrl()).toEqual([absoluteUrl('/download')]);
  });

  it.each([
    ['/', '/'],
    ['/?ref=x#top', '/'],
    ['/download/', '/download'],
    ['/DOWNLOAD', '/download'],
    ['/Download/?utm_source=chat#verify', '/download'],
    ['/Documentation/installation/', '/documentation/installation'],
    ['/NEWS/introducing-vanta', '/news/introducing-vanta'],
    ['/this-page-does-not-exist/', '/this-page-does-not-exist'],
  ])('declares %s as %s in the canonical link and og:url', (entry, expected) => {
    renderWithRouter(<PageMeta title="Page" description="Text" />, entry);
    expect(canonical()).toEqual([absoluteUrl(expected)]);
    expect(ogUrl()).toEqual([absoluteUrl(expected)]);
  });

  it('updates the single canonical link when the route changes', async () => {
    const router = createMemoryRouter(
      [{ path: '*', element: <PageMeta title="Page" description="Text" /> }],
      { initialEntries: ['/Features/'] },
    );
    render(<RouterProvider router={router} />);
    expect(canonical()).toEqual([absoluteUrl('/features')]);
    await act(async () => {
      await router.navigate('/Changelog?product=launcher#launcher-1.0.1');
    });
    expect(canonical()).toEqual([absoluteUrl('/changelog')]);
    expect(ogUrl()).toEqual([absoluteUrl('/changelog')]);
  });
});
