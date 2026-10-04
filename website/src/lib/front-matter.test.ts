import { describe, expect, it } from 'vitest';
import {
  ContentError,
  optionalBoolean,
  optionalList,
  optionalString,
  parseFrontMatterTyped,
  parseInlineArray,
  parseScalar,
  requireDate,
  requireInteger,
  requireString,
} from './front-matter';

describe('parseFrontMatterTyped', () => {
  it('parses strings, integers, booleans and inline arrays', () => {
    const doc = parseFrontMatterTyped(
      [
        '---',
        'title: "Hello: world"',
        "author: 'VANTA team'",
        'order: 12',
        'draft: true',
        'tags: [announcement, client, "two words"]',
        '# a comment line',
        'empty:',
        '---',
        '',
        '# Body',
        '',
      ].join('\n'),
    );
    expect(doc.hasFrontMatter).toBe(true);
    expect(doc.values).toEqual({
      title: 'Hello: world',
      author: 'VANTA team',
      order: 12,
      draft: true,
      tags: ['announcement', 'client', 'two words'],
      empty: '',
    });
    expect(doc.raw.title).toBe('Hello: world');
    expect(doc.raw.order).toBe('12');
    expect(doc.body).toBe('# Body');
  });

  it('treats documents without a block as body only and normalises CRLF and BOM', () => {
    const doc = parseFrontMatterTyped('﻿Just text\r\nmore\r\n');
    expect(doc.hasFrontMatter).toBe(false);
    expect(doc.values).toEqual({});
    expect(doc.body).toBe('Just text\nmore');
  });

  it('keeps dates and versions as strings', () => {
    const doc = parseFrontMatterTyped('---\ndate: 2026-10-04\nversion: 1.0.0\nmc: 1.21.11\n---\n');
    expect(doc.values).toEqual({ date: '2026-10-04', version: '1.0.0', mc: '1.21.11' });
  });

  it('ignores a block that is not closed', () => {
    const doc = parseFrontMatterTyped('---\ntitle: x\nno end');
    expect(doc.hasFrontMatter).toBe(false);
    expect(doc.body).toBe('---\ntitle: x\nno end');
  });
});

describe('parseInlineArray / parseScalar', () => {
  it('splits on commas outside quotes and drops empty items', () => {
    expect(parseInlineArray('[a, "b, c", d, ]')).toEqual(['a', 'b, c', 'd']);
    expect(parseInlineArray('[]')).toEqual([]);
  });
  it('converts scalars', () => {
    expect(parseScalar(' 42 ')).toBe(42);
    expect(parseScalar('-1')).toBe(-1);
    expect(parseScalar('1.5')).toBe('1.5');
    expect(parseScalar('false')).toBe(false);
    expect(parseScalar('"true"')).toBe('true');
  });
});

describe('typed accessors', () => {
  const meta = parseFrontMatterTyped(
    '---\ntitle: T\nnum: 3\ndate: 2026-01-02\nbad: 2026-1-2\ntags: [a]\nsingle: x\nflag: true\n---\n',
  );
  it('returns values of the right type', () => {
    expect(requireString(meta, 'title', 'f')).toBe('T');
    expect(requireString(meta, 'num', 'f')).toBe('3');
    expect(requireInteger(meta, 'num', 'f')).toBe(3);
    expect(requireDate(meta, 'date', 'f')).toBe('2026-01-02');
    expect(optionalList(meta, 'tags')).toEqual(['a']);
    expect(optionalList(meta, 'single')).toEqual(['x']);
    expect(optionalList(meta, 'missing')).toEqual([]);
    expect(optionalBoolean(meta, 'flag')).toBe(true);
    expect(optionalBoolean(meta, 'missing')).toBe(false);
    expect(optionalString(meta, 'missing')).toBeUndefined();
  });
  it('throws ContentError naming the file and field', () => {
    expect(() => requireString(meta, 'missing', 'post.md')).toThrow(ContentError);
    expect(() => requireString(meta, 'missing', 'post.md')).toThrow(/post\.md.*"missing"/);
    expect(() => requireInteger(meta, 'title', 'f')).toThrow(/non-negative integer/);
    expect(() => requireDate(meta, 'bad', 'f')).toThrow(/YYYY-MM-DD/);
  });
});
