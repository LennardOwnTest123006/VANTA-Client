#!/usr/bin/env node
/**
 * Dependency-free JSON Schema validator (draft 2020-12 subset) for the VANTA repository.
 *
 *   node scripts/release/validate-json.mjs <schema.json> <file.json> [more files...]
 *   node scripts/release/validate-json.mjs --front-matter <schema.json> <file.md> [more files...]
 *
 * Exit code 0 when every file is valid, 1 when at least one is invalid, 2 on usage errors.
 *
 * Supported keywords (enough for shared/schemas/*.json): type (incl. "integer" and arrays of types),
 * required, properties, additionalProperties (boolean or schema), items, prefixItems, enum, const,
 * pattern, minimum, maximum, exclusiveMinimum, exclusiveMaximum, minLength, maxLength, minItems,
 * maxItems, uniqueItems, format (date, date-time, uri, email), $ref (same document "#/..." and sibling
 * files "name.schema.json#/..."), $defs/definitions, allOf, anyOf, oneOf, not, if/then/else.
 * Unknown keywords are ignored, as the specification requires.
 */
import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs, exitOnUsageErrors } from './lib/args.mjs';
import { parseFrontMatter } from './lib/front-matter.mjs';

const USAGE = `Usage: node scripts/release/validate-json.mjs [--front-matter] [--quiet] <schema.json> <file...>

  --front-matter   validate the YAML front matter of markdown files instead of JSON documents
  --quiet          print failures only`;

/**
 * @typedef {{ path: string, message: string }} ValidationError
 * @typedef {{ valid: boolean, errors: ValidationError[] }} ValidationResult
 */

/**
 * Validates `data` against `schema`.
 *
 * @param {object|boolean} schema      schema document
 * @param {unknown} data               JSON value
 * @param {{ baseDir?: string, loader?: (file: string) => object }} [options]
 *        `baseDir` is needed to resolve `$ref`s to sibling schema files
 * @returns {ValidationResult}
 */
export function validate(schema, data, options = {}) {
  const context = {
    root: schema,
    baseDir: options.baseDir,
    loader: options.loader ?? defaultLoader,
    externals: new Map(),
    errors: [],
  };
  validateNode(schema, data, '$', context, schema);
  return { valid: context.errors.length === 0, errors: context.errors };
}

/**
 * Loads a schema file and validates `data` against it (resolving sibling `$ref`s).
 * @param {string} schemaPath
 * @param {unknown} data
 */
export function validateWithSchemaFile(schemaPath, data) {
  const schema = JSON.parse(readFileSync(schemaPath, 'utf8'));
  return validate(schema, data, { baseDir: dirname(resolve(schemaPath)) });
}

function defaultLoader(file) {
  return JSON.parse(readFileSync(file, 'utf8'));
}

function typeOf(value) {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'array';
  return typeof value;
}

function matchesType(expected, value) {
  const actual = typeOf(value);
  if (expected === 'integer') return actual === 'number' && Number.isInteger(value);
  if (expected === 'number') return actual === 'number';
  return expected === actual;
}

function fail(context, path, message) {
  context.errors.push({ path, message });
}

function deepEqual(a, b) {
  if (a === b) return true;
  if (typeOf(a) !== typeOf(b)) return false;
  if (Array.isArray(a)) {
    return a.length === b.length && a.every((item, i) => deepEqual(item, b[i]));
  }
  if (typeOf(a) === 'object') {
    const keysA = Object.keys(a);
    const keysB = Object.keys(b);
    return keysA.length === keysB.length && keysA.every((key) => deepEqual(a[key], b[key]));
  }
  return false;
}

/** Resolves a JSON pointer like `#/$defs/x` inside `document`. */
function resolvePointer(document, pointer) {
  if (pointer === '' || pointer === '#') return document;
  const parts = pointer.replace(/^#\/?/, '').split('/').filter((p) => p !== '');
  let node = document;
  for (const rawPart of parts) {
    const part = rawPart.replace(/~1/g, '/').replace(/~0/g, '~');
    if (node === null || typeof node !== 'object' || !(part in node)) {
      throw new Error(`Cannot resolve $ref pointer '${pointer}'`);
    }
    node = node[part];
  }
  return node;
}

/** Resolves `$ref` to a {schema, document} pair. `document` becomes the new root for nested refs. */
function resolveRef(ref, context, document) {
  const hashIndex = ref.indexOf('#');
  const file = hashIndex >= 0 ? ref.slice(0, hashIndex) : ref;
  const pointer = hashIndex >= 0 ? ref.slice(hashIndex) : '#';
  if (file === '') return { schema: resolvePointer(document, pointer), document };
  if (/^https?:\/\//.test(file)) {
    // Remote $id-based references: look for a sibling file with the same basename.
    return resolveRef(`${basename(file)}${pointer}`, context, document);
  }
  if (!context.baseDir) {
    throw new Error(`Cannot resolve external $ref '${ref}' without a schema base directory`);
  }
  const path = resolve(context.baseDir, file);
  if (!context.externals.has(path)) {
    if (!existsSync(path)) throw new Error(`Referenced schema file not found: ${path}`);
    context.externals.set(path, context.loader(path));
  }
  const external = context.externals.get(path);
  return { schema: resolvePointer(external, pointer), document: external };
}

function checkFormat(format, value) {
  switch (format) {
    case 'date': {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
      const date = new Date(`${value}T00:00:00Z`);
      return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
    }
    case 'date-time':
      return !Number.isNaN(new Date(value).getTime());
    case 'uri':
      try {
        const url = new URL(value);
        return url.protocol !== '';
      } catch {
        return false;
      }
    case 'email':
      return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    default:
      return true; // unknown formats are annotations only
  }
}

/**
 * @param {object|boolean} schema
 * @param {unknown} data
 * @param {string} path JSON path for messages
 * @param {object} context
 * @param {object} document the schema document `schema` belongs to (for local $ref)
 */
function validateNode(schema, data, path, context, document) {
  if (schema === true) return;
  if (schema === false) {
    fail(context, path, 'schema forbids any value');
    return;
  }
  if (schema === null || typeof schema !== 'object') {
    throw new Error(`Invalid schema at ${path}: expected an object or boolean`);
  }

  if (typeof schema.$ref === 'string') {
    const target = resolveRef(schema.$ref, context, document);
    validateNode(target.schema, data, path, context, target.document);
  }

  if (schema.type !== undefined) {
    const types = Array.isArray(schema.type) ? schema.type : [schema.type];
    if (!types.some((t) => matchesType(t, data))) {
      fail(context, path, `expected type ${types.join(' | ')}, got ${typeOf(data)}`);
      return; // further keyword checks assume the right type
    }
  }

  if (schema.enum !== undefined && !schema.enum.some((candidate) => deepEqual(candidate, data))) {
    fail(context, path, `must be one of ${JSON.stringify(schema.enum)}, got ${JSON.stringify(data)}`);
  }
  if (schema.const !== undefined && !deepEqual(schema.const, data)) {
    fail(context, path, `must equal ${JSON.stringify(schema.const)}, got ${JSON.stringify(data)}`);
  }

  if (typeof data === 'string') {
    if (schema.minLength !== undefined && [...data].length < schema.minLength) {
      fail(context, path, `must be at least ${schema.minLength} characters`);
    }
    if (schema.maxLength !== undefined && [...data].length > schema.maxLength) {
      fail(context, path, `must be at most ${schema.maxLength} characters`);
    }
    if (schema.pattern !== undefined && !new RegExp(schema.pattern, 'u').test(data)) {
      fail(context, path, `must match pattern ${schema.pattern}, got ${JSON.stringify(data)}`);
    }
    if (schema.format !== undefined && !checkFormat(schema.format, data)) {
      fail(context, path, `is not a valid ${schema.format}: ${JSON.stringify(data)}`);
    }
  }

  if (typeof data === 'number') {
    if (schema.minimum !== undefined && data < schema.minimum) {
      fail(context, path, `must be >= ${schema.minimum}, got ${data}`);
    }
    if (schema.maximum !== undefined && data > schema.maximum) {
      fail(context, path, `must be <= ${schema.maximum}, got ${data}`);
    }
    if (schema.exclusiveMinimum !== undefined && data <= schema.exclusiveMinimum) {
      fail(context, path, `must be > ${schema.exclusiveMinimum}, got ${data}`);
    }
    if (schema.exclusiveMaximum !== undefined && data >= schema.exclusiveMaximum) {
      fail(context, path, `must be < ${schema.exclusiveMaximum}, got ${data}`);
    }
  }

  if (Array.isArray(data)) {
    if (schema.minItems !== undefined && data.length < schema.minItems) {
      fail(context, path, `must have at least ${schema.minItems} items`);
    }
    if (schema.maxItems !== undefined && data.length > schema.maxItems) {
      fail(context, path, `must have at most ${schema.maxItems} items`);
    }
    if (schema.uniqueItems === true) {
      for (let i = 0; i < data.length; i += 1) {
        for (let j = i + 1; j < data.length; j += 1) {
          if (deepEqual(data[i], data[j])) {
            fail(context, `${path}[${j}]`, 'duplicate item');
          }
        }
      }
    }
    const prefix = Array.isArray(schema.prefixItems) ? schema.prefixItems : [];
    data.forEach((item, index) => {
      if (index < prefix.length) {
        validateNode(prefix[index], item, `${path}[${index}]`, context, document);
      } else if (schema.items !== undefined) {
        validateNode(schema.items, item, `${path}[${index}]`, context, document);
      }
    });
  }

  if (typeOf(data) === 'object') {
    for (const key of schema.required ?? []) {
      if (!(key in data)) fail(context, path, `missing required property '${key}'`);
    }
    const properties = schema.properties ?? {};
    for (const [key, value] of Object.entries(data)) {
      const childPath = `${path}.${key}`;
      if (key in properties) {
        validateNode(properties[key], value, childPath, context, document);
      } else if (schema.additionalProperties === false) {
        fail(context, childPath, 'unexpected property');
      } else if (schema.additionalProperties !== undefined && schema.additionalProperties !== true) {
        validateNode(schema.additionalProperties, value, childPath, context, document);
      }
    }
  }

  for (const sub of schema.allOf ?? []) validateNode(sub, data, path, context, document);

  if (Array.isArray(schema.anyOf)) {
    const matched = schema.anyOf.some((sub) => validateQuietly(sub, data, path, context, document));
    if (!matched) fail(context, path, 'does not match any of the allowed alternatives');
  }
  if (Array.isArray(schema.oneOf)) {
    const matches = schema.oneOf.filter((sub) => validateQuietly(sub, data, path, context, document)).length;
    if (matches !== 1) fail(context, path, `must match exactly one alternative, matched ${matches}`);
  }
  if (schema.not !== undefined && validateQuietly(schema.not, data, path, context, document)) {
    fail(context, path, 'must not match the forbidden schema');
  }
  if (schema.if !== undefined) {
    const condition = validateQuietly(schema.if, data, path, context, document);
    if (condition && schema.then !== undefined) validateNode(schema.then, data, path, context, document);
    if (!condition && schema.else !== undefined) validateNode(schema.else, data, path, context, document);
  }
}

/** Validates without recording errors; returns true when the sub-schema matches. */
function validateQuietly(schema, data, path, context, document) {
  const saved = context.errors;
  context.errors = [];
  validateNode(schema, data, path, context, document);
  const ok = context.errors.length === 0;
  context.errors = saved;
  return ok;
}

/**
 * Loads the document to validate: JSON, or the front matter of a markdown file.
 * @param {string} file
 * @param {boolean} frontMatter
 */
export function loadDocument(file, frontMatter) {
  const text = readFileSync(file, 'utf8');
  if (!frontMatter) return JSON.parse(text);
  const parsed = parseFrontMatter(text);
  if (!parsed.hasFrontMatter) throw new Error('file has no front matter block');
  return parsed.meta;
}

/**
 * Validates every file and prints a report.
 * @returns {number} process exit code
 */
export function run(argv, log = console.log, logError = console.error) {
  const parsed = parseArgs(argv, { flags: ['front-matter', 'quiet', 'help'], aliases: { h: 'help', q: 'quiet' } });
  if (parsed.flags.has('help')) {
    log(USAGE);
    return 0;
  }
  if (parsed.errors.length > 0 || parsed.positional.length < 2) {
    if (parsed.positional.length < 2) parsed.errors.push('expected a schema and at least one file');
    for (const error of parsed.errors) logError(`error: ${error}`);
    logError(USAGE);
    return 2;
  }
  const [schemaPath, ...files] = parsed.positional;
  const quiet = parsed.flags.has('quiet');
  const frontMatter = parsed.flags.has('front-matter');
  let schema;
  try {
    schema = JSON.parse(readFileSync(schemaPath, 'utf8'));
  } catch (error) {
    logError(`error: cannot read schema ${schemaPath}: ${error.message}`);
    return 2;
  }
  const baseDir = dirname(resolve(schemaPath));
  let failures = 0;
  for (const file of files) {
    let data;
    try {
      data = loadDocument(file, frontMatter);
    } catch (error) {
      failures += 1;
      logError(`FAIL ${file}: ${error.message}`);
      continue;
    }
    const result = validate(schema, data, { baseDir });
    if (result.valid) {
      if (!quiet) log(`ok   ${file}`);
    } else {
      failures += 1;
      logError(`FAIL ${file}`);
      for (const error of result.errors) logError(`     ${error.path}: ${error.message}`);
    }
  }
  const total = files.length;
  if (!quiet || failures > 0) {
    log(`${total - failures}/${total} file(s) valid against ${basename(schemaPath)}`);
  }
  return failures === 0 ? 0 : 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  exitOnUsageErrors({ errors: [] }, USAGE);
  process.exit(run(process.argv.slice(2)));
}
