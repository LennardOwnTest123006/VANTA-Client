import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { validate, validateWithSchemaFile, run, loadDocument } from './validate-json.mjs';
import { parseFrontMatter, parseScalar } from './lib/front-matter.mjs';
import { REPO_ROOT } from './lib/repo.mjs';

const SCHEMAS = resolve(REPO_ROOT, 'shared', 'schemas');

describe('validate(): keywords', () => {
  test('type, including integer and type arrays', () => {
    assert.equal(validate({ type: 'integer' }, 3).valid, true);
    assert.equal(validate({ type: 'integer' }, 3.5).valid, false);
    assert.equal(validate({ type: ['string', 'null'] }, null).valid, true);
    assert.equal(validate({ type: 'array' }, {}).valid, false);
    assert.equal(validate({ type: 'object' }, []).valid, false);
  });

  test('required and additionalProperties', () => {
    const schema = { type: 'object', required: ['a'], properties: { a: { type: 'string' } }, additionalProperties: false };
    assert.equal(validate(schema, { a: 'x' }).valid, true);
    const missing = validate(schema, {});
    assert.equal(missing.valid, false);
    assert.match(missing.errors[0].message, /missing required property 'a'/);
    const extra = validate(schema, { a: 'x', b: 1 });
    assert.equal(extra.valid, false);
    assert.equal(extra.errors[0].path, '$.b');
  });

  test('additionalProperties as a schema', () => {
    const schema = { type: 'object', additionalProperties: { type: 'number' } };
    assert.equal(validate(schema, { a: 1, b: 2 }).valid, true);
    assert.equal(validate(schema, { a: 'no' }).valid, false);
  });

  test('enum, const, pattern', () => {
    assert.equal(validate({ enum: ['a', 'b'] }, 'b').valid, true);
    assert.equal(validate({ enum: ['a', 'b'] }, 'c').valid, false);
    assert.equal(validate({ const: 1 }, 1).valid, true);
    assert.equal(validate({ const: 1 }, '1').valid, false);
    assert.equal(validate({ type: 'string', pattern: '^[a-f0-9]{4}$' }, 'beef').valid, true);
    assert.equal(validate({ type: 'string', pattern: '^[a-f0-9]{4}$' }, 'BEEF').valid, false);
  });

  test('numeric bounds', () => {
    assert.equal(validate({ minimum: 0 }, -1).valid, false);
    assert.equal(validate({ minimum: 0 }, 0).valid, true);
    assert.equal(validate({ maximum: 10 }, 11).valid, false);
    assert.equal(validate({ exclusiveMinimum: 0 }, 0).valid, false);
    assert.equal(validate({ exclusiveMaximum: 1 }, 1).valid, false);
  });

  test('string length and formats', () => {
    assert.equal(validate({ minLength: 2 }, 'a').valid, false);
    assert.equal(validate({ maxLength: 2 }, 'abc').valid, false);
    assert.equal(validate({ format: 'date' }, '2026-10-04').valid, true);
    assert.equal(validate({ format: 'date' }, '2026-13-40').valid, false);
    assert.equal(validate({ format: 'uri' }, 'https://example.com/x').valid, true);
    assert.equal(validate({ format: 'uri' }, 'not a uri').valid, false);
    assert.equal(validate({ format: 'email' }, 'a@b.co').valid, true);
    assert.equal(validate({ format: 'email' }, 'a@b').valid, false);
    assert.equal(validate({ format: 'date-time' }, '2026-10-04T12:00:00Z').valid, true);
    assert.equal(validate({ format: 'unknown-format' }, 'anything').valid, true);
  });

  test('arrays: items, prefixItems, bounds, uniqueItems', () => {
    assert.equal(validate({ items: { type: 'number' } }, [1, 2]).valid, true);
    const bad = validate({ items: { type: 'number' } }, [1, 'x']);
    assert.equal(bad.valid, false);
    assert.equal(bad.errors[0].path, '$[1]');
    assert.equal(validate({ prefixItems: [{ type: 'string' }], items: { type: 'number' } }, ['a', 1]).valid, true);
    assert.equal(validate({ prefixItems: [{ type: 'string' }], items: { type: 'number' } }, [1, 1]).valid, false);
    assert.equal(validate({ minItems: 1 }, []).valid, false);
    assert.equal(validate({ maxItems: 1 }, [1, 2]).valid, false);
    assert.equal(validate({ uniqueItems: true }, ['a', 'a']).valid, false);
    assert.equal(validate({ uniqueItems: true }, [{ a: 1 }, { a: 2 }]).valid, true);
  });

  test('allOf / anyOf / oneOf / not', () => {
    assert.equal(validate({ allOf: [{ type: 'string' }, { minLength: 2 }] }, 'ab').valid, true);
    assert.equal(validate({ allOf: [{ type: 'string' }, { minLength: 2 }] }, 'a').valid, false);
    assert.equal(validate({ anyOf: [{ type: 'string' }, { type: 'number' }] }, 1).valid, true);
    assert.equal(validate({ anyOf: [{ type: 'string' }, { type: 'number' }] }, true).valid, false);
    assert.equal(validate({ oneOf: [{ type: 'number' }, { minimum: 0 }] }, 1).valid, false);
    assert.equal(validate({ oneOf: [{ type: 'number' }, { type: 'string' }] }, 1).valid, true);
    assert.equal(validate({ not: { type: 'string' } }, 'x').valid, false);
    assert.equal(validate({ not: { type: 'string' } }, 1).valid, true);
  });

  test('if / then / else', () => {
    const schema = {
      type: 'object',
      if: { properties: { kind: { const: 'a' } } },
      then: { required: ['onlyForA'] },
      else: { required: ['onlyForOthers'] },
    };
    assert.equal(validate(schema, { kind: 'a', onlyForA: 1 }).valid, true);
    assert.equal(validate(schema, { kind: 'a' }).valid, false);
    assert.equal(validate(schema, { kind: 'b', onlyForOthers: 1 }).valid, true);
    assert.equal(validate(schema, { kind: 'b' }).valid, false);
  });

  test('local $ref and $defs', () => {
    const schema = {
      type: 'object',
      properties: { color: { $ref: '#/$defs/color' } },
      $defs: { color: { type: 'string', pattern: '^#[0-9A-F]{6}$' } },
    };
    assert.equal(validate(schema, { color: '#FFAA00' }).valid, true);
    assert.equal(validate(schema, { color: 'red' }).valid, false);
    assert.throws(() => validate({ $ref: '#/$defs/missing' }, 1), /Cannot resolve/);
  });

  test('external $ref needs a base directory and resolves sibling files', () => {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-schema-'));
    writeFileSync(join(dir, 'a.json'), JSON.stringify({ $defs: { pos: { type: 'integer', minimum: 1 } } }));
    const schema = { type: 'object', properties: { n: { $ref: 'a.json#/$defs/pos' } } };
    assert.throws(() => validate(schema, { n: 1 }), /without a schema base directory/);
    assert.equal(validate(schema, { n: 1 }, { baseDir: dir }).valid, true);
    assert.equal(validate(schema, { n: 0 }, { baseDir: dir }).valid, false);
    assert.throws(() => validate({ $ref: 'nope.json#/x' }, 1, { baseDir: dir }), /not found/);
  });

  test('boolean schemas', () => {
    assert.equal(validate(true, 'anything').valid, true);
    assert.equal(validate(false, 'anything').valid, false);
  });

  test('error paths point at the offending node', () => {
    const schema = { type: 'object', properties: { files: { type: 'array', items: { type: 'object', properties: { size: { type: 'integer' } } } } } };
    const result = validate(schema, { files: [{ size: 1 }, { size: 'big' }] });
    assert.deepEqual(result.errors.map((e) => e.path), ['$.files[1].size']);
  });
});

describe('repository schemas', () => {
  const releaseSchema = join(SCHEMAS, 'release-manifest.schema.json');

  test('every shared/releases manifest is valid', () => {
    const dir = resolve(REPO_ROOT, 'shared', 'releases');
    const files = readdirSync(dir).filter((f) => f.endsWith('.json'));
    assert.ok(files.length >= 2, 'expected the two initial manifests');
    for (const file of files) {
      const result = validateWithSchemaFile(releaseSchema, JSON.parse(readFileSync(join(dir, file), 'utf8')));
      assert.deepEqual(result.errors, [], `${file} should be valid`);
    }
  });

  test('release manifest: client must target 1.21.11, http URLs and bad digests are rejected', () => {
    const base = JSON.parse(readFileSync(resolve(REPO_ROOT, 'shared', 'releases', 'client-1.0.0.json'), 'utf8'));
    assert.equal(validateWithSchemaFile(releaseSchema, base).valid, true);
    assert.equal(validateWithSchemaFile(releaseSchema, { ...base, minecraftVersion: '1.21.10' }).valid, false);
    assert.equal(validateWithSchemaFile(releaseSchema, { ...base, product: 'launcher', minecraftVersion: '1.21.10' }).valid, true);
    assert.equal(validateWithSchemaFile(releaseSchema, { ...base, version: '1.0' }).valid, false);
    assert.equal(validateWithSchemaFile(releaseSchema, { ...base, javaVersion: 17 }).valid, false);
    assert.equal(validateWithSchemaFile(releaseSchema, { ...base, channel: 'nightly' }).valid, false);
    assert.equal(validateWithSchemaFile(releaseSchema, { ...base, files: [] }).valid, false);
    const withHttp = { ...base, files: [{ ...base.files[0], downloadUrl: 'http://example.com/x.jar' }] };
    assert.equal(validateWithSchemaFile(releaseSchema, withHttp).valid, false);
    const badSha = { ...base, files: [{ ...base.files[0], sha256: 'ABC' }] };
    assert.equal(validateWithSchemaFile(releaseSchema, badSha).valid, false);
    const published = {
      ...base,
      files: [{ name: 'vanta-client-1.0.0.jar', downloadUrl: 'https://github.com/x/y/releases/download/client-v1.0.0/vanta-client-1.0.0.jar', size: 123, sha256: 'a'.repeat(64) }],
    };
    assert.equal(validateWithSchemaFile(releaseSchema, published).valid, true);
    assert.equal(validateWithSchemaFile(releaseSchema, { ...base, unknown: 1 }).valid, false);
  });

  test('built-in HUD presets match hud-preset.schema.json', () => {
    const dir = resolve(REPO_ROOT, 'core', 'src', 'main', 'resources', 'vanta', 'presets', 'hud');
    const files = readdirSync(dir).filter((f) => f.endsWith('.json'));
    assert.ok(files.length >= 5);
    for (const file of files) {
      const result = validateWithSchemaFile(join(SCHEMAS, 'hud-preset.schema.json'), JSON.parse(readFileSync(join(dir, file), 'utf8')));
      assert.deepEqual(result.errors, [], `${file} should be valid`);
    }
  });

  test('profile schema accepts a minimal export and resolves the HUD layout from the sibling schema', () => {
    const crosshair = JSON.parse(readFileSync(resolve(REPO_ROOT, 'core/src/main/resources/vanta/presets/crosshair/default.json'), 'utf8'));
    const profile = {
      schemaVersion: 1,
      id: 'my-profile',
      name: 'My profile',
      icon: 'profile',
      createdAt: 1,
      updatedAt: 2,
      settings: { 'hud.globalScale': 1.0, 'menu.customMainMenu': true, 'general.themeId': 'vanta-dark' },
      hud: { schemaVersion: 1, widgets: [{ id: 'fps', type: 'fps', anchor: 'top_left', offsetX: 4, offsetY: 4 }] },
      keybinds: { 'key.vanta.zoom': 'keyboard:67:key.keyboard.c' },
      crosshair,
      cosmetics: { theme: 'vanta-dark', menuBackground: 'violet_horizon', menuParticles: 'embers', hudTheme: 'clean', badge: 'none', crosshairPreset: 'default' },
    };
    const schema = join(SCHEMAS, 'profile.schema.json');
    assert.deepEqual(validateWithSchemaFile(schema, profile).errors, []);
    const badWidget = { ...profile, hud: { widgets: [{ id: 'fps', type: 'not-a-widget' }] } };
    assert.equal(validateWithSchemaFile(schema, badWidget).valid, false);
    const badSettings = { ...profile, settings: { nested: { a: 1 } } };
    assert.equal(validateWithSchemaFile(schema, badSettings).valid, false);
    assert.equal(validateWithSchemaFile(schema, { ...profile, schemaVersion: 2 }).valid, false);
  });

  test('changelog entries require minecraftVersion 1.21.11 for client and launcher only', () => {
    const schema = join(SCHEMAS, 'changelog-entry.schema.json');
    assert.equal(validateWithSchemaFile(schema, { product: 'client', version: '1.0.0', date: '2026-10-04', minecraftVersion: '1.21.11' }).valid, true);
    assert.equal(validateWithSchemaFile(schema, { product: 'client', version: '1.0.0', date: '2026-10-04' }).valid, false);
    assert.equal(validateWithSchemaFile(schema, { product: 'launcher', version: '1.0.0', date: '2026-10-04', minecraftVersion: '1.20.1' }).valid, false);
    assert.equal(validateWithSchemaFile(schema, { product: 'website', version: '1.0.0', date: '2026-10-04' }).valid, true);
  });

  test('all shared schemas are themselves well-formed JSON objects with $schema', () => {
    for (const file of readdirSync(SCHEMAS).filter((f) => f.endsWith('.json'))) {
      const schema = JSON.parse(readFileSync(join(SCHEMAS, file), 'utf8'));
      assert.equal(schema.$schema, 'https://json-schema.org/draft/2020-12/schema', file);
      assert.equal(typeof schema.title, 'string', file);
    }
  });
});

describe('front matter', () => {
  test('parses scalars, quotes, arrays, numbers and booleans', () => {
    const doc = parseFrontMatter('---\ntitle: "Hello: world"\norder: 3\nratio: 0.5\ndraft: false\ntags: [a, "b c", 7]\nempty:\n---\nBody\n');
    assert.equal(doc.hasFrontMatter, true);
    assert.deepEqual(doc.meta, { title: 'Hello: world', order: 3, ratio: 0.5, draft: false, tags: ['a', 'b c', 7], empty: '' });
    assert.equal(doc.body, 'Body\n');
  });

  test('documents without front matter', () => {
    const doc = parseFrontMatter('# Just markdown\n');
    assert.equal(doc.hasFrontMatter, false);
    assert.deepEqual(doc.meta, {});
    assert.equal(doc.body, '# Just markdown\n');
  });

  test('CRLF line endings and comments', () => {
    const doc = parseFrontMatter('---\r\n# comment\r\ntitle: X\r\n---\r\nbody');
    assert.deepEqual(doc.meta, { title: 'X' });
    assert.equal(doc.body, 'body');
  });

  test('parseScalar edge cases', () => {
    assert.deepEqual(parseScalar('[]'), []);
    assert.equal(parseScalar("'quoted'"), 'quoted');
    assert.equal(parseScalar('-12'), -12);
    assert.equal(parseScalar('1.21.11'), '1.21.11');
  });
});

describe('run(): CLI', () => {
  test('validates JSON files and reports failures with exit code 1', () => {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-validate-'));
    const schema = join(dir, 'schema.json');
    writeFileSync(schema, JSON.stringify({ type: 'object', required: ['a'] }));
    writeFileSync(join(dir, 'good.json'), '{"a":1}');
    writeFileSync(join(dir, 'bad.json'), '{}');
    writeFileSync(join(dir, 'broken.json'), '{');
    const out = [];
    const err = [];
    assert.equal(run([schema, join(dir, 'good.json')], (m) => out.push(m), (m) => err.push(m)), 0);
    assert.match(out.join('\n'), /ok {3}.*good\.json/);
    assert.equal(run([schema, join(dir, 'good.json'), join(dir, 'bad.json'), join(dir, 'broken.json')], (m) => out.push(m), (m) => err.push(m)), 1);
    assert.match(err.join('\n'), /FAIL .*bad\.json/);
    assert.match(err.join('\n'), /missing required property 'a'/);
    assert.match(err.join('\n'), /FAIL .*broken\.json/);
  });

  test('--front-matter validates markdown front matter', () => {
    const dir = mkdtempSync(join(tmpdir(), 'vanta-validate-fm-'));
    const schema = join(dir, 'schema.json');
    writeFileSync(schema, JSON.stringify({ type: 'object', required: ['title', 'order'], properties: { order: { type: 'integer' } } }));
    writeFileSync(join(dir, 'ok.md'), '---\ntitle: T\norder: 2\n---\n# T\n');
    writeFileSync(join(dir, 'missing.md'), '# no front matter\n');
    assert.equal(run(['--front-matter', '--quiet', schema, join(dir, 'ok.md')], () => {}, () => {}), 0);
    const err = [];
    assert.equal(run(['--front-matter', schema, join(dir, 'missing.md')], () => {}, (m) => err.push(m)), 1);
    assert.match(err.join('\n'), /no front matter/);
    assert.deepEqual(loadDocument(join(dir, 'ok.md'), true), { title: 'T', order: 2 });
  });

  test('usage errors exit with 2', () => {
    const err = [];
    assert.equal(run([], () => {}, (m) => err.push(m)), 2);
    assert.equal(run(['--bogus', 'a', 'b'], () => {}, (m) => err.push(m)), 2);
    assert.equal(run(['/definitely/missing.json', 'x.json'], () => {}, (m) => err.push(m)), 2);
    assert.equal(run(['--help'], () => {}, () => {}), 0);
  });
});
