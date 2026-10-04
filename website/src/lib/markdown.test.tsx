import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
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

describe('MarkdownBlock', () => {
  it('lazily loads the renderer', async () => {
    render(<MarkdownBlock source="Hello **world**" />);
    expect(await screen.findByText('world')).toBeInTheDocument();
  });
});
