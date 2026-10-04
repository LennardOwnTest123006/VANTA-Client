#!/usr/bin/env node
/**
 * Renders the VANTA brand SVGs (assets/brand) into every raster format the products need.
 *
 *   node scripts/brand/generate-icons.mjs
 *
 * Outputs (all tracked in git so builds never need this script):
 *   client/src/main/resources/assets/vanta/icon.png          128x128  Fabric mod icon
 *   client/src/main/resources/assets/vanta/textures/gui/logo.png  256x256 in-game logo (mark only)
 *   client/src/main/resources/assets/vanta/textures/gui/wordmark.png 512x128 in-game wordmark
 *   launcher/src/main/resources/dev/vanta/launcher/ui/icon-{16..512}.png  window icons
 *   launcher/packaging/icon.ico                              Windows application icon
 *   website/public/favicon.svg, favicon-32.png, apple-touch-icon.png (180), icon-192.png, icon-512.png
 *   assets/brand/png/vanta-mark-{64,256,1024}.png, vanta-app-icon-{256,1024}.png
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const brand = resolve(root, 'assets/brand');

const markSvg = await readFile(resolve(brand, 'vanta-mark.svg'));
const appIconSvg = await readFile(resolve(brand, 'vanta-app-icon.svg'));
const wordmarkSvg = await readFile(resolve(brand, 'vanta-wordmark.svg'));

async function png(svg, size, out, { width = size, height = size } = {}) {
  await mkdir(dirname(out), { recursive: true });
  const buf = await sharp(svg, { density: Math.max(72, (Math.max(width, height) / 64) * 72 * 1.5) })
    .resize(width, height, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png({ compressionLevel: 9 })
    .toBuffer();
  await writeFile(out, buf);
  console.log('wrote', out.replace(root + '/', ''), `${width}x${height}`);
  return buf;
}

/** Builds a Windows .ico from PNG buffers (PNG-compressed entries, supported since Windows Vista). */
function ico(entries) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(entries.length, 4);
  const dir = Buffer.alloc(16 * entries.length);
  let offset = 6 + dir.length;
  entries.forEach(({ size, buf }, i) => {
    const o = i * 16;
    dir.writeUInt8(size >= 256 ? 0 : size, o);
    dir.writeUInt8(size >= 256 ? 0 : size, o + 1);
    dir.writeUInt8(0, o + 2); // palette
    dir.writeUInt8(0, o + 3); // reserved
    dir.writeUInt16LE(1, o + 4); // planes
    dir.writeUInt16LE(32, o + 6); // bpp
    dir.writeUInt32LE(buf.length, o + 8);
    dir.writeUInt32LE(offset, o + 12);
    offset += buf.length;
  });
  return Buffer.concat([header, dir, ...entries.map((e) => e.buf)]);
}

// Client (Fabric mod)
await png(appIconSvg, 128, resolve(root, 'client/src/main/resources/assets/vanta/icon.png'));
await png(markSvg, 256, resolve(root, 'client/src/main/resources/assets/vanta/textures/gui/logo.png'));
await png(wordmarkSvg, 0, resolve(root, 'client/src/main/resources/assets/vanta/textures/gui/wordmark.png'), { width: 512, height: 128 });

// Launcher
const launcherIcons = [];
for (const size of [16, 24, 32, 48, 64, 128, 256, 512]) {
  const buf = await png(appIconSvg, size, resolve(root, `launcher/src/main/resources/dev/vanta/launcher/ui/icon-${size}.png`));
  if (size <= 256) launcherIcons.push({ size, buf });
}
await mkdir(resolve(root, 'launcher/packaging'), { recursive: true });
await writeFile(resolve(root, 'launcher/packaging/icon.ico'), ico(launcherIcons));
console.log('wrote launcher/packaging/icon.ico', launcherIcons.map((e) => e.size).join(','));
await png(appIconSvg, 512, resolve(root, 'launcher/packaging/icon-512.png'));

// Website
await mkdir(resolve(root, 'website/public'), { recursive: true });
await writeFile(resolve(root, 'website/public/favicon.svg'), markSvg);
await png(appIconSvg, 32, resolve(root, 'website/public/favicon-32.png'));
await png(appIconSvg, 180, resolve(root, 'website/public/apple-touch-icon.png'));
await png(appIconSvg, 192, resolve(root, 'website/public/icon-192.png'));
await png(appIconSvg, 512, resolve(root, 'website/public/icon-512.png'));
await writeFile(resolve(root, 'website/public/vanta-mark.svg'), markSvg);
await writeFile(resolve(root, 'website/public/vanta-wordmark.svg'), wordmarkSvg);

// Brand kit
for (const size of [64, 256, 1024]) await png(markSvg, size, resolve(brand, `png/vanta-mark-${size}.png`));
for (const size of [256, 1024]) await png(appIconSvg, size, resolve(brand, `png/vanta-app-icon-${size}.png`));
await png(wordmarkSvg, 0, resolve(brand, 'png/vanta-wordmark-1024.png'), { width: 1024, height: 256 });
console.log('done');
