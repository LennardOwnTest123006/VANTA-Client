import { cleanup, render, screen, within } from '@testing-library/react';
import { Suspense } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeAll, describe, expect, it } from 'vitest';
import { loadDocs } from '../lib/docs';
import { MarkdownBlock } from '../lib/markdown';
import DocumentationPage from './DocumentationPage';

/*
 * The documentation and the lazy markdown renderer are loaded up front so the page renders
 * synchronously inside Testing Library's act scope (React 19 does not retry a tree that suspended
 * inside a synchronous act). In the browser both simply suspend behind the route skeleton.
 */
beforeAll(async () => {
  await loadDocs();
  render(<MarkdownBlock source="warm up" />);
  await screen.findByText('warm up');
  cleanup();
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Suspense fallback={<p>loading</p>}>
        <Routes>
          <Route path="/documentation" element={<DocumentationPage />} />
          <Route path="/documentation/:slug" element={<DocumentationPage />} />
        </Routes>
      </Suspense>
    </MemoryRouter>,
  );
}

describe('DocumentationPage', () => {
  it('renders the index with sidebar groups and search', async () => {
    renderAt('/documentation');
    expect(
      await screen.findByRole(
        'heading',
        { level: 1, name: 'VANTA documentation' },
        { timeout: 8000 },
      ),
    ).toBeInTheDocument();
    const sidebar = screen.getAllByRole('navigation', { name: 'Documentation' })[0]!;
    expect(within(sidebar).getByRole('link', { name: 'Installation' })).toHaveAttribute(
      'href',
      '/documentation/installation',
    );
    expect(within(sidebar).getByRole('link', { name: 'Privacy' })).toHaveAttribute(
      'href',
      '/privacy',
    );
    expect(within(sidebar).getByText('Getting started')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Search the documentation' })).toBeInTheDocument();
    expect(document.title).toBe('Documentation — VANTA Client');
  });

  it('renders a page with heading anchors, rewritten cross-links, TOC and neighbours', async () => {
    renderAt('/documentation/installation');
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Installation' }, { timeout: 8000 }),
    ).toBeInTheDocument();
    const heading = await screen.findByRole(
      'heading',
      { level: 2, name: '2. Verify the checksum' },
      { timeout: 8000 },
    );
    expect(heading).toHaveAttribute('id', '2-verify-the-checksum');
    const article = screen.getByRole('article');
    const links = within(article).getAllByRole('link');
    const fabricLink = links.find((link) =>
      link.getAttribute('href')?.startsWith('/documentation/fabric#'),
    );
    expect(fabricLink).toBeDefined();
    expect(within(article).queryByRole('link', { name: /\.md/ })).toBeNull();
    expect(screen.getByRole('link', { name: /Edit on GitHub/ })).toHaveAttribute(
      'href',
      'https://github.com/LennardOwnTest123006/VANTA-Client/blob/HEAD/docs/installation.md',
    );
    const pagination = screen.getByRole('navigation', { name: 'Pagination' });
    expect(within(pagination).getByRole('link', { name: /Previous/ })).toHaveAttribute(
      'href',
      '/documentation',
    );
    expect(within(pagination).getByRole('link', { name: /Next/ })).toHaveAttribute(
      'href',
      '/documentation/minecraft-requirements',
    );
    expect(screen.getByRole('navigation', { name: 'On this page' })).toBeInTheDocument();
    expect(document.title).toBe('Installation · Documentation — VANTA Client');
  });

  it('shows the 404 page for an unknown slug', async () => {
    renderAt('/documentation/does-not-exist');
    expect(await screen.findByRole('heading', { level: 1 }, { timeout: 8000 })).toHaveTextContent(
      'This page does not exist.',
    );
  });
});
