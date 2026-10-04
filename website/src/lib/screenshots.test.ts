import { describe, expect, it } from 'vitest';
import { captionFromFileName, loadScreenshots, parseCaptions, screenshots } from './screenshots';

describe('captionFromFileName', () => {
  it('humanises numbered file names', () => {
    expect(captionFromFileName('01_main_menu.png')).toBe('Main menu');
    expect(captionFromFileName('20-hud-ingame.png')).toBe('Hud ingame');
    expect(captionFromFileName('x.png')).toBe('X');
  });
});

describe('parseCaptions', () => {
  it('accepts the documented shape and ignores junk', () => {
    expect(
      parseCaptions({
        'a.png': { caption: 'A', alt: 'alt a', capturedWith: 'client 1.0.0' },
        'b.png': { caption: 7 },
        'c.png': 'nope',
      }),
    ).toEqual({
      'a.png': { caption: 'A', alt: 'alt a', capturedWith: 'client 1.0.0' },
      'b.png': {},
    });
    expect(parseCaptions(undefined)).toEqual({});
    expect(parseCaptions([1])).toEqual({});
  });
});

describe('loadScreenshots', () => {
  it('joins images with captions, sorted by file name, with sensible fallbacks', () => {
    const items = loadScreenshots(
      {
        '/assets/screenshots/02_settings.png': '/assets/b.png',
        '/assets/screenshots/01_main_menu.png': '/assets/a.png',
      },
      { '01_main_menu.png': { caption: 'The main menu', alt: 'Dark menu' } },
    );
    expect(items).toEqual([
      {
        file: '01_main_menu.png',
        src: '/assets/a.png',
        caption: 'The main menu',
        alt: 'Dark menu',
        capturedWith: undefined,
      },
      {
        file: '02_settings.png',
        src: '/assets/b.png',
        caption: 'Settings',
        alt: 'Settings',
        capturedWith: undefined,
      },
    ]);
  });
  it('reflects the repository state honestly (no files means no screenshots)', () => {
    for (const item of screenshots) expect(item.file.endsWith('.png')).toBe(true);
  });
});
