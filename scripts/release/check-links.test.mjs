import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { checkLinks, extractLinks, headingAnchors, slugify, classify, stripCode, probeExternal, main } from './check-links.mjs';

describe('parsing helpers', () => {
  test('slugify follows GitHub rules', () => {
    assert.equal(slugify('Hello World'), 'hello-world');
    assert.equal(slugify('Java 21: why?'), 'java-21-why');
    assert.equal(slugify('`settings.json` & more'), 'settingsjson--more');
    assert.equal(slugify('A [link](x.md) in heading'), 'a-link-in-heading');
  });

  test('headingAnchors dedupes and honours explicit ids', () => {
    const anchors = headingAnchors('---\ntitle: X\n---\n# Intro\n## Step\n## Step\n### Custom {#my-id}\n<a id="raw"></a>\n```\n# not a heading\n```\n');
    assert.deepEqual([...anchors].sort(), ['custom', 'intro', 'my-id', 'raw', 'step', 'step-1']);
  });

  test('extractLinks finds inline, image, reference and autolinks but ignores code', () => {
    const md = [
      'See [docs](./a.md) and ![img](img.png "title") and <https://example.com/x>.',
      '[ref]: https://example.com/ref',
      '`[not](a-link.md)`',
      '```',
      '[also not](code.md)',
      '```',
      '[title with (parens)](<b c.md>)',
    ].join('\n');
    const links = extractLinks(md);
    assert.deepEqual(links.map((l) => [l.target, l.line, l.image]), [
      ['./a.md', 1, false],
      ['img.png', 1, true],
      ['https://example.com/x', 1, false],
      ['https://example.com/ref', 2, false],
      ['b c.md', 7, false],
    ]);
    assert.equal(stripCode('a `b` c').length, 'a `b` c'.length, 'length preserved');
  });

  test('classify', () => {
    assert.equal(classify('https://x.y'), 'external');
    assert.equal(classify('HTTP://x.y'), 'external');
    assert.equal(classify('mailto:a@b.co'), 'mailto');
    assert.equal(classify('ftp://x'), 'other');
    assert.equal(classify('#top'), 'anchor');
    assert.equal(classify('../a.md'), 'relative');
  });
});

describe('checkLinks()', () => {
  function fixture() {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-links-'));
    mkdirSync(join(dir, 'sub'));
    writeFileSync(join(dir, 'a.md'), [
      '---',
      'title: A',
      '---',
      '# Title A',
      '',
      '## Section Two',
      '',
      'Good: [b](b.md), [b anchor](b.md#intro), [self](#section-two), [dir](sub), [sub file](sub/c.md),',
      '[ext](https://example.com/page), [mail](mailto:team@example.com), <https://example.com/auto>.',
      '',
      '```',
      '[ignored](missing-in-code.md)',
      '```',
    ].join('\n'));
    writeFileSync(join(dir, 'b.md'), '# Intro\n\nBack to [a](a.md#title-a).\n');
    writeFileSync(join(dir, 'sub', 'c.md'), '# C\n\n[up](../a.md)\n');
    return dir;
  }

  test('passes for a consistent set of files', async () => {
    const dir = fixture();
    const result = await checkLinks([dir]);
    assert.equal(result.files, 3);
    assert.deepEqual(result.problems, []);
    assert.ok(result.links >= 10);
    assert.equal(result.externalChecked, 0);
  });

  test('reports missing files, bad anchors, root-absolute paths, bad schemes and malformed mailto', async () => {
    const dir = fixture();
    writeFileSync(join(dir, 'bad.md'), [
      '# Bad',
      '[missing](nope.md)',
      '[bad anchor](b.md#nowhere)',
      '[bad self anchor](#nope)',
      '[root](/docs/a.md)',
      '[ftp](ftp://example.com/x)',
      '[mail](mailto:not-an-address)',
      '[frag on png](sub/c.md#c) [img frag](img.png#x)',
      '[bad url](https://[bad)',
    ].join('\n'));
    writeFileSync(join(dir, 'img.png'), 'png');
    const result = await checkLinks([dir]);
    const messages = result.problems.map((p) => `${p.line}:${p.message}`);
    assert.equal(result.problems.length, 8, messages.join('\n'));
    assert.match(messages.find((m) => m.startsWith('2:')), /does not exist/);
    assert.match(messages.find((m) => m.startsWith('3:')), /no heading with anchor #nowhere/);
    assert.match(messages.find((m) => m.startsWith('4:')), /#nope in this file/);
    assert.match(messages.find((m) => m.startsWith('5:')), /root-absolute/);
    assert.match(messages.find((m) => m.startsWith('6:')), /unsupported URL scheme/);
    assert.match(messages.find((m) => m.startsWith('7:')), /malformed mailto/);
    assert.match(messages.find((m) => m.startsWith('8:')), /non-markdown target/);
    assert.match(messages.find((m) => m.startsWith('9:')), /malformed URL/);
  });

  test('--external probes each distinct URL once and attributes failures to every usage', async () => {
    const dir = fixture();
    writeFileSync(join(dir, 'ext.md'), '# E\n[one](https://example.com/page)\n[dead](https://example.com/dead)\n[dead again](https://example.com/dead)\n');
    const calls = [];
    const fetchImpl = async (url, init) => {
      calls.push(`${init.method} ${url}`);
      if (url.endsWith('/dead')) return new Response('', { status: 404 });
      if (url.endsWith('/auto') && init.method === 'HEAD') return new Response('', { status: 405 });
      return new Response('', { status: 200 });
    };
    const result = await checkLinks([dir], { external: true, fetchImpl });
    assert.equal(result.externalChecked, 3);
    assert.equal(result.problems.length, 2);
    assert.ok(result.problems.every((p) => p.target === 'https://example.com/dead' && p.message === 'HTTP 404'));
    assert.equal(calls.filter((c) => c.endsWith('/dead')).length, 1, 'each URL fetched once');
    assert.ok(calls.includes('GET https://example.com/auto'), 'HEAD 405 falls back to GET');
    assert.equal(await probeExternal('https://example.com/x', { fetchImpl: async () => { throw new Error('offline'); } }), 'offline');
  });

  test('CLI exit codes', async () => {
    const dir = fixture();
    const out = [];
    const err = [];
    assert.equal(await main([dir], (m) => out.push(m), (m) => err.push(m)), 0);
    assert.match(out.join('\n'), /3 file\(s\)/);
    writeFileSync(join(dir, 'broken.md'), '[x](missing.md)\n');
    assert.equal(await main([dir, '--quiet'], (m) => out.push(m), (m) => err.push(m)), 1);
    assert.match(err.join('\n'), /broken\.md:1: target does not exist/);
    assert.equal(await main([], () => {}, (m) => err.push(m)), 2);
    assert.equal(await main([join(dir, 'does-not-exist')], () => {}, (m) => err.push(m)), 2);
    assert.equal(await main(['--help'], (m) => out.push(m), () => {}), 0);
  });
});
