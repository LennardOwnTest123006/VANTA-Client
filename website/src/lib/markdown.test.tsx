import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithRouter } from '../test/render';
import { findChangelog } from './content';
import { MarkdownBlock } from './markdown';
import MarkdownRenderer from './markdown-renderer';

const source = [
  '# Title',
  '',
  'Some **bold** text with a [safe link](https://example.com) and a [bad link](javascript:alert(1)).',
  '',
  '- item one',
  '- [ ] task',
  '',
  '| a | b |',
  '| - | - |',
  '| 1 | 2 |',
  '',
  '<script>alert("x")</script>',
  '',
  '![alt text](javascript:evil)',
].join('\n');

describe('MarkdownRenderer', () => {
  it('renders GitHub-flavoured markdown through the sanitised component map', () => {
    render(<MarkdownRenderer source={source} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Title' })).toBeInTheDocument();
    expect(screen.getByText('bold')).toHaveClass('font-semibold');
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.getAllByRole('listitem').map((li) => li.textContent)).toContain('item one');
  });

  it('opens external links safely and neutralises dangerous URLs', () => {
    render(<MarkdownRenderer source={source} />);
    const safe = screen.getByRole('link', { name: 'safe link' });
    expect(safe).toHaveAttribute('target', '_blank');
    expect(safe).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.queryByRole('link', { name: 'bad link' })).not.toBeInTheDocument();
    expect(screen.getByText('bad link')).toBeInTheDocument();
  });

  it('skips raw HTML and refuses non-https images', () => {
    const { container } = render(<MarkdownRenderer source={source} />);
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText('alt text')).toBeInTheDocument();
  });
});

describe('heading ids and link rewriting', () => {
  it('gives headings GitHub-style ids with duplicate suffixes', () => {
    render(<MarkdownRenderer source={'## Notes\n\n## Notes\n\n### `settings.json` file'} />);
    expect(screen.getAllByRole('heading', { level: 2 })[0]).toHaveAttribute('id', 'notes');
    expect(screen.getAllByRole('heading', { level: 2 })[1]).toHaveAttribute('id', 'notes-1');
    expect(screen.getByRole('heading', { level: 3 })).toHaveAttribute('id', 'settingsjson-file');
  });

  it('can switch heading ids off', () => {
    render(<MarkdownRenderer source="## Plain" headingIds={false} />);
    expect(screen.getByRole('heading')).not.toHaveAttribute('id');
  });

  it('rewrites links through the resolver and renders internal links through the router', () => {
    renderWithRouter(
      <MarkdownRenderer
        source="[Install](installation.md#2-verify) and [home](/) and [same](#x)"
        resolveLink={(href) =>
          href.endsWith('.md#2-verify') ? '/documentation/installation#2-verify' : undefined
        }
      />,
    );
    expect(screen.getByRole('link', { name: 'Install' })).toHaveAttribute(
      'href',
      '/documentation/installation#2-verify',
    );
    expect(screen.getByRole('link', { name: 'home' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'home' })).not.toHaveAttribute('target');
    expect(screen.getByRole('link', { name: 'same' })).toHaveAttribute('href', '#x');
  });
});

describe('MarkdownBlock', () => {
  it('lazily loads the renderer', async () => {
    render(<MarkdownBlock source="Hello **world**" />);
    expect(await screen.findByText('world')).toBeInTheDocument();
  });
});

describe('repository release notes', () => {
  it('links the launcher 1.0.1 release page from the note for launcher 1.0.0 users', () => {
    const notes = findChangelog('launcher', '1.0.1')?.sections.find((s) => s.kind === 'notes');
    expect(notes?.body).toMatch(/^- Updating from launcher 1\.0\.0: /);
    render(<MarkdownRenderer source={notes?.body ?? ''} />);
    const url = 'https://github.com/LennardOwnTest123006/VANTA-Client/releases/tag/launcher-v1.0.1';
    // A bare URL in parentheses: GFM autolinks it without the closing parenthesis and comma.
    const link = screen.getByRole('link', { name: url });
    expect(link).toHaveAttribute('href', url);
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByRole('listitem')).toHaveTextContent(
      `from the launcher-v1.0.1 release (${url}), then compare its SHA-256.`,
    );
    expect(screen.getByText('VANTA-Launcher-1.0.1-windows-portable.zip').tagName).toBe('CODE');
  });
});
