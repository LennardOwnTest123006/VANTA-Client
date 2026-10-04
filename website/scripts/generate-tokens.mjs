#!/usr/bin/env node
/**
 * Generates `src/styles/tokens.css` from the canonical design tokens in `shared/design/tokens.json`.
 *
 * Usage: `npm run tokens` (the output is committed so the build never depends on this step).
 *
 * Every token becomes a CSS custom property on `:root`. Naming follows the JSON path in kebab-case
 * with a group prefix, e.g. `color.surface.1` → `--color-surface-1`, `radius.lg` → `--radius-lg`,
 * `motion.duration.base` → `--duration-base`. Tailwind 4 picks these up through `@theme` in
 * `src/styles/index.css`.
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const tokensPath = resolve(here, '../../shared/design/tokens.json');
const outPath = resolve(here, '../src/styles/tokens.css');

/** @type {import('./tokens').Tokens} */
const tokens = JSON.parse(readFileSync(tokensPath, 'utf8'));

/** Converts `violetHover` → `violet-hover`, `2xl` stays `2xl`. */
const kebab = (key) => key.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();

/** @type {string[]} */
const lines = [];
const emit = (name, value) => lines.push(`  --${name}: ${value};`);

const px = (n) => `${n}px`;

// Colors -----------------------------------------------------------------------------------
for (const [group, values] of Object.entries(tokens.color)) {
  if (group === 'gradient') continue;
  for (const [key, value] of Object.entries(values)) {
    emit(`color-${kebab(group)}-${kebab(key)}`, value);
  }
}
for (const [key, gradient] of Object.entries(tokens.color.gradient)) {
  emit(`gradient-${kebab(key)}`, `linear-gradient(${gradient.angle}deg, ${gradient.stops.join(', ')})`);
}

// Radii ------------------------------------------------------------------------------------
for (const [key, value] of Object.entries(tokens.radius)) {
  emit(`radius-${key}`, px(value));
}

// Spacing ----------------------------------------------------------------------------------
for (const [key, value] of Object.entries(tokens.spacing)) {
  emit(`space-${key}`, px(value));
}

// Typography -------------------------------------------------------------------------------
emit('font-ui', `"${tokens.typography.family.ui}", system-ui, -apple-system, "Segoe UI", sans-serif`);
emit(
  'font-display',
  `"${tokens.typography.family.display}", "${tokens.typography.family.ui}", system-ui, sans-serif`,
);
emit('font-mono', tokens.typography.family.mono);
for (const [key, value] of Object.entries(tokens.typography.weight)) {
  emit(`font-weight-${key}`, String(value));
}
for (const [key, value] of Object.entries(tokens.typography.size)) {
  emit(`text-${key}`, px(value));
}
for (const [key, value] of Object.entries(tokens.typography.tracking)) {
  emit(`tracking-${key}`, value);
}
for (const [key, value] of Object.entries(tokens.typography.leading)) {
  emit(`leading-${key}`, String(value));
}

// Shadows ----------------------------------------------------------------------------------
for (const [key, value] of Object.entries(tokens.shadow)) {
  emit(`shadow-${key}`, value);
}

// Motion -----------------------------------------------------------------------------------
for (const [key, value] of Object.entries(tokens.motion.duration)) {
  emit(`duration-${key}`, `${value}ms`);
}
for (const [key, value] of Object.entries(tokens.motion.easing)) {
  emit(`ease-${key}`, value);
}

const header = `/*
 * GENERATED FILE — do not edit by hand.
 * Source: shared/design/tokens.json (${tokens.name} v${tokens.version})
 * Regenerate with: npm run tokens
 */
`;

const css = `${header}:root {\n${lines.join('\n')}\n}\n`;
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, css, 'utf8');
process.stdout.write(`tokens: wrote ${lines.length} custom properties to ${outPath}\n`);
